package com.thatcoffeelock.mobilehome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import org.jetbrains.annotations.Nullable;

/**
 * The vehicle's dashboard: a 3-row chest screen built on the server so vanilla clients can use it.
 * Slot 11 is a real slot you drop fuel into; everything else is a button.
 */
final class VehicleMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int GAUGE = 10;
	private static final int INTAKE = 11;

	@FunctionalInterface
	private interface Action {
		void run(int button, ContainerInput type);
	}

	private final ServerPlayer viewer;
	private final Vehicle vehicle;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private int shownFuel = -1;
	private boolean confirmPack;

	static void open(ServerPlayer player, Vehicle vehicle) {
		String title = vehicle.data.ownerName.isEmpty() ? vehicle.type.displayName : vehicle.data.ownerName + "'s " + vehicle.type.displayName;
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new VehicleMenu(id, player, vehicle),
			Component.literal(title).withStyle(ChatFormatting.DARK_GREEN)));
	}

	private VehicleMenu(int syncId, ServerPlayer viewer, Vehicle vehicle) {
		this(syncId, viewer, vehicle, new SimpleContainer(SIZE));
	}

	private VehicleMenu(int syncId, ServerPlayer viewer, Vehicle vehicle, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.vehicle = vehicle;
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
		return Kits.text(text, formats);
	}

	// ---------------------------------------------------------------- layout

	private boolean isButton(int slot) {
		return slot >= 0 && slot < SIZE && slot != INTAKE;
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
		ItemStack filler = icon(item("minecraft:gray_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			if (isButton(i)) {
				box.setItem(i, filler.copy());
			}
		}
		VehicleData data = vehicle.data;
		boolean owner = vehicle.isOwner(viewer);
		boolean mayUse = vehicle.mayDrive(viewer);

		shownFuel = data.fuelPercent();
		button(GAUGE, icon(data.fuel > 0 ? Items.COAL : Items.CHARCOAL, t("Fuel: " + data.fuelPercent() + "%", ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("About " + data.fuelTime() + " of driving left.", ChatFormatting.GRAY),
			Component.empty(),
			t("Drop fuel in the slot to the right →", ChatFormatting.YELLOW),
			t("Coal, charcoal, wood, lava buckets, blaze rods:", ChatFormatting.DARK_GRAY),
			t("if a furnace burns it, so do we.", ChatFormatting.DARK_GRAY)), null);

		button(13, icon(Items.BARREL, t("Storage", ChatFormatting.AQUA, ChatFormatting.BOLD),
			t(data.usedSlots() + " / " + VehicleData.SLOTS + " slots used", ChatFormatting.GRAY),
			mayUse ? t("Click to open.", ChatFormatting.YELLOW) : t("Locked by " + data.ownerName + ".", ChatFormatting.RED)), (b, type) -> {
			if (!vehicle.mayDrive(viewer)) {
				nope("The owner locked the storage.");
				return;
			}
			MobileHomeMod.nextTick(() -> openStorage(viewer, vehicle));
		});

		button(14, icon(Items.CRAFTING_TABLE, t("Workbench", ChatFormatting.AQUA, ChatFormatting.BOLD),
			t("A crafting table on wheels.", ChatFormatting.GRAY)), (b, type) ->
			MobileHomeMod.nextTick(() -> openCrafting(viewer, vehicle)));

		button(15, icon(Items.ENDER_CHEST, t("Ender Stash", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
			t("Your own ender chest, glovebox edition.", ChatFormatting.GRAY)), (b, type) ->
			MobileHomeMod.nextTick(() -> viewer.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> ChestMenu.threeRows(id, inv, p.getEnderChestInventory()), Component.literal("Ender Stash")))));

		int mySeat = vehicle.seatOf(viewer);
		button(19, icon(Items.SADDLE, t(mySeat >= 0 ? "Switch seat" : "Get in", ChatFormatting.GREEN, ChatFormatting.BOLD),
			t(mySeat >= 0 ? "You're in: " + vehicle.type.seats[mySeat].name() : "Hop in the first free seat.", ChatFormatting.GRAY),
			t(seatSummary(), ChatFormatting.DARK_GRAY)), (b, type) -> switchSeat());

		if (owner) {
			button(21, icon(data.locked ? Items.IRON_BARS : Items.TRIPWIRE_HOOK,
				t(data.locked ? "Locked" : "Unlocked", data.locked ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD),
				t(data.locked ? "Only you can drive and open the storage." : "Anyone can drive and open the storage.", ChatFormatting.GRAY),
				t("Passengers can always get in.", ChatFormatting.DARK_GRAY),
				t("Click to toggle.", ChatFormatting.YELLOW)), (b, type) -> {
				data.locked = !data.locked;
				click();
				render();
			});
		}

		button(22, icon(Items.NOTE_BLOCK, t("Honk", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			t("For emergencies. And for fun. Mostly for fun.", ChatFormatting.GRAY)), (b, type) -> vehicle.honk());

		if (owner || viewer.isCreative()) {
			button(24, icon(vehicle.type.kitItem(), t(confirmPack ? "Click again to pack up" : "Pack up", ChatFormatting.GOLD, ChatFormatting.BOLD),
				t("Turns the vehicle back into an item.", ChatFormatting.GRAY),
				t("Fuel and storage come along inside it.", ChatFormatting.GRAY),
				t("Everyone inside gets out first.", ChatFormatting.DARK_GRAY)), (b, type) -> {
				if (!confirmPack) {
					confirmPack = true;
					click();
					render();
					return;
				}
				packUp();
			});
		}

		button(26, icon(Items.BARRIER, t("Close", ChatFormatting.RED)), (b, type) -> MobileHomeMod.nextTick(viewer::closeContainer));
	}

	private String seatSummary() {
		StringBuilder sb = new StringBuilder();
		int free = 0;
		for (int i = 0; i < vehicle.seats.length; i++) {
			if (vehicle.seats[i] != null && vehicle.seats[i].getPassengers().isEmpty()) {
				free++;
			}
		}
		sb.append(free).append(" of ").append(vehicle.seats.length).append(" seats free");
		return sb.toString();
	}

	private void switchSeat() {
		int current = vehicle.seatOf(viewer);
		if (current < 0) {
			MobileHomeMod.nextTick(() -> {
				viewer.closeContainer();
				Vehicles.board(viewer, vehicle);
			});
			return;
		}
		int n = vehicle.seats.length;
		for (int k = 1; k <= n; k++) {
			int i = (current + k) % n;
			if (i == 0 && !vehicle.mayDrive(viewer)) {
				continue;
			}
			if (vehicle.seats[i] != null && vehicle.seats[i].getPassengers().isEmpty()) {
				if (vehicle.seat(viewer, i)) {
					click();
					viewer.sendSystemMessage(Component.literal("Moved to: " + vehicle.type.seats[i].name()).withStyle(ChatFormatting.GREEN));
					render();
					return;
				}
			}
		}
		nope("No other seat is free.");
	}

	private void packUp() {
		if (!vehicle.isOwner(viewer) && !viewer.isCreative()) {
			nope("Only the owner can pack it up.");
			return;
		}
		ItemStack kit = Kits.packed(vehicle.level, vehicle.data);
		vehicle.remove();
		vehicle.data.storage.clearContent();
		Kits.give(viewer, kit);
		Cmd.sound(vehicle.level, "minecraft:block.anvil.use", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.4f);
		viewer.sendSystemMessage(Component.literal("Packed up your " + vehicle.type.displayName + ". Fuel and storage are inside the item.")
			.withStyle(ChatFormatting.GOLD));
		MobileHomeMod.nextTick(viewer::closeContainer);
	}

	private void click() {
		Cmd.sound(vehicle.level, "minecraft:ui.button.click", viewer.getX(), viewer.getY(), viewer.getZ(), 0.4f, 1.0f);
	}

	private void nope(String why) {
		Cmd.sound(vehicle.level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	// ---------------------------------------------------------------- other screens

	static boolean canUse(Vehicle vehicle, Player player) {
		return !vehicle.isRemoved() && (Vehicles.vehicleOf(player) == vehicle || player.distanceToSqr(vehicle.root) <= 64);
	}

	static void openStorage(ServerPlayer player, Vehicle vehicle) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, vehicle.data.storage, 6) {
			@Override
			public boolean stillValid(Player who) {
				return canUse(vehicle, who);
			}
		}, Component.literal(vehicle.type.displayName + " Storage")));
	}

	static void openCrafting(ServerPlayer player, Vehicle vehicle) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingMenu(id, inv, ContainerLevelAccess.create(vehicle.level, vehicle.root.blockPosition())) {
			@Override
			public boolean stillValid(Player who) {
				return canUse(vehicle, who);
			}
		}, Component.literal("Workbench")));
	}

	// ---------------------------------------------------------------- slot handling

	/** Runs every tick while the screen is open: burn whatever is in the fuel slot, refresh the gauge. */
	@Override
	public void broadcastChanges() {
		ItemStack fuel = box.getItem(INTAKE);
		if (!fuel.isEmpty()) {
			ItemStack rest = vehicle.feed(fuel);
			if (rest != fuel) {
				box.setItem(INTAKE, rest);
			}
		}
		if (vehicle.data.fuelPercent() != shownFuel) {
			render();
		}
		super.broadcastChanges();
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (isButton(slotId)) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run(button, clickType);
			}
			sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == INTAKE) {
			if (!moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= SIZE && Fuel.burnTicks(vehicle.level, stack) > 0) {
			if (!moveItemStackTo(stack, INTAKE, INTAKE + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return !isButtonSlot(slot) && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return !isButtonSlot(slot) && super.canDragTo(slot);
	}

	private boolean isButtonSlot(Slot slot) {
		return slot.container == box && isButton(slot.getContainerSlot());
	}

	@Override
	public boolean stillValid(Player player) {
		return canUse(vehicle, player);
	}

	@Override
	public void removed(Player player) {
		for (int i = 0; i < SIZE; i++) {
			if (isButton(i)) {
				box.setItem(i, ItemStack.EMPTY);
			}
		}
		super.removed(player);
		clearContainer(player, box);
	}
}
