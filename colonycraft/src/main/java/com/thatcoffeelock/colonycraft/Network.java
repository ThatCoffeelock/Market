package com.thatcoffeelock.colonycraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * The colony network: a player's Headquarters and the colonies linked to it. From the HQ's Governor's Table you see
 * every colony at once and run them from there; linked colonies send each other convoys of goods (for a fee, and they
 * take days on the road), may take on a role that makes them better at one thing, and their mayors act as governors:
 * they rehire dead workers without being asked. Saved to <world>/colonycraft-network.json.
 * <p>
 * The network's clock is Colonycraft's own payday: every payday is a day on the road.
 */
final class Network {
	/** What a colony specialises in, if anything. Only counts while it's linked to the HQ. */
	enum Role {
		NONE("No role", "Does a bit of everything, like any colony.", "barrier"),
		BREADBASKET("Breadbasket", "Farms, ranches, apiaries, fisheries and tobacco +25%. Mines and lumber camps -15%.", "wheat"),
		MINING_TOWN("Mining Town", "Mines and lumber camps +25%. Farms, ranches, apiaries, fisheries and tobacco -15%.", "iron_pickaxe"),
		MANUFACTORY("Manufactory", "Workshops get through 40% more. Everything gathered -10%.", "crafting_table"),
		MARKET_TOWN("Market Town", "Auto-sales +10%, the town street earns 20% more. Everything gathered -10%.", "emerald"),
		PORT("Port", "Fisheries +25%, the harbor's bonus doubled, convoys to and from it cost a quarter less. Everything else gathered -10%.", "oak_boat"),
		FORTRESS("Fortress", "Walls and a garrison: besieged, it still works at 75% and its convoys get through, and sieges lift in half the time. Everything gathered -10%.", "shield");

		final String title;
		final String text;
		final String icon;

		Role(String title, String text, String icon) {
			this.title = title;
			this.text = text;
			this.icon = icon;
		}

		static Role byName(@Nullable String name) {
			try {
				return name == null ? NONE : valueOf(name);
			} catch (IllegalArgumentException e) {
				return NONE;
			}
		}
	}

	/** Goods on the road: from one colony's storehouses to another's. */
	static final class Convoy {
		String id = "";
		String from = "";
		String to = "";
		List<JsonElement> cargoJson = new ArrayList<>();
		transient List<ItemStack> cargo = new ArrayList<>();
		long items;
		long fee;
		long sent;
		long arrives;
		/** The standing order that sent it, or "". */
		String order = "";
	}

	/** A rival's army camped outside a colony (De Hooghe Heeren): nothing gets in or out, nobody works the fields. */
	static final class Siege {
		String by = "";
		long until;
	}

	/** "Every payday, send up to so many of this from here to there." */
	static final class Order {
		String id = "";
		String from = "";
		String to = "";
		JsonElement kindJson;
		transient ItemStack kind = ItemStack.EMPTY;
		int amount;
	}

	/** One player's network. */
	static final class Net {
		String owner = "";
		List<String> linked = new ArrayList<>();
		/** Colony id -> Role name. */
		Map<String, String> roles = new HashMap<>();
		/** Colonies whose mayor has been told not to rehire on their own. */
		List<String> noGovernor = new ArrayList<>();
		List<Convoy> convoys = new ArrayList<>();
		List<Order> orders = new ArrayList<>();
		/** What happened lately, newest last. */
		List<String> report = new ArrayList<>();
		/** Governor's Desks, by "dimension|x,y,z". */
		List<String> desks = new ArrayList<>();
		/** The desk you govern from (when there's no palace), and its chancery's tier (kept when the desk moves). */
		String hqDesk = "";
		int hqTier;
		/** Chancery wings added to the palace from the table, past its tier 3: each runs 5 more colonies. */
		int annex;
		/** Colony id -> the siege it's under. */
		Map<String, Siege> sieges = new HashMap<>();
		/** Each colony's books since its last payday: {earned, wages, convoy fees, rehiring}. */
		Map<String, long[]> books = new HashMap<>();
		/** The owner's balance at the last payday, and how much it moved over the day before (from everything they do). */
		long lastBalance = Long.MIN_VALUE;
		long balanceChange;
		boolean balanceKnown;

		long[] book(String colony) {
			return books.computeIfAbsent(colony, k -> new long[4]);
		}
		int nextId = 1;

		String newId(String prefix) {
			return prefix + (nextId++);
		}

		void report(String line) {
			report.add(line);
			while (report.size() > 30) {
				report.remove(0);
			}
		}

		@Nullable Order order(String id) {
			for (Order o : orders) {
				if (o.id.equals(id)) {
					return o;
				}
			}
			return null;
		}
	}

