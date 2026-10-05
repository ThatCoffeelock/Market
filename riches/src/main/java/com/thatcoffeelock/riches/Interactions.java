package com.thatcoffeelock.riches;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Placing, right-clicking and breaking Riches blocks. */
public final class Interactions {
	private Interactions() {
	}

	private static void tell(@Nullable ServerPlayer player, String text, ChatFormatting color) {
		if (player != null) {
			player.sendSystemMessage(Component.literal(text).withStyle(color));
		}
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	// ---------------------------------------------------------------- placing (called from the BlockItem mixin)

	public static void onPlaced(Level world, @Nullable Player player, BlockPos pos, ItemStack stack) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
		String owner = sp == null ? "" : sp.getUUID().toString();
		String name = sp == null ? "" : sp.getName().getString();
		String dim = Places.dim(level);
		BlockPos at = pos.immutable();
		if (RichesItems.isLedger(stack)) {
			Places.Vault v = new Places.Vault(dim, at);
			v.owner = owner;
			v.ownerName = name;
			Places.VAULTS.put(Places.key(dim, at), v);
			tell(sp, "Vault Ledger placed. Your Market balance piles up around it in gold. Leave the floor around it clear.", ChatFormatting.GOLD);
		} else if (RichesItems.isDoor(stack)) {
			Places.Door d = new Places.Door(dim, at);
			d.owner = owner;
			d.ownerName = name;
			Places.DOORS.put(Places.key(dim, at), d);
			tell(sp, "Vault Door hung. It opens for you and anyone you /riches trust.", ChatFormatting.GOLD);
		} else if (RichesItems.isCase(stack) || RichesItems.isPedestal(stack)) {
			Places.Showcase s = new Places.Showcase(dim, at, RichesItems.isCase(stack) ? Places.Kind.CASE : Places.Kind.PEDESTAL);
			s.owner = owner;
			s.ownerName = name;
			s.drawn = true;
			Places.SHOWCASES.put(Places.key(dim, at), s);
			tell(sp, (s.kind == Places.Kind.CASE ? "Display Case" : "Pedestal") + " set up. Right-click it with something worth showing off.",
				ChatFormatting.AQUA);
		} else {
			return;
		}
		Store.changed();
	}

	// ---------------------------------------------------------------- right-clicking

