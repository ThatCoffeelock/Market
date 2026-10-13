package com.thatcoffeelock.compagnie;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Every company, and the clock they run on. A company day is a Minecraft day (24000 ticks of game time, so it
 * keeps going while the owner's away, and /time set can't skip it). At every dawn each company pays its agents,
 * brings home finished jobs, gets telegrams from the road, settles the Ledger and gets new offers.
 * Saved to <world>/compagnie.json.
 */
final class Companies {
	static final long DAY = 24000;
	/** No more than this many days are caught up at once (after a long time with the server off, say). */
	static final int CATCH_UP = 7;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Random RANDOM = new Random();

	static final Map<String, Company> ALL = new LinkedHashMap<>();
	static long lastDay = -1;
	private static @Nullable MinecraftServer server;
	private static @Nullable Path file;
	private static boolean dirty;

	private static final class Snapshot {
		long lastDay = -1;
		Map<String, Company> companies = new LinkedHashMap<>();
		List<Desks.Desk> desks = new ArrayList<>();
	}

	private Companies() {
	}

	static void changed() {
		dirty = true;
	}

	static long today() {
		return server == null ? 0 : server.overworld().getGameTime() / DAY;
	}

	static @Nullable Company get(UUID owner) {
		return ALL.get(owner.toString());
	}

	/** The owner's company, founded on the spot (with a full board) if they didn't have one. */
	static Company of(UUID owner, String name) {
		Company c = ALL.get(owner.toString());
		if (c == null) {
			c = new Company();
			c.owner = owner.toString();
			c.ownerName = name;
			ALL.put(c.owner, c);
			long today = today();
			fillBoard(c, today, RANDOM);
			Hiring.refresh(c, today, RANDOM);
			c.loans.add(Ledger.roll(c, today, RANDOM));
			c.news("The Compagnie is founded. Hire an agent, pick a job, and may God and the shareholders forgive you.");
			changed();
		} else if (!name.isEmpty()) {
			c.ownerName = name;
		}
		return c;
	}

	// ---------------------------------------------------------------- the clock

	/** Every few seconds: has a new day dawned? Then run it (catching up a few days at most). */
	static void tick(MinecraftServer srv) {
		server = srv;
		long day = today();
		if (lastDay < 0 || day < lastDay) {
			lastDay = day;
			changed();
			return;
		}
		if (day == lastDay) {
			return;
		}
		for (long d = Math.max(lastDay + 1, day - CATCH_UP + 1); d <= day; d++) {
			dawn(d);
		}
		lastDay = day;
		changed();
	}

	static void dawn(long day) {
		for (Company c : new ArrayList<>(ALL.values())) {
			try {
				dawn(c, day, RANDOM);
			} catch (RuntimeException e) {
				CompagnieMod.LOG.error("Dawn failed for {}'s company", c.ownerName, e);
			}
		}
	}

	static void dawn(Company c, long day, Random random) {
		int before = c.news.size();
		Rank rank = c.rank();
		Hiring.payday(c, day);
		for (Job job : new ArrayList<>(c.active)) {
			if (job.end <= day) {
				Jobs.resolve(c, job, day, random, server == null ? null : server.overworld());
			} else if (Jobs.maybeTelegram(c, job, day, random)) {
				Telegram t = Telegram.byName(job.telegram);
				JobType type = job.type();
				c.news("📨 Telegram about the " + (type == null ? "job" : type.title) + ": " + (t == null ? "" : t.text(job))
					+ " Answer it at your Company Desk (or with /compagnie telegrams).");
			}
		}
		Ledger.dawn(c, day, random);
		fillBoard(c, day, random);
		Hiring.refresh(c, day, random);
		c.buffs.entrySet().removeIf(e -> e.getValue() < day);
		if (c.rank() != rank) {
			c.news("★ The company is now a " + c.rank().title + "! More job slots, more agents, bigger jobs.");
		}
		c.unread += Math.max(0, Math.min(c.news.size(), c.news.size() - before));
		tell(c, before);
		changed();
	}

