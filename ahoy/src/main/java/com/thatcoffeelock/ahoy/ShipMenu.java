package com.thatcoffeelock.ahoy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.jetbrains.annotations.Nullable;

/** The captain's menu: a 3-row chest screen built on the server so vanilla clients can use it. */
final class ShipMenu extends ChestMenu {
	private static final int SIZE = 27;

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final Ship ship;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private boolean confirmBottle;

	static void open(ServerPlayer player, Ship ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ShipMenu(id, player, ship),
			Component.literal(ship.data.name).withStyle(ChatFormatting.DARK_BLUE)));
	}

	static void openCargo(ServerPlayer player, Ship ship, int bay) {
		SimpleContainer cargo = bay == 0 ? ship.data.cargoA : ship.data.cargoB;
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, cargo, 6) {
			@Override
			public boolean stillValid(Player who) {
				return canUse(ship, who);
			}
		}, Component.literal(ship.data.name + " · Cargo " + (bay == 0 ? "A (port)" : "B (starboard)"))));
	}

	static boolean canUse(Ship ship, Player player) {
		return !ship.isRemoved() && (Ships.shipOf(player) == ship || player.distanceToSqr(ship.root) <= 24 * 24);
	}

	private ShipMenu(int syncId, ServerPlayer viewer, Ship ship) {
		this(syncId, viewer, ship, new SimpleContainer(SIZE));
	}

	private ShipMenu(int syncId, ServerPlayer viewer, Ship ship, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.ship = ship;
		this.box = box;
		render();
	}

	// ---------------------------------------------------------------- icons

	private static final Map<String, Item> ITEMS = new HashMap<>();

	private static Item item(String id, Item fallback) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id);
		return item == null || item == Items.AIR ? fallback : item;
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

	private void button(int slot, ItemStack icon, @Nullable Action action) {
		box.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	private void render() {
		actions.clear();
		ItemStack filler = icon(item("minecraft:blue_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		ShipData data = ship.data;
		boolean owner = ship.isOwner(viewer);
		boolean command = ship.mayCommand(viewer);

		button(4, icon(Items.FILLED_MAP, t(data.name, ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Captain: " + (data.ownerName.isEmpty() ? "nobody" : data.ownerName), ChatFormatting.GRAY),
			t("Cargo: " + data.usedSlots() + " / " + (ShipData.BAY * 2) + " slots", ChatFormatting.GRAY),
			t("Wind: " + Wind.arrow(ship.level, ship.root.getYRot()) + " " + Wind.label(ship.level, ship.root.getYRot()), ChatFormatting.GRAY),
			t("W/S sails · A/D rudder · Space bell · Shift ashore", ChatFormatting.DARK_GRAY)), null);

		int mine = ship.seatOf(viewer);
		button(10, icon(Items.SADDLE, t(mine >= 0 ? "Switch seat" : "Climb aboard", ChatFormatting.GREEN, ChatFormatting.BOLD),
			t(mine >= 0 ? "You're at: " + ship.seatName(mine) : "Takes the first free spot.", ChatFormatting.GRAY),
			t("Moving to the wheel makes you captain (if allowed).", ChatFormatting.DARK_GRAY)), this::switchSeat);

		button(12, icon(Items.BARREL, t("Cargo A (port)", ChatFormatting.AQUA, ChatFormatting.BOLD),
			t("54 slots below deck.", ChatFormatting.GRAY)), () -> cargo(0));
		button(13, icon(Items.BARREL, t("Cargo B (starboard)", ChatFormatting.AQUA, ChatFormatting.BOLD),
			t("54 slots below deck.", ChatFormatting.GRAY)), () -> cargo(1));
		button(15, icon(Items.BELL, t("Ring the bell", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			t("Ding. Absolutely necessary.", ChatFormatting.GRAY)), ship::ringBell);

		if (owner) {
			button(21, icon(data.locked ? Items.IRON_BARS : Items.TRIPWIRE_HOOK,
				t(data.locked ? "Locked" : "Unlocked", data.locked ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD),
				t(data.locked ? "Only you can steer and open the cargo." : "Anyone can steer and open the cargo.", ChatFormatting.GRAY),
				t("Click to toggle.", ChatFormatting.YELLOW)), () -> {
				data.locked = !data.locked;
				render();
			});
		}
		if (owner || viewer.isCreative()) {
			button(23, icon(Items.GLASS_BOTTLE, t(confirmBottle ? "Click again to bottle it up" : "Bottle it up", ChatFormatting.GOLD, ChatFormatting.BOLD),
				t("Shrinks the ship back into a Ship in a Bottle.", ChatFormatting.GRAY),
				t("The cargo comes along inside it.", ChatFormatting.GRAY),
				t("Everyone aboard is put ashore first.", ChatFormatting.DARK_GRAY)), () -> {
				if (!confirmBottle) {
					confirmBottle = true;
					render();
					return;
				}
				AhoyMod.nextTick(() -> {
					viewer.closeContainer();
					String name = ship.data.name;
					Bottle.give(viewer, ship.bottleUp());
					viewer.sendSystemMessage(Component.literal("The " + name + " is back in its bottle.").withStyle(ChatFormatting.GOLD));
				});
			});
		}
		if (ship.gunDeck != null) {
			button(14, icon(Items.IRON_BLOCK, t("Gun deck", ChatFormatting.RED, ChatFormatting.BOLD),
				t("Four gun ports: slot cannons in, man them.", ChatFormatting.GRAY),
				t("Cannons: " + gunCount() + " / " + ShipData.GUNS, ChatFormatting.GRAY)), () -> AhoyMod.nextTick(() -> GunMenu.open(viewer, ship)));
		}
		button(26, icon(Items.BARRIER, t("Close", ChatFormatting.RED)), () -> AhoyMod.nextTick(viewer::closeContainer));
		extraButtons();
	}

	/** Buttons other mods added through {@link AhoyApi#addMenuButton} (e.g. the Warehouse's Loading Dock). */
	private void extraButtons() {
		int[] slots = {19, 25};
		List<AhoyApi.MenuButton> extra = AhoyApi.menuButtons();
		for (int i = 0; i < extra.size() && i < slots.length; i++) {
			AhoyApi.MenuButton extension = extra.get(i);
			try {
				ItemStack icon = extension.icon(viewer, ship);
				if (icon != null) {
					button(slots[i], icon, () -> {
						extension.click(viewer, ship);
						render();
					});
				}
			} catch (RuntimeException e) {
				AhoyMod.LOG.warn("A ship menu button from another mod failed", e);
			}
		}
	}

	private int gunCount() {
		int n = 0;
		for (int i = 0; i < ShipData.GUNS; i++) {
			n += ship.gunDeck.accepts(ship.data.guns.getItem(i)) ? 1 : 0;
		}
		return n;
	}

	private void cargo(int bay) {
		if (!ship.mayCommand(viewer)) {
			nope("The cargo is locked by the captain.");
			return;
		}
		AhoyMod.nextTick(() -> openCargo(viewer, ship, bay));
	}

	private void switchSeat() {
		int current = ship.seatOf(viewer);
		if (current < 0) {
			AhoyMod.nextTick(() -> {
				viewer.closeContainer();
				int seat = ship.pickSeat(viewer);
				if (seat < 0 || !ship.seat(viewer, seat)) {
					nope("No room aboard.");
				}
			});
			return;
		}
		int n = ship.seatCount();
		for (int k = 1; k <= n; k++) {
			int i = (current + k) % n;
			if (i == current || (i == 0 && !ship.mayCommand(viewer)) || !ship.seatFree(i)) {
				continue;
			}
			if (ship.seat(viewer, i)) {
				viewer.sendSystemMessage(Component.literal("Moved to: " + ship.seatName(i)).withStyle(ChatFormatting.GREEN));
				render();
				return;
			}
		}
		nope("No other spot is free.");
	}

	private void nope(String why) {
		Cmd.sound(ship.level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run();
			}
			sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, button, clickType, player);
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
		return canUse(ship, player);
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