	/** What changing a colony's role costs (choosing the first one is free). */
	static final long ROLE_CHANGE = Bank.cents(500);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	static final Map<String, Net> ALL = new LinkedHashMap<>();
	/** Paydays since the network began: the convoys' clock. */
	static long day;
	private static @Nullable MinecraftServer server;
	private static @Nullable Path file;
	private static boolean dirty;

	private static final class Snapshot {
		long day;
		Map<String, Net> networks = new LinkedHashMap<>();
	}

	private Network() {
	}

	static void changed() {
		dirty = true;
	}

	static Net of(UUID owner) {
		return ALL.computeIfAbsent(owner.toString(), k -> {
			Net n = new Net();
			n.owner = k;
			return n;
		});
	}

	// ---------------------------------------------------------------- the HQ and its links

	/** The player's Headquarters (there's only ever one), or null. */
	static @Nullable Colony.Building hq(UUID owner) {
		for (Colony c : Colonies.ownedBy(owner)) {
			for (Colony.Building b : c.buildings) {
				if (b.type == BuildingType.HEADQUARTERS) {
					return b;
				}
			}
		}
		return null;
	}

	/**
	 * Where you govern from: the Headquarters palace if you have one, otherwise a Governor's Desk you've given the
	 * charter (in a house of your own, say). {@code home} is the colony it stands in (your capital), or null if it's
	 * out in the wild; {@code where} is how the menus name it.
	 */
	record Seat(int tier, @Nullable Colony home, String dimension, String where, boolean palace) {
	}

	/**
	 * What the charter of a desk seat costs, then its upgrades: the first two the same as the palace's, and past tier 3
	 * the chancery keeps growing for as long as you pay, each tier a bit dearer (tier 4 twice tier 3, then +1x a tier).
	 */
	static long charterPrice(int toTier) {
		double base = BuildingType.HEADQUARTERS.price;
		return Bank.cents(toTier <= 1 ? base : toTier == 2 ? base * 1.5 : base * 3 * (toTier - 2));
	}

	static @Nullable Seat seat(UUID owner) {
		Colony.Building palace = hq(owner);
		if (palace != null) {
			Net wings = ALL.get(owner.toString());
			return new Seat(palace.tier + (palace.tier >= 3 && wings != null ? wings.annex : 0), palace.colony, palace.colony.dimension, "your palace in " + palace.colony.name, true);
		}
		Net n = ALL.get(owner.toString());
		if (n == null || n.hqDesk.isEmpty() || n.hqTier <= 0 || !n.desks.contains(n.hqDesk)) {
			return null;
		}
		String[] parts = n.hqDesk.split("\\|", 2);
		String dim = parts[0];
		String[] xyz = parts.length > 1 ? parts[1].split(",") : new String[0];
		Colony home = null;
		String where = "your desk";
		if (xyz.length == 3) {
			net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
			for (Colony c : Colonies.ownedBy(owner)) {
				if (c.dimension.equals(dim) && c.claims(pos)) {
					home = c;
				}
			}
			where = home != null ? "your desk in " + home.name : "your house at " + pos.getX() + ", " + pos.getZ();
		}
		return new Seat(n.hqTier, home, dim, where, false);
	}

	/** How many colonies besides its own a seat of government can link: 3, 6, 10, then 5 more every tier, no end. */
	static int capacity(int tier) {
		return tier <= 1 ? 3 : tier == 2 ? 6 : 10 + 5 * (tier - 3);
	}

	static int capacity(UUID owner) {
		Seat seat = seat(owner);
		return seat == null ? 0 : capacity(seat.tier());
	}

	/** Is this colony part of its owner's network (the capital always is)? */
	static boolean linked(Colony c) {
		Seat seat = seat(c.owner);
		if (seat == null) {
			return false;
		}
		if (seat.home() == c) {
			return true;
		}
		Net n = ALL.get(c.owner.toString());
		if (n == null || !n.linked.contains(c.id)) {
			return false;
		}
		// over capacity (the seat moved somewhere smaller, say): the first ones linked stay
		int index = n.linked.indexOf(c.id);
		return index < capacity(seat.tier());
	}

	static @Nullable String link(Colony c) {
		Seat seat = seat(c.owner);
		if (seat == null) {
			return "You need a seat of government first: build Headquarters, or give a Governor's Desk the charter.";
		}
		if (seat.home() == c) {
			return c.name + " is your capital: it's always in the network.";
		}
		if (!c.dimension.equals(seat.dimension())) {
			return "No road reaches " + c.name + " from " + seat.where() + ": it's in another dimension.";
		}
		Net n = of(c.owner);
		if (n.linked.contains(c.id)) {
			return null;
		}
		if (n.linked.size() >= capacity(seat.tier())) {
			return "Your seat of government runs " + capacity(seat.tier()) + " colonies" + (seat.home() == null ? "" : " besides its own")
				+ ". Upgrade it to run more.";
		}
		n.linked.add(c.id);
		n.report(c.name + " joins the network.");
		changed();
		return null;
	}

