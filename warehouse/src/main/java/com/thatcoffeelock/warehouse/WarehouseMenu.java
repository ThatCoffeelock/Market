package com.thatcoffeelock.warehouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The warehouse screen. The top five rows are the stock, a page at a time: click to take things out, drop things on
 * it (or shift-click them in your inventory) to put them in. The bottom row has the tabs, sorting, search and
 * settings. Opened from a ship at a Loading Dock, taking things out loads them onto the ship instead.
 */
final class WarehouseMenu extends BaseMenu {
	private static final int LIST = 45;

	/** What you're looking at; kept when the screen closes for a chat prompt and opens again. */
	static final class View {
		Category tab = Category.ALL;
		boolean byName;
		int page;
		String search = "";
		boolean settings;
	}

	private enum Take { ONE, STACK, ALL }

	private final Warehouse w;
	private final BooleanSupplier still;
	private final @Nullable ShipTarget ship;
	private final View view;
	private int seen = Integer.MIN_VALUE;

	/** Right-clicked the core or one of its racks. */
	static void open(ServerPlayer player, Warehouse w, BlockPos clicked, @Nullable ShipTarget ship) {
		ServerLevel level = (ServerLevel) player.level();
		open(player, w, () -> near(player, clicked) && Warehouses.at(level, clicked) == w, ship, new View());
	}

	/** Opened from a Loading Dock (with or without a ship moored there). */
	static void openFromDock(ServerPlayer player, Warehouse w, BlockPos dock, @Nullable ShipTarget ship) {
		open(player, w, () -> near(player, dock) || (ship != null && ship.present().getAsBoolean()), ship, new View());
	}

