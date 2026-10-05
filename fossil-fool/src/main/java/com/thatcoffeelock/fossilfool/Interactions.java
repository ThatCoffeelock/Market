package com.thatcoffeelock.fossilfool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Placing, right-clicking and breaking our blocks; buckets; and the dowsing rod. */
public final class Interactions {
	/** Last dowse per player, so the rod can't be spammed. */
	private static final Map<UUID, Long> LAST_DOWSE = new HashMap<>();

	private Interactions() {
	}

	static boolean isTankBlock(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("cauldron");
	}

	static boolean isRefineryBlock(BlockState state) {
		return state.is(Blocks.BLAST_FURNACE);
	}

	static void forget(UUID player) {
		LAST_DOWSE.remove(player);
	}

	// ---------------------------------------------------------------- placing (called from the BlockItem mixin)

	/** A block was just placed from this stack. Remembers it if it's one of ours. */
	public static void onPlaced(Level world, @Nullable Player player, BlockPos pos, ItemStack stack) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
		CompoundTag data = OilItems.data(stack);
		if (OilItems.isTank(stack)) {
			Tank tank = Machines.addTank(level, pos);
			Fluid fluid = Fluid.byName(data.getStringOr(OilItems.FLUID, ""));
			int amount = data.getIntOr(OilItems.AMOUNT, 0);
			if (fluid != Fluid.NONE && amount > 0) {
				tank.fill(fluid, amount);
			}
			tell(sp, "Oil Tank set up. It holds " + Gui.n(Tank.capacity()) + " buckets of crude or diesel. Drill Rigs and Refineries within "
				+ FossilConfig.get().pipeReach + " blocks pipe into it.", ChatFormatting.AQUA);
		} else if (OilItems.isRefinery(stack)) {
			Refinery r = Machines.addRefinery(level, pos);
			r.crude = Math.max(0, data.getIntOr(OilItems.CRUDE_IN, 0));
			r.diesel = Math.max(0, data.getIntOr(OilItems.DIESEL_OUT, 0));
			if (sp != null) {
				r.owner = sp.getUUID().toString();
			}
			tell(sp, "Refinery set up. Right-click it, put fuel in its firebox, and pour in crude.", ChatFormatting.GOLD);
		}
	}

	private static void tell(@Nullable ServerPlayer player, String text, ChatFormatting color) {
		if (player != null) {
			player.sendSystemMessage(Component.literal(text).withStyle(color));
		}
	}

	static void actionBar(ServerPlayer player, Component line) {
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	// ---------------------------------------------------------------- buckets

	private static boolean isEmptyBucket(ItemStack stack) {
		return stack.is(Items.BUCKET) && OilItems.kind(stack).isEmpty();
	}

	/** Turns up to {@code max} of the player's empty buckets into buckets of this oil. Returns how many. */
	static int fillBuckets(ServerPlayer player, Fluid fluid, int max) {
		if (max <= 0) {
			return 0;
		}
		Inventory inv = player.getInventory();
		int taken = 0;
		for (int i = 0; i < inv.getContainerSize() && taken < max; i++) {
			ItemStack stack = inv.getItem(i);
			if (isEmptyBucket(stack)) {
				int n = Math.min(stack.getCount(), max - taken);
				stack.shrink(n);
				taken += n;
			}
		}
		inv.setChanged();
		give(player, fluid, taken);
		return taken;
	}

	/** Pours up to {@code max} buckets of this oil out of the player's inventory; they get the empty buckets back. */
	static int pourBuckets(ServerPlayer player, Fluid fluid, int max) {
		if (max <= 0) {
			return 0;
		}
		Inventory inv = player.getInventory();
		int taken = 0;
		for (int i = 0; i < inv.getContainerSize() && taken < max; i++) {
			ItemStack stack = inv.getItem(i);
			if (OilItems.fluidOf(stack) == fluid) {
				int n = Math.min(stack.getCount(), max - taken);
				stack.shrink(n);
				taken += n;
			}
		}
		inv.setChanged();
		int left = taken;
		while (left > 0) {
			int n = Math.min(16, left);
			OilItems.give(player, new ItemStack(Items.BUCKET, n));
			left -= n;
		}
		return taken;
	}

	private static void give(ServerPlayer player, Fluid fluid, int count) {
		while (count > 0) {
			int n = Math.min(OilItems.BUCKET_STACK, count);
			OilItems.give(player, OilItems.bucketOf(fluid, n));
			count -= n;
		}
	}

	// ---------------------------------------------------------------- right-clicking

	static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, @Nullable BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (OilItems.isRod(held)) {
			if (hand == InteractionHand.MAIN_HAND) {
				dowse(player, level);
			}
			return InteractionResult.SUCCESS;
		}
		if (hit == null) {
			return InteractionResult.PASS;
		}
		InteractionResult kit = Rigs.useKit(player, level, hand, hit);
		if (kit != InteractionResult.PASS) {
			return kit;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Tank tank = isTankBlock(state) ? Machines.tankAt(level, pos) : null;
		Refinery refinery = tank == null && isRefineryBlock(state) ? Machines.refineryAt(level, pos) : null;
		if (tank == null && refinery == null) {
			if (isEmptyBucket(held) && Pockets.isOil(level, pos)) {
				if (hand == InteractionHand.MAIN_HAND) {
					scoop(player, level, pos, held);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		// sneak + right-click with a block: let it be placed against ours
		if (player.isShiftKeyDown() && !held.isEmpty() && OilItems.fluidOf(held) == Fluid.NONE && !isEmptyBucket(held)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (refinery != null) {
			RefineryMenu.open(player, refinery);
			return InteractionResult.SUCCESS;
		}
		useTank(player, tank, held);
		return InteractionResult.SUCCESS;
	}

	/** Buckets of oil in: pour them all. Empty buckets in: fill them all. Anything else: how full is it? */
	private static void useTank(ServerPlayer player, Tank tank, ItemStack held) {
		Fluid fluid = OilItems.fluidOf(held);
		if (fluid != Fluid.NONE) {
			if (!tank.takes(fluid)) {
				tell(player, tank.space() == 0 ? "The tank is full." : "This tank holds " + tank.fluid.title.toLowerCase(java.util.Locale.ROOT)
					+ ". Don't mix your oils.", ChatFormatting.RED);
				return;
			}
			int n = pourBuckets(player, fluid, tank.space());
			tank.fill(fluid, n);
			Cmd.sound((ServerLevel) player.level(), "minecraft:item.bucket.empty_lava", tank.pos.getX() + 0.5, tank.pos.getY() + 1, tank.pos.getZ() + 0.5, 0.8f, 0.7f);
			actionBar(player, Component.literal("Poured " + n + " × " + fluid.title + " into the tank (" + tank.amount + " / " + Tank.capacity() + ").")
				.withStyle(ChatFormatting.GREEN));
			return;
		}
		if (isEmptyBucket(held)) {
			if (tank.amount == 0) {
				tell(player, "The tank is empty.", ChatFormatting.RED);
				return;
			}
			Fluid kind = tank.fluid;
			int n = Math.min(held.getCount(), tank.amount);
			held.shrink(n);
			tank.drain(kind, n);
			give(player, kind, n);
			Cmd.sound((ServerLevel) player.level(), "minecraft:item.bucket.fill_lava", tank.pos.getX() + 0.5, tank.pos.getY() + 1, tank.pos.getZ() + 0.5, 0.8f, 0.7f);
			actionBar(player, Component.literal("Filled " + n + " × " + kind.title + " (" + tank.amount + " left).").withStyle(ChatFormatting.GREEN));
			return;
		}
		actionBar(player, Component.literal("Oil Tank: " + (tank.amount == 0 ? "empty" : Gui.n(tank.amount) + " buckets of " + tank.fluid.title)
			+ " / " + Gui.n(Tank.capacity()) + ". Use buckets on it.").withStyle(ChatFormatting.AQUA));
	}

	/** Empty bucket on a crude block in the ground: one bucket of crude, and the block is gone. */
	private static void scoop(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
		if (!Pockets.drain(level, pos)) {
			return;
		}
		held.shrink(1);
		give(player, Fluid.CRUDE, 1);
		Cmd.sound(level, "minecraft:item.bucket.fill_lava", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.8f, 0.6f);
		Hooks.xp(player.getUUID(), 1.0);
	}

	// ---------------------------------------------------------------- dowsing

	private static void dowse(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		Long last = LAST_DOWSE.get(player.getUUID());
		if (last != null && now - last < 40) {
			return;
		}
		LAST_DOWSE.put(player.getUUID(), now);
		if (!Pockets.hasOil(level)) {
			actionBar(player, Component.literal("The rod hangs limp. There's no oil in this dimension.").withStyle(ChatFormatting.GRAY));
			return;
		}
		int range = FossilConfig.get().dowsingRange + (int) Hooks.bonus(player.getUUID(), "dowse");
		BlockPos at = player.blockPosition();
		Pockets.Reading r = Pockets.dowse(level, at, range);
		Cmd.sound(level, "minecraft:block.amethyst_block.chime", player.getX(), player.getY() + 1, player.getZ(), 0.8f, r == null ? 0.5f : 1.4f);
		if (r == null) {
			actionBar(player, Component.literal("The rod doesn't move. No oil within " + range + " blocks.").withStyle(ChatFormatting.GRAY));
			return;
		}
		int depth = Math.max(1, r.depth());
		String down = "about " + (Math.round(depth / 5.0) * 5) + " blocks down";
		Component line;
		if (r.distance() < 4) {
			line = Component.literal("The rod points straight down! Oil right below you, " + down + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		} else {
			String strength = r.distance() < 16 ? "jerks hard" : r.distance() < 32 ? "tugs" : "twitches faintly";
			line = Component.literal("The rod " + strength + " to the " + Pockets.direction(at, r.pocket().x(), r.pocket().z()) + ". About "
				+ (Math.round(r.distance() / 4.0) * 4) + " blocks away, " + down + ".").withStyle(ChatFormatting.YELLOW);
		}
		actionBar(player, line);
		Hooks.xp(player.getUUID(), 0.5);
	}

	// ---------------------------------------------------------------- breaking

	/** @return false to cancel the vanilla break (we handled it). */
	static boolean beforeBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		if (isTankBlock(state)) {
			Tank tank = Machines.tankAt(level, pos);
			if (tank != null) {
				Machines.removeTank(tank);
				level.removeBlock(pos, false);
				if (!player.isCreative() || tank.amount > 0) {
					Block.popResource(level, pos, OilItems.tank(tank.fluid, tank.amount));
				}
				return false;
			}
		}
		if (isRefineryBlock(state)) {
			Refinery r = Machines.refineryAt(level, pos);
			if (r != null) {
				Machines.removeRefinery(r);
				for (int i = 0; i < r.firebox.getContainerSize(); i++) {
					ItemStack fuel = r.firebox.getItem(i);
					if (!fuel.isEmpty()) {
						Block.popResource(level, pos, fuel.copy());
						r.firebox.setItem(i, ItemStack.EMPTY);
					}
				}
				level.removeBlock(pos, false);
				if (!player.isCreative() || r.crude > 0 || r.diesel > 0) {
					Block.popResource(level, pos, OilItems.refinery(r.crude, r.diesel));
				}
				return false;
			}
		}
		if (Pockets.isOil(level, pos)) {
			Pockets.drain(level, pos);
			Cmd.particles(level, "minecraft:squid_ink", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.3, 0.05, 12);
			if (player instanceof ServerPlayer sp) {
				actionBar(sp, Component.literal("The crude oozes away. Right-click oil with an empty bucket to keep it.").withStyle(ChatFormatting.GRAY));
			}
			return false;
		}
		return true;
	}

	/** After a player breaks a block: did they break into an oil pocket? */
	static void afterBreak(ServerLevel level, Player player, BlockPos pos) {
		if (!Pockets.hasOil(level)) {
			return;
		}
		Pockets.breach(level, pos, player instanceof ServerPlayer sp ? sp : null);
	}
}
