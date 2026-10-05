package com.thatcoffeelock.skills;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Wildcatting, the oil skill. The XP and the bonuses all happen inside Fossil Fool (drilling, pumping, refining,
 * dowsing). The mods meet through Fabric's ObjectShare, like Mercantile and the Market: Skills publishes two functions,
 * Fossil Fool calls them if they exist. Drill Rigs and Refineries keep working while their owner is offline, so both
 * take the owner's UUID; XP earned offline is banked without the level-up fanfare.
 */
final class Wildcatting {
	/** (owner, "fuel" | "speed" | "refine" | "dowse") -> bonus. Fractions, except "dowse", which is in blocks. */
	static final String BONUS = "skills:wildcatting_bonus";
	/** (owner, xp) -> awards Wildcatting XP. */
	static final String XP = "skills:wildcatting_xp";

	private static @Nullable MinecraftServer server;

	private Wildcatting() {
	}

	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(BONUS, (BiFunction<UUID, String, Double>) Wildcatting::bonus);
		share.put(XP, (BiConsumer<UUID, Double>) Wildcatting::xp);
	}

	static void start(MinecraftServer srv) {
		server = srv;
	}

	static void stop() {
		server = null;
	}

	static Double bonus(UUID player, String what) {
		SkillsStore.Profile profile = SkillsStore.of(player);
		if (profile == null || what == null) {
			return 0.0;
		}
		return switch (what) {
			case "fuel" -> Skill.WILDCATTING.passive(profile.level(Skill.WILDCATTING));
			case "speed" -> Perk.ROUGHNECK.value(profile.rank(Perk.ROUGHNECK));
			case "refine" -> Perk.REFINER.value(profile.rank(Perk.REFINER));
			case "dowse" -> Perk.DOWSER.value(profile.rank(Perk.DOWSER));
			default -> 0.0;
		};
	}

	static void xp(UUID player, Double amount) {
		if (player == null || amount == null || amount <= 0) {
			return;
		}
		ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(player);
		if (online != null) {
			Skills.award(online, Skill.WILDCATTING, amount);
			return;
		}
		SkillsStore.Profile profile = SkillsStore.of(player);
		if (profile == null) {
			return;
		}
		double max = Skill.totalFor(Skill.MAX_LEVEL);
		double before = profile.xp(Skill.WILDCATTING);
		double gained = amount * SkillsConfig.get().multiplier(Skill.WILDCATTING);
		if (gained <= 0 || before >= max) {
			return;
		}
		profile.xp.put(Skill.WILDCATTING.id(), Math.min(max, before + gained));
		SkillsStore.changed();
	}
}
