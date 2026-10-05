package com.thatcoffeelock.skills;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The general way other mods use skills, through Fabric's ObjectShare, so nobody needs anybody else to compile:
 * <ul>
 * <li>{@code "skills:bonus"}: (player, "skill/passive" or "skill/perk_id") -> the bonus, e.g. ("artillery/passive")
 * or ("artillery/powder_monkey"). 0 for unknown keys and players without a profile.</li>
 * <li>{@code "skills:xp"}: (player, skill id -> amount) gives XP, also to offline players (their level-up message
 * waits until they're back).</li>
 * </ul>
 * Mods ask by UUID because their machines and colonies keep working while the owner is offline.
 */
public final class SkillsApi {
	public static final String BONUS = "skills:bonus";
	public static final String XP = "skills:xp";

	private static @Nullable MinecraftServer server;

	private SkillsApi() {
	}

	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(BONUS, (BiFunction<UUID, String, Double>) SkillsApi::bonus);
		share.put(XP, (BiConsumer<UUID, Map.Entry<String, Double>>) (player, entry) -> {
			if (entry != null) {
				Skill skill = Skill.byId(entry.getKey());
				if (skill != null && entry.getValue() != null) {
					xp(player, skill, entry.getValue());
				}
			}
		});
	}

	static void start(MinecraftServer srv) {
		server = srv;
	}

	static void stop() {
		server = null;
	}

	/** "artillery/passive" -> the Artillery passive; "artillery/powder_monkey" -> that perk's value. */
	static Double bonus(UUID player, String key) {
		if (player == null || key == null) {
			return 0.0;
		}
		SkillsStore.Profile profile = SkillsStore.of(player);
		int slash = key.indexOf('/');
		if (profile == null || slash < 0) {
			return 0.0;
		}
		Skill skill = Skill.byId(key.substring(0, slash));
		String what = key.substring(slash + 1).toLowerCase(Locale.ROOT);
		if (skill == null) {
			return 0.0;
		}
		if (what.equals("passive")) {
			return skill.passive(profile.level(skill));
		}
		Perk perk = Perk.byId(what);
		return perk == null || perk.skill != skill ? 0.0 : perk.value(profile.rank(perk));
	}

	/** Online: the usual award with popups and level-ups. Offline: banked quietly. */
	static void xp(UUID player, Skill skill, double amount) {
		if (player == null || amount <= 0) {
			return;
		}
		ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(player);
		if (online != null) {
			Skills.award(online, skill, amount);
			return;
		}
		SkillsStore.Profile profile = SkillsStore.of(player);
		if (profile == null) {
			return;
		}
		double max = Skill.totalFor(Skill.MAX_LEVEL);
		double before = profile.xp(skill);
		double gained = amount * SkillsConfig.get().multiplier(skill);
		if (gained <= 0 || before >= max) {
			return;
		}
		profile.xp.put(skill.id(), Math.min(max, before + gained));
		SkillsStore.changed();
	}
}
