package com.thatcoffeelock.cargotrain;

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
import net.minecraft.world.entity.Entity;
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

/** The locomotive's menu: a 3-row chest screen built on the server so vanilla clients can use it. */
final class TrainMenu extends ChestMenu {
	private static final int SIZE = 27;

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final Train train;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private boolean confirmPickUp;

	static void open(ServerPlayer player, Train train) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TrainMenu(id, player, train),
			Component.literal("Cargo Train · " + (train.data.ownerName.isEmpty() ? "nobody" : train.data.ownerName)).withStyle(ChatFormatting.DARK_BLUE)));
	}

	/** Opens one wagon's 27 slots (0 = right behind the locomotive). */
	static void openCargo(ServerPlayer player, Train train, int wagon) {
		if (wagon < 0 || wagon >= train.data.wagons()) {
			return;
		}
		SimpleContainer cargo = train.data.cargo.get(wagon);
		String title = train.data.wagons() > 1 ? "Cargo Wagon " + (wagon + 1) + " of " + train.data.wagons() : "Cargo Wagon";
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, cargo, 3) {
			@Override
			public boolean stillValid(Player who) {
				// an uncoupled wagon's cargo is gone from the train: close the screen
				return canUse(train, who) && train.data.cargo.contains(cargo);
			}
		}, Component.literal(title)));
	}

	static boolean canUse(Train train, Player player) {
		if (train.isRemoved()) {
			return false;
		}
		if (Trains.trainOf(player) == train || player.distanceToSqr(train.root) <= 8 * 8) {
			return true;
		}
		for (int i = 0; i < train.data.wagons(); i++) {
			Entity wagon = train.wagon(i);
			if (wagon != null && player.distanceToSqr(wagon) <= 8 * 8) {
				return true;
			}
		}
		return false;
	}

	private TrainMenu(int syncId, ServerPlayer viewer, Train train) {
		this(syncId, viewer, train, new SimpleContainer(SIZE));
	}

	private TrainMenu(int syncId, ServerPlayer viewer, Train train, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.train = train;
		this.box = box;
		render();
	}

	private static final Map<String, Item> ITEMS = new HashMap<>();

	/** Looks an item up by id (some, like the dyed ones, aren't constants in Items any more). */
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
		return TrainItems.text(text, formats);
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
		ItemStack filler = icon(item("minecraft:yellow_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "));
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		TrainData data = train.data;
		boolean owner = train.isOwner(viewer);
		boolean use = train.mayUse(viewer);

		button(4, icon(Items.FILLED_MAP, t("Cargo Train", ChatFormatting.GOLD, ChatFormatting.BOLD),
			t("Owner: " + (data.ownerName.isEmpty() ? "nobody" : data.ownerName), ChatFormatting.GRAY),
			t(train.status(), ChatFormatting.WHITE),
			t("Speed: " + train.kmh() + " km/h", ChatFormatting.GRAY),
			t("Wagons: " + data.wagons() + " / " + TrainData.MAX_WAGONS, ChatFormatting.GRAY),
			t("Cargo: " + data.usedSlots() + " / " + data.totalSlots() + " slots (" + data.itemCount() + " items)", ChatFormatting.GRAY),
			t("Delivered so far: " + data.hauled + " items", ChatFormatting.GRAY),
			t(train.lastStop == null ? "No stations visited yet." : "Last stop: " + train.lastStop, ChatFormatting.DARK_GRAY)), null);

		if (use) {
			button(10, icon(data.running ? Items.REDSTONE_TORCH : Items.LEVER,
				t(data.running ? "Stop the train" : "Start the route", data.running ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD),
				t(data.running ? "It brakes and parks where it is." : "It runs to the end of the line, turns around and comes back,", ChatFormatting.GRAY),
				t(data.running ? "Parked, you can drive it yourself from the cab (W/S)." : "stopping at every station. Forever. It doesn't unionise.", ChatFormatting.GRAY)), () -> {
				data.running = !data.running;
				if (data.running) {
					train.horn();
				}
				render();
			});
		}

		boolean riding = Trains.trainOf(viewer) == train;
		if (use || riding) {
			button(12, icon(Items.SADDLE, t(riding ? "Get off" : "Climb into the cab", ChatFormatting.AQUA, ChatFormatting.BOLD),
				t(riding ? "You'll be put down next to the track." : "Ride along. When it's parked, W/S drives.", ChatFormatting.GRAY)), () -> CargoTrainMod.nextTick(() -> {
					viewer.closeContainer();
					if (riding) {
						train.getOff(viewer);
					} else if (!train.board(viewer)) {
						nope("Someone's already in the cab.");
					}
				}));
		}
		button(14, icon(Items.GOAT_HORN, t("Horn", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			t("TOOOOOT. Absolutely necessary.", ChatFormatting.GRAY)), train::horn);

		if (owner) {
			button(16, icon(data.locked ? Items.IRON_BARS : Items.TRIPWIRE_HOOK,
				t(data.locked ? "Locked" : "Unlocked", data.locked ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD),
				t(data.locked ? "Only you can drive it, start it, couple wagons and open the cargo." : "Anyone can drive it, start it, couple wagons and open the cargo.", ChatFormatting.GRAY),
				t("Click to toggle.", ChatFormatting.YELLOW)), () -> {
				data.locked = !data.locked;
				render();
			});
		}

		// bottom row: one button per wagon, then an empty spot showing how to add the next one
		for (int i = 0; i < TrainData.MAX_WAGONS; i++) {
			int wagon = i;
			if (i < data.wagons() && use) {
				button(18 + i, icon(Items.CHEST, t("Wagon " + (i + 1), ChatFormatting.GOLD, ChatFormatting.BOLD),
					t(data.usedSlots(i) + " / " + TrainData.SLOTS + " slots used.", ChatFormatting.GRAY),
					t("Click to open. You can also right-click the wagon.", ChatFormatting.DARK_GRAY)),
					() -> CargoTrainMod.nextTick(() -> openCargo(viewer, train, wagon)));
			} else if (i == data.wagons()) {
				button(18 + i, icon(Items.MINECART, t("Room for another wagon", ChatFormatting.GRAY),
					t("Craft a Cargo Wagon (Chest Minecart + Iron Ingot)", ChatFormatting.DARK_GRAY),
					t("and right-click the train with it while it stands still.", ChatFormatting.DARK_GRAY)), null);
			}
		}
		if (use && data.wagons() > 1) {
			button(22, icon(Items.SHEARS, t("Uncouple the last wagon", ChatFormatting.YELLOW, ChatFormatting.BOLD),
				t("You get the Cargo Wagon back. It has to be empty.", ChatFormatting.GRAY)), this::uncouple);
		}
		if (owner || viewer.isCreative()) {
			button(24, icon(Items.FURNACE_MINECART, t(confirmPickUp ? "Click again to pick it up" : "Pick up the train", ChatFormatting.GOLD, ChatFormatting.BOLD),
				t("Takes it off the track and gives you the items back.", ChatFormatting.GRAY),
				t(data.wagons() > 1 ? "The wagons have to be empty first." : "The wagon has to be empty first.", ChatFormatting.DARK_GRAY)), this::pickUp);
		}
		button(26, icon(Items.BARRIER, t("Close", ChatFormatting.RED)), () -> CargoTrainMod.nextTick(viewer::closeContainer));
	}

	private void pickUp() {
		if (!train.data.isEmpty()) {
			nope("Empty the cargo first. We don't do surprise cargo.");
			return;
		}
		if (!confirmPickUp) {
			confirmPickUp = true;
			render();
			return;
		}
		CargoTrainMod.nextTick(() -> {
			viewer.closeContainer();
			if (!train.isRemoved()) {
				for (ItemStack item : Trains.pickUp(train)) {
					TrainItems.give(viewer, item);
				}
				viewer.sendSystemMessage(Component.literal("Train packed up. The rails miss it already.").withStyle(ChatFormatting.GOLD));
			}
		});
	}

	private void uncouple() {
		int last = train.data.wagons() - 1;
		if (!train.data.cargo.get(last).isEmpty()) {
			nope("Wagon " + (last + 1) + " isn't empty. Unload it first.");
			return;
		}
		if (train.speed > 0) {
			nope("Wait until it's standing still.");
			return;
		}
		if (!train.uncouple()) {
			nope("Can't uncouple that wagon right now.");
			return;
		}
		TrainItems.give(viewer, TrainItems.wagon());
		render();
	}

	private void nope(String why) {
		Cmd.sound(train.level, "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
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
		return canUse(train, player);
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
