package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Stations are plain chests, trapped chests or barrels with a station name: "Pickup Station", "Drop-off
 * Station" or "Swap Station". Craft one, or just rename a chest in an anvil. They work when they're right
 * next to the track (or under it, for a barrel). Sneak + right-click one with an empty hand to switch its mode.
 *
 * No extra state anywhere: the name is the whole configuration, and it survives breaking and placing the
 * chest again like any named chest does.
 */
final class Stations {
	enum Mode {
		PICKUP("Pickup Station", "gold", ChatFormatting.GOLD, "The train loads everything in here and takes it away."),
		DROPOFF("Drop-off Station", "aqua", ChatFormatting.AQUA, "The train unloads its cargo in here."),
		SWAP("Swap Station", "light_purple", ChatFormatting.LIGHT_PURPLE, "The train unloads its cargo here, then loads what was in here.");

		final String title;
		final String color;
		final ChatFormatting format;
		final String blurb;

		Mode(String title, String color, ChatFormatting format, String blurb) {
			this.title = title;
			this.color = color;
			this.format = format;
			this.blurb = blurb;
		}

		Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** What happened at one station. */
	record Visit(BlockPos pos, Mode mode, int loaded, int unloaded) {
	}

	/** Neighbours of a rail where a station can stand: the four sides (level with the rail or one lower), and right under it. */
	private static final int[][] AROUND = {
		{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
		{1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
		{0, -1, 0}};

	private Stations() {
	}

	static @Nullable Mode parse(String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		if (!s.contains("station")) {
			return null;
		}
		if (s.contains("pickup")) {
			return Mode.PICKUP;
		}
		if (s.contains("dropoff")) {
			return Mode.DROPOFF;
		}
		if (s.contains("swap")) {
			return Mode.SWAP;
		}
		return null;
	}

	private static boolean isStationBlock(BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
	}

	/** The station mode of the block at pos, or null if it isn't a station. Never loads a chunk. */
	static @Nullable Mode modeAt(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isStationBlock(level.getBlockState(pos))) {
			return null;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof Container) || !(entity instanceof Nameable named)) {
			return null;
		}
		Component name = named.getCustomName();
		return name == null ? null : parse(name.getString());
	}

	static @Nullable Container container(ServerLevel level, BlockPos pos) {
		return modeAt(level, pos) != null && level.getBlockEntity(pos) instanceof Container box ? box : null;
	}

	/** Stations next to this rail, except the ones in {@code skip}. */
	static List<BlockPos> near(ServerLevel level, BlockPos rail, Collection<BlockPos> skip) {
		List<BlockPos> found = new ArrayList<>(1);
		for (int[] d : AROUND) {
			BlockPos pos = rail.offset(d[0], d[1], d[2]);
			if (!skip.contains(pos) && modeAt(level, pos) != null) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	/** Renames the chest, which is what switches its mode. Goes through a command so the client hears about it. */
	static void setMode(ServerLevel level, BlockPos pos, Mode mode) {
		Cmd.run(level, "data merge block " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
			+ " {CustomName:{text:\"" + mode.title + "\",color:\"" + mode.color + "\",italic:false}}");
	}

	/** Sneak + right-click a station with an empty hand: next mode. */
	static InteractionResult useChest(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		Mode mode = modeAt(level, pos);
		if (mode == null) {
			return InteractionResult.PASS;
		}
		Mode next = mode.next();
		setMode(level, pos, next);
		Cmd.sound(level, "minecraft:block.note_block.chime", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.8f, next == Mode.PICKUP ? 1.0f : next == Mode.DROPOFF ? 1.25f : 1.5f);
		player.sendSystemMessage(Component.literal("This is now a ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(next.title).withStyle(next.format, ChatFormatting.BOLD))
			.append(Component.literal(". " + next.blurb).withStyle(ChatFormatting.GRAY)));
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- moving items

	/** Puts as much of the stack as fits into the container (topping up stacks first). Shrinks the stack; returns how many moved. */
	static int insert(Container to, ItemStack stack) {
		int before = stack.getCount();
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < to.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack slot = to.getItem(i);
				int max = Math.min(to.getMaxStackSize(), stack.getMaxStackSize());
				if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < max) {
					int move = Math.min(stack.getCount(), max - slot.getCount());
					slot.grow(move);
					stack.shrink(move);
				} else if (pass == 1 && slot.isEmpty() && to.canPlaceItem(i, stack)) {
					to.setItem(i, stack.split(Math.min(stack.getCount(), max)));
				}
			}
		}
		int moved = before - stack.getCount();
		if (moved > 0) {
			to.setChanged();
		}
		return moved;
	}

	/** Moves everything that fits from one container to the other. Returns how many items moved. */
	static int moveAll(Container from, Container to) {
		int moved = 0;
		for (int i = 0; i < from.getContainerSize(); i++) {
			ItemStack stack = from.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			moved += insert(to, stack);
			if (stack.isEmpty()) {
				from.setItem(i, ItemStack.EMPTY);
			}
		}
		if (moved > 0) {
			from.setChanged();
		}
		return moved;
	}

	/** Puts as much of the stack as fits into the wagons, front wagon first. */
	static int insert(List<? extends Container> wagons, ItemStack stack) {
		int moved = 0;
		for (Container wagon : wagons) {
			if (stack.isEmpty()) {
				break;
			}
			moved += insert(wagon, stack);
		}
		return moved;
	}

	/** Loads a chest into the wagons. */
	static int load(Container chest, List<? extends Container> wagons) {
		int moved = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			moved += insert(wagons, stack);
			if (stack.isEmpty()) {
				chest.setItem(i, ItemStack.EMPTY);
			}
		}
		if (moved > 0) {
			chest.setChanged();
		}
		return moved;
	}