	/** Tells an owner who's online what happened at dawn. */
	private static void tell(Company c, int before) {
		ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(c.uuid());
		if (owner == null) {
			return;
		}
		for (int i = Math.max(0, Math.min(before, c.news.size())); i < c.news.size(); i++) {
			owner.sendSystemMessage(Component.literal("[Compagnie] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(c.news.get(i)).withStyle(ChatFormatting.YELLOW)));
		}
		c.unread = 0;
	}

	/** Withdraws stale offers and puts up new ones the company's rank allows, until the board's full. */
	static void fillBoard(Company c, long day, Random random) {
		c.board.removeIf(j -> j.expires < day);
		List<JobType> pool = new ArrayList<>();
		for (JobType t : JobType.values()) {
			if (!t.onBoard() || !t.available() || t.minRank > c.rank().ordinal()) {
				continue;
			}
			boolean listed = false;
			for (Job j : c.board) {
				if (j.type.equals(t.id())) {
					listed = true;
				}
			}
			if (!listed) {
				pool.add(t);
			}
		}
		while (c.board.size() < c.rank().board && !pool.isEmpty()) {
			JobType t = pool.remove(random.nextInt(pool.size()));
			Job offer = new Job();
			offer.id = c.newId("j");
			offer.type = t.id();
			offer.expires = day + 2 + random.nextInt(3);
			c.board.add(offer);
		}
		changed();
	}

	// ---------------------------------------------------------------- saving

	private static DynamicOps<JsonElement> ops(MinecraftServer srv) {
		return srv.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static void load(MinecraftServer srv) {
		server = srv;
		ALL.clear();
		Desks.ALL.clear();
		lastDay = -1;
		dirty = false;
		file = srv.getWorldPath(LevelResource.ROOT).resolve("compagnie.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Snapshot snap = GSON.fromJson(reader, Snapshot.class);
			if (snap == null) {
				return;
			}
			lastDay = snap.lastDay;
			DynamicOps<JsonElement> ops = ops(srv);
			if (snap.companies != null) {
				snap.companies.forEach((owner, c) -> {
					fix(c);
					for (JsonElement json : c.strongboxJson) {
						ItemStack.CODEC.parse(ops, json).result().ifPresent(c.strongbox::add);
					}
					ALL.put(owner, c);
				});
			}
			if (snap.desks != null) {
				for (Desks.Desk d : snap.desks) {
					Desks.ALL.put(d.key(), d);
				}
			}
		} catch (Exception e) {
			CompagnieMod.LOG.error("Could not read compagnie.json! Companies start fresh; the old file is kept as compagnie.json.broken", e);
			try {
				Files.copy(file, file.resolveSibling("compagnie.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
	}

	/** Lists Gson left out of an old or hand-edited file. */
	private static void fix(Company c) {
		if (c.agents == null) {
			c.agents = new ArrayList<>();
		}
		if (c.candidates == null) {
			c.candidates = new ArrayList<>();
		}
		if (c.board == null) {
			c.board = new ArrayList<>();
		}
		if (c.active == null) {
			c.active = new ArrayList<>();
		}
		if (c.loans == null) {
			c.loans = new ArrayList<>();
		}
		if (c.news == null) {
			c.news = new ArrayList<>();
		}
		if (c.buffs == null) {
			c.buffs = new java.util.HashMap<>();
		}
		if (c.strongboxJson == null) {
			c.strongboxJson = new ArrayList<>();
		}
		c.strongbox = new ArrayList<>();
		for (Agent a : c.agents) {
			if (a.traits == null) {
				a.traits = new ArrayList<>();
			}
		}
		for (Agent a : c.candidates) {
			if (a.traits == null) {
				a.traits = new ArrayList<>();
			}
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
		snap.lastDay = lastDay;
		for (Company c : ALL.values()) {
			c.strongboxJson = new ArrayList<>();
			for (ItemStack stack : c.strongbox) {
				if (!stack.isEmpty()) {
					ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(c.strongboxJson::add);
				}
			}
			snap.companies.put(c.owner, c);
		}
		snap.desks.addAll(Desks.ALL.values());
		try {
			Path tmp = file.resolveSibling("compagnie.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(snap, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (Exception e) {
			CompagnieMod.LOG.error("Could not save compagnie.json", e);
		}
	}

	static void stop() {
		save();
		server = null;
	}

	/** Test hook: forget everything. */
	static void forgetAll() {
		ALL.clear();
	}
}