	/**
	 * Gives a Governor's Desk the charter: from now on you govern from there. The first charter costs what the palace
	 * does; moving it to another desk later is free. Not while you have the palace (that's your seat).
	 */
	static @Nullable String charter(UUID owner, String desk) {
		Colony.Building palace = hq(owner);
		if (palace != null) {
			return "Your palace in " + palace.colony.name + " is your seat of government. Demolish it first if you'd rather govern from here.";
		}
		Net n = of(owner);
		if (!n.desks.contains(desk)) {
			return "That desk isn't yours.";
		}
		if (desk.equals(n.hqDesk) && n.hqTier > 0) {
			return null;
		}
		if (n.hqTier <= 0) {
			if (!Bank.charge(owner, charterPrice(1))) {
				return "The charter of government costs " + Bank.format(charterPrice(1)) + ".";
			}
			n.hqTier = 1;
		}
		n.hqDesk = desk;
		Seat seat = seat(owner);
		n.report("You now govern from " + (seat == null ? "your desk" : seat.where()) + ".");
		changed();
		return null;
	}

	/** Can the table expand this seat's chancery? A desk always can; the palace once its Town Hall has it at tier 3. */
	static boolean expandable(@Nullable Seat seat) {
		return seat != null && (!seat.palace() || seat.tier() >= 3);
	}

	/** Expands the seat's chancery by a tier: more colonies in the network. There's no top. */
	static @Nullable String expand(UUID owner) {
		Net n = of(owner);
		Seat seat = seat(owner);
		if (seat == null) {
			return "You need a seat of government first.";
		}
		if (!expandable(seat)) {
			return "Upgrade the palace at its Town Hall first. Past tier 3 its chancery grows from the Governor's Table.";
		}
		long price = charterPrice(seat.tier() + 1);
		if (!Bank.charge(owner, price)) {
			return "Expanding the chancery costs " + Bank.format(price) + ".";
		}
		if (seat.palace()) {
			n.annex++;
		} else {
			n.hqTier++;
		}
		n.report("The chancery grows: it runs " + capacity(seat.tier() + 1) + " colonies now.");
		changed();
		return null;
	}

	static void unlink(Colony c) {
		Net n = ALL.get(c.owner.toString());
		if (n != null && n.linked.remove(c.id)) {
			n.report(c.name + " leaves the network. Its standing orders are cancelled.");
			n.orders.removeIf(o -> o.from.equals(c.id) || o.to.equals(c.id));
			changed();
		}
	}

	static @Nullable Colony colony(String id) {
		for (Colony c : Colonies.all()) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return null;
	}

	/** The player's colonies, the capital first, then the linked ones, then the rest. */
	static List<Colony> colonies(UUID owner) {
		List<Colony> out = new ArrayList<>(Colonies.ownedBy(owner));
		Seat seat = seat(owner);
		out.sort((a, b) -> Integer.compare(rank(a, seat), rank(b, seat)));
		return out;
	}

	private static int rank(Colony c, @Nullable Seat seat) {
		return seat != null && seat.home() == c ? 0 : linked(c) ? 1 : 2;
	}

	/** The capital: the colony your seat of government stands in, or null. */
	static @Nullable Colony capital(UUID owner) {
		Seat seat = seat(owner);
		return seat == null ? null : seat.home();
	}

	// ---------------------------------------------------------------- roles

	static Role role(Colony c) {
		if (!linked(c)) {
			return Role.NONE;
		}
		Net n = ALL.get(c.owner.toString());
		return n == null ? Role.NONE : Role.byName(n.roles.get(c.id));
	}

	/** Sets a colony's role: the first one is free, changing it costs {@link #ROLE_CHANGE}. Returns why not, or null. */
	static @Nullable String setRole(Colony c, Role role) {
		if (!linked(c)) {
			return "Only colonies in the network take on a role. Link " + c.name + " first.";
		}
		Net n = of(c.owner);
		Role now = Role.byName(n.roles.get(c.id));
		if (now == role) {
			return null;
		}
		if (now != Role.NONE && !Bank.charge(c.owner, ROLE_CHANGE)) {
			return "Reorganising a colony costs " + Bank.format(ROLE_CHANGE) + ".";
		}
		if (role == Role.NONE) {
			n.roles.remove(c.id);
		} else {
			n.roles.put(c.id, role.name());
		}
		n.report(c.name + (role == Role.NONE ? " goes back to doing a bit of everything." : " is now a " + role.title + "."));
		changed();
		return null;
	}

