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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The gun deck: four gun ports. Click one with a Cannon on your cursor to slot it in, click a slotted cannon with an
 * empty cursor to take it out; the button under a port sits you behind that cannon.
 */
final class GunMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int[] PORT_SLOTS = {10, 12, 14, 16};
	private static final int BACK = 18;
	private static final int CLOSE = 26;

	private final ServerPlayer viewer;
	private final Ship ship;
	private final GunDeck deck;
	private final SimpleContainer box;

	static void open(ServerPlayer player, Ship ship) {
		if (ship.gunDeck == null) {
			return;
		}
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GunMenu(id, player, ship, new SimpleContainer(SIZE)),
			Component.literal(ship.data.name + " · Gun deck").withStyle(ChatFormatting.DARK_RED)));
	}

	private GunMenu(int syncId, ServerPlayer viewer, Ship ship, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.ship = ship;
		this.deck = ship.gunDeck;
		this.box = box;
		render();
	}

	private static ItemStack icon(net.minecraft.world.item.Item item, Component name, Component... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Bottle.text(text, formats);
	}

	private boolean aboard() {
		return Ships.shipOf(viewer) == ship;
	}

	private void render() {
		ItemStack filler = icon(Items.GRAY_STAINED_GLASS_PANE, Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		box.setItem(4, icon(Items.FILLED_MAP, t("Gun deck", ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Cannonballs in the holds: " + deck.ballsInHold(), ChatFormatting.GRAY),
			t("Gunners shoot with their own balls first.", ChatFormatting.DARK_GRAY)));
		for (int port = 0; port < ShipData.GUNS; port++) {
			ItemStack held = ship.data.guns.getItem(port);
			String name = ShipModel.GUN_PORTS.get(port).name();
			if (deck.accepts(held)) {
				ItemStack shown = held.copy();
				shown.set(DataComponents.LORE, new ItemLore(List.of(t(name, ChatFormatting.GRAY), t("Click with an empty cursor to take it out.", ChatFormatting.YELLOW))));
				box.setItem(PORT_SLOTS[port], shown);
				var gunner = deck.gunner(port);
				boolean busy = gunner != null && gunner != viewer;
				box.setItem(PORT_SLOTS[port] + 9, icon(Items.TNT,
					t(gunner == viewer ? "You're manning it" : busy ? gunner.getName().getString() + " is manning it" : "Man this cannon", ChatFormatting.RED, ChatFormatting.BOLD),
					t(deck.reload(port) > 0 ? "Reloading…" : "Ready", ChatFormatting.GRAY),
					t("Right-click aims and fires, Shift gets you down.", ChatFormatting.DARK_GRAY)));
			} else {
				box.setItem(PORT_SLOTS[port], icon(Items.IRON_BARS, t(name, ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
					t("Empty. Click with a Cannon on your cursor.", ChatFormatting.GRAY)));
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
				for (int port = 0; port < ShipData.GUNS; port++) {
					if (slotId == PORT_SLOTS[port]) {
						handlePort(port);
					} else if (slotId == PORT_SLOTS[port] + 9 && deck.accepts(ship.data.guns.getItem(port))) {
						man(port);
					}
				}
			}
		}
		render();
		sendAllDataToRemote();
	}

	private void handlePort(int port) {
		if (!ship.mayCommand(viewer)) {
			nope("The guns are locked by the captain.");
			return;
		}
		ItemStack inPort = ship.data.guns.getItem(port);
		ItemStack carried = getCarried();
		if (deck.accepts(inPort)) {
			if (carried.isEmpty()) {
				if (deck.gunner(port) != null) {
					nope("Somebody's manning that cannon.");
					return;
				}
				setCarried(inPort.copy());
				ship.data.guns.setItem(port, ItemStack.EMPTY);
			}
		} else if (deck.accepts(carried)) {
			ship.data.guns.setItem(port, carried.split(1));
		}
	}

	private void man(int port) {
		if (!ship.mayCommand(viewer)) {
			nope("The guns are locked by the captain.");
			return;
		}
		if (!aboard()) {
			nope("Climb aboard first.");
			return;
		}
		AhoyMod.nextTick(() -> {
			viewer.closeContainer();
			if (!deck.man(viewer, port, ship.seatOf(viewer))) {
				nope("You can't man that one right now.");
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
