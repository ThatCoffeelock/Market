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

/** The Rig Workshop's screen: one button per upgrade track, and the way back to the rig. */
final class WorkshopMenu extends MachineMenu {
	private final Rig rig;
	private final ServerLevel level;

	private WorkshopMenu(int syncId, ServerPlayer viewer, Rig rig, ServerLevel level) {
		super(syncId, viewer, 2, new View(2, List.of(),
			() -> !Rigs.isRemoved(rig) && viewer.distanceToSqr(rig.modelX(), rig.modelY(), rig.modelZ()) < 16 * 16));
		this.rig = rig;
		this.level = level;
		render();
	}

	static void open(ServerPlayer player, Rig rig, ServerLevel level) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new WorkshopMenu(id, player, rig, level),
			Component.literal("Rig Workshop").withStyle(ChatFormatting.DARK_GRAY)));
	}

	@Override
	void render() {
		int slot = 1;
		for (Workshop.Track track : Workshop.TRACKS) {
			button(slot, icon(track), shift -> {
				String why = Workshop.upgrade(viewer, level, rig, track);
				if (why != null) {
					nope(why);
					return;
				}
				click();
				Cmd.sound(level, "minecraft:block.anvil.use", rig.modelX(), rig.modelY(), rig.modelZ(), 0.7f, 1.0f);
				int now = track.level(rig);
				viewer.sendSystemMessage(Component.literal(track.title() + " " + Workshop.roman(now) + ": " + track.levels().get(now).name() + ". ")
					.withStyle(ChatFormatting.GOLD).append(Component.literal(track.levels().get(now).blurb()).withStyle(ChatFormatting.YELLOW)));
				if (track == Workshop.SIZE) {
					viewer.sendSystemMessage(Component.literal("Hoppers and pipes now go up to " + rig.reach() + " blocks from the middle, around the wider shaft.")
						.withStyle(ChatFormatting.GRAY));
				}
			});
			slot += 2;
		}
		button(8, Gui.icon(Items.ARROW, Gui.text("Back to the rig", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of()), shift -> {
			click();
			RigMenu.open(viewer, rig, level);
		});
		for (int i : new int[] {0, 2, 4, 6}) {
			view.icons[i] = ItemStack.EMPTY;
		}
		fillRow();
	}

	private ItemStack icon(Workshop.Track track) {
		int level = track.level(rig);
		List<Component> lore = new ArrayList<>();
		lore.add(Gui.text("Now: " + track.levels().get(level).name() + (level > 0 ? " (" + Workshop.roman(level) + ")" : ""), ChatFormatting.GRAY));
		lore.add(Gui.text(track.levels().get(level).blurb(), ChatFormatting.DARK_GRAY));
		Workshop.Upgrade next = Workshop.next(track, rig);
		boolean ready = false;
		if (next == null) {
			lore.add(Component.empty());
			lore.add(Gui.text("Fully upgraded.", ChatFormatting.GREEN));
		} else {
			lore.add(Component.empty());
			lore.add(Gui.text("Next: " + next.name() + " (" + Workshop.roman(level + 1) + ")", ChatFormatting.YELLOW));
			lore.add(Gui.text(next.blurb(), ChatFormatting.GRAY));
			for (Workshop.Cost cost : next.costs()) {
				int have = Workshop.count(viewer, cost.item());
				boolean enough = viewer.isCreative() || have >= cost.count();
				lore.add(Gui.text(" " + cost.count() + " × " + cost.name() + " (you have " + have + ")", enough ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
			String why = Workshop.blocked(track, rig);
			if (why != null) {
				lore.add(Gui.text(why, ChatFormatting.RED));
			} else {
				ready = Workshop.canAfford(viewer, next);
				lore.add(Gui.text(ready ? "Click to upgrade." : "Bring the materials, then click.", ready ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
			}
		}
		ItemStack icon = Gui.icon(track.icon(), Gui.text(track.title() + (level > 0 ? " " + Workshop.roman(level) : ""), ChatFormatting.GOLD,
			ChatFormatting.BOLD), lore);
		return ready ? Gui.glow(icon) : icon;
	}
}