	/** What a colony's role does to a day's harvest from this kind of building. */
	static double gather(Colony c, BuildingType type) {
		Role role = role(c);
		boolean food = type == BuildingType.FARM || type == BuildingType.RANCH || type == BuildingType.APIARY || type == BuildingType.FISHERY
			|| type == BuildingType.TOBACCO_FARM;
		boolean dig = type == BuildingType.MINE || type == BuildingType.LUMBER_CAMP;
		if (!food && !dig) {
			return siegeFactor(c);
		}
		double r = switch (role) {
			case NONE -> 1.0;
			case BREADBASKET -> food ? 1.25 : 0.85;
			case MINING_TOWN -> dig ? 1.25 : 0.85;
			case MANUFACTORY, MARKET_TOWN -> 0.9;
			case PORT -> type == BuildingType.FISHERY ? 1.25 : 0.9;
			case FORTRESS -> 0.9;
		};
		return r * siegeFactor(c);
	}

	static double workshop(Colony c) {
		return (role(c) == Role.MANUFACTORY ? 1.4 : 1.0) * siegeFactor(c);
	}

	// ---------------------------------------------------------------- sieges

	/** The siege this colony is under, or null. */
	static @Nullable Siege siege(Colony c) {
		Net n = ALL.get(c.owner.toString());
		Siege s = n == null ? null : n.sieges.get(c.id);
		return s != null && s.until >= day ? s : null;
	}

	/** Is it cut off? A Fortress never quite is. */
	static boolean cutOff(Colony c) {
		return siege(c) != null && role(c) != Role.FORTRESS;
	}

	/** How much work gets done: everything, 75% in a besieged Fortress, nothing in any other besieged colony. */
	static double siegeFactor(Colony c) {
		return siege(c) == null ? 1.0 : role(c) == Role.FORTRESS ? 0.75 : 0.0;
	}

	/** An army camps outside the colony for so many days (half that at a Fortress). Returns why not, or null. */
	static @Nullable String besiege(Colony c, String by, int days) {
		if (siege(c) != null) {
			return c.name + " is already under siege.";
		}
		Net n = of(c.owner);
		Siege s = new Siege();
		s.by = by;
		s.until = day + Math.max(1, role(c) == Role.FORTRESS ? (days + 1) / 2 : days);
		n.sieges.put(c.id, s);
		n.report("⚔ " + by + "'s army has laid siege to " + c.name + "! " + (role(c) == Role.FORTRESS
			? "Its walls hold: it works at 75% and its convoys get through."
			: "Nobody works the fields, no convoy gets in or out.") + " Win a battle, make peace, or wait them out (day " + s.until + ").");
		changed();
		return null;
	}

	/** Lifts the sieges of this rival on the owner's colonies (all rivals' with by = null). Returns how many. */
	static int lift(UUID owner, @Nullable String by) {
		Net n = ALL.get(owner.toString());
		if (n == null) {
			return 0;
		}
		int lifted = 0;
		for (Map.Entry<String, Siege> e : new ArrayList<>(n.sieges.entrySet())) {
			if (by == null || e.getValue().by.equals(by)) {
				n.sieges.remove(e.getKey());
				Colony c = colony(e.getKey());
				if (e.getValue().until >= day) {
					lifted++;
					n.report("🏳 The siege of " + (c == null ? "a colony" : c.name) + " is lifted: " + e.getValue().by + "'s army marches away.");
				}
			}
		}
		if (lifted > 0) {
			changed();
		}
		return lifted;
	}

	/** Raiders fall on a convoy under way: it's lost. Returns the convoy taken, or null when nothing's on the road. */
	static @Nullable Convoy raid(UUID owner, String by, java.util.Random random) {
		Net n = ALL.get(owner.toString());
		if (n == null || n.convoys.isEmpty()) {
			return null;
		}
		Convoy cv = n.convoys.remove(random.nextInt(n.convoys.size()));
		Colony from = colony(cv.from);
		Colony to = colony(cv.to);
		n.report("⚔ " + by + "'s riders fell on the convoy from " + (from == null ? "?" : from.name) + " to " + (to == null ? "?" : to.name)
			+ " and carried off " + cv.items + " items.");
		changed();
		return cv;
	}

	/** What a convoy's cargo would fetch at the Market. */
	static long value(Convoy cv) {
		long v = 0;
		for (ItemStack stack : cv.cargo) {
			v += Math.max(0, Bank.sellValue(stack));
		}
		return v;
	}

	static double autosell(Colony c) {
		return role(c) == Role.MARKET_TOWN ? 1.1 : 1.0;
	}

	static double street(Colony c) {
		return (role(c) == Role.MARKET_TOWN ? 1.2 : 1.0) * siegeFactor(c);
	}

