package com.thatcoffeelock.blimey;

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

/** The Engineer: one button per refit track (engines, fuel economy, holds). Click one to buy its next refit. */
final class EngineerMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int[] TRACK_SLOTS = {11, 13, 15};
	private static final int BACK = 18;
	private static final int CLOSE = 26;

	private final ServerPlayer viewer;
	private final Airship ship;
	private final SimpleContainer box;

	static void open(ServerPlayer player, Airship ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new EngineerMenu(id, player, ship, new SimpleContainer(SIZE)),
			Component.literal(ship.data.name + " · Engineer").withStyle(ChatFormatting.GOLD)));
	}

	private EngineerMenu(int syncId, ServerPlayer viewer, Airship ship, SimpleContainer box) {
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
		return BlimeyItems.text(text, formats);
	}

	private void render() {
		ItemStack filler = icon(Items.GLASS_PANE, Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		boolean mayBuy = ship.isOwner(viewer) || viewer.isCreative();
		box.setItem(4, icon(Items.ANVIL, t("Engineer", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			t("Refits are paid in materials from your inventory.", ChatFormatting.GRAY),
			t("Faster engines drink more at full throttle; economy refits cut every burn.", ChatFormatting.DARK_GRAY),
			t(mayBuy ? "Click a refit to buy it." : "Only the captain can order refits.", ChatFormatting.YELLOW))));
		for (int i = 0; i < Engineer.TRACKS.size(); i++) {
			box.setItem(TRACK_SLOTS[i], trackIcon(Engineer.TRACKS.get(i), mayBuy));
		}
		box.setItem(BACK, icon(Items.ARROW, t("Back to the airship menu", ChatFormatting.YELLOW), List.of()));
		box.setItem(CLOSE, icon(Items.BARRIER, t("Close", ChatFormatting.RED), List.of()));
	}

	private ItemStack trackIcon(Engineer.Track track, boolean mayBuy) {
		AirshipData data = ship.data;
		int level = track.level(data);
		Engineer.Upgrade now = track.levels().get(level);
		Engineer.Upgrade next = Engineer.next(track, data);
		List<Component> lore = new ArrayList<>();
		lore.add(t("Now: " + now.name() + (level > 0 ? " (" + Engineer.roman(level) + ")" : ""), ChatFormatting.GRAY));
		if (next == null) {
			lore.add(t("Fully fitted.", ChatFormatting.GOLD));
		} else {
			lore.add(t("Next: " + next.name(), ChatFormatting.YELLOW));
			lore.add(t(next.blurb(), ChatFormatting.DARK_GRAY));
			for (Engineer.Cost cost : next.costs()) {
				int have = Engineer.count(viewer, cost.item());
				lore.add(t("  " + cost.count() + " × " + cost.name() + "  (you have " + have + ")",
					have >= cost.count() || viewer.isCreative() ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
			if (mayBuy) {
				lore.add(t("Click to refit.", ChatFormatting.YELLOW));
			}
		}
		return icon(Engineer.item(track.icon()), t(track.title() + " " + Engineer.roman(level), ChatFormatting.GOLD, ChatFormatting.BOLD), lore);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId < 0 || slotId >= SIZE) {
			super.clicked(slotId, button, clickType, player);
			return;
		}
		if (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE) {
			if (slotId == CLOSE) {
				BlimeyMod.nextTick(viewer::closeContainer);
			} else if (slotId == BACK) {
				BlimeyMod.nextTick(() -> AirshipMenu.open(viewer, ship));
			} else {
				for (int i = 0; i < TRACK_SLOTS.length; i++) {
					if (slotId == TRACK_SLOTS[i]) {
						buy(Engineer.TRACKS.get(i));
					}
				}
			}
		}
		render();
		sendAllDataToRemote();
	}

	private void buy(Engineer.Track track) {
		if (Engineer.next(track, ship.data) == null) {
			return;
		}
		String why = Engineer.upgrade(viewer, ship, track);
		if (why != null) {
			Cmd.sound(ship.level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
			viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
			return;
		}
		Engineer.Upgrade done = track.levels().get(track.level(ship.data));
		Cmd.sound(ship.level, "minecraft:block.anvil.use", viewer.getX(), viewer.getY(), viewer.getZ(), 0.8f, 1.1f);
		viewer.sendSystemMessage(Component.literal("Refitted: " + done.name() + ".").withStyle(ChatFormatting.GOLD));
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
		return AirshipMenu.canUse(ship, player);
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
