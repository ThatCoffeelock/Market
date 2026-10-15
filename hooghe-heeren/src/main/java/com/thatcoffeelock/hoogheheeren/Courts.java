package com.thatcoffeelock.hoogheheeren;

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
import java.util.Random;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Every player's court, saved to <world>/hoogheheeren.json. Courts run on De Compagnie's clock: at every dawn (a
 * Minecraft day of game time) the rivals collect their income, pay their regiments, carry out what they planned,
 * fight their wars and plan the next day.
 */
final class Courts {
	/** What a regiment costs to raise, and to keep for a day. The same for you and for them. */
	static final long REGIMENT = Bank.cents(500);
	static final long UPKEEP = Bank.cents(25);
	static final Random RANDOM = new Random();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	static final Map<String, Court> ALL = new LinkedHashMap<>();
	private static @Nullable MinecraftServer server;
	private static @Nullable Path file;
	private static boolean dirty;
	/** While a dawn runs, news is gathered from the chronicle afterwards (and told by De Compagnie with the rest). */
	private static boolean dawning;

	private static final class Snapshot {
		Map<String, Court> courts = new LinkedHashMap<>();
	}

	private Courts() {
	}

	static void changed() {
		dirty = true;
	}

	static long today() {
		return CompagnieHooks.today();
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	static @Nullable Court get(UUID owner) {
		return ALL.get(owner.toString());
	}

	/** The owner's court, with a full table of rivals (and a company to go with it) if they had none. */
	static Court of(UUID owner, String name) {
		CompagnieHooks.found(owner, name);
		Court c = ALL.get(owner.toString());
		if (c == null) {
			c = new Court();
			c.owner = owner.toString();
			c.ownerName = name;
			ALL.put(c.owner, c);
			fill(c, today(), RANDOM);
			c.chronicle("You take your seat among the Hooghe Heeren. They are already whispering about you.");
			changed();
		} else if (!name.isEmpty()) {
			c.ownerName = name;
		}
		return c;
	}

	/** How many houses sit at your table: three for a Charter, four for a Compagnie, five for the VOC. */
	static int seats(Court c) {
		int rank = Math.max(0, CompagnieHooks.rank(c.uuid()));
		return rank >= 3 ? 5 : rank >= 2 ? 4 : 3;
	}

	/** Fills empty seats at the table with newcomers. */
	static void fill(Court c, long today, Random random) {
		while (c.rivals.size() < seats(c)) {
			Rival r = newcomer(c, today, random);
			c.rivals.add(r);
			if (c.rivals.size() > 1 || today > 0) {
				c.chronicle("A new house joins the table: " + r.name() + " of " + r.seatName + ".");
			}
		}
	}

	/** A brand new house: a name, a temperament, a purse and an army to match your company's size, and a seat. */
	static Rival newcomer(Court c, long today, Random random) {
		Rival r = new Rival();
		r.id = c.newId("r");
		r.founded = today;
		Names.lord(r, random, false);
		// a colour nobody else at the table wears
		List<Names.Colour> free = new ArrayList<>(List.of(Names.COLOURS));
		for (Rival other : c.rivals) {
			free.removeIf(col -> col.dye().equals(other.colour));
		}
		r.colour = (free.isEmpty() ? Names.COLOURS[random.nextInt(Names.COLOURS.length)] : free.get(random.nextInt(free.size()))).dye();
		r.temperament = Temperament.values()[random.nextInt(Temperament.values().length)].name();
		r.quirk = Quirk.values()[random.nextInt(Quirk.values().length)].name();
		int rank = Math.max(0, CompagnieHooks.rank(c.uuid()));
		double size = 1 + rank * 0.8;
		r.treasury = Bank.cents(Math.round((3000 + random.nextInt(9000)) * size));
		r.income = Bank.cents(Math.round((120 + random.nextInt(300)) * size));
		r.regiments = Math.max(1, (int) Math.round((2 + random.nextInt(5)) * (r.is(Temperament.WARLORD) ? 1.5 : 1.0) * (1 + rank * 0.5)));
		for (Rival other : c.rivals) {
			int rel = random.nextInt(41) - 20;
			r.relation(other.id, rel);
			other.relation(r.id, rel + random.nextInt(11) - 5);
		}
		// the seat: somewhere out there, 700 to 1400 blocks from the desk
		int[] home = CompagnieHooks.home(c.uuid());
		int hx = home == null ? 0 : home[0];
		int hz = home == null ? 0 : home[1];
		double angle = random.nextDouble() * Math.PI * 2;
		double dist = 700 + random.nextInt(700);
		r.seatX = hx + (int) Math.round(Math.cos(angle) * dist);
		r.seatZ = hz + (int) Math.round(Math.sin(angle) * dist);
		Ai.plan(c, r, today, random);
		return r;
	}

	// ---------------------------------------------------------------- the clock

	/** Called by De Compagnie at every dawn, for each company. */
	static void dawn(UUID owner, long day, Random random) {
		Court c = get(owner);
		if (c == null) {
			return;
		}
		dawn(c, day, random);
	}

	static void dawn(Court c, long day, Random random) {
		dawning = true;
		try {
			dawnInner(c, day, random);
		} finally {
			dawning = false;
		}
	}

	private static void dawnInner(Court c, long day, Random random) {
		List<String> before = new ArrayList<>(c.chronicle);
		// your regiments: paid, or they walk
		payRegiments(c);
		// the rivals' day: income and upkeep, then what they meant to do, then their wars
		for (Rival r : new ArrayList<>(c.rivals)) {
			Ai.economy(c, r, day, random);
		}
		for (Rival r : new ArrayList<>(c.rivals)) {
			if (c.rivals.contains(r)) {
				Ai.act(c, r, day, random);
			}
		}
		Wars.dawn(c, day, random);
		Covert.dawn(c, day, random);
		// dispatches nobody answered get their default answer
		for (Court.Dispatch d : new ArrayList<>(c.dispatches)) {
			if (d.expires <= day) {
				Dispatches.answer(c, d, Dispatches.options(c, d).size() - 1, day, true);
			}
		}
		// guests go home
		Hall.dawn(c, day);
		// houses rise and fall
		Ai.succession(c, day, random);
		fill(c, day, random);
		for (Rival r : c.rivals) {
			Ai.plan(c, r, day, random);
		}
		// fear fades if you don't remind them
		if (c.fear > 10 && random.nextInt(2) == 0) {
			c.fear(-1);
		}
		// what happened goes into the company's news (told at dawn with the rest)
		for (String line : c.chronicle) {
			if (!before.contains(line)) {
				CompagnieHooks.news(c.uuid(), "⚜ " + line);
			}
		}
		changed();
	}

	/** Regiments cost {@link #UPKEEP} a day each. Can't pay? One walks out for every day's pay you're short. */
	static void payRegiments(Court c) {
		if (c.regiments <= 0) {
			return;
		}
		long due = c.regiments * UPKEEP;
		if (Bank.charge(c.uuid(), due)) {
			return;
		}
		long have = Math.max(0, Bank.balance(c.uuid()));
		int paid = (int) (have / UPKEEP);
		Bank.charge(c.uuid(), paid * UPKEEP);
		int gone = c.regiments - paid;
		c.regiments = paid;
		c.chronicle("No pay for the regiments (" + Bank.format(due) + " due): " + gone + (gone == 1 ? " regiment" : " regiments") + " deserted.");
	}

	/** Tells the owner something now, if they're online, and writes it in the chronicle (and the company's news). */
	static void tell(Court c, String line, ChatFormatting color) {
		c.chronicle(line);
		if (dawning) {
			changed();
			return;
		}
		CompagnieHooks.news(c.uuid(), "⚜ " + line);
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(c.uuid());
		if (player != null) {
			player.sendSystemMessage(Component.literal("[Heeren] ").withStyle(ChatFormatting.DARK_PURPLE).append(Component.literal(line).withStyle(color)));
		}
		changed();
	}

	static @Nullable ServerPlayer player(Court c) {
		return server == null ? null : server.getPlayerList().getPlayer(c.uuid());
	}

	// ---------------------------------------------------------------- saving

	static void load(MinecraftServer srv) {
		server = srv;
		ALL.clear();
		dirty = false;
		file = srv.getWorldPath(LevelResource.ROOT).resolve("hoogheheeren.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap != null && snap.courts != null) {
				snap.courts.forEach((owner, c) -> {
					fix(c);
					ALL.put(owner, c);
				});
			}
		} catch (Exception e) {
			HeerenMod.LOG.error("Could not read hoogheheeren.json! Courts start fresh; the old file is kept as hoogheheeren.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("hoogheheeren.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	/** Lists and maps Gson left out of an old or hand-edited file. */
	private static void fix(Court c) {
		if (c.rivals == null) {
			c.rivals = new ArrayList<>();
		}
		if (c.dispatches == null) {
			c.dispatches = new ArrayList<>();
		}
		if (c.ops == null) {
			c.ops = new ArrayList<>();
		}
		if (c.chronicle == null) {
			c.chronicle = new ArrayList<>();
		}
		if (c.guest == null) {
			c.guest = "";
		}
		for (Rival r : c.rivals) {
			if (r.memories == null) {
				r.memories = new ArrayList<>();
			}
			if (r.relations == null) {
				r.relations = new HashMap<>();
			}
			if (r.wars == null) {
				r.wars = new ArrayList<>();
			}
			if (r.partners == null) {
				r.partners = new ArrayList<>();
			}
			if (r.intent == null) {
				r.intent = "";
			}
			if (r.intentTarget == null) {
				r.intentTarget = "";
			}
			if (r.dressed == null) {
				r.dressed = "";
			}
			if (r.nation == null) {
				r.nation = "";
			}
		}
	}

	static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	static void save() {
		if (file == null) {
			return;
		}
		Snapshot snap = new Snapshot();
		snap.courts.putAll(ALL);
		try {
			Path tmp = file.resolveSibling("hoogheheeren.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			HeerenMod.LOG.error("Could not save hoogheheeren.json", e);
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
	}
}