	static double harbor(Colony c) {
		return role(c) == Role.PORT ? 2.0 : 1.0;
	}

	// ---------------------------------------------------------------- what's in a colony's storehouses

	/** One kind of goods and how many of it. */
	record Stock(ItemStack kind, long count) {
	}

	/** Everything in the colony's storehouses (and their warehouses), the biggest piles first. */
	static List<Stock> stock(Colony c) {
		List<ItemStack> kinds = new ArrayList<>();
		List<Long> counts = new ArrayList<>();
		for (Colony.Building s : Colonies.storesOf(c)) {
			String warehouse = Colonies.warehouseOf(s);
			if (warehouse != null) {
				for (Map.Entry<ItemStack, Long> e : WarehouseLink.stock(warehouse)) {
					add(kinds, counts, e.getKey(), e.getValue());
				}
			}
			for (int i = 0; i < s.storage.getContainerSize(); i++) {
				ItemStack stack = s.storage.getItem(i);
				if (!stack.isEmpty()) {
					add(kinds, counts, stack, stack.getCount());
				}
			}
		}
		List<Stock> out = new ArrayList<>();
		for (int i = 0; i < kinds.size(); i++) {
			out.add(new Stock(kinds.get(i), counts.get(i)));
		}
		out.sort((a, b) -> Long.compare(b.count(), a.count()));
		return out;
	}

	private static void add(List<ItemStack> kinds, List<Long> counts, ItemStack stack, long n) {
		for (int i = 0; i < kinds.size(); i++) {
			if (ItemStack.isSameItemSameComponents(kinds.get(i), stack)) {
				counts.set(i, counts.get(i) + n);
				return;
			}
		}
		kinds.add(stack.copyWithCount(1));
		counts.add(n);
	}

	static long count(Colony c, ItemStack kind) {
		long n = 0;
		for (Stock s : stock(c)) {
			if (ItemStack.isSameItemSameComponents(s.kind(), kind)) {
				n += s.count();
			}
		}
		return n;
	}

	/** Takes up to {@code amount} of this kind out of the colony's storehouses, as stacks. */
	static List<ItemStack> take(Colony c, ItemStack kind, long amount) {
		List<ItemStack> out = new ArrayList<>();
		long left = amount;
		for (Colony.Building s : Colonies.storesOf(c)) {
			String warehouse = Colonies.warehouseOf(s);
			if (warehouse != null && left > 0) {
				long got = WarehouseLink.take(warehouse, kind, left);
				left -= got;
				split(out, kind, got);
			}
			for (int i = 0; i < s.storage.getContainerSize() && left > 0; i++) {
				ItemStack stack = s.storage.getItem(i);
				if (ItemStack.isSameItemSameComponents(stack, kind)) {
					int n = (int) Math.min(left, stack.getCount());
					out.add(stack.copyWithCount(n));
					stack.shrink(n);
					left -= n;
					if (stack.isEmpty()) {
						s.storage.setItem(i, ItemStack.EMPTY);
					}
				}
			}
		}
		return out;
	}

	private static void split(List<ItemStack> out, ItemStack kind, long n) {
		int max = Math.max(1, kind.getMaxStackSize());
		while (n > 0) {
			int part = (int) Math.min(n, max);
			out.add(kind.copyWithCount(part));
			n -= part;
		}
	}

	// ---------------------------------------------------------------- convoys

