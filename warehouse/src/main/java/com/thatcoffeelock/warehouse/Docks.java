package com.thatcoffeelock.warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Loading Docks: a ship moored next to one can unload into every warehouse the dock serves. Specialist warehouses
 * (the ones that only accept, say, food) get their kind of cargo first; the rest goes to the general warehouses,
 * nearest first. Whatever doesn't fit stays aboard.
 */
final class Docks {
	/** What an unload did. */
	record Report(long moved, Map<String, Long> perWarehouse, int stacksLeft) {
	}

	private Docks() {
	}

	static List<Warehouse> served(ServerLevel level, BlockPos dock) {
		return Warehouses.near(level, dock, WarehouseConfig.get().dockReach);
	}

	/** Unloads the holds into these warehouses. */
	static Report unload(List<Warehouse> targets, List<Container> holds) {
		Map<String, Long> per = new LinkedHashMap<>();
		long moved = 0;
		int left = 0;
		for (Container hold : holds) {
			boolean changed = false;
			for (int i = 0; i < hold.getContainerSize(); i++) {
				ItemStack stack = hold.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				Category kind = Category.of(stack);
				List<Warehouse> order = new ArrayList<>();
				for (Warehouse w : targets) {
					if (w.filter == kind) {
						order.add(w);
					}
				}
				for (Warehouse w : targets) {
					if (w.filter == Category.ALL) {
						order.add(w);
					}
				}
				for (Warehouse w : order) {
					int n = w.deposit(stack);
					if (n > 0) {
						moved += n;
						changed = true;
						per.merge(w.name, (long) n, Long::sum);
					}
					if (stack.isEmpty()) {
						break;
					}
				}
				if (stack.isEmpty()) {
					hold.setItem(i, ItemStack.EMPTY);
				} else {
					left++;
				}
			}
			if (changed) {
				hold.setChanged();
			}
		}
		return new Report(moved, per, left);
	}

	static void tell(ServerPlayer player, String ship, Report report) {
		if (report.moved() == 0) {
			player.sendSystemMessage(Component.literal(report.stacksLeft() == 0
				? "The " + ship + " has nothing to unload."
				: "Nothing could be unloaded: the warehouses are full or don't take this cargo.").withStyle(ChatFormatting.RED));
			return;
		}
		List<String> parts = new ArrayList<>();
		report.perWarehouse().forEach((name, n) -> parts.add(Gui.n(n) + " → " + name));
		player.sendSystemMessage(Component.literal("Unloaded " + Gui.n(report.moved()) + " items from the " + ship + ": ")
			.withStyle(ChatFormatting.GREEN)
			.append(Component.literal(String.join(", ", parts)).withStyle(ChatFormatting.AQUA)));
		if (report.stacksLeft() > 0) {
			player.sendSystemMessage(Component.literal(report.stacksLeft() + (report.stacksLeft() == 1 ? " stack stays" : " stacks stay")
				+ " aboard: no room, or no warehouse here takes it.").withStyle(ChatFormatting.YELLOW));
		}
	}
}
