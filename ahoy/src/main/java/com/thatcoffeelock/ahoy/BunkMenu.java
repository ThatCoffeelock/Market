package com.thatcoffeelock.ahoy;

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

/** The bunks: two berths on the foredeck. Click one with a bed on your cursor to slot it in, then lie down in it. */
final class BunkMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int[] BUNK_SLOTS = {11, 15};
	private static final int BACK = 18;
	private static final int CLOSE = 26;

	private final ServerPlayer viewer;
	private final Ship ship;
	private final SimpleContainer box;

	static void open(ServerPlayer player, Ship ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BunkMenu(id, player, ship, new SimpleContainer(SIZE)),
			Component.literal(ship.data.name + " · Bunks").withStyle(ChatFormatting.DARK_PURPLE)));
	}

	private BunkMenu(int syncId, ServerPlayer viewer, Ship ship, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.ship = ship;
		this.box = box;
		render();
	}

	private static ItemStack icon(Item item, Component name, Component... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Bottle.text(text, formats);
	}

	private void render() {
		ItemStack filler = icon(Items.GLASS_PANE, Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		box.setItem(4, icon(Items.FILLED_MAP, t("Bunks", ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Slot a bed in, then lie down in it.", ChatFormatting.GRAY),
			t("Night only. Anchored: rough water wakes you.", ChatFormatting.DARK_GRAY),
			t("Sneak (Shift) to get up. If everyone sleeps, the night is skipped.", ChatFormatting.DARK_GRAY)));
		for (int bunk = 0; bunk < BUNK_SLOTS.length; bunk++) {
			ItemStack held = ship.data.bunks.getItem(bunk);
			String name = ShipModel.BUNKS.get(bunk).name();
			if (BunkDeck.isBed(held)) {
				ItemStack shown = held.copy();
				shown.set(DataComponents.LORE, new ItemLore(List.of(t(name, ChatFormatting.GRAY), t("Click with an empty cursor to take it out.", ChatFormatting.YELLOW))));
				box.setItem(BUNK_SLOTS[bunk], shown);
				ServerPlayer in = ship.bunkDeck.sleeperIn(bunk);
				boolean busy = in != null && in != viewer;
				box.setItem(BUNK_SLOTS[bunk] + 9, icon(Items.RED_BED,
					t(busy ? in.getName().getString() + " is asleep here" : "Lie down", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
					t(ship.level.isBrightOutside() ? "Too bright to sleep" : "Good night", ChatFormatting.GRAY)));
			} else {
				box.setItem(BUNK_SLOTS[bunk], icon(Items.IRON_BARS, t(name, ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
					t("Empty. Click with a Bed on your cursor.", ChatFormatting.GRAY)));
			}
		}
		box.setItem(BACK, icon(Items.ARROW, t("Back to the ship menu", ChatFormatting.YELLOW)));
		box.setItem(CLOSE, icon(Items.BARRIER, t("Close", ChatFormatting.RED)));
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
				for (int bunk = 0; bunk < BUNK_SLOTS.length; bunk++) {
					if (slotId == BUNK_SLOTS[bunk]) {
						handleBunk(bunk);
					} else if (slotId == BUNK_SLOTS[bunk] + 9 && BunkDeck.isBed(ship.data.bunks.getItem(bunk))) {
						lieDown(bunk);
					}
				}
			}
		}
		render();
		sendAllDataToRemote();
	}

	private void handleBunk(int bunk) {
		if (!ship.mayCommand(viewer)) {
			nope("The bunks are locked by the captain.");
			return;
		}
		ItemStack inBunk = ship.data.bunks.getItem(bunk);
		ItemStack carried = getCarried();
		if (BunkDeck.isBed(inBunk)) {
			if (carried.isEmpty()) {
				if (ship.bunkDeck.sleeperIn(bunk) != null) {
					nope("Somebody's asleep in it.");
					return;
				}
				setCarried(inBunk.copy());
				ship.data.bunks.setItem(bunk, ItemStack.EMPTY);
			}
		} else if (BunkDeck.isBed(carried)) {
			ship.data.bunks.setItem(bunk, carried.split(1));
		}
	}

	private void lieDown(int bunk) {
		if (!ship.mayCommand(viewer)) {
			nope("The bunks are locked by the captain.");
			return;
		}
		if (Ships.shipOf(viewer) != ship) {
			nope("Climb aboard first.");
			return;
		}
		AhoyMod.nextTick(() -> {
			viewer.closeContainer();
			String why = ship.bunkDeck.lieDown(viewer, bunk);
			if (why != null) {
				nope(why);
			}
		});
	}

	private void nope(String why) {
		Cmd.sound(ship.level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
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
