package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
 * The Governor's Table at your Headquarters: every colony on one screen. Open any colony's Town Hall from here (by
 * letter), link colonies into the network, give them a role, tell their mayors whether to govern, send convoys of
 * goods between their storehouses (once, or every day), and read the reports.
 */
final class NetworkMenu extends ChestMenu {
	private static final int SIZE = 54;
	private static final int[] GRID = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

	enum Page {
		COLONIES, COLONY, ROLES, SEND, CONVOYS, ORDERS, REPORT, TREASURY
	}

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private Page page = Page.COLONIES;
	private @Nullable Colony selected;
	// sending a convoy
	private @Nullable Colony to;
	private ItemStack kind = ItemStack.EMPTY;
	private long amount = 64;
	private boolean daily;
	private boolean shift;
	private boolean right;
	/** The Governor's Desk this table was opened from ("dimension|x,y,z"), or "" at the palace. */
	private String desk = "";

	static void open(ServerPlayer player) {
		open(player, "");
	}

	static void open(ServerPlayer player, String desk) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			NetworkMenu menu = new NetworkMenu(id, player, new SimpleContainer(SIZE));
			menu.desk = desk;
			menu.render();
			return menu;
		},
			Component.literal("The Governor's Table").withStyle(ChatFormatting.DARK_GREEN)));
	}

	private NetworkMenu(int syncId, ServerPlayer viewer, SimpleContainer box) {
		super(MenuType.GENERIC_9x6, syncId, viewer.getInventory(), box, 6);
		this.viewer = viewer;
		this.box = box;
		render();
	}

	// ---------------------------------------------------------------- helpers

	private static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static ItemStack glow(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Blueprints.text(text, formats);
	}

	private static Item item(String id) {
		return TownHallMenu.item("minecraft:" + id, Items.PAPER);
	}

	private void button(int slot, ItemStack icon, @Nullable Action action) {
		box.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	private void go(Page p) {
		click();
		page = p;
	}

	private void click() {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:ui.button.click", viewer.getX(), viewer.getY(), viewer.getZ(), 0.4f, 1f);
	}

	private void kaching() {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.experience_orb.pickup", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.3f);
	}

	private void nope(@Nullable String why) {
		if (why == null) {
			kaching();
			return;
		}
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	private static String days(long n) {
		return n + (n == 1 ? " day" : " days");
	}

	private static List<Component> wrap(String text, ChatFormatting color) {
		List<Component> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() + word.length() + 1 > 42 && line.length() > 0) {
				lines.add(t(line.toString(), color));
				line.setLength(0);
			}
			line.append(line.length() > 0 ? " " : "").append(word);
		}
		if (line.length() > 0) {
			lines.add(t(line.toString(), color));
		}
		return lines;
	}

	// ---------------------------------------------------------------- drawing

	private void render() {
		actions.clear();
		ItemStack filler = icon(item("green_stained_glass_pane"), Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		if (selected != null && !Colonies.all().contains(selected)) {
			selected = null;
			page = Page.COLONIES;
		}
		tabs();
		switch (page) {
			case COLONIES -> colonies();
			case COLONY -> colony();
			case ROLES -> roles();
			case SEND -> send();
			case CONVOYS -> convoys();
			case ORDERS -> orders();
			case REPORT -> report();
			case TREASURY -> treasury();
		}
	}

	private ItemStack tab(String icon, String name, boolean on, List<Component> lore) {
		ItemStack stack = icon(item(icon), t(name, on ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD), lore);
		return on ? glow(stack) : stack;
	}

	private void tabs() {
		Network.Net net = Network.of(viewer.getUUID());
		Network.Seat seat = Network.seat(viewer.getUUID());
		int linked = 0;
		for (Colony c : Colonies.ownedBy(viewer.getUUID())) {
			if (Network.linked(c)) {
				linked++;
			}
		}
		button(0, tab("filled_map", "The colonies", page == Page.COLONIES || page == Page.COLONY || page == Page.ROLES,
			List.of(t(linked + " in the network" + (seat == null ? "" : " (room for " + (Network.capacity(seat.tier()) + (seat.home() == null ? 0 : 1)) + ")"), ChatFormatting.GRAY))),
			() -> go(Page.COLONIES));
		button(1, tab("chest_minecart", "On the road", page == Page.CONVOYS, List.of(t(net.convoys.size() + " convoys under way", ChatFormatting.GRAY))),
			() -> go(Page.CONVOYS));
		button(2, tab("clock", "Standing orders", page == Page.ORDERS, List.of(t(net.orders.size() + " every day", ChatFormatting.GRAY))),
			() -> go(Page.ORDERS));
		button(3, tab("written_book", "Reports", page == Page.REPORT, List.of(t("What the mayors and carriers wrote.", ChatFormatting.GRAY))),
			() -> go(Page.REPORT));
		long total = Bank.balance(viewer) + Network.vaults(viewer.getUUID()) + Ledgers.assets(Ledgers.lines(viewer.getUUID()));
		button(5, tab("gold_ingot", "The treasury", page == Page.TREASURY, List.of(t("Everything you're worth: " + Bank.format(total), ChatFormatting.GOLD),
			t("Daily income, wages, what's in the vaults.", ChatFormatting.GRAY))), () -> go(Page.TREASURY));
		button(8, icon(Items.BARRIER, t("Close", ChatFormatting.RED), List.of()), () -> ColonycraftMod.nextTick(viewer::closeContainer));
		button(49, icon(Items.SUNFLOWER, t("Your balance", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(t(Bank.format(Bank.balance(viewer)), ChatFormatting.GOLD))),
			null);
	}

	/** A colony at a glance. */
	private List<Component> summary(Colony c) {
		List<Component> lore = new ArrayList<>();
		Network.Seat seat = Network.seat(c.owner);
		boolean home = seat != null && seat.home() == c;
		boolean linked = Network.linked(c);
		lore.add(t(home ? "★ Your capital: the seat of government" : linked ? "In the network" : "Not in the network", linked ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		if (linked) {
			lore.add(t("Role: " + Network.role(c).title, ChatFormatting.AQUA));
		}
		int dead = 0;
		int jobs = 0;
		for (Colony.Building b : c.buildings) {
			dead += b.dead();
			jobs += b.villagers.size();
		}
		lore.add(t("Level " + c.tier() + " · " + c.slotsUsed() + " / " + c.maxBuildings() + " buildings", ChatFormatting.GRAY));
		lore.add(t("Workers " + (jobs - dead) + " / " + jobs + (dead > 0 ? " · " + dead + " jobs empty!" : ""), dead > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
		lore.add(t("Wages " + Bank.format(c.dailyWages()) + " a day", ChatFormatting.GRAY));
		long[] book = Network.of(c.owner).books.get(c.id);
		if (book != null) {
			long net = Network.net(c);
			lore.add(t("Last payday: +" + Bank.format(book[0]) + " earned, -" + Bank.format(book[1] + book[2] + book[3]) + " costs = "
				+ (net >= 0 ? "+" : "") + Bank.format(net), net >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		if (c.striking) {
			lore.add(t("ON STRIKE: wages unpaid", ChatFormatting.RED, ChatFormatting.BOLD));
		}
		long items = 0;
		List<Network.Stock> stock = Network.stock(c);
		for (Network.Stock s : stock) {
			items += s.count();
		}
		lore.add(t("In store: " + items + " items, " + stock.size() + " kinds", ChatFormatting.GOLD));
		for (int i = 0; i < Math.min(3, stock.size()); i++) {
			lore.add(t("  " + stock.get(i).count() + " × " + stock.get(i).kind().getHoverName().getString(), ChatFormatting.DARK_GRAY));
		}
		Colony.Building hall = c.townHall();
		if (linked) {
			lore.add(t(hall == null || hall.alive() == 0 ? "No mayor: nobody governs!" : Network.governs(c) ? "The mayor governs: rehires the dead"
				: "The mayor waits for orders", hall == null || hall.alive() == 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
		}
		if (seat != null && seat.home() != null && !home && c.dimension.equals(seat.dimension())) {
			lore.add(t(Math.round(Network.distance(c, seat.home())) + " blocks from the capital", ChatFormatting.DARK_GRAY));
		}
		return lore;
	}

	private void colonies() {
		Network.Seat seat = Network.seat(viewer.getUUID());
		int i = 0;
		for (Colony c : Network.colonies(viewer.getUUID())) {
			if (i >= GRID.length) {
				break;
			}
			boolean home = seat != null && seat.home() == c;
			List<Component> lore = summary(c);
			lore.add(t("Click to manage it.", ChatFormatting.YELLOW));
			ItemStack stack = icon(home ? Items.FILLED_MAP : Network.linked(c) ? Items.BELL : Items.MAP, t(c.name, home ? ChatFormatting.GOLD
				: ChatFormatting.WHITE, ChatFormatting.BOLD), lore);
			boolean trouble = c.striking;
			for (Colony.Building b : c.buildings) {
				trouble |= b.dead() > 0;
			}
			button(GRID[i++], trouble ? glow(stack) : stack, () -> {
				click();
				selected = c;
				page = Page.COLONY;
			});
		}
		if (i == 0) {
			button(22, icon(Items.BARRIER, t("No colonies", ChatFormatting.GRAY), List.of(t("Found one with a Colony Charter.", ChatFormatting.DARK_GRAY))), null);
		}
		button(13, glow(icon(Items.FILLED_MAP, t("The Governor's Table", ChatFormatting.GOLD, ChatFormatting.BOLD), seat != null ? List.of(
			t("You govern from " + seat.where() + " (tier " + seat.tier() + ")", ChatFormatting.GRAY),
			t("Runs " + Network.capacity(seat.tier()) + " colonies" + (seat.home() == null ? "." : " besides its own."), ChatFormatting.GRAY),
			t("Linked colonies send each other convoys, may take", ChatFormatting.DARK_GRAY),
			t("a role, and their mayors govern for you.", ChatFormatting.DARK_GRAY),
			t("Glowing: trouble (empty jobs, a strike).", ChatFormatting.DARK_GRAY)) : List.of(
			t("No seat of government: every colony at a glance,", ChatFormatting.GRAY),
			t("its money, its Town Hall by letter. Build Headquarters", ChatFormatting.GRAY),
			t("or give a Governor's Desk the charter to link them:", ChatFormatting.DARK_GRAY),
			t("convoys, roles, and mayors who govern.", ChatFormatting.DARK_GRAY),
			t("Glowing: trouble (empty jobs, a strike).", ChatFormatting.DARK_GRAY)))), null);
		charterButton(seat);
	}

	/** At a Governor's Desk: give it the charter (govern from here), or expand its chancery. */
	private void charterButton(@Nullable Network.Seat seat) {
		if (desk.isEmpty()) {
			return;
		}
		Network.Net net = Network.of(viewer.getUUID());
		boolean here = seat != null && !seat.palace() && desk.equals(net.hqDesk);
		if (seat != null && seat.palace()) {
			button(15, icon(Items.LECTERN, t("A desk, not the seat", ChatFormatting.GRAY, ChatFormatting.BOLD), List.of(
				t("You govern from " + seat.where() + ".", ChatFormatting.GRAY),
				t("This desk is a window onto the same table.", ChatFormatting.DARK_GRAY))), null);
		} else if (!here) {
			long price = net.hqTier > 0 ? 0 : Network.charterPrice(1);
			button(15, glow(icon(Items.WRITABLE_BOOK, t("Govern from this desk", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
				t("Make this house your Headquarters: the colony", ChatFormatting.GRAY),
				t("whose land it stands on becomes your capital.", ChatFormatting.GRAY),
				t(price > 0 ? "The charter costs " + Bank.format(price) + "." : "Moving the charter here is free.", ChatFormatting.GOLD),
				t("Break the desk and the charter waits for another.", ChatFormatting.DARK_GRAY)))), () -> {
					String why = Network.charter(viewer.getUUID(), desk);
					if (why == null) {
						click();
						Network.Seat now = Network.seat(viewer.getUUID());
						viewer.sendSystemMessage(Component.literal("Sealed and signed: you govern from " + (now == null ? "this desk" : now.where()) + ".")
							.withStyle(ChatFormatting.GOLD));
					}
					nope(why);
				});
		} else if (net.hqTier < 3) {
			long price = Network.charterPrice(net.hqTier + 1);
			button(15, icon(Items.WRITABLE_BOOK, t("Expand the chancery", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
				t("Tier " + net.hqTier + " → " + (net.hqTier + 1) + ": runs " + Network.capacity(net.hqTier + 1) + " colonies.", ChatFormatting.GRAY),
				t("Costs " + Bank.format(price) + ".", ChatFormatting.GOLD))), () -> {
					String why = Network.expand(viewer.getUUID());
					if (why == null) {
						click();
					}
					nope(why);
				});
		} else {
			button(15, glow(icon(Items.LECTERN, t("Your seat of government", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
				t("The chancery is as big as it gets.", ChatFormatting.GRAY)))), null);
		}
	}

	// ---------------------------------------------------------------- one colony

	private void colony() {
		Colony c = selected;
		if (c == null) {
			page = Page.COLONIES;
			colonies();
			return;
		}
		Network.Seat seat = Network.seat(viewer.getUUID());
		boolean home = seat != null && seat.home() == c;
		boolean linked = Network.linked(c);
		button(4, icon(home ? Items.FILLED_MAP : Items.BELL, t(c.name, ChatFormatting.GOLD, ChatFormatting.BOLD), summary(c)), null);
		button(19, icon(Items.LECTERN, t("Open its Town Hall", ChatFormatting.AQUA, ChatFormatting.BOLD), List.of(
			t("The counter, by letter: buy, upgrade, replace", ChatFormatting.GRAY), t("workers, repair, demolish, from right here.", ChatFormatting.GRAY))),
			() -> {
				click();
				ColonycraftMod.nextTick(() -> TownHallMenu.openRemote(viewer, c));
			});
		if (!home) {
			button(20, icon(linked ? Items.FILLED_MAP : Items.MAP, t(linked ? "Unlink it" : "Link it to the network", ChatFormatting.AQUA, ChatFormatting.BOLD),
				List.of(t(linked ? "It leaves the network: no convoys, no role," : "Convoys, a role, and a mayor who governs.", ChatFormatting.GRAY),
					t(linked ? "and its standing orders are cancelled." : "Your seat runs " + (seat == null ? 0 : Network.capacity(seat.tier())) + " colonies.",
						ChatFormatting.GRAY))), () -> {
					if (linked) {
						Network.unlink(c);
						click();
					} else {
						nope(Network.link(c));
					}
				});
		}
		if (linked) {
			Network.Role role = Network.role(c);
			List<Component> lore = new ArrayList<>(wrap(role.text, ChatFormatting.GRAY));
			lore.add(t("Optional. The first role is free; changing costs " + Bank.format(Network.ROLE_CHANGE) + ".", ChatFormatting.DARK_GRAY));
			lore.add(t("Click to choose.", ChatFormatting.YELLOW));
			button(21, icon(item(role.icon), t("Role: " + role.title, ChatFormatting.AQUA, ChatFormatting.BOLD), lore), () -> go(Page.ROLES));
			boolean governs = Network.governs(c);
			Colony.Building hall = c.townHall();
			boolean mayor = hall != null && hall.alive() > 0;
			button(22, icon(governs ? Items.GOLDEN_HELMET : Items.LEATHER_HELMET, t("Governor: the mayor", ChatFormatting.AQUA, ChatFormatting.BOLD), List.of(
				t(governs ? "Governs: rehires dead workers every" : "Waits for orders: dead workers stay dead", ChatFormatting.GRAY),
				t(governs ? "morning, at " + Bank.format(Colonies.replacePrice(c)) + " each (you pay)." : "until you replace them yourself.", ChatFormatting.GRAY),
				mayor ? t("The mayor is at their desk.", ChatFormatting.GREEN) : t("There's no mayor! Replace them at the Town Hall.", ChatFormatting.RED),
				t("Click to " + (governs ? "stop" : "start") + ".", ChatFormatting.YELLOW))), () -> {
				Network.setGoverns(c, !governs);
				click();
			});
			button(23, icon(Items.CHEST_MINECART, t("Send a convoy from here", ChatFormatting.AQUA, ChatFormatting.BOLD), List.of(
				t("Goods from its storehouses to another colony's,", ChatFormatting.GRAY), t("once or every day. Costs a fee, takes days.", ChatFormatting.GRAY))),
				() -> {
					click();
					to = null;
					kind = ItemStack.EMPTY;
					amount = 64;
					daily = false;
					page = Page.SEND;
				});
		}
		// what's in store
		List<Network.Stock> stock = Network.stock(c);
		for (int i = 0; i < Math.min(18, stock.size()); i++) {
			Network.Stock s = stock.get(i);
			ItemStack show = s.kind().copyWithCount((int) Math.min(64, Math.max(1, s.count())));
			show.set(DataComponents.LORE, new ItemLore(List.of(t(s.count() + " in store", ChatFormatting.GOLD))));
			button(27 + i, show, null);
		}
		button(45, icon(Items.ARROW, t("Back", ChatFormatting.YELLOW), List.of()), () -> go(Page.COLONIES));
	}

	private void roles() {
		Colony c = selected;
		if (c == null) {
			page = Page.COLONIES;
			colonies();
			return;
		}
		Network.Role now = Network.role(c);
		button(4, icon(Items.BELL, t(c.name + ": choose a role", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			t("Optional: a role makes a colony better at one", ChatFormatting.GRAY), t("thing and a little worse at the rest.", ChatFormatting.GRAY),
			t("Only counts while it's in the network.", ChatFormatting.DARK_GRAY))), null);
		int[] slots = {20, 21, 22, 23, 24, 31};
		Network.Role[] roles = Network.Role.values();
		for (int i = 0; i < roles.length && i < slots.length; i++) {
			Network.Role r = roles[i];
			List<Component> lore = new ArrayList<>(wrap(r.text, ChatFormatting.GRAY));
			lore.add(t(r == now ? "Its role now." : "Click to choose." + (now != Network.Role.NONE ? " (" + Bank.format(Network.ROLE_CHANGE) + ")" : ""),
				r == now ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			ItemStack stack = icon(item(r.icon), t(r.title, r == now ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD), lore);
			button(slots[i], r == now ? glow(stack) : stack, r == now ? null : () -> {
				String why = Network.setRole(c, r);
				nope(why);
				if (why == null) {
					page = Page.COLONY;
				}
			});
		}
		button(45, icon(Items.ARROW, t("Back", ChatFormatting.YELLOW), List.of()), () -> go(Page.COLONY));
	}

	// ---------------------------------------------------------------- sending a convoy

	private void send() {
		Colony from = selected;
		if (from == null) {
			page = Page.COLONIES;
			colonies();
			return;
		}
		if (to != null && !Colonies.all().contains(to)) {
			to = null;
		}
		// where to
		int i = 0;
		for (Colony c : Network.colonies(viewer.getUUID())) {
			if (c == from || !Network.linked(c) || i >= 9) {
				continue;
			}
			boolean on = c == to;
			List<Component> lore = new ArrayList<>();
			String why = Network.route(from, c);
			lore.add(why == null ? t(Math.round(Network.distance(from, c)) + " blocks · " + days(Network.days(from, c)) + " on the road", ChatFormatting.GRAY)
				: t(why, ChatFormatting.RED));
			lore.add(t(on ? "The destination." : "Click to send it here.", on ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			ItemStack stack = icon(Items.BELL, t("To " + c.name, on ? ChatFormatting.GOLD : ChatFormatting.WHITE, ChatFormatting.BOLD), lore);
			button(9 + i++, on ? glow(stack) : stack, why != null ? null : () -> {
				click();
				to = c;
			});
		}
		if (i == 0) {
			button(13, icon(Items.BARRIER, t("Nowhere to send it", ChatFormatting.RED), List.of(t("Link another colony first.", ChatFormatting.GRAY))), null);
		}
		// what
		List<Network.Stock> stock = Network.stock(from);
		for (int k = 0; k < Math.min(18, stock.size()); k++) {
			Network.Stock s = stock.get(k);
			boolean on = ItemStack.isSameItemSameComponents(s.kind(), kind) && !kind.isEmpty();
			ItemStack show = s.kind().copyWithCount((int) Math.min(64, Math.max(1, s.count())));
			show.set(DataComponents.LORE, new ItemLore(List.of(t(s.count() + " in store at " + from.name, ChatFormatting.GOLD),
				t(on ? "The cargo." : "Click to load it.", on ? ChatFormatting.GREEN : ChatFormatting.YELLOW))));
			if (on) {
				glow(show);
			}
			button(18 + k, show, () -> {
				click();
				kind = s.kind().copyWithCount(1);
			});
		}
		// how much, how often, and go
		long have = kind.isEmpty() ? 0 : Network.count(from, kind);
		button(36, icon(item("red_stained_glass_pane"), t("-64", ChatFormatting.RED), List.of()), () -> {
			amount = Math.max(1, amount - 64);
			click();
		});
		button(37, icon(item("red_stained_glass_pane"), t("-8", ChatFormatting.RED), List.of()), () -> {
			amount = Math.max(1, amount - 8);
			click();
		});
		button(39, icon(item("lime_stained_glass_pane"), t("+8", ChatFormatting.GREEN), List.of()), () -> {
			amount += 8;
			click();
		});
		button(40, icon(item("lime_stained_glass_pane"), t("+64", ChatFormatting.GREEN), List.of()), () -> {
			amount += 64;
			click();
		});
		button(41, icon(Items.HOPPER, t("All of it", ChatFormatting.YELLOW), List.of(t(have + " in store", ChatFormatting.GRAY))), () -> {
			amount = Math.max(1, have);
			click();
		});
		button(38, icon(Items.PAPER, t(amount + (daily ? " every day" : ""), ChatFormatting.WHITE, ChatFormatting.BOLD), List.of(
			t("How many to send" + (daily ? " (at most) each day." : "."), ChatFormatting.GRAY))), null);
		button(43, icon(daily ? Items.CLOCK : Items.MAP, t(daily ? "Every day (a standing order)" : "Just once", ChatFormatting.AQUA, ChatFormatting.BOLD), List.of(
			t(daily ? "Every morning, before the storehouses auto-sell," : "One convoy, leaving now.", ChatFormatting.GRAY),
			t(daily ? "up to this many leave, if there are any." : "", ChatFormatting.GRAY), t("Click to switch.", ChatFormatting.YELLOW))), () -> {
			daily = !daily;
			click();
		});
		if (to != null && !kind.isEmpty()) {
			long n = daily ? amount : Math.min(amount, have);
			Colony dest = to;
			List<Component> lore = new ArrayList<>();
			lore.add(t((daily ? "Up to " : "") + n + " × " + kind.getHoverName().getString(), ChatFormatting.WHITE));
			lore.add(t(from.name + " → " + dest.name, ChatFormatting.GRAY));
			lore.add(t("Fee: " + Bank.format(Network.fee(from, dest, Math.max(1, n))) + (daily ? " a day (for a full load)" : ""), ChatFormatting.GOLD));
			lore.add(t("Arrives in " + days(Network.days(from, dest)) + " (at the morning payday).", ChatFormatting.GRAY));
			lore.add(t("Half the fee when both have a train station", ChatFormatting.DARK_GRAY));
			lore.add(t("(or both a harbor): your own lines carry it.", ChatFormatting.DARK_GRAY));
			lore.add(t("Click to send.", ChatFormatting.YELLOW));
			button(53, glow(icon(Items.EMERALD, t(daily ? "Place the standing order" : "Send the convoy", ChatFormatting.GREEN, ChatFormatting.BOLD), lore)), () -> {
				String why = daily ? Network.addOrder(from, dest, kind, (int) Math.min(Integer.MAX_VALUE, amount))
					: Network.send(from, dest, kind, amount, "");
				nope(why);
				if (why == null) {
					viewer.sendSystemMessage(Component.literal(daily ? "Standing order placed: " + from.name + " → " + dest.name + "."
						: "The convoy has left " + from.name + " for " + dest.name + ".").withStyle(ChatFormatting.GREEN));
					page = daily ? Page.ORDERS : Page.CONVOYS;
				}
			});
		} else {
			button(53, icon(item("gray_dye"), t("Pick a destination and the cargo", ChatFormatting.GRAY), List.of()), null);
		}
		button(4, icon(Items.CHEST_MINECART, t("A convoy from " + from.name, ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			t("Top row: where to. Middle: what (click a pile).", ChatFormatting.GRAY),
			t("Bottom: how many, once or every day, then send.", ChatFormatting.GRAY))), null);
		button(45, icon(Items.ARROW, t("Back", ChatFormatting.YELLOW), List.of()), () -> go(Page.COLONY));
	}

	// ---------------------------------------------------------------- the road, the orders, the reports

	private void convoys() {
		Network.Net net = Network.of(viewer.getUUID());
		int i = 0;
		for (Network.Convoy cv : net.convoys) {
			if (i >= GRID.length) {
				break;
			}
			Colony from = Network.colony(cv.from);
			Colony dest = Network.colony(cv.to);
			List<Component> lore = new ArrayList<>();
			lore.add(t((from == null ? "?" : from.name) + " → " + (dest == null ? "?" : dest.name), ChatFormatting.WHITE));
			for (int k = 0; k < Math.min(4, cv.cargo.size()); k++) {
				ItemStack s = cv.cargo.get(k);
				lore.add(t("  " + s.getCount() + " × " + s.getHoverName().getString(), ChatFormatting.GRAY));
			}
			if (cv.cargo.size() > 4) {
				lore.add(t("  and " + (cv.cargo.size() - 4) + " more stacks", ChatFormatting.DARK_GRAY));
			}
			long left = Math.max(1, cv.arrives - Network.day);
			lore.add(t("Arrives " + (left == 1 ? "at the next payday" : "in " + days(left)), ChatFormatting.AQUA));
			lore.add(t("Paid " + Bank.format(cv.fee), ChatFormatting.GOLD));
			if (!cv.order.isEmpty()) {
				lore.add(t("(a standing order)", ChatFormatting.DARK_GRAY));
			}
			button(GRID[i++], icon(Items.CHEST_MINECART, t(cv.items + " items on the road", ChatFormatting.AQUA, ChatFormatting.BOLD), lore), null);
		}
		if (i == 0) {
			button(22, icon(Items.MINECART, t("Nothing on the road", ChatFormatting.GRAY), List.of(t("Send a convoy from a colony's page.", ChatFormatting.DARK_GRAY))),
				null);
		}
	}

	private void orders() {
		Network.Net net = Network.of(viewer.getUUID());
		int i = 0;
		for (Network.Order o : new ArrayList<>(net.orders)) {
			if (i >= GRID.length) {
				break;
			}
			Colony from = Network.colony(o.from);
			Colony dest = Network.colony(o.to);
			List<Component> lore = new ArrayList<>();
			lore.add(t("Every day, up to " + o.amount, ChatFormatting.WHITE));
			lore.add(t((from == null ? "?" : from.name) + " → " + (dest == null ? "?" : dest.name), ChatFormatting.GRAY));
			if (from != null && dest != null) {
				lore.add(t("About " + Bank.format(Network.fee(from, dest, o.amount)) + " a day for a full load", ChatFormatting.GOLD));
			}
			lore.add(t("Shift-click to cancel it.", ChatFormatting.YELLOW));
			ItemStack show = o.kind.copyWithCount(Math.max(1, Math.min(64, o.amount)));
			show.set(DataComponents.ITEM_NAME, t(o.kind.getHoverName().getString(), ChatFormatting.AQUA, ChatFormatting.BOLD));
			show.set(DataComponents.LORE, new ItemLore(lore));
			button(GRID[i++], show, () -> {
				if (!shift) {
					nope("Shift-click to cancel the order.");
					return;
				}
				net.orders.remove(o);
				net.report("Standing order cancelled: " + o.kind.getHoverName().getString() + (from == null ? "" : " from " + from.name)
					+ (dest == null ? "" : " to " + dest.name) + ".");
				Network.changed();
				click();
			});
		}
		if (i == 0) {
			button(22, icon(Items.CLOCK, t("No standing orders", ChatFormatting.GRAY), List.of(
				t("Send a convoy \"every day\" from a colony's page:", ChatFormatting.DARK_GRAY), t("wheat to the distillery, ore to the workshop...", ChatFormatting.DARK_GRAY))),
				null);
		}
	}

	private void treasury() {
		java.util.UUID owner = viewer.getUUID();
		Network.Net net = Network.of(owner);
		long cash = Bank.balance(viewer);
		long vaults = Network.vaults(owner);
		List<Ledgers.Line> elsewhere = Ledgers.lines(owner);
		long assets = Ledgers.assets(elsewhere);
		long otherDaily = Ledgers.daily(elsewhere);
		List<Component> worth = new ArrayList<>();
		worth.add(t("In hand (your Market balance): " + Bank.format(cash), ChatFormatting.GOLD));
		worth.add(t("In your banks' vaults: " + Bank.format(vaults), ChatFormatting.GOLD));
		for (Colony c : Colonies.ownedBy(owner)) {
			for (Colony.Building b : c.buildings) {
				if (b.type == BuildingType.BANK && b.vault > 0) {
					worth.add(t("  " + c.name + ": " + Bank.format(b.vault), ChatFormatting.DARK_GRAY));
				}
			}
		}
		for (Ledgers.Line l : elsewhere) {
			if (!l.daily()) {
				worth.add(t(l.label() + ": " + Bank.format(l.cents()), ChatFormatting.GOLD));
			}
		}
		worth.add(t("(A vault is open to anyone at the teller.)", ChatFormatting.DARK_GRAY));
		button(20, glow(icon(Items.GOLD_BLOCK, t("Worth: " + Bank.format(cash + vaults + assets), ChatFormatting.GOLD, ChatFormatting.BOLD), worth)), null);

		long earned = 0;
		long wages = 0;
		long fees = 0;
		long hires = 0;
		List<Component> per = new ArrayList<>();
		for (Colony c : Colonies.ownedBy(owner)) {
			long[] b = net.books.get(c.id);
			if (b == null) {
				continue;
			}
			earned += b[0];
			wages += b[1];
			fees += b[2];
			hires += b[3];
			long n = Network.net(c);
			per.add(t("  " + c.name + ": " + (n >= 0 ? "+" : "") + Bank.format(n), n >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		long netDay = earned - wages - fees - hires;
		List<Component> books = new ArrayList<>();
		books.add(t("Earned (auto-sales, the town street, taverns): +" + Bank.format(earned), ChatFormatting.GREEN));
		books.add(t("Wages: -" + Bank.format(wages), ChatFormatting.RED));
		books.add(t("Convoys: -" + Bank.format(fees), ChatFormatting.RED));
		books.add(t("Rehiring the dead: -" + Bank.format(hires), ChatFormatting.RED));
		books.add(t("By colony:", ChatFormatting.WHITE));
		books.addAll(per);
		button(22, glow(icon(Items.WRITABLE_BOOK, t("The colonies' last payday: " + (netDay >= 0 ? "+" : "") + Bank.format(netDay),
			netDay >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD), books)), null);

		List<Component> all = new ArrayList<>();
		if (net.balanceKnown) {
			all.add(t("Your balance moved " + (net.balanceChange >= 0 ? "+" : "") + Bank.format(net.balanceChange), net.balanceChange >= 0
				? ChatFormatting.GREEN : ChatFormatting.RED));
			all.add(t("between the last two paydays, from everything:", ChatFormatting.GRAY));
			all.add(t("colonies, the Compagnie, oil, sales, spending.", ChatFormatting.GRAY));
		} else {
			all.add(t("Known after the next two paydays.", ChatFormatting.GRAY));
		}
		button(24, icon(Items.CLOCK, t("Your whole day", ChatFormatting.AQUA, ChatFormatting.BOLD), all), null);

		List<Component> affairs = new ArrayList<>();
		for (Ledgers.Line l : elsewhere) {
			if (l.daily()) {
				affairs.add(t(l.label() + ": " + (l.cents() >= 0 ? "+" : "-") + Bank.format(Math.abs(l.cents())) + " a day",
					l.cents() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
		}
		if (affairs.isEmpty()) {
			affairs.add(t("Nothing from De Compagnie or the Heeren.", ChatFormatting.GRAY));
		}
		affairs.add(t("Colonies and other affairs together:", ChatFormatting.WHITE));
		long together = netDay + otherDaily;
		affairs.add(t((together >= 0 ? "+" : "-") + Bank.format(Math.abs(together)) + " a day", together >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		button(29, icon(Items.FILLED_MAP, t("Your other affairs: " + (otherDaily >= 0 ? "+" : "-") + Bank.format(Math.abs(otherDaily)) + " a day",
			ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), affairs), null);

		long wageBill = 0;
		for (Colony c : Colonies.ownedBy(owner)) {
			wageBill += c.dailyWages();
		}
		long orders = 0;
		for (Network.Order o : net.orders) {
			Colony from = Network.colony(o.from);
			Colony to = Network.colony(o.to);
			if (from != null && to != null) {
				orders += Network.fee(from, to, o.amount);
			}
		}
		long otherBills = 0;
		List<Component> bills = new ArrayList<>();
		bills.add(t("Wages: " + Bank.format(wageBill), ChatFormatting.GRAY));
		bills.add(t("Standing orders (full loads): up to " + Bank.format(orders), ChatFormatting.GRAY));
		for (Ledgers.Line l : elsewhere) {
			if (l.daily() && l.cents() < 0) {
				otherBills -= l.cents();
				bills.add(t(l.label() + ": " + Bank.format(-l.cents()), ChatFormatting.GRAY));
			}
		}
		long due = wageBill + orders + otherBills;
		bills.add(t(cash >= due ? "Covered." : "Short! Unpaid colonies strike, unpaid troops desert.", cash >= due ? ChatFormatting.GREEN : ChatFormatting.RED));
		button(31, icon(Items.PAPER, t("Tomorrow's bills: " + Bank.format(due), ChatFormatting.YELLOW, ChatFormatting.BOLD), bills), null);
	}

	private void report() {
		Network.Net net = Network.of(viewer.getUUID());
		List<Component> lines = new ArrayList<>();
		for (int i = Math.max(0, net.report.size() - 10); i < net.report.size(); i++) {
			lines.addAll(wrap(net.report.get(i), ChatFormatting.GRAY));
		}
		if (lines.isEmpty()) {
			lines.add(t("Nothing to report yet.", ChatFormatting.GRAY));
		}
		button(22, icon(Items.WRITTEN_BOOK, t("Reports from the colonies", ChatFormatting.GOLD, ChatFormatting.BOLD), lines), null);
	}

	// ---------------------------------------------------------------- slots

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				shift = clickType == ContainerInput.QUICK_MOVE;
				right = button == 1;
				action.run();
			}
			render();
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
		return true;
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