	static double distance(Colony a, Colony b) {
		double dx = a.centre().getX() - b.centre().getX();
		double dz = a.centre().getZ() - b.centre().getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Paydays on the road: one, plus one for every 1,500 blocks. */
	static int days(Colony from, Colony to) {
		return 1 + (int) (distance(from, to) / 1500);
	}

	private static boolean has(Colony c, BuildingType type) {
		for (Colony.Building b : c.buildings) {
			if (b.type == type) {
				return true;
			}
		}
		return false;
	}

	/**
	 * What carrying this many items costs: ₥0.50 a stack per 100 blocks, at least ₥5. Half price when both colonies
	 * have a train station (or both a harbor): your own lines carry it. A Port at either end takes a quarter off.
	 */
	static long fee(Colony from, Colony to, long items) {
		double cents = items * distance(from, to) / 128.0;
		if ((has(from, BuildingType.TRAIN_STATION) && has(to, BuildingType.TRAIN_STATION))
			|| (has(from, BuildingType.HARBOR_OFFICE) && has(to, BuildingType.HARBOR_OFFICE))) {
			cents *= 0.5;
		}
		if (role(from) == Role.PORT || role(to) == Role.PORT) {
			cents *= 0.75;
		}
		return Math.max(Bank.cents(5), Math.round(cents));
	}

	/** Can goods go from one to the other? Returns why not, or null. */
	static @Nullable String route(Colony from, Colony to) {
		if (from == to) {
			return "That's the same colony.";
		}
		if (!linked(from) || !linked(to)) {
			return "Convoys only run between colonies in the network.";
		}
		if (!from.dimension.equals(to.dimension)) {
			return "No road runs between dimensions.";
		}
		for (Colony c : List.of(from, to)) {
			Siege s = siege(c);
			if (s != null && cutOff(c)) {
				return c.name + " is under siege by " + s.by + ": nothing gets in or out.";
			}
		}
		return null;
	}

	/**
	 * Loads up to {@code amount} of this kind of goods at one colony and sends it to another: paid for at once, there
	 * in {@link #days} paydays. Returns why not, or null when it's on the road.
	 */
	static @Nullable String send(Colony from, Colony to, ItemStack kind, long amount, String order) {
		String why = route(from, to);
		if (why != null) {
			return why;
		}
		long have = count(from, kind);
		long n = Math.min(have, amount);
		if (n <= 0) {
			return from.name + " has no " + kind.getHoverName().getString() + " to send.";
		}
		long fee = fee(from, to, n);
		if (!Bank.charge(from.owner, fee)) {
			return "The carriers want " + Bank.format(fee) + " for that, and you don't have it.";
		}
		Net net = of(from.owner);
		Convoy c = new Convoy();
		c.id = net.newId("c");
		c.from = from.id;
		c.to = to.id;
		c.cargo = take(from, kind, n);
		c.items = 0;
		for (ItemStack s : c.cargo) {
			c.items += s.getCount();
		}
		c.fee = fee;
		net.book(from.id)[2] += fee;
		c.sent = day;
		c.arrives = day + days(from, to);
		c.order = order;
		net.convoys.add(c);
		net.report("🐎 " + c.items + " × " + kind.getHoverName().getString() + " left " + from.name + " for " + to.name + " ("
			+ Bank.format(fee) + "), " + (c.arrives - day == 1 ? "there tomorrow." : "there in " + (c.arrives - day) + " days."));
		changed();
		return null;
	}

	static @Nullable String addOrder(Colony from, Colony to, ItemStack kind, int amount) {
		String why = route(from, to);
		if (why != null) {
			return why;
		}
		Net net = of(from.owner);
		if (net.orders.size() >= 27) {
			return "That's enough standing orders for one network.";
		}
		Order o = new Order();
		o.id = net.newId("o");
		o.from = from.id;
		o.to = to.id;
		o.kind = kind.copyWithCount(1);
		o.amount = Math.max(1, amount);
		net.orders.add(o);
		net.report("Standing order: every day up to " + o.amount + " × " + kind.getHoverName().getString() + " from " + from.name + " to " + to.name + ".");
		changed();
		return null;
	}

	// ---------------------------------------------------------------- payday

	/** Before the colonies' paydays: another day on the road, and a line drawn under everyone's balance. */
	static void newDay() {
		day++;
		for (Colony c : Colonies.all()) {
			of(c.owner);
		}
		for (Net n : ALL.values()) {
			try {
				long now = Bank.balance(UUID.fromString(n.owner));
				if (n.lastBalance != Long.MIN_VALUE) {
					n.balanceChange = now - n.lastBalance;
					n.balanceKnown = true;
				}
				n.lastBalance = now;
			} catch (IllegalArgumentException ignored) {
			}
		}
		changed();
	}

	/** The colony's payday begins: a fresh page in its books. */
	static void openBooks(Colony c) {
		of(c.owner).books.put(c.id, new long[4]);
		changed();
	}

	static void wagesPaid(Colony c, long wages) {
		of(c.owner).book(c.id)[1] += wages;
		changed();
	}

	static void earned(Colony c, long earned) {
		of(c.owner).book(c.id)[0] += earned;
		changed();
	}

	/** What the colony made since its last payday, less what it cost: earnings, wages, convoys it sent, rehiring. */
	static long net(Colony c) {
		Net n = ALL.get(c.owner.toString());
		long[] b = n == null ? null : n.books.get(c.id);
		return b == null ? 0 : b[0] - b[1] - b[2] - b[3];
	}

	/** Money in the colonies' bank vaults (anyone can take from a vault, mind). */
	static long vaults(UUID owner) {
		long sum = 0;
		for (Colony c : Colonies.ownedBy(owner)) {
			for (Colony.Building b : c.buildings) {
				if (b.type == BuildingType.BANK) {
					sum += b.vault;
				}
			}
		}
		return sum;
	}

	/** At the start of a colony's payday: convoys that have arrived unload into its storehouses (in time for today's work). */
	static void arrivals(Colony c) {
		Net net = ALL.get(c.owner.toString());
		if (net == null) {
			return;
		}
		List<Colony.Building> stores = Colonies.storesOf(c);
		if (cutOff(c)) {
			return; // the convoys wait outside the siege lines
		}
		for (Convoy cv : new ArrayList<>(net.convoys)) {
			if (!cv.to.equals(c.id) || cv.arrives > day) {
				continue;
			}
			net.convoys.remove(cv);
			long back = 0;
			long sold = 0;
			for (ItemStack stack : cv.cargo) {
				ItemStack rest = stores.isEmpty() ? stack : Colonies.stow(stores, stack.copy());
				if (rest.isEmpty()) {
					continue;
				}
				// no room: back to where it came from, or sold on the quay
				Colony from = colony(cv.from);
				ItemStack still = from == null ? rest : Colonies.stow(Colonies.storesOf(from), rest);
				back += rest.getCount() - still.getCount();
				if (!still.isEmpty()) {
					long value = Math.max(0, Bank.sellValue(still));
					sold += value;
				}
			}
			if (sold > 0) {
				Bank.credit(c.owner, c.ownerName, sold);
			}
			Colony from = colony(cv.from);
			net.report("📦 A convoy from " + (from == null ? "a colony that's gone" : from.name) + " reached " + c.name + ": " + cv.items + " items."
				+ (back > 0 ? " " + back + " didn't fit and went back." : "") + (sold > 0 ? " The rest was sold on the quay for " + Bank.format(sold) + "." : ""));
			changed();
		}
	}

	/** Just before a colony's storehouses auto-sell: its standing orders load up and leave. */
	static void dispatch(Colony c) {
		Net net = ALL.get(c.owner.toString());
		if (net == null || !linked(c)) {
			return;
		}
		for (Order o : new ArrayList<>(net.orders)) {
			if (!o.from.equals(c.id)) {
				continue;
			}
			Colony to = colony(o.to);
			if (to == null) {
				net.orders.remove(o);
				continue;
			}
			if (count(c, o.kind) <= 0) {
				continue;
			}
			String why = send(c, to, o.kind, o.amount, o.id);
			if (why != null) {
				net.report("Standing order " + c.name + " → " + to.name + " didn't go: " + why);
			}
		}
	}

	/**
	 * After a colony's payday: the mayor governs. With the colony in the network (and not told otherwise), a living
	 * mayor rehires every worker who died, at the usual price, if there's money for it. Far away and unloaded, the
	 * colony is woken up for a moment so the new hands can be shown in.
	 */
	static void govern(Colony c) {
		if (!linked(c)) {
			return;
		}
		Net net = of(c.owner);
		if (net.noGovernor.contains(c.id)) {
			return;
		}
		Colony.Building hall = c.townHall();
		if (hall == null || hall.alive() == 0) {
			int dead = 0;
			for (Colony.Building b : c.buildings) {
				dead += b.dead();
			}
			if (dead > 0) {
				net.report("⚠ " + c.name + " has no mayor to govern it: " + dead + " jobs stand empty. Replace the mayor at its Town Hall.");
			}
			return;
		}
		ServerLevel level = Colonies.level(c);
		if (level == null) {
			return;
		}
		List<Colony.Building> short_ = new ArrayList<>();
		for (Colony.Building b : c.buildings) {
			if (b != hall && b.dead() > 0 && Colonies.hiresVillagers(b.type)) {
				short_.add(b);
			}
		}
		if (short_.isEmpty()) {
			return;
		}
		// wake the colony's chunks up for a moment, then hire
		List<int[]> woken = new ArrayList<>();
		for (Colony.Building b : short_) {
			int cx = b.origin.getX() >> 4;
			int cz = b.origin.getZ() >> 4;
			boolean already = false;
			for (int[] w : woken) {
				already |= w[0] == cx && w[1] == cz;
			}
			if (!already && !level.isLoaded(b.origin)) {
				level.setChunkForced(cx, cz, true);
				woken.add(new int[] {cx, cz});
			}
		}
		ColonycraftMod.later(woken.isEmpty() ? 1 : 100, () -> {
			int hired = 0;
			long paid = 0;
			for (Colony.Building b : short_) {
				for (int i = 0; i < b.villagers.size(); i++) {
					if (b.villagers.get(i) != null) {
						continue;
					}
					long price = Colonies.replacePrice(c);
					if (!Bank.charge(c.owner, price)) {
						break;
					}
					java.util.UUID id = Colonies.spawnVillager(level, b, i);
					if (id == null) {
						Bank.credit(c.owner, c.ownerName, price);
						continue;
					}
					b.villagers.set(i, id);
					hired++;
					paid += price;
				}
			}
			for (int[] w : woken) {
				level.setChunkForced(w[0], w[1], false);
			}
			if (hired > 0) {
				Colonies.markDirty();
				Net n = of(c.owner);
				n.book(c.id)[3] += paid;
				n.report("🏛 The mayor of " + c.name + " hired " + hired + (hired == 1 ? " new worker" : " new workers") + " (" + Bank.format(paid) + ").");
				changed();
			}
		});
	}

	static boolean governs(Colony c) {
		Net n = ALL.get(c.owner.toString());
		return n == null || !n.noGovernor.contains(c.id);
	}

	static void setGoverns(Colony c, boolean on) {
		Net n = of(c.owner);
		n.noGovernor.remove(c.id);
		if (!on) {
			n.noGovernor.add(c.id);
		}
		changed();
	}

	/** A colony is gone: its links, roles and orders go too; convoys headed there turn round. */
	static void forget(Colony c) {
		Net n = ALL.get(c.owner.toString());
		if (n == null) {
			return;
		}
		n.linked.remove(c.id);
		n.books.remove(c.id);
		n.roles.remove(c.id);
		n.noGovernor.remove(c.id);
		n.orders.removeIf(o -> o.from.equals(c.id) || o.to.equals(c.id));
		for (Convoy cv : n.convoys) {
			if (cv.to.equals(c.id)) {
				String from = cv.from;
				cv.from = c.id;
				cv.to = from;
			}
		}
		changed();
	}

	/** Tells the owner, if they're online. */
	static void tell(UUID owner, String line) {
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (p != null) {
			p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GOLD));
		}
	}

	// ---------------------------------------------------------------- saving

	private static DynamicOps<JsonElement> ops(MinecraftServer srv) {
		return srv.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static void load(MinecraftServer srv) {
		server = srv;
		ALL.clear();
		day = 0;
		dirty = false;
		file = srv.getWorldPath(LevelResource.ROOT).resolve("colonycraft-network.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap == null) {
				return;
			}
			day = snap.day;
			DynamicOps<JsonElement> ops = ops(srv);
			if (snap.networks != null) {
				snap.networks.forEach((owner, n) -> {
					fix(n);
					for (Convoy c : n.convoys) {
						c.cargo = new ArrayList<>();
						if (c.cargoJson != null) {
							for (JsonElement json : c.cargoJson) {
								ItemStack.CODEC.parse(ops, json).result().ifPresent(c.cargo::add);
							}
						}
					}
					for (Order o : n.orders) {
						o.kind = o.kindJson == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, o.kindJson).result().orElse(ItemStack.EMPTY);
					}
					n.orders.removeIf(o -> o.kind.isEmpty());
					ALL.put(owner, n);
				});
			}
		} catch (Exception e) {
			ColonycraftMod.LOG.error("Could not read colonycraft-network.json! The network starts fresh; the old file is kept as .broken", e);
			try {
				Files.copy(file, file.resolveSibling("colonycraft-network.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	private static void fix(Net n) {
		if (n.linked == null) {
			n.linked = new ArrayList<>();
		}
		if (n.roles == null) {
			n.roles = new HashMap<>();
		}
		if (n.noGovernor == null) {
			n.noGovernor = new ArrayList<>();
		}
		if (n.convoys == null) {
			n.convoys = new ArrayList<>();
		}
		if (n.orders == null) {
			n.orders = new ArrayList<>();
		}
		if (n.report == null) {
			n.report = new ArrayList<>();
		}
		if (n.sieges == null) {
			n.sieges = new HashMap<>();
		}
		if (n.desks == null) {
			n.desks = new ArrayList<>();
		}
		if (n.hqDesk == null) {
			n.hqDesk = "";
		}
		if (n.books == null) {
			n.books = new HashMap<>();
		}
	}

	static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	static void save() {
		if (file == null || server == null) {
			return;
		}
		DynamicOps<JsonElement> ops = ops(server);
		Snapshot snap = new Snapshot();
		snap.day = day;
		for (Net n : ALL.values()) {
			for (Convoy c : n.convoys) {
				c.cargoJson = new ArrayList<>();
				for (ItemStack stack : c.cargo) {
					if (!stack.isEmpty()) {
						ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(c.cargoJson::add);
					}
				}
			}
			for (Order o : n.orders) {
				o.kindJson = o.kind.isEmpty() ? null : ItemStack.CODEC.encodeStart(ops, o.kind).result().orElse(null);
			}
			snap.networks.put(n.owner, n);
		}
		try {
			Path tmp = file.resolveSibling("colonycraft-network.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			ColonycraftMod.LOG.error("Could not save colonycraft-network.json", e);
		}
	}

	static void stop() {
		save();
		server = null;
		file = null;
	}

	/** Test hook: forget everything. */
	static void forgetAll() {
		ALL.clear();
		day = 0;
	}
}
