package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Items;

/** The Refinery's screen: switches and gauges on top, the firebox below. */
final class RefineryMenu extends MachineMenu {
	private final Refinery refinery;

	private RefineryMenu(int syncId, ServerPlayer viewer, Refinery refinery) {
		super(syncId, viewer, 2, new View(2, List.of(new Section(refinery.firebox, 9, true)),
			() -> Machines.REFINERIES.containsValue(refinery) && viewer.distanceToSqr(refinery.pos.getX() + 0.5, refinery.pos.getY() + 0.5,
				refinery.pos.getZ() + 0.5) < 8 * 8));
		this.refinery = refinery;
		render();
	}

	static void open(ServerPlayer player, Refinery refinery) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RefineryMenu(id, player, refinery),
			Component.literal("Refinery").withStyle(ChatFormatting.DARK_GRAY)));
	}

	@Override
	void render() {
		FossilConfig c = FossilConfig.get();
		int cap = Refinery.capacity();
		button(0, Gui.icon(Items.LEVER, Gui.text("Still: " + (refinery.on ? "ON" : "OFF"), refinery.on ? ChatFormatting.GREEN : ChatFormatting.RED,
			ChatFormatting.BOLD), List.of(Gui.text("Click to switch it " + (refinery.on ? "off." : "on."), ChatFormatting.GRAY))), shift -> {
			click();
			refinery.on = !refinery.on;
			Store.changed();
		});

		List<Component> status = new ArrayList<>();
		status.add(Gui.text(refinery.state.text + ".", refinery.state == Refinery.State.WORKING ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		if (refinery.state == Refinery.State.WORKING) {
			status.add(Gui.text("Next bucket: " + Gui.bar(refinery.progress / c.refineTicks, 10), ChatFormatting.GRAY));
		}
		status.add(Gui.text(c.crudePerDiesel + " crude → 1 diesel, " + Math.round(c.refineHeat) + " blocks' worth of fuel each.", ChatFormatting.DARK_GRAY));
		button(1, Gui.icon(Items.COMPASS, Gui.text("Status", ChatFormatting.YELLOW, ChatFormatting.BOLD), status), null);

		button(2, Gui.icon(Items.BUCKET, Gui.text("Crude: " + refinery.crude + " / " + cap, ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), List.of(
			Gui.text(Gui.bar(refinery.crude / (double) cap, 10), ChatFormatting.GRAY),
			Gui.text("Click: pour in the crude you carry.", ChatFormatting.YELLOW),
			Gui.text("It also drinks from Oil Tanks within " + c.pipeReach + " blocks.", ChatFormatting.DARK_GRAY))), shift -> {
			int room = cap - refinery.crude;
			int n = Interactions.pourBuckets(viewer, Fluid.CRUDE, room);
			if (n == 0) {
				nope(room <= 0 ? "The refinery is full of crude." : "You aren't carrying any crude.");
				return;
			}
			refinery.crude += n;
			Store.changed();
			click();
		});

		button(3, Gui.icon(Items.LAVA_BUCKET, Gui.text("Diesel: " + refinery.diesel + " / " + cap, ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			Gui.text(Gui.bar(refinery.diesel / (double) cap, 10), ChatFormatting.GRAY),
			Gui.text("Click: fill the empty buckets you carry.", ChatFormatting.YELLOW),
			Gui.text("It also fills Oil Tanks within " + c.pipeReach + " blocks.", ChatFormatting.DARK_GRAY))), shift -> {
			int n = Interactions.fillBuckets(viewer, Fluid.DIESEL, refinery.diesel);
			if (n == 0) {
				nope(refinery.diesel == 0 ? "There's no diesel yet." : "You have no empty buckets.");
				return;
			}
			refinery.diesel -= n;
			Store.changed();
			click();
		});

		button(4, Gui.glow(Gui.icon(Items.BLAST_FURNACE, Gui.text("Refinery", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			Gui.text("Put fuel in the row below.", ChatFormatting.GRAY)))), null);

		List<Component> fuel = new ArrayList<>();
		fuel.add(Gui.text("In the fire: " + String.format(java.util.Locale.ROOT, "%.1f", refinery.energy) + " blocks' worth", ChatFormatting.GRAY));
		for (Fuel f : Fuel.values()) {
			fuel.add(Gui.text(" " + Fuel.ladderLine(f), f.color));
		}
		button(5, Gui.icon(Items.COAL, Gui.text("Fuel", ChatFormatting.GOLD, ChatFormatting.BOLD), fuel), null);
		for (int i = 6; i < 9; i++) {
			view.icons[i] = net.minecraft.world.item.ItemStack.EMPTY;
		}
		fillRow();
	}
}