	/** Unloads the wagons into a chest, front wagon first. */
	static int unload(List<? extends Container> wagons, Container chest) {
		int moved = 0;
		for (Container wagon : wagons) {
			moved += moveAll(wagon, chest);
		}
		return moved;
	}

	/** Unloads into whatever another mod put behind this Drop-off Station (see {@link CargoTrainApi}), front wagon first. */
	static int unloadBehind(ServerLevel level, BlockPos station, List<? extends Container> wagons) {
		CargoTrainApi.Intake intake = CargoTrainApi.intakeBehind(level, station);
		if (intake == null) {
			return 0;
		}
		int moved = 0;
		for (Container wagon : wagons) {
			int before = moved;
			for (int i = 0; i < wagon.getContainerSize(); i++) {
				ItemStack stack = wagon.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				moved += Math.max(0, intake.accept(stack));
				if (stack.isEmpty()) {
					wagon.setItem(i, ItemStack.EMPTY);
				}
			}
			if (moved > before) {
				wagon.setChanged();
			}
		}
		return moved;
	}

	/** The train stops at a station: load, unload or swap. Nothing is ever lost; the last resort is dropping it on top. */
	static @Nullable Visit serve(ServerLevel level, BlockPos pos, List<? extends Container> wagons) {
		Mode mode = modeAt(level, pos);
		Container chest = container(level, pos);
		if (mode == null || chest == null) {
			return null;
		}
		return switch (mode) {
			case PICKUP -> new Visit(pos, mode, load(chest, wagons), 0);
			case DROPOFF -> new Visit(pos, mode, 0, unloadBehind(level, pos, wagons) + unload(wagons, chest));
			case SWAP -> {
				List<ItemStack> outgoing = new ArrayList<>();
				for (int i = 0; i < chest.getContainerSize(); i++) {
					ItemStack stack = chest.getItem(i);
					if (!stack.isEmpty()) {
						outgoing.add(stack);
						chest.setItem(i, ItemStack.EMPTY);
					}
				}
				int unloaded = unload(wagons, chest);
				int loaded = 0;
				for (ItemStack stack : outgoing) {
					loaded += insert(wagons, stack);
					if (!stack.isEmpty()) {
						insert(chest, stack);
					}
					if (!stack.isEmpty()) {
						Block.popResource(level, pos.above(), stack);
					}
				}
				chest.setChanged();
				yield new Visit(pos, mode, loaded, unloaded);
			}
		};
	}
}
