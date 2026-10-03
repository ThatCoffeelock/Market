package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/**
 * The Town Hall counter: a 6-row chest screen built on the server. The main page is the shop and
 * the list of buildings; clicking a building opens its page (upgrade, replace workers, storage...).
 */
final class TownHallMenu extends ChestMenu {
	private static final int SIZE = 54;
	private static final int PER_PAGE = 18;
	private static final BuildingType[] SHOP = {BuildingType.RESIDENCE, BuildingType.FARM, BuildingType.LUMBER_CAMP,
		BuildingType.MINE, BuildingType.WORKSHOP, BuildingType.STOREHOUSE, BuildingType.BARRACKS, BuildingType.TOWN_HALL};
	private static final BuildingType[] FORTIFICATIONS = {BuildingType.WALL, BuildingType.GATEHOUSE, BuildingType.WATCHTOWER};

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final Colony colony;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private @Nullable Colony.Building selected;
	private int page;
	private boolean confirmDemolish;

	static void open(ServerPlayer player, Colony colony) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TownHallMenu(id, player, colony),
			Component.literal(colony.name + " · Town Hall").withStyle(ChatFormatting.DARK_GREEN)));
	}

	static void openStorage(ServerPlayer player, Colony.Building store) {
		int rows = store.storage.getContainerSize() / 9;
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(rows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3,
			id, inv, store.storage, rows) {
			@Override
			public boolean stillValid(Player who) {
				return store.colony.buildings.contains(store) && store.colony.claims(who.blockPosition());
			}
		}, Component.literal(store.colony.name + " · " + store.title())));
	}

	private TownHallMenu(int syncId, ServerPlayer viewer, Colony colony) {
		this(syncId, viewer, colony, new SimpleContainer(SIZE));
	}

	private TownHallMenu(int syncId, ServerPlayer viewer, Colony colony, SimpleContainer box) {
		super(MenuType.GENERIC_9x6, syncId, viewer.getInventory(), box, 6);
		this.viewer = viewer;
		this.colony = colony;
		this.box = box;
		render();
	}

	// ---------------------------------------------------------------- icons

	private static final Map<String, Item> ITEMS = new HashMap<>();

	static Item item(String id, Item fallback) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id);
		return item == null || item == Items.AIR ? fallback : item;
	}

	private static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Blueprints.text(text, formats);
	}

	private static Component money(String label, long cents) {
		return Blueprints.text(label, ChatFormatting.GRAY).append(Bank.text(cents).withStyle(style -> style.withItalic(false)));
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
		ItemStack filler = icon(item("minecraft:green_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		if (selected != null && colony.buildings.contains(selected)) {
			renderBuilding(selected);
		} else {
			selected = null;
			renderMain();
		}
		button(49, icon(Items.SUNFLOWER, t("Your balance", ChatFormatting.GOLD, ChatFormatting.BOLD),
			List.of(money("", Bank.balance(viewer)))), null);
		button(53, icon(Items.BARRIER, t("Close", ChatFormatting.RED), List.of()), () -> ColonycraftMod.nextTick(viewer::closeContainer));
	}

	private void renderMain() {
		List<Component> info = new ArrayList<>();
		info.add(t("Founded by " + colony.ownerName, ChatFormatting.GRAY));
		info.add(t("Level " + colony.tier() + " · land " + (colony.radius() * 2 + 1) + " × " + (colony.radius() * 2 + 1), ChatFormatting.GRAY));
		info.add(t("Buildings: " + colony.slotsUsed() + " / " + colony.maxBuildings(), ChatFormatting.GRAY));
		info.add(t("Workers: " + colony.workers() + " (beds for " + colony.housing() + ")", ChatFormatting.GRAY));
		info.add(money("Wages per day: ", colony.dailyWages()));
		info.add(Component.empty());
		info.add(colony.striking
			? t("ON STRIKE: yesterday's wages weren't paid. Nobody gathers until they are.", ChatFormatting.RED, ChatFormatting.BOLD)
			: t("Everyone's hard at work.", ChatFormatting.GREEN));
		if (colony.striking) {
			info.add(t("The guards are standing down too.", ChatFormatting.RED));
		}
		button(4, icon(Items.BELL, t(colony.name, ChatFormatting.GOLD, ChatFormatting.BOLD), info), null);

		button(9, icon(Items.WRITABLE_BOOK, t("Blueprints for sale", ChatFormatting.AQUA, ChatFormatting.BOLD),
			List.of(t("Buy one, then right-click the ground", ChatFormatting.GRAY), t("inside the colony to build it.", ChatFormatting.GRAY))), null);
		for (int i = 0; i < SHOP.length; i++) {
			shopButton(10 + i, SHOP[i]);
		}
		button(18, icon(Items.SHIELD, t("Fortifications", ChatFormatting.AQUA, ChatFormatting.BOLD),
			List.of(t("Thick walls you can walk on, gates and towers.", ChatFormatting.GRAY),
				t("They don't use building slots, and snap", ChatFormatting.GRAY), t("together end to end.", ChatFormatting.GRAY))), null);
		for (int i = 0; i < FORTIFICATIONS.length; i++) {
			shopButton(19 + i, FORTIFICATIONS[i]);
		}

		button(23, icon(Items.IRON_BARS, t("Law and order", ChatFormatting.AQUA, ChatFormatting.BOLD),
			List.of(t("Lock up the illagers you catch.", ChatFormatting.GRAY), t("Beat one down, shackle them, put the", ChatFormatting.GRAY),
				t("shackles in a cell's holding block.", ChatFormatting.GRAY))), null);
		shopButton(24, BuildingType.CELLBLOCK);
		shacklesButton(25);

		button(26, icon(Items.OAK_SIGN, t("Your buildings (below)", ChatFormatting.AQUA, ChatFormatting.BOLD),
			List.of(t("Click one to upgrade it, repair it, hire", ChatFormatting.GRAY), t("replacements, open storage or demolish it.", ChatFormatting.GRAY))), null);
		List<Colony.Building> list = colony.buildings;
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < PER_PAGE; i++) {
			int index = page * PER_PAGE + i;
			if (index >= list.size()) {
				break;
			}
			Colony.Building b = list.get(index);
			List<Component> lore = new ArrayList<>();
			if (!b.villagers.isEmpty()) {
				lore.add(t("Workers: " + b.alive() + " / " + b.villagers.size(), b.dead() > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
			}
			if (b.type.housing(b.tier) > 0) {
				lore.add(t("Beds: " + b.type.housing(b.tier), ChatFormatting.GRAY));
			}
			if (b.type == BuildingType.STOREHOUSE) {
				lore.add(t("Autosell: " + (b.autosell ? "on" : "off"), b.autosell ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}
			if (b.type == BuildingType.CELLBLOCK) {
				lore.add(t("Prisoners: " + b.prisoners.size() + " / " + BuildingType.cells(b.tier), ChatFormatting.GRAY));
			}
			lore.add(t("at " + b.origin.getX() + ", " + b.origin.getY() + ", " + b.origin.getZ(), ChatFormatting.DARK_GRAY));
			ItemStack icon = icon(b.type.icon, t(b.title(), ChatFormatting.WHITE, ChatFormatting.BOLD), lore);
			if (b.dead() > 0) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			button(27 + i, icon, () -> {
				selected = b;
				confirmDemolish = false;
				render();
			});
		}
		if (page > 0) {
			button(45, icon(Items.ARROW, t("Previous page", ChatFormatting.YELLOW), List.of()), () -> {
				page--;
				render();
			});
		}
		if (page < pages - 1) {
			button(51, icon(Items.ARROW, t("Next page", ChatFormatting.YELLOW), List.of()), () -> {
				page++;
				render();
			});
		}
	}

	private void shopButton(int slot, BuildingType type) {
		long price = Bank.cents(type.price);
		List<Component> lore = new ArrayList<>();
		lore.add(money("Price: ", price));
		lore.add(t(describe(type), ChatFormatting.GRAY));
		int crew = type.workers(1);
		if (crew > 0) {
			lore.add(t(crew + " " + type.crewNoun(crew) + ", " + Bank.format(Bank.cents(type.wage) * crew) + " wages a day", ChatFormatting.DARK_GRAY));
		}
		if (type.fortification) {
			lore.add(t("Doesn't use a building slot.", ChatFormatting.DARK_GRAY));
		}
		lore.add(Component.empty());
		lore.add(t("Click to buy.", ChatFormatting.YELLOW));
		String name = type == BuildingType.TOWN_HALL ? "Colony Charter (a new colony)" : type.displayName;
		button(slot, icon(type.icon, t(name, ChatFormatting.AQUA, ChatFormatting.BOLD), lore), () -> buy(type, price));
	}

	private void shacklesButton(int slot) {
		long price = Bank.cents(Prison.SHACKLES_PRICE);
		ItemStack icon = Prison.emptyShackles();
		List<Component> lore = new ArrayList<>();
		lore.add(money("Price: ", price));
		lore.add(t("Beat an illager below " + Math.round(Prison.WEAK * 100) + "% health,", ChatFormatting.GRAY));
		lore.add(t("then right-click them. Reusable.", ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(t("Click to buy.", ChatFormatting.YELLOW));
		icon.set(DataComponents.ITEM_NAME, t("Shackles", ChatFormatting.AQUA, ChatFormatting.BOLD));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		button(slot, icon, () -> {
			if (!Bank.pay(viewer, price)) {
				nope("You need " + Bank.format(price) + " for that.");
				return;
			}
			Blueprints.give(viewer, Prison.emptyShackles());
			kaching();
			viewer.sendSystemMessage(Component.literal("Bought: Shackles. Go catch yourself an illager.").withStyle(ChatFormatting.GREEN));
			render();
		});
	}

	private static String describe(BuildingType type) {
		return switch (type) {
			case TOWN_HALL -> "Found another colony somewhere else.";
			case RESIDENCE -> "Beds for your workers. Every worker needs one.";
			case FARM -> "Wheat, carrots, potatoes, beetroot, pumpkins, melons.";
			case LUMBER_CAMP -> "Oak, spruce and birch logs, sticks, saplings, apples.";
			case MINE -> "Cobblestone, coal, iron, copper, gold, redstone, lapis, the odd diamond.";
			case WORKSHOP -> "Turns logs, ores, cobble and wheat into planks, ingots, stone and bread.";
			case STOREHOUSE -> "Everything the colony makes goes here. Can auto-sell to the Market.";
			case BARRACKS -> "Iron golem guards that patrol the colony's land.";
			case WATCHTOWER -> "Archers on top shoot monsters up to 24 blocks away.";
			case WALL -> "9 blocks of thick wall with a walkway on top.";
			case GATEHOUSE -> "A way through the wall. You can open the gates, monsters can't.";
			case CELLBLOCK -> "Cells for the illagers you catch. Lock them up, or execute them for a bounty.";
		};
	}

	private void renderBuilding(Colony.Building b) {
		List<Component> info = new ArrayList<>();
		info.add(t(describe(b.type), ChatFormatting.GRAY));
		if (!b.villagers.isEmpty()) {
			info.add(t("Crew: " + b.alive() + " / " + b.villagers.size() + " " + b.type.crewNoun(b.villagers.size()), ChatFormatting.GRAY));
			info.add(money("Wages per day: ", Bank.cents(b.type.wage) * b.alive()));
		}
		if (b.type.housing(b.tier) > 0) {
			info.add(t("Beds: " + b.type.housing(b.tier), ChatFormatting.GRAY));
		}
		if (b.type == BuildingType.CELLBLOCK) {
			info.add(t("Prisoners: " + b.prisoners.size() + " / " + BuildingType.cells(b.tier) + " cells", ChatFormatting.GRAY));
			for (Map.Entry<Integer, Prison.Prisoner> e : b.prisoners.entrySet()) {
				info.add(t(" Cell " + (e.getKey() + 1) + ": " + e.getValue().name(), ChatFormatting.DARK_GRAY));
			}
			if (!b.prisoners.isEmpty()) {
				info.add(money("Upkeep per day: ", Bank.cents(Prison.UPKEEP) * b.prisoners.size()));
			}
		}
		info.add(t("at " + b.origin.getX() + ", " + b.origin.getY() + ", " + b.origin.getZ(), ChatFormatting.DARK_GRAY));
		button(13, icon(b.type.icon, t(b.title(), ChatFormatting.GOLD, ChatFormatting.BOLD), info), null);

		// upgrade
		if (b.tier < BuildingType.MAX_TIER) {
			long price = Bank.cents(b.type.upgradePrice(b.tier));
			String why = Colonies.whyNoUpgrade(b);
			List<Component> lore = new ArrayList<>();
			lore.add(money("Price: ", price));
			lore.add(t(upgradeText(b), ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.add(why == null ? t("Click to upgrade.", ChatFormatting.YELLOW) : t(why, ChatFormatting.RED));
			button(28, icon(Items.EXPERIENCE_BOTTLE, t("Upgrade to Tier " + (b.tier + 1), ChatFormatting.GREEN, ChatFormatting.BOLD), lore),
				() -> upgrade(b, price));
		} else {
			button(28, icon(Items.NETHER_STAR, t("Top tier", ChatFormatting.GOLD, ChatFormatting.BOLD),
				List.of(t("This one's as good as it gets.", ChatFormatting.GRAY))), null);
		}
		// replacements
		if (b.dead() > 0) {
			long price = Bank.cents(Colonies.REPLACE_PRICE) * b.dead();
			button(30, icon(Items.EMERALD, t("Hire " + b.dead() + " replacement" + (b.dead() > 1 ? "s" : ""), ChatFormatting.GREEN, ChatFormatting.BOLD),
				List.of(money("Price: ", price), t("They move in right away.", ChatFormatting.GRAY))), () -> replace(b, price));
		}
		// storehouse
		if (b.type == BuildingType.STOREHOUSE) {
			int used = 0;
			for (int i = 0; i < b.storage.getContainerSize(); i++) {
				used += b.storage.getItem(i).isEmpty() ? 0 : 1;
			}
			button(32, icon(Items.BARREL, t("Open storage", ChatFormatting.AQUA, ChatFormatting.BOLD),
				List.of(t(used + " / " + b.storage.getContainerSize() + " slots used", ChatFormatting.GRAY),
					t("(or right-click any barrel in the storehouse)", ChatFormatting.DARK_GRAY))),
				() -> ColonycraftMod.nextTick(() -> openStorage(viewer, b)));
			button(34, icon(b.autosell ? Items.EMERALD_BLOCK : Items.COAL_BLOCK,
				t("Autosell: " + (b.autosell ? "ON" : "OFF"), b.autosell ? ChatFormatting.GREEN : ChatFormatting.GRAY, ChatFormatting.BOLD),
				List.of(t("When on, everything in this storehouse that", ChatFormatting.GRAY),
					t("the Market buys is sold every morning.", ChatFormatting.GRAY),
					b.tier >= 3 ? t("Tier 3 bonus: +10% on every sale.", ChatFormatting.GOLD) : t("Tier 3 gets +10% on sales.", ChatFormatting.DARK_GRAY),
					Component.empty(), t("Click to switch.", ChatFormatting.YELLOW))), () -> {
				b.autosell = !b.autosell;
				Colonies.markDirty();
				render();
			});
		}
		// cellblock
		if (b.type == BuildingType.CELLBLOCK) {
			shacklesButton(32);
		}
		// repair and renovate
		ServerLevel here = level();
		int broken = here == null ? 0 : Colonies.damaged(here, b);
		long rebuildPrice = Colonies.rebuildPrice(b);
		button(38, icon(Items.BRICKS, t("Repair & renovate", ChatFormatting.AQUA, ChatFormatting.BOLD),
			List.of(money("Price: ", rebuildPrice),
				broken > 0 ? t(broken + " blocks missing or out of place.", ChatFormatting.RED) : t("Nothing's broken.", ChatFormatting.GREEN),
				t("Rebuilds it exactly as designed. Older", ChatFormatting.GRAY), t("buildings get the latest look.", ChatFormatting.GRAY),
				t("Anything else in it gets cleared.", ChatFormatting.DARK_GRAY), Component.empty(), t("Click to rebuild.", ChatFormatting.YELLOW))),
			() -> rebuild(b, rebuildPrice));
		// demolish
		String why = Colonies.whyNoDemolish(b);
		long refund = Math.round(b.spent * Colonies.REFUND);
		button(40, icon(Items.TNT, t(confirmDemolish ? "Click again to demolish" : "Demolish", ChatFormatting.RED, ChatFormatting.BOLD),
			List.of(money("Refund: ", refund), t(b.type == BuildingType.TOWN_HALL ? "This disbands the colony." : "The workers leave.", ChatFormatting.GRAY),
				why == null ? Component.empty() : t(why, ChatFormatting.RED))), () -> demolish(b, why));

		button(45, icon(Items.ARROW, t("Back", ChatFormatting.YELLOW), List.of()), () -> {
			selected = null;
			render();
		});
	}

	private static String upgradeText(Colony.Building b) {
		int next = b.tier + 1;
		return switch (b.type) {
			case TOWN_HALL -> "Bigger land and room for " + (8 + 6 * (next - 1)) + " buildings.";
			case RESIDENCE -> "Beds for " + b.type.housing(next) + " workers.";
			case STOREHOUSE -> next == 2 ? "54 slots of storage." : "+10% on everything it auto-sells.";
			case BARRACKS -> b.type.workers(next) + " iron golems.";
			case WATCHTOWER -> b.type.workers(next) + " archers, rebuilt in " + (next == 2 ? "stone bricks." : "deepslate.");
			case WALL, GATEHOUSE -> "Rebuilt in " + (next == 2 ? "stone bricks." : "deepslate.");
			case CELLBLOCK -> BuildingType.cells(next) + " cells: two more get unbricked.";
			default -> b.type.workers(next) + " workers, and each one works harder.";
		};
	}

	// ---------------------------------------------------------------- actions

	private @Nullable ServerLevel level() {
		return Colonies.level(colony);
	}

	private void buy(BuildingType type, long price) {
		if (!Bank.pay(viewer, price)) {
			nope("You need " + Bank.format(price) + " for that.");
			return;
		}
		Blueprints.give(viewer, Blueprints.of(type));
		kaching();
		viewer.sendSystemMessage(Component.literal("Bought: " + (type == BuildingType.TOWN_HALL ? "Colony Charter" : "Blueprint: " + type.displayName)
			+ ". Right-click the ground to build it.").withStyle(ChatFormatting.GREEN));
		render();
	}

	private void upgrade(Colony.Building b, long price) {
		ServerLevel level = level();
		String why = Colonies.whyNoUpgrade(b);
		if (why != null || level == null) {
			nope(why == null ? "Can't reach that building right now." : why);
			return;
		}
		if (!level.isLoaded(b.world(b.type.home()))) {
			nope("Go a bit closer to that building first.");
			return;
		}
		if (b.type.rebuildsOnUpgrade()) {
			String blocked = Colonies.whyNoRebuild(level, b);
			if (blocked != null) {
				nope(blocked);
				return;
			}
		}
		if (!Bank.pay(viewer, price)) {
			nope("You need " + Bank.format(price) + " for that.");
			return;
		}
		Colonies.upgrade(level, b, price);
		kaching();
		render();
	}

	private void replace(Colony.Building b, long price) {
		ServerLevel level = level();
		BlockPos home = b.world(b.type.home());
		if (level == null || !level.isLoaded(home)) {
			nope("Go a bit closer to that building first.");
			return;
		}
		int dead = b.dead();
		if (!Bank.pay(viewer, price)) {
			nope("You need " + Bank.format(price) + " for that.");
			return;
		}
		int hired = Colonies.replaceDead(level, b);
		if (hired < dead) {
			Bank.credit(viewer.getUUID(), viewer.getName().getString(), Bank.cents(Colonies.REPLACE_PRICE) * (dead - hired));
		}
		kaching();
		render();
	}

	private void rebuild(Colony.Building b, long price) {
		ServerLevel level = level();
		if (level == null) {
			nope("Can't reach that building right now.");
			return;
		}
		String why = Colonies.whyNoRebuild(level, b);
		if (why != null) {
			nope(why);
			return;
		}
		if (!Bank.pay(viewer, price)) {
			nope("You need " + Bank.format(price) + " for that.");
			return;
		}
		Colonies.rebuild(level, b, false);
		kaching();
		viewer.sendSystemMessage(Component.literal("The builders are on it: the " + b.title() + " is going back up as designed.")
			.withStyle(ChatFormatting.GOLD));
		render();
	}

	private void demolish(Colony.Building b, @Nullable String why) {
		if (why != null) {
			nope(why);
			return;
		}
		if (!confirmDemolish) {
			confirmDemolish = true;
			render();
			return;
		}
		ServerLevel level = level();
		if (level == null) {
			return;
		}
		boolean hall = b.type == BuildingType.TOWN_HALL;
		String name = b.title();
		long refund = Colonies.demolish(level, b);
		viewer.sendSystemMessage(Component.literal("Demolished the " + name + ". Refunded ").withStyle(ChatFormatting.GOLD).append(Bank.text(refund)));
		selected = null;
		confirmDemolish = false;
		if (hall) {
			ColonycraftMod.nextTick(viewer::closeContainer);
		} else {
			render();
		}
	}

	private void kaching() {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.experience_orb.pickup", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.3f);
	}

	private void nope(String why) {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	// ---------------------------------------------------------------- slots

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
		return Colonies.all().contains(colony) && colony.claims(player.blockPosition());
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
