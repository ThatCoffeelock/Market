package com.thatcoffeelock.flintlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Right-clicking with a gun: fires it if it's loaded, otherwise starts reloading it.
 * <p>
 * Reloading takes the gun's reload time and only carries on while you keep holding that same gun. Switch away and
 * it's interrupted (the ammo is only used up when the reload finishes). A gun stays loaded until it's fired, so you
 * can load a few and carry them, like a proper pirate.
 */
final class Guns {
	private static final Map<UUID, Reload> RELOADING = new HashMap<>();

	private Guns() {
	}

	private static final class Reload {
		final ServerPlayer player;
		final InteractionHand hand;
		final ItemStack stack;
		final Gun gun;
		int ticks;

		Reload(ServerPlayer player, InteractionHand hand, ItemStack stack, Gun gun) {
			this.player = player;
			this.hand = hand;
			this.stack = stack;
			this.gun = gun;
		}
	}

	// ---------------------------------------------------------------- using

	static InteractionResult use(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Gun gun = GunItems.gunOf(stack);
		if (gun == null) {
			return InteractionResult.PASS;
		}
		if (RELOADING.containsKey(player.getUUID())) {
			return InteractionResult.SUCCESS; // busy with the ramrod
		}
		if (GunItems.isLoaded(stack)) {
			shoot(player, hand, stack, gun);
			return InteractionResult.SUCCESS;
		}
		if (!player.isCreative() && GunItems.countAmmo(player, gun.ammo) == 0) {
			// an empty pistol in the main hand shouldn't stop a loaded one in the other hand from firing
			if (hand == InteractionHand.MAIN_HAND && isLoadedGun(player.getOffhandItem())) {
				return InteractionResult.PASS;
			}
			ServerLevel level = (ServerLevel) player.level();
			Cmd.sound(level, "minecraft:block.dispenser.fail", player.getX(), player.getEyeY(), player.getZ(), 0.6f, 1.6f);
			actionBar(player, Component.literal("No " + gun.ammo.title + ". " + recipeHint(gun.ammo)).withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		startReload(player, hand, stack, gun);
		return InteractionResult.SUCCESS;
	}

	private static boolean isLoadedGun(ItemStack stack) {
		return GunItems.gunOf(stack) != null && GunItems.isLoaded(stack);
	}

	static String recipeHint(Gun.Ammo ammo) {
		return switch (ammo) {
			case CARTRIDGE -> "Craft them: paper + gunpowder + iron nugget.";
			case SCATTERSHOT -> "Craft it: paper + gunpowder + flint + gravel.";
		};
	}

	/** Bang. The gun is empty afterwards. */
	static void shoot(ServerPlayer player, InteractionHand hand, ItemStack stack, Gun gun) {
		ServerLevel level = (ServerLevel) player.level();
		GunItems.setLoaded(stack, gun, false);
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		Shot.fire(level, gun, eye.add(look.scale(0.3)), look, player, true);
		muzzle(level, gun, eye.add(look.scale(1.0)));
		// after firing, so the last shot still goes off. Vanilla handles Unbreaking, creative and the breaking sound
		stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		if (gun.recoil > 0) {
			player.setDeltaMovement(player.getDeltaMovement().add(look.scale(-gun.recoil)));
			player.connection.send(new ClientboundSetEntityMotionPacket(player)); // players move themselves: tell their client
		}
	}

	/** The bang, the flash and the cloud of smoke you can't see through for a second. */
	static void muzzle(ServerLevel level, Gun gun, Vec3 at) {
		Cmd.sound(level, "minecraft:entity.generic.explode", at.x, at.y, at.z, 1.2f, gun.pitch);
		Cmd.sound(level, "minecraft:entity.firework_rocket.blast", at.x, at.y, at.z, 1.5f, gun.pitch * 0.8f);
		Cmd.particles(level, "minecraft:flame", at.x, at.y, at.z, 0.05, 0.03, 4);
		Cmd.particles(level, "minecraft:large_smoke", at.x, at.y, at.z, 0.15, 0.02, gun.pellets > 1 ? 14 : 6);
		Cmd.particles(level, "minecraft:poof", at.x, at.y, at.z, 0.1, 0.02, 4);
	}

	// ---------------------------------------------------------------- reloading

	private static void startReload(ServerPlayer player, InteractionHand hand, ItemStack stack, Gun gun) {
		RELOADING.put(player.getUUID(), new Reload(player, hand, stack, gun));
		ServerLevel level = (ServerLevel) player.level();
		Cmd.sound(level, "minecraft:item.crossbow.loading_start", player.getX(), player.getEyeY(), player.getZ(), 0.8f, 0.8f);
		progress(player, gun, 0);
	}

	static void tick() {
		if (RELOADING.isEmpty()) {
			return;
		}
		for (Reload reload : new ArrayList<>(RELOADING.values())) {
			try {
				tickReload(reload);
			} catch (RuntimeException e) {
				FlintlockMod.LOG.error("Reloading crashed for {}", reload.player.getName().getString(), e);
				RELOADING.remove(reload.player.getUUID());
			}
		}
	}

	private static void tickReload(Reload reload) {
		ServerPlayer player = reload.player;
		if (player.isRemoved() || !player.isAlive()) {
			RELOADING.remove(player.getUUID());
			return;
		}
		if (player.getItemInHand(reload.hand) != reload.stack) {
			RELOADING.remove(player.getUUID());
			actionBar(player, Component.literal("Reload interrupted. Hold the gun until it's done.").withStyle(ChatFormatting.YELLOW));
			return;
		}
		reload.ticks++;
		ServerLevel level = (ServerLevel) player.level();
		if (reload.ticks == reload.gun.reloadTicks / 2) {
			Cmd.sound(level, "minecraft:item.crossbow.loading_middle", player.getX(), player.getEyeY(), player.getZ(), 0.8f, 0.8f);
		}
		if (reload.ticks < reload.gun.reloadTicks) {
			if (reload.ticks % 2 == 0) {
				progress(player, reload.gun, reload.ticks);
			}
			return;
		}
		RELOADING.remove(player.getUUID());
		if (!player.isCreative() && !GunItems.takeAmmo(player, reload.gun.ammo)) {
			actionBar(player, Component.literal("Your " + reload.gun.ammo.title + " went missing halfway through. Impressive.").withStyle(ChatFormatting.RED));
			return;
		}
		GunItems.setLoaded(reload.stack, reload.gun, true);
		Cmd.sound(level, "minecraft:item.crossbow.loading_end", player.getX(), player.getEyeY(), player.getZ(), 0.9f, 0.8f);
		Cmd.sound(level, "minecraft:block.iron_trapdoor.close", player.getX(), player.getEyeY(), player.getZ(), 0.4f, 1.8f);
		actionBar(player, Component.literal(reload.gun.title + " loaded. ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal(ammoLeft(player, reload.gun.ammo)).withStyle(ChatFormatting.GRAY)));
	}

	private static String ammoLeft(ServerPlayer player, Gun.Ammo ammo) {
		return player.isCreative() ? "" : GunItems.countAmmo(player, ammo) + " " + ammo.title + " left.";
	}

	private static void progress(ServerPlayer player, Gun gun, int ticks) {
		int done = ticks * 10 / gun.reloadTicks;
		MutableComponent line = Component.literal("Reloading " + gun.title + " ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("█".repeat(done)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("█".repeat(10 - done)).withStyle(ChatFormatting.DARK_GRAY));
		actionBar(player, line);
	}

	private static void actionBar(ServerPlayer player, Component line) {
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	static void onDisconnect(ServerPlayer player) {
		RELOADING.remove(player.getUUID());
	}

	static void clear() {
		RELOADING.clear();
	}
}
