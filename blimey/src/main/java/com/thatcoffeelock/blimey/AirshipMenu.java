package com.thatcoffeelock.blimey;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import org.jetbrains.annotations.Nullable;

/** The captain's menu: a 3-row chest screen built on the server so vanilla clients can use it. */
final class AirshipMenu extends ChestMenu {
	private static final int SIZE = 27;
	static final String[] HOLD_NAMES = {"Cargo A", "Cargo B", "Cargo C", "Cargo D", "Cargo E"};

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final Airship ship;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private boolean confirmPack;

	static void open(ServerPlayer player, Airship ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AirshipMenu(id, player, ship, new SimpleContainer(SIZE)),
			Component.literal(ship.data.name).withStyle(ChatFormatting.DARK_RED)));
	}

	static void openCargo(ServerPlayer player, Airship ship, int bay) {
		List<SimpleContainer> holds = ship.data.holds();
		if (bay < 0 || bay >= holds.size()) {
			return;
		}
		SimpleContainer cargo = holds.get(bay);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, cargo, 6) {
			@Override
			public boolean stillValid(Player who) {
				return canUse(ship, who);
			}
		}, Component.literal(ship.data.name + " · " + HOLD_NAMES[bay])));
	}

	/** The fuel tank: one row. Diesel in, empty buckets out. */
	static void openTank(ServerPlayer player, Airship ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, ship.data.tank, 1) {
			@Override
			public boolean stillValid(Player who) {
				return canUse(ship, who);
			}
		}, Component.literal(ship.data.name + " · Fuel tank (diesel)")));
	}

	static boolean canUse(Airship ship, Player player) {
		return !ship.isRemoved() && (Airships.shipOf(player) == ship || player.distanceToSqr(ship.root) <= 24 * 24);
	}

	private AirshipMenu(int syncId, ServerPlayer viewer, Airship ship, SimpleContainer box) {
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
		return BlimeyItems.text(text, formats);
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
		ItemStack filler = icon(Engineer.item("minecraft:gray_stained_glass_pane"), Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		AirshipData data = ship.data;
		boolean owner = ship.isOwner(viewer);
		double left = ship.dieselLeft();
		double perBucket = BlimeyConfig.get().ticksPerBucket / 20.0 / Engineer.burnFactor(data.efficiencyLevel);

		button(4, icon(Items.FILLED_MAP, t(data.name, ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Captain: " + (data.ownerName.isEmpty() ? "nobody" : data.ownerName), ChatFormatting.GRAY),
			t("Diesel: " + String.format(Locale.ROOT, "%.1f", left) + " buckets" + (ship.burning ? " (engines running)" : ""), ChatFormatting.GRAY),
			t("A bucket lasts about " + Math.round(perBucket / 60.0 * 10) / 10.0 + " min cruising, longer hovering.", ChatFormatting.DARK_GRAY),
			t("Cargo: " + data.usedSlots() + " / " + data.capacity() + " slots", ChatFormatting.GRAY),
			t("W/S throttle · A/D turn · Space up · Ctrl down · Shift bail out", ChatFormatting.DARK_GRAY)), null);

		int mine = ship.seatOf(viewer);
		button(10, icon(Items.SADDLE, t(mine >= 0 ? "Switch seat" : "Climb aboard", ChatFormatting.GREEN, ChatFormatting.BOLD),
			t(mine >= 0 ? "You're at: " + ship.seatName(mine) : "Takes the first free seat.", ChatFormatting.GRAY),
			t("Moving to the controls makes you captain (if allowed).", ChatFormatting.DARK_GRAY)), this::switchSeat);

		int holds = data.holds().size();
		for (int bay = 0; bay < holds; bay++) {
			final int b = bay;
			button(11 + bay, icon(bay < 2 ? Items.CHEST : Items.BARREL, t(HOLD_NAMES[bay], ChatFormatting.AQUA, ChatFormatting.BOLD),
				t("54 slots.", ChatFormatting.GRAY)), () -> cargo(b));
		}
		button(16, icon(Engineer.item("minecraft:lava_bucket"), t("Fuel tank", ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Put Buckets of Diesel in here (from a Fossil Fool Refinery).", ChatFormatting.GRAY),
			t("The engines take one at a time; empty buckets come back out.", ChatFormatting.GRAY),
			t("In the tank: " + data.dieselAboard() + " buckets", ChatFormatting.YELLOW)), this::tank);
		button(17, icon(Engineer.item("minecraft:tnt"), t("Bombing 101", ChatFormatting.RED, ChatFormatting.BOLD),
			t("Hold a bomb and right-click while aboard: it drops out of the hatch.", ChatFormatting.GRAY),
			t("It keeps the airship's speed, so it lands ahead of you. Lead the target.", ChatFormatting.GRAY),
			t("Crew don't take blast or fall damage aboard. Everyone else does.", ChatFormatting.DARK_GRAY)), null);

		button(22, icon(Items.ANVIL, t("Engineer", ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Engines " + Engineer.roman(data.speedLevel) + " · Economy " + Engineer.roman(data.efficiencyLevel) + " · " + holds + " holds", ChatFormatting.GRAY),
			t("Refit for speed, fuel economy and cargo. Not cheap.", ChatFormatting.DARK_GRAY)), () -> BlimeyMod.nextTick(() -> EngineerMenu.open(viewer, ship)));

		if (owner) {
			button(21, icon(data.locked ? Items.IRON_BARS : Items.TRIPWIRE_HOOK,
				t(data.locked ? "Locked" : "Unlocked", data.locked ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD),
				t(data.locked ? "Only you can fly it and open the holds and tank." : "Anyone can fly it and open the holds and tank.", ChatFormatting.GRAY),
				t("Click to toggle.", ChatFormatting.YELLOW)), () -> {
				data.locked = !data.locked;
				render();
			});
		}
		if (owner || viewer.isCreative()) {
			button(23, icon(Items.PAPER, t(confirmPack ? "Click again to fold it up" : "Fold it up", ChatFormatting.GOLD, ChatFormatting.BOLD),
				t("Turns the airship back into a Flat-Pack Airship.", ChatFormatting.GRAY),
				t("Refits, diesel and cargo come along inside it.", ChatFormatting.GRAY),
				t("Only on the ground. Everyone aboard gets off first.", ChatFormatting.DARK_GRAY)), this::packUp);
		}
		button(26, icon(Items.BARRIER, t("Close", ChatFormatting.RED)), () -> BlimeyMod.nextTick(viewer::closeContainer));
	}

	private void cargo(int bay) {
		if (!ship.mayCommand(viewer)) {
			nope("The holds are locked by the captain.");
			return;
		}
		BlimeyMod.nextTick(() -> openCargo(viewer, ship, bay));
	}

	private void tank() {
		if (!ship.mayCommand(viewer)) {
			nope("The fuel tank is locked by the captain.");
			return;
		}
		BlimeyMod.nextTick(() -> openTank(viewer, ship));
	}

	private void packUp() {
		if (!ship.isGrounded()) {
			nope("Land first. Folding an airship in mid-air is how you get a very fast airship.");
			return;
		}
		if (!confirmPack) {
			confirmPack = true;
			render();
			return;
		}
		BlimeyMod.nextTick(() -> {
			viewer.closeContainer();
			if (ship.isRemoved()) {
				return;
			}
			String name = ship.data.name;
			BlimeyItems.give(viewer, ship.packUp());
			viewer.sendSystemMessage(Component.literal("The " + name + " folds itself up with an awful lot of swearing.").withStyle(ChatFormatting.GOLD));
		});
	}

	private void switchSeat() {
		int current = ship.seatOf(viewer);
		if (current < 0) {
			BlimeyMod.nextTick(() -> {
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
			if (i == current || (i == AirshipModel.CAPTAIN && !ship.mayCommand(viewer)) || !ship.seatFree(i)) {
				continue;
			}
			if (ship.seat(viewer, i)) {
				viewer.sendSystemMessage(Component.literal("Moved to: " + ship.seatName(i)).withStyle(ChatFormatting.GREEN));
				render();
				return;
			}
		}
		nope("No other seat is free.");
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
