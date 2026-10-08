package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Industrial Oven's screen: switches and gauges on top, the input row, then two rows of output. */
final class OvenMenu extends MachineMenu {
	private final Oven oven;

	private OvenMenu(int syncId, ServerPlayer viewer, Oven oven) {
		super(syncId, viewer, 4, new View(4, List.of(new Section(oven.input, 9, true), new Section(oven.output, 18, false)),
			() -> Machines.OVENS.containsValue(oven) && viewer.distanceToSqr(oven.pos.getX() + 0.5, oven.pos.getY() + 0.5,
				oven.pos.getZ() + 0.5) < 8 * 8));
		this.oven = oven;
		render();
	}

	static void open(ServerPlayer player, Oven oven) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new OvenMenu(id, player, oven),
			Component.literal("Industrial Oven").withStyle(ChatFormatting.DARK_GRAY)));
	}

	@Override
	void render() {
		FossilConfig c = FossilConfig.get();
		int cap = Oven.capacity();
		button(0, Gui.icon(Items.LEVER, Gui.text("Burners: " + (oven.on ? "ON" : "OFF"), oven.on ? ChatFormatting.GREEN : ChatFormatting.RED,
			ChatFormatting.BOLD), List.of(Gui.text("Click to switch it " + (oven.on ? "off." : "on."), ChatFormatting.GRAY))), shift -> {
			click();
			oven.on = !oven.on;
			Store.changed();
		});

		List<Component> status = new ArrayList<>();
		status.add(Gui.text(oven.state.text + ".", oven.state == Oven.State.WORKING ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		status.add(Gui.text(Gui.n(oven.smelted) + " items smelted so far.", ChatFormatting.GRAY));
		if (oven.state == Oven.State.FULL) {
			status.add(Gui.text("Take the output, or put a hopper under the oven.", ChatFormatting.YELLOW));
		}
		if (oven.state == Oven.State.IDLE && !oven.input.isEmpty()) {
			status.add(Gui.text("Nothing in the top row smelts.", ChatFormatting.YELLOW));
		}
		button(1, Gui.icon(Items.COMPASS, Gui.text("Status", ChatFormatting.YELLOW, ChatFormatting.BOLD), status), null);

		button(2, Gui.icon(Items.LAVA_BUCKET, Gui.text("Diesel: " + oven.diesel + " / " + cap, ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			Gui.text(Gui.bar(oven.diesel / (double) cap, 10), ChatFormatting.GRAY),
			Gui.text("Burning: " + Math.max(0, oven.charge) + " items left on this bucket.", ChatFormatting.GRAY),
			Gui.text("Click: pour in the diesel you carry.", ChatFormatting.YELLOW),
			Gui.text("It also drinks from Tanks within " + c.pipeReach + " blocks, or on its pipeline.", ChatFormatting.DARK_GRAY))), shift -> {
			int room = cap - oven.diesel;
			int n = Interactions.pourBuckets(viewer, Fluid.DIESEL, room);
			if (n == 0) {
				nope(room <= 0 ? "The oven's diesel tank is full." : "You aren't carrying any diesel.");
				return;
			}
			oven.diesel += n;
			Store.changed();
			click();
		});

		button(4, Gui.glow(Gui.icon(Items.SMOKER, Gui.text("Industrial Oven", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			Gui.text("Row 2: put in anything a furnace takes.", ChatFormatting.GRAY),
			Gui.text("Rows 3-4: what comes out. Take it like from a chest.", ChatFormatting.GRAY),
			Gui.text("One bucket of diesel smelts " + c.ovenItemsPerDiesel + " items.", ChatFormatting.DARK_GRAY),
			Gui.text("Hoppers: in at the top, out at the bottom.", ChatFormatting.DARK_GRAY)))), null);

		button(5, Gui.glow(Gui.icon(Items.RAW_IRON, Gui.text("Double smelt", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
			Gui.text("Ores, raw iron, gold and copper and", ChatFormatting.GRAY),
			Gui.text("ancient debris come out double.", ChatFormatting.GRAY),
			Gui.text("A Drill Rig on its pipeline sends its ores here first.", ChatFormatting.DARK_GRAY)))), null);
		for (int i : new int[] {3, 6, 7, 8}) {
			view.icons[i] = ItemStack.EMPTY;
		}
		fillRow();
	}
}
