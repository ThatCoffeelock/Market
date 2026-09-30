package com.thatcoffeelock.cannon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Keeps track of every loaded cannon and routes clicks, ticks and chunk loads to it. */
public final class Cannons {
	private static final Map<UUID, Cannon> BY_ROOT = new HashMap<>();
	private static final Map<UUID, Cannon> BY_SEAT = new HashMap<>();
	private static final List<Entity> PENDING = new ArrayList<>();
	private static final List<Entity> ORPHANS = new ArrayList<>();

	private Cannons() {
	}

	// ---------------------------------------------------------------- registry

	static Cannon register(ServerLevel level, Entity root) {
		Cannon existing = BY_ROOT.get(root.getUUID());
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		CannonData data = root.getAttached(CannonMod.DATA);
		if (data == null) {
			data = new CannonData();
			root.setAttached(CannonMod.DATA, data);
		}
		Cannon cannon = new Cannon(level, root, data);
		BY_ROOT.put(root.getUUID(), cannon);
		return cannon;
	}

	static void registerSeat(Entity seat, Cannon cannon) {
		BY_SEAT.put(seat.getUUID(), cannon);
	}

	static void unregister(Cannon cannon) {
		BY_ROOT.remove(cannon.root.getUUID(), cannon);
		if (cannon.seat != null) {
			BY_SEAT.remove(cannon.seat.getUUID(), cannon);
		}
	}

	/** The cannon this player is manning, if any. */
	static @Nullable Cannon mannedBy(Entity entity) {
		Entity seat = entity.getVehicle();
		Cannon cannon = seat == null ? null : BY_SEAT.get(seat.getUUID());
		return cannon == null || cannon.isRemoved() ? null : cannon;
	}

	static List<Cannon> all() {
		return new ArrayList<>(BY_ROOT.values());
	}

	// ---------------------------------------------------------------- lifecycle

	static void onLoad(Entity entity, ServerLevel level) {
		if (entity.hasAttached(CannonMod.DATA)) {
			PENDING.add(entity);
		} else if (entity.hasAttached(CannonMod.SEAT) && !BY_SEAT.containsKey(entity.getUUID())) {
			ORPHANS.add(entity);
		} else if (entity.hasAttached(CannonMod.BALL) && !Cannonball.isFlying(entity)) {
			ORPHANS.add(entity);
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		if (entity.hasAttached(CannonMod.DATA)) {
			Cannon cannon = BY_ROOT.get(entity.getUUID());
			if (cannon != null) {
				// not while the chunk is busy unloading: tidy the seat up at the end of the tick
				CannonMod.nextTick(cannon::unload);
			}
		}
	}

	static void tick() {
		if (!PENDING.isEmpty()) {
			List<Entity> pending = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Entity root : pending) {
				if (!root.isRemoved() && root.level() instanceof ServerLevel level) {
					register(level, root);
				}
			}
		}
		if (!ORPHANS.isEmpty()) {
			List<Entity> orphans = new ArrayList<>(ORPHANS);
			ORPHANS.clear();
			for (Entity orphan : orphans) {
				if (!orphan.isRemoved() && !BY_SEAT.containsKey(orphan.getUUID()) && !Cannonball.isFlying(orphan)) {
					orphan.ejectPassengers();
					orphan.discard();
				}
			}
		}
		for (Cannon cannon : all()) {
			if (cannon.isRemoved()) {
				continue;
			}
			try {
				cannon.tick();
			} catch (RuntimeException e) {
				CannonMod.LOG.error("Cannon {} crashed while ticking", cannon.root.getUUID(), e);
			}
		}
		Cannonball.tickAll();
	}

	/** Server stopping: get everyone off and remove the seats and balls, so nobody logs back in riding a ghost. */
	static void shutdown() {
		for (Cannon cannon : all()) {
			cannon.unload();
		}
		Cannonball.clear();
		BY_ROOT.clear();
		BY_SEAT.clear();
		PENDING.clear();
		ORPHANS.clear();
	}

	/** Vanilla saves a player together with whatever they ride. Don't let them take the seat home. */
	static void onDisconnect(ServerPlayer player) {
		if (mannedBy(player) != null) {
			player.stopRiding();
		}
	}