	static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Places.Door door = Places.doorAt(level, pos);
		Places.Showcase show = Places.SHOWCASES.get(Places.key(level, pos));
		Places.Vault vault = Places.VAULTS.get(Places.key(level, pos));
		ItemStack held = player.getItemInHand(hand);
		if (door == null && show == null && vault == null) {
			Relics.opening(level, player, pos);
			return InteractionResult.PASS;
		}
		// sneak + right-click with a block: let it be placed against ours
		if (player.isShiftKeyDown() && !held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.BlockItem) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (door != null) {
			if (!Places.allowed(player, door.owner)) {
				Cmd.sound(level, "minecraft:block.iron_trapdoor.close", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.8f, 0.5f);
				actionBar(player, "Locked. This is " + door.ownerName + "'s vault. Nice try.", ChatFormatting.RED);
			} else if (Vaults.isOpen(level, door)) {
				door.openUntil = 0; // shuts on the next check
			} else {
				Vaults.open(level, door);
			}
			return InteractionResult.SUCCESS;
		}
		if (vault != null) {
			long cents = 0;
			try {
				cents = Bank.balance(java.util.UUID.fromString(vault.owner));
			} catch (IllegalArgumentException ignored) {
				// no owner
			}
			actionBar(player, (vault.ownerName.isEmpty() ? "This vault" : vault.ownerName + "'s vault") + " holds " + Bank.format(cents) + ".",
				ChatFormatting.GOLD);
			return InteractionResult.SUCCESS;
		}
		return useShowcase(player, level, show, held);
	}

	private static InteractionResult useShowcase(ServerPlayer player, ServerLevel level, Places.Showcase show, ItemStack held) {
		boolean mine = Places.allowed(player, show.owner);
		if (show.item.isEmpty()) {
			if (held.isEmpty()) {
				actionBar(player, "Empty. Right-click it with something worth showing off.", ChatFormatting.GRAY);
			} else if (!mine) {
				actionBar(player, "That's " + show.ownerName + "'s " + (show.kind == Places.Kind.CASE ? "display case." : "pedestal."), ChatFormatting.RED);
			} else {
				Showcases.put(level, show, held, player.getName().getString());
				if (!player.isCreative()) {
					held.shrink(1);
				}
				Cmd.sound(level, "minecraft:block.amethyst_block.place", show.pos.getX() + 0.5, show.pos.getY() + 1, show.pos.getZ() + 0.5, 0.8f, 1.2f);
				if (RichesItems.relicOf(show.item) != null) {
					Relics.checkCollections(level, show.owner, show.ownerName);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && mine) {
			RichesItems.give(player, Showcases.take(level, show));
			Cmd.sound(level, "minecraft:entity.item.pickup", player.getX(), player.getY() + 1, player.getZ(), 0.6f, 0.9f);
			return InteractionResult.SUCCESS;
		}
		StringBuilder line = new StringBuilder();
		for (String[] l : Showcases.plaque(show)) {
			line.append(line.length() == 0 ? "" : " · ").append(l[0]);
		}
		actionBar(player, line.toString(), ChatFormatting.GOLD);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- breaking

	/** @return false to cancel the vanilla break (we handled it, or it isn't allowed). */
	static boolean beforeBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		Places.Door door = Places.doorAt(level, pos);
		if (door != null) {
			if (!Places.allowed(player, door.owner)) {
				player.sendSystemMessage(Component.literal("You can't break into " + door.ownerName + "'s vault. It's a vault.").withStyle(ChatFormatting.RED));
				return false;
			}
			Places.DOORS.remove(Places.key(level, door.pos));
			Store.changed();
			level.removeBlock(door.pos.above(), false);
			level.removeBlock(door.pos, false);
			if (!player.isCreative()) {
				Block.popResource(level, door.pos, RichesItems.door());
			}
			return false;
		}
		String key = Places.key(level, pos);
		Places.Vault vault = Places.VAULTS.get(key);
		if (vault != null) {
			if (!Places.allowed(player, vault.owner)) {
				player.sendSystemMessage(Component.literal("That's " + vault.ownerName + "'s Vault Ledger.").withStyle(ChatFormatting.RED));
				return false;
			}
			Places.VAULTS.remove(key);
			Vaults.clear(level, vault);
			Store.changed();
			level.removeBlock(pos, false);
			if (!player.isCreative()) {
				Block.popResource(level, pos, RichesItems.ledger());
			}
			return false;
		}
		Places.Showcase show = Places.SHOWCASES.get(key);
		if (show != null) {
			if (!Places.allowed(player, show.owner)) {
				player.sendSystemMessage(Component.literal("That's " + show.ownerName + "'s. Hands off the exhibits.").withStyle(ChatFormatting.RED));
				return false;
			}
			Places.SHOWCASES.remove(key);
			Showcases.clear(level, show);
			Store.changed();
			level.removeBlock(pos, false);
			if (!show.item.isEmpty()) {
				Block.popResource(level, pos, show.item);
			}
			if (!player.isCreative()) {
				Block.popResource(level, pos, show.kind == Places.Kind.CASE ? RichesItems.displayCase() : RichesItems.pedestal());
			}
			return false;
		}
		return true;
	}

	/** Forgets blocks that disappeared without a player breaking them (explosions, pistons, commands). */
	static void validate() {
		Places.VAULTS.values().removeIf(v -> gone(v.dimension, v.pos, "lodestone"));
		Places.DOORS.values().removeIf(d -> gone(d.dimension, d.pos, "iron_door"));
		Places.SHOWCASES.values().removeIf(s -> {
			boolean gone = gone(s.dimension, s.pos, s.kind == Places.Kind.CASE ? "glass" : "quartz_pillar");
			if (gone) {
				ServerLevel level = Places.level(s.dimension);
				if (level != null) {
					Showcases.clear(level, s);
					if (!s.item.isEmpty()) {
						Block.popResource(level, s.pos, s.item);
					}
				}
			}
			return gone;
		});
	}

	private static boolean gone(String dim, BlockPos pos, String block) {
		ServerLevel level = Places.level(dim);
		if (level == null || !level.isLoaded(pos)) {
			return false;
		}
		boolean there = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().equals(block);
		if (!there) {
			RichesMod.LOG.info("A Riches {} at {} is gone", block, pos);
			Store.changed();
		}
		return !there;
	}
}
