package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Drill Rig's screen. Top row: switches and gauges. Second row: the firebox (put fuel in it). Rows three and
 * four: the ore hold. Rows five and six: the stone hold. Take things out of the holds like from any chest.
 */
final class RigMenu extends MachineMenu {
	private final Rig rig;
	private final ServerLevel level;

	private RigMenu(int syncId, ServerPlayer viewer, Rig rig, ServerLevel level) {
		super(syncId, viewer, 6, new View(6, List.of(
			new Section(rig.firebox, 9, true),
			new Section(rig.ores, 18, false),
			new Section(rig.stone, 36, false)),
			() -> !Rigs.isRemoved(rig) && viewer.distanceToSqr(rig.modelX(), rig.modelY(), rig.modelZ()) < 16 * 16));
		this.rig = rig;
		this.level = level;
		render();
	}

	static void open(ServerPlayer player, Rig rig, ServerLevel level) {
		String title = rig.ownerName.isEmpty() ? "Drill Rig" : rig.ownerName + "'s Drill Rig";
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RigMenu(id, player, rig, level),
			Component.literal(title).withStyle(ChatFormatting.DARK_GRAY)));
	}

	@Override
	void render() {
		FossilConfig c = FossilConfig.get();
		button(0, Gui.icon(Items.LEVER, Gui.text("Engine: " + (rig.on ? "ON" : "OFF"), rig.on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD),
			List.of(Gui.text(rig.on ? "Click to shut the engine down." : "Click to fire it up.", ChatFormatting.GRAY))), shift -> {
			click();
			rig.on = !rig.on;
			Store.changed();
		});

		List<Component> status = new ArrayList<>();
		status.add(Gui.text(rig.state.text + ".", rig.state.color));
		status.add(Gui.text("Depth: " + rig.depth() + " blocks (y " + rig.layer + ")", ChatFormatting.GRAY));
		status.add(Gui.text("This layer: " + Gui.bar(rig.cell / 25.0, 10) + " " + rig.cell + "/25", ChatFormatting.GRAY));
		if (rig.blockedAt != null && rig.state == Rig.State.BLOCKED) {
			status.add(Gui.text("Can't drill the block at " + rig.blockedAt.getX() + " " + rig.blockedAt.getY() + " " + rig.blockedAt.getZ()
				+ " (it has contents).", ChatFormatting.YELLOW));
		}
		if (rig.state == Rig.State.HOLD_FULL) {
			status.add(Gui.text("Empty the holds, or switch Keep Stone off.", ChatFormatting.YELLOW));
		}
		if (rig.struck != null) {
			Pockets.Pocket p = Pockets.OPENED.get(rig.struck);
			status.add(Gui.text("Pocket: about " + (p == null ? 0 : Pockets.left(p)) + " buckets left.", ChatFormatting.GOLD));
		}
		button(1, Gui.icon(Items.COMPASS, Gui.text("Status", ChatFormatting.YELLOW, ChatFormatting.BOLD), status), null);

		List<Component> fuel = new ArrayList<>();
		fuel.add(Gui.text("In the fire: " + String.format(java.util.Locale.ROOT, "%.1f", rig.energy) + " blocks' worth", ChatFormatting.GRAY));
		fuel.add(Gui.text("Burning: " + (rig.burning == null ? "nothing yet" : rig.burning.title + " (" + rig.burning.speed() + "× speed)"),
			rig.burning == null ? ChatFormatting.DARK_GRAY : rig.burning.color));
		fuel.add(Component.empty());
		fuel.add(Gui.text("Put fuel in the row below. Each is better:", ChatFormatting.GRAY));
		for (Fuel f : Fuel.values()) {
			fuel.add(Gui.text(" " + Fuel.ladderLine(f), f.color));
		}
		fuel.add(Gui.text(" (a coal block is 9 coal)", ChatFormatting.DARK_GRAY));
		ItemStack fuelIcon = new ItemStack(rig.burning == Fuel.LAVA ? Items.LAVA_BUCKET : rig.burning == Fuel.CRUDE || rig.burning == Fuel.DIESEL
			? Items.BUCKET : Items.COAL);
		button(2, Gui.icon(fuelIcon.getItem(), Gui.text("Fuel", ChatFormatting.GOLD, ChatFormatting.BOLD), fuel), null);

		button(3, Gui.icon(Items.CAULDRON, Gui.text("Crude tank: " + rig.crude + " / " + c.rigTank + " buckets", ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
			List.of(Gui.text(Gui.bar(rig.crude / (double) c.rigTank, 10), ChatFormatting.GRAY),
				Gui.text("Click: fill the empty buckets you carry.", ChatFormatting.YELLOW),
				Gui.text("Oil Tanks within " + (c.pipeReach + 3) + " blocks fill by themselves.", ChatFormatting.DARK_GRAY))), shift -> {
			int n = Interactions.fillBuckets(viewer, Fluid.CRUDE, rig.crude);
			if (n == 0) {
				nope(rig.crude == 0 ? "The rig's tank is empty." : "You have no empty buckets.");
				return;
			}
			rig.crude -= n;
			Store.changed();
			click();
		});

		button(4, Gui.glow(Gui.icon(Items.PISTON, Gui.text("Drill Rig", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			Gui.text("Owner: " + (rig.ownerName.isEmpty() ? "nobody" : rig.ownerName), ChatFormatting.GRAY),
			Gui.text("Row 2: firebox. Rows 3-4: ores. Rows 5-6: stone.", ChatFormatting.DARK_GRAY)))), null);

		button(5, Gui.icon(Items.COBBLESTONE, Gui.text("Keep stone: " + (rig.keepStone ? "ON" : "OFF"), rig.keepStone ? ChatFormatting.GREEN : ChatFormatting.RED,
			ChatFormatting.BOLD), List.of(Gui.text(rig.keepStone ? "Stone, dirt and gravel go to the stone hold." : "Stone, dirt and gravel are thrown away.",
				ChatFormatting.GRAY), Gui.text("Ores are always kept.", ChatFormatting.DARK_GRAY))), shift -> {
			click();
			rig.keepStone = !rig.keepStone;
			Store.changed();
		});

		if (FossilFoolApi.hasUnloaders()) {
			button(6, Gui.icon(Items.BARREL, Gui.text("Unload to a Warehouse", ChatFormatting.AQUA, ChatFormatting.BOLD), List.of(
				Gui.text("Sends both holds to the warehouses within " + c.warehouseReach + " blocks.", ChatFormatting.GRAY),
				Gui.text("Ores go to an ores-only warehouse first.", ChatFormatting.GRAY),
				Gui.text("It also happens by itself every few seconds.", ChatFormatting.DARK_GRAY))), shift -> {
				long moved = rig.unload(level);
				if (moved == 0) {
					nope("Nothing went: no warehouse within " + c.warehouseReach + " blocks has room, or the holds are empty.");
				} else {
					click();
					viewer.sendSystemMessage(Component.literal("Unloaded " + Gui.n(moved) + " items into the warehouse.").withStyle(ChatFormatting.GREEN));
				}
			});
		} else {
			button(6, Gui.icon(Items.BARREL, Gui.text("Warehouse", ChatFormatting.DARK_GRAY), List.of(
				Gui.text("With the Warehouse mod, a rig unloads its", ChatFormatting.GRAY),
				Gui.text("holds into warehouses nearby by itself.", ChatFormatting.GRAY))), null);
		}

		button(8, Gui.icon(Items.BARRIER, Gui.text("Pack up the rig", ChatFormatting.RED, ChatFormatting.BOLD), List.of(
			Gui.text("Shift-click: take the rig down and get it back,", ChatFormatting.GRAY),
			Gui.text("with everything in its firebox and holds.", ChatFormatting.GRAY),
			Gui.text("Empty its crude tank first.", ChatFormatting.DARK_GRAY))), shift -> {
			if (!shift) {
				nope("Shift-click to pack up the rig.");
				return;
			}
			if (!rig.isOwner(viewer) && !viewer.isCreative()) {
				nope("Only " + rig.ownerName + " can pack up this rig.");
				return;
			}
			if (rig.crude > 0) {
				nope("There are still " + rig.crude + " buckets of crude in the rig's tank. Fill some buckets first.");
				return;
			}
			viewer.closeContainer();
			Rigs.packUp(rig, viewer);
		});
		view.icons[7] = ItemStack.EMPTY;
		fillRow();
	}
}