	static void open(ServerPlayer player, Warehouse w, BooleanSupplier still, @Nullable ShipTarget ship, View view) {
		Component title = Component.literal(w.name + (ship != null ? " → " + ship.name() : "")).withStyle(ChatFormatting.DARK_BLUE);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new WarehouseMenu(id, player, w, still, ship, view), title));
	}

	static boolean near(Player player, BlockPos pos) {
		return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
	}

	private WarehouseMenu(int syncId, ServerPlayer viewer, Warehouse w, BooleanSupplier still, @Nullable ShipTarget ship, View view) {
		super(syncId, viewer, 6);
		this.w = w;
		this.still = still;
		this.ship = ship;
		this.view = view;
		render();
	}

	private void render() {
		seen = w.version;
		clearButtons();
		if (view.settings) {
			renderSettings();
			fill(0, size);
		} else {
			renderStock();
			fill(LIST, size);
		}
	}

	/** Someone else (a hopper, a train, another player) changed the stock: redraw. Runs every tick while open. */
	@Override
	public void broadcastChanges() {
		if (w.version != seen) {
			render();
		}
		super.broadcastChanges();
	}

	// ---------------------------------------------------------------- the stock

	private static String nameOf(ItemStack stack) {
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
	}

	private boolean matches(ItemStack stack) {
		if (view.search.isEmpty()) {
			return true;
		}
		String q = view.search.toLowerCase(Locale.ROOT);
		return nameOf(stack).contains(q) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(q.replace(' ', '_'));
	}

	private List<Map.Entry<Warehouse.Key, Long>> shown() {
		List<Map.Entry<Warehouse.Key, Long>> entries = new ArrayList<>();
		for (Map.Entry<Warehouse.Key, Long> e : w.items.entrySet()) {
			if (view.tab.takes(e.getKey().stack) && matches(e.getKey().stack)) {
				entries.add(Map.entry(e.getKey(), e.getValue()));
			}
		}
		Comparator<Map.Entry<Warehouse.Key, Long>> byName = Comparator.comparing(e -> nameOf(e.getKey().stack));
		if (view.byName) {
			entries.sort(byName);
		} else {
			entries.sort(Comparator.comparingLong((Map.Entry<Warehouse.Key, Long> e) -> e.getValue()).reversed().thenComparing(byName));
		}
		return entries;
	}

	private void renderStock() {
		List<Map.Entry<Warehouse.Key, Long>> entries = shown();
		int pages = Math.max(1, (entries.size() + LIST - 1) / LIST);
		view.page = Math.max(0, Math.min(view.page, pages - 1));
		for (int i = 0; i < LIST; i++) {
			int index = view.page * LIST + i;
			if (index >= entries.size()) {
				break;
			}
			Warehouse.Key key = entries.get(index).getKey();
			long count = entries.get(index).getValue();
			button(i, stockIcon(key, count), (b, t) -> withdraw(key, t == ContainerInput.QUICK_MOVE ? Take.ALL : b == 1 ? Take.ONE : Take.STACK));
		}
		if (entries.isEmpty()) {
			boolean filtered = !view.search.isEmpty() || view.tab != Category.ALL;
			button(22, Gui.icon(Items.COBWEB, Gui.text(filtered ? "Nothing matches" : "Nothing here yet", ChatFormatting.GRAY),
				Gui.text("Drop items on an empty slot, shift-click them", ChatFormatting.DARK_GRAY),
				Gui.text("in your inventory, or use the chest button.", ChatFormatting.DARK_GRAY)), null);
		}

		if (ship != null) {
			button(45, Gui.icon(Items.BARREL, Gui.text("Unload the " + ship.name() + " here", ChatFormatting.AQUA, ChatFormatting.BOLD),
				Gui.text("Moves the ship's cargo into this warehouse,", ChatFormatting.GRAY),
				Gui.text("as much as fits and it accepts.", ChatFormatting.GRAY),
				Gui.text("Clicking stock above loads it onto the ship.", ChatFormatting.DARK_GRAY)), (b, t) -> unloadShip());
		} else {
			button(45, Gui.icon(Items.CHEST, Gui.text("Deposit your backpack", ChatFormatting.AQUA, ChatFormatting.BOLD),
				Gui.text("Everything in your inventory except the hotbar.", ChatFormatting.GRAY),
				Gui.text("Tip: shift-click items in your inventory,", ChatFormatting.DARK_GRAY),
				Gui.text("or drop them on an empty slot above.", ChatFormatting.DARK_GRAY)), (b, t) -> depositBackpack());
		}

		List<Component> tabs = new ArrayList<>();
		for (Category c : Category.values()) {
			tabs.add(Gui.text((c == view.tab ? "▶ " : "   ") + c.title, c == view.tab ? c.color : ChatFormatting.DARK_GRAY));
		}
		tabs.add(Component.empty());
		tabs.add(Gui.text("Left-click: next · Right-click: previous", ChatFormatting.YELLOW));
		button(46, Gui.icon(view.tab.icon, Gui.text("Showing: " + view.tab.title, view.tab.color, ChatFormatting.BOLD), tabs), (b, t) -> {
			click();
			view.tab = b == 1 ? view.tab.previous() : view.tab.next();
			view.page = 0;
			render();
		});

		button(47, Gui.icon(Items.HOPPER, Gui.text("Sorted by: " + (view.byName ? "name (A–Z)" : "most stock first"), ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text("Click to sort by " + (view.byName ? "stock" : "name") + ".", ChatFormatting.GRAY)), (b, t) -> {
			click();
			view.byName = !view.byName;
			render();
		});

		if (view.page > 0) {
			button(48, Gui.icon(Items.ARROW, Gui.text("Previous page", ChatFormatting.YELLOW),
				Gui.text("Page " + (view.page + 1) + " of " + pages, ChatFormatting.GRAY)), (b, t) -> {
				click();
				view.page--;
				render();
			});
		}
		button(49, infoIcon(entries.size()), null);
		if (view.page < pages - 1) {
			button(50, Gui.icon(Items.ARROW, Gui.text("Next page", ChatFormatting.YELLOW),
				Gui.text("Page " + (view.page + 1) + " of " + pages, ChatFormatting.GRAY)), (b, t) -> {
				click();
				view.page++;
				render();
			});
		}

		button(51, Gui.icon(Items.SPYGLASS, Gui.text("Search", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text(view.search.isEmpty() ? "Not searching." : "Searching for: " + view.search, ChatFormatting.GRAY),
			Gui.text("Left-click: type a search in chat", ChatFormatting.YELLOW),
			Gui.text("Right-click: clear the search", ChatFormatting.YELLOW)), (b, t) -> {
			click();
			if (b == 1) {
				view.search = "";
				view.page = 0;
				render();
			} else {
				ask("Type what you're looking for in chat (or 'cancel').", text -> {
					if (!text.equalsIgnoreCase("cancel")) {
						view.search = text;
						view.page = 0;
					}
				});
			}
		});

		button(52, Gui.icon(Items.COMPARATOR, Gui.text("Warehouse settings", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text("What it accepts, lock, name, racks.", ChatFormatting.GRAY)), (b, t) -> {
			click();
			view.settings = true;
			render();
		});
		button(53, Gui.icon(Items.BARRIER, Gui.text("Close", ChatFormatting.RED)), (b, t) -> WarehouseMod.nextTick(viewer::closeContainer));
	}

	private ItemStack stockIcon(Warehouse.Key key, long count) {
		int max = key.stack.getMaxStackSize();
		ItemStack icon = key.stack.copyWithCount((int) Math.max(1, Math.min(count, max)));
		List<Component> lore = new ArrayList<>();
		ItemLore own = icon.get(DataComponents.LORE);
		if (own != null) {
			lore.addAll(own.lines());
		}
		lore.add(Gui.text("Stored: " + Gui.n(count) + (max > 1 && count > max ? " (" + Gui.n((count + max - 1) / max) + " stacks)" : ""),
			ChatFormatting.AQUA));
		lore.add(Component.empty());
		String where = ship != null ? " onto the ship" : "";
		lore.add(Gui.text("Left-click: take " + (max > 1 ? "a stack" : "one") + where, ChatFormatting.YELLOW));
		if (max > 1) {
			lore.add(Gui.text("Right-click: take one" + where, ChatFormatting.YELLOW));
		}
		lore.add(Gui.text("Shift-click: take as much as fits" + where, ChatFormatting.YELLOW));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return icon;
	}

	private ItemStack infoIcon(int shownKinds) {
		List<Component> lore = new ArrayList<>();
		lore.add(Gui.text("Stock: " + Gui.n(w.total()) + " / " + Gui.n(w.capacity()) + " items (" + w.percentFull() + "%)",
			w.space() == 0 ? ChatFormatting.RED : ChatFormatting.AQUA));
		lore.add(Gui.text(Gui.n(w.kinds()) + " kinds of items" + (shownKinds != w.kinds() ? ", " + Gui.n(shownKinds) + " shown" : ""), ChatFormatting.GRAY));
		lore.add(Gui.text("Storage Racks: " + w.racks, ChatFormatting.GRAY));
		if (w.disputed > 0) {
			lore.add(Gui.text(w.disputed + " rack(s) also touch another warehouse and don't count", ChatFormatting.RED));
		}
		lore.add(Gui.text("Accepts: " + w.filter.title, ChatFormatting.GRAY));
		lore.add(Gui.text("Owner: " + (w.ownerName.isEmpty() ? "nobody" : w.ownerName), ChatFormatting.GRAY));
		lore.add(Gui.text(w.locked ? "Locked: anyone can bring things, only the owner takes" : "Open: anyone can take things out",
			w.locked ? ChatFormatting.GOLD : ChatFormatting.GREEN));
		if (w.total() > w.capacity()) {
			lore.add(Gui.text("Over capacity! Only lets things out until there's room.", ChatFormatting.RED));
		}
		return Gui.icon(Items.FILLED_MAP, Gui.text(w.name, ChatFormatting.GOLD, ChatFormatting.BOLD), lore);
	}

	// ---------------------------------------------------------------- settings

	private void renderSettings() {
		boolean boss = w.mayManage(viewer);
		String only = boss ? "Click to change." : "Only " + w.ownerName + " can change this.";

		List<Component> filterLore = new ArrayList<>();
		filterLore.add(Gui.text("A specialist warehouse only takes one kind", ChatFormatting.GRAY));
		filterLore.add(Gui.text("of thing. Docks send that kind there first.", ChatFormatting.GRAY));
		filterLore.add(Component.empty());
		for (Category c : Category.values()) {
			filterLore.add(Gui.text((c == w.filter ? "▶ " : "   ") + c.title, c == w.filter ? c.color : ChatFormatting.DARK_GRAY));
		}
		filterLore.add(Component.empty());
		filterLore.add(Gui.text(only, ChatFormatting.YELLOW));
		button(11, Gui.icon(w.filter.icon, Gui.text("Accepts: " + w.filter.title, w.filter.color, ChatFormatting.BOLD), filterLore), boss ? (b, t) -> {
			click();
			w.filter = b == 1 ? w.filter.previous() : w.filter.next();
			w.changed();
		} : null);

		button(13, Gui.icon(w.locked ? Items.IRON_BARS : Items.TRIPWIRE_HOOK,
			Gui.text(w.locked ? "Locked" : "Open", w.locked ? ChatFormatting.GOLD : ChatFormatting.GREEN, ChatFormatting.BOLD),
			Gui.text(w.locked ? "Anyone can bring things in (hoppers, trains, ships)," : "Anyone can put things in and take them out.", ChatFormatting.GRAY),
			Gui.text(w.locked ? "but only the owner can take them out or break it." : "Lock it to keep the stock for yourself.", ChatFormatting.GRAY),
			Gui.text(only, ChatFormatting.YELLOW)), boss ? (b, t) -> {
			click();
			w.locked = !w.locked;
			w.changed();
		} : null);

		button(15, Gui.icon(Items.NAME_TAG, Gui.text("Rename", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text("Now: " + w.name, ChatFormatting.GRAY),
			Gui.text(boss ? "Click, then type the new name in chat." : only, ChatFormatting.YELLOW)), boss ? (b, t) -> {
			click();
			ask("Type the new name for " + w.name + " in chat (or 'cancel').", text -> {
				String name = Warehouses.clean(text);
				if (!text.equalsIgnoreCase("cancel") && !name.isEmpty()) {
					w.name = name;
					w.changed();
				}
			});
		} : null);

		WarehouseConfig config = WarehouseConfig.get();
		List<Component> rackLore = new ArrayList<>();
		rackLore.add(Gui.text("Room: " + Gui.n(config.coreCapacity) + " (core) + " + w.racks + " × " + Gui.n(config.rackCapacity)
			+ " = " + Gui.n(w.capacity()), ChatFormatting.GRAY));
		rackLore.add(Gui.text("Racks count when they touch the core or a", ChatFormatting.GRAY));
		rackLore.add(Gui.text("counted rack, up to " + config.maxRacks + " racks within " + config.rackReach + " blocks.", ChatFormatting.GRAY));
		if (w.disputed > 0) {
			rackLore.add(Gui.text(w.disputed + " rack(s) touch another warehouse too, so they", ChatFormatting.RED));
			rackLore.add(Gui.text("count for neither. Move them.", ChatFormatting.RED));
		}
		button(29, Gui.icon(Items.BARREL, Gui.text("Storage Racks: " + w.racks + " / " + config.maxRacks, ChatFormatting.AQUA, ChatFormatting.BOLD), rackLore), null);

		button(31, Gui.icon(Items.BOOK, Gui.text("How to fill it", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text("• By hand: this screen, or sneak + right-click", ChatFormatting.GRAY),
			Gui.text("  the core with an item.", ChatFormatting.GRAY),
			Gui.text("• Hoppers: point them into any Storage Rack.", ChatFormatting.GRAY),
			Gui.text("• Chests: name one \"Warehouse Intake\" and put it", ChatFormatting.GRAY),
			Gui.text("  against the core or a rack.", ChatFormatting.GRAY),
			Gui.text("• Trains: a Cargo Train Drop-off Station touching", ChatFormatting.GRAY),
			Gui.text("  the warehouse unloads straight onto the shelves.", ChatFormatting.GRAY),
			Gui.text("• Ships: build a Loading Dock on your pier.", ChatFormatting.GRAY)), null);

		button(33, Gui.icon(Items.LANTERN, Gui.text("Loading Docks", ChatFormatting.YELLOW, ChatFormatting.BOLD),
			Gui.text("A dock serves every warehouse within " + config.dockReach + " blocks.", ChatFormatting.GRAY),
			Gui.text("Moor an Ahoy ship within " + config.shipReach + " blocks of it and", ChatFormatting.GRAY),
			Gui.text("the captain's menu gets a Loading Dock button:", ChatFormatting.GRAY),
			Gui.text("unload everything, or load up from a warehouse.", ChatFormatting.GRAY)), null);

		button(45, Gui.icon(Items.ARROW, Gui.text("Back to the stock", ChatFormatting.YELLOW)), (b, t) -> {
			click();
			view.settings = false;
			render();
		});
		button(49, infoIcon(w.kinds()), null);
		button(53, Gui.icon(Items.BARRIER, Gui.text("Close", ChatFormatting.RED)), (b, t) -> WarehouseMod.nextTick(viewer::closeContainer));
	}

	/** Closes the screen, asks a question in chat, then opens the screen again. */
	private void ask(String question, java.util.function.Consumer<String> answer) {
		WarehouseMod.nextTick(() -> {
			viewer.closeContainer();
			Prompts.ask(viewer, question, text -> {
				if (w.packed || !still.getAsBoolean()) {
					viewer.sendSystemMessage(Component.literal("You walked away from " + w.name + ".").withStyle(ChatFormatting.GRAY));
					return;
				}
				answer.accept(text);
				open(viewer, w, still, ship, view);
			});
		});
	}

	// ---------------------------------------------------------------- taking things out

	private void withdraw(Warehouse.Key key, Take how) {
		if (!w.mayWithdraw(viewer)) {
			nope(w.name + " is locked. Only " + w.ownerName + " can take things out.");
			return;
		}
		if (ship != null && !ship.usable()) {
			nope("The " + ship.name() + " has sailed off, or its captain locked the cargo.");
			return;
		}
		long have = w.count(key);
		if (have <= 0) {
			render();
			return;
		}
		int max = key.stack.getMaxStackSize();
		long want = how == Take.ONE ? 1 : how == Take.STACK ? max : Long.MAX_VALUE;
		long room = ship != null ? Slots.room(ship.holds(), key.stack) : Slots.room(viewer, key.stack);
		long n = Math.min(want, Math.min(room, have));
		if (n <= 0) {
			nope(ship != null ? "The " + ship.name() + "'s holds are full." : "Your inventory is full.");
			return;
		}
		long got = w.take(key, n);
		if (ship != null) {
			long left = Slots.insert(ship.holds(), key.stack, got);
			if (left > 0) {
				w.restore(key.stack, left);
				w.changed();
			}
		} else {
			Slots.give(viewer, key.stack, got);
		}
		click();
		render();
	}

	// ---------------------------------------------------------------- putting things in

	/** Puts the stack in (shrinking it). Complains and returns 0 if it can't. */
	private int deposit(ItemStack stack, boolean complain) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (!w.accepts(stack)) {
			if (complain) {
				nope(w.name + " only takes " + w.filter.title.toLowerCase(Locale.ROOT) + ".");
			}
			return 0;
		}
		int n = w.deposit(stack);
		if (n == 0 && complain) {
			nope(w.name + " is full. Add Storage Racks!");
		}
		return n;
	}

	/** Dropping the item you're holding on the stock puts it in. */
	@Override
	protected boolean clickedOwnSlot(int slot, int button, ContainerInput type) {
		if (view.settings || slot >= LIST || type != ContainerInput.PICKUP) {
			return false;
		}
		ItemStack carried = getCarried();
		if (carried.isEmpty()) {
			return false;
		}
		if (button == 1) {
			ItemStack one = carried.copyWithCount(1);
			if (deposit(one, true) > 0) {
				carried.shrink(1);
				stored();
			}
		} else if (deposit(carried, true) > 0) {
			stored();
		}
		setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
		return true;
	}

	/** Shift-clicking an item in your own inventory puts it in. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < size || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		if (deposit(stack, true) > 0) {
			stored();
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return ItemStack.EMPTY;
	}

	private void depositBackpack() {
		Inventory inventory = viewer.getInventory();
		long moved = 0;
		int refused = 0;
		for (int i = 9; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			moved += deposit(stack, false);
			if (stack.isEmpty()) {
				inventory.setItem(i, ItemStack.EMPTY);
			} else {
				refused++;
			}
		}
		if (moved == 0) {
			nope(refused == 0 ? "Your backpack is empty." : w.space() == 0 ? w.name + " is full. Add Storage Racks!"
				: w.name + " only takes " + w.filter.title.toLowerCase(Locale.ROOT) + ".");
			return;
		}
		stored();
		Interactions.actionBar(viewer, Component.literal("Stored " + Gui.n(moved) + " items" + (refused > 0 ? " (" + refused + " stacks didn't fit or aren't accepted)" : "") + ".")
			.withStyle(ChatFormatting.GREEN));
		render();
	}

	private void unloadShip() {
		if (ship == null) {
			return;
		}
		if (!ship.usable()) {
			nope("The " + ship.name() + " has sailed off, or its captain locked the cargo.");
			return;
		}
		Docks.Report report = Docks.unload(List.of(w), ship.holds());
		if (report.moved() > 0) {
			stored();
		}
		Docks.tell(viewer, ship.name(), report);
		render();
	}

	@Override
	public boolean stillValid(Player player) {
		return !w.packed && Warehouses.byId(w.id) == w && still.getAsBoolean();
	}
}