	// ---------------------------------------------------------------- spawning

	/** Builds a cannon. Returns null if the summon failed. */
	static @Nullable Cannon spawn(ServerLevel level, CannonData data, double x, double y, double z, float yaw) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, Cannon.summonCommand(id, x, y, z, yaw, data.elevation));
		Entity root = level.getEntity(id);
		if (root == null) {
			CannonMod.LOG.error("Could not summon a cannon at {} {} {}", x, y, z);
			return null;
		}
		// passengers come out in the order they were summoned: hitbox, frame, then barrel
		List<Entity> parts = root.getPassengers();
		int first = 1 + Cannon.FRAME.size();
		for (int i = 0; i < Cannon.BARREL.size() && first + i < parts.size(); i++) {
			parts.get(first + i).setAttached(CannonMod.BARREL_PART, i);
		}
		root.setAttached(CannonMod.DATA, data);
		PENDING.remove(root);
		return register(level, root);
	}

	/** Takes the cannon apart and returns it as an item. */
	static ItemStack pickUp(Cannon cannon) {
		cannon.remove();
		return CannonItems.cannon();
	}

	// ---------------------------------------------------------------- interaction

	/** Right-clicking the ground with a cannon item sets it up there, pointing where you look. */
	static InteractionResult useKit(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!CannonItems.isCannon(held)) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos base = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		double x = base.getX() + 0.5;
		double y = base.getY();
		double z = base.getZ() + 0.5;
		if (!Cannon.fits(level, x, y, z)) {
			player.sendSystemMessage(Component.literal("Not enough room for a cannon here. It needs about 2 × 2 blocks of space.")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		CannonData data = new CannonData();
		data.owner = player.getUUID().toString();
		data.ownerName = player.getName().getString();
		Cannon cannon = spawn(level, data, x, y, z, player.getYRot());
		if (cannon == null) {
			player.sendSystemMessage(Component.literal("Something went wrong building the cannon. Check the server log.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.anvil.place", x, y + 1, z, 0.6f, 0.8f);
		player.sendSystemMessage(Component.literal("Cannon in position. ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Right-click it to man it. Sneak + right-click to pick it back up.").withStyle(ChatFormatting.YELLOW)));
		return InteractionResult.SUCCESS;
	}

	/** Right-clicking the cannon's hitbox. */
	static InteractionResult useHitbox(ServerPlayer player, InteractionHand hand, Entity hitbox) {
		if (!(hitbox instanceof Interaction) || hitbox.getVehicle() == null) {
			return InteractionResult.PASS;
		}
		Cannon cannon = BY_ROOT.get(hitbox.getVehicle().getUUID());
		if (cannon == null || cannon.isRemoved()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND || mannedBy(player) == cannon) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			if (!cannon.isOwner(player) && !player.isCreative()) {
				player.sendSystemMessage(Component.literal("That's " + cannon.data.ownerName + "'s cannon. Hands off the artillery.")
					.withStyle(ChatFormatting.RED));
			} else if (cannon.gunner() != null) {
				player.sendSystemMessage(Component.literal("Someone's manning it. Wait until they're done shooting things.").withStyle(ChatFormatting.RED));
			} else {
				CannonItems.give(player, pickUp(cannon));
				Cmd.sound(cannon.level, "minecraft:entity.item.pickup", player.getX(), player.getY() + 1, player.getZ(), 0.6f, 0.8f);
				player.sendSystemMessage(Component.literal("Cannon packed up.").withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.SUCCESS;
		}
		if (!cannon.man(player)) {
			player.sendSystemMessage(Component.literal("Someone's already on this cannon. One gunner at a time.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		player.sendSystemMessage(Component.literal("You're manning the cannon. ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Look to aim · Space to fire · Shift to get off").withStyle(ChatFormatting.GRAY)));
		if (!player.isCreative() && CannonItems.countCannonballs(player) == 0) {
			player.sendSystemMessage(Component.literal("You have no cannonballs. Craft them from 1 iron ingot + 1 gunpowder.")
				.withStyle(ChatFormatting.YELLOW));
		}
		return InteractionResult.SUCCESS;
	}
}
