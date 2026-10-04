package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** The Shipwright: one button per refit track (rigging, holds, canal drill). Click one to buy its next refit. */
final class ShipwrightMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int[] TRACK_SLOTS = {11, 13, 15};
	private static final int BACK = 18;
	private static final int CLOSE = 26;

	private final ServerPlayer viewer;
	private final Ship ship;
	private final SimpleContainer box;

	static void open(ServerPlayer player, Ship ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ShipwrightMenu(id, player, ship, new SimpleContainer(SIZE)),
			Component.literal(ship.data.name + " · Shipwright").withStyle(ChatFormatting.GOLD)));
	}

	private ShipwrightMenu(int syncId, ServerPlayer viewer, Ship ship, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.ship = ship;
		this.box = box;
		render();
	}

	private static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Bottle.text(text, formats);
	}

	private void render() {
		ItemStack filler = icon(Items.GLASS_PANE, Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		boolean mayBuy = ship.isOwner(viewer) || viewer.isCreative();
		box.setItem(4, icon(Items.ANVIL, t("Shipwright", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			t("Refits are paid in materials from your inventory.", ChatFormatting.GRAY),
			t(mayBuy ? "Click a refit to buy it." : "Only the captain can order refits.", ChatFormatting.YELLOW))));
		for (int i = 0; i < Shipwright.TRACKS.size(); i++) {
			box.setItem(TRACK_SLOTS[i], trackIcon(Shipwright.TRACKS.get(i), mayBuy));
		}
		box.setItem(BACK, icon(Items.ARROW, t("Back to the ship menu", ChatFormatting.YELLOW), List.of()));
		box.setItem(CLOSE, icon(Items.BARRIER, t("Close", ChatFormatting.RED), List.of()));
	}

	private ItemStack trackIcon(Shipwright.Track track, boolean mayBuy) {
		ShipData data = ship.data;
		int level = track.level(data);
		Shipwright.Upgrade now = track.levels().get(level);
		Shipwright.Upgrade next = Shipwright.next(track, data);
		List<Component> lore = new ArrayList<>();
		lore.add(t("Now: " + now.name() + (track.max() > 1 && level > 0 ? " (" + Shipwright.roman(level) + ")" : ""), ChatFormatting.GRAY));
		if (next == null) {
			lore.add(t("Fully fitted.", ChatFormatting.GOLD));
		} else {
			lore.add(t("Next: " + next.name(), ChatFormatting.YELLOW));
			lore.add(t(next.blurb(), ChatFormatting.DARK_GRAY));
			for (Shipwright.Cost cost : next.costs()) {
				int have = Shipwright.count(viewer, cost.item());
				lore.add(t("  " + cost.count() + " × " + cost.name() + "  (you have " + have + ")",
					have >= cost.count() || viewer.isCreative() ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
			if (mayBuy) {
				lore.add(t("Click to refit.", ChatFormatting.YELLOW));
			}
		}
		String title = track.title() + (track.max() > 1 ? " " + Shipwright.roman(level) : level > 0 ? " ✔" : "");
		return icon(track.icon(), t(title, ChatFormatting.GOLD, ChatFormatting.BOLD), lore);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId < 0 || slotId >= SIZE) {
			super.clicked(slotId, button, clickType, player);
			return;
		}
		if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE) {
			if (slotId == CLOSE) {
				AhoyMod.nextTick(viewer::closeContainer);
			} else if (slotId == BACK) {
				AhoyMod.nextTick(() -> ShipMenu.open(viewer, ship));
			} else {
				for (int i = 0; i < TRACK_SLOTS.length; i++) {
					if (slotId == TRACK_SLOTS[i]) {
						buy(Shipwright.TRACKS.get(i));
					}
				}
			}
		}
		render();
		sendAllDataToRemote();
	}

	private void buy(Shipwright.Track track) {
		if (Shipwright.next(track, ship.data) == null) {
			return;
		}
		String why = Shipwright.upgrade(viewer, ship, track);
		if (why != null) {
			Cmd.sound(ship.level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
			viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
			return;
		}
		Shipwright.Upgrade done = track.levels().get(track.level(ship.data));
		Cmd.sound(ship.level, "minecraft:block.anvil.use", viewer.getX(), viewer.getY(), viewer.getZ(), 0.8f, 1.1f);
		String extra = track == Shipwright.DRILL ? " Switch it on in the ship menu." : "";
		viewer.sendSystemMessage(Component.literal("Refitted: " + done.name() + "." + extra).withStyle(ChatFormatting.GOLD));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != box && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.container != box && super.canDragTo(slot);
	}

	@Override
	public boolean stillValid(Player player) {
		return ShipMenu.canUse(ship, player);
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
