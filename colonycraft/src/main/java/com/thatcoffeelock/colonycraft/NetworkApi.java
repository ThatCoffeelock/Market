package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * The colony network for other mods (De Hooghe Heeren), through Fabric's ObjectShare: {@code "colonycraft:network"}, a
 * {@code BiFunction<String, Map<String, Object>, Object>} taking an operation and its arguments:
 * <ul>
 * <li>{@code colonies} {owner}: a list of maps (id, name, linked, role, besieged: the besieger or "", dimension, x, y, z
 * of its Town Hall).</li>
 * <li>{@code convoys} {owner}: how many convoys are on the road (Integer).</li>
 * <li>{@code raid_convoy} {owner, by}: raiders take a convoy under way; a map (items, value in cents, from, to) or null.</li>
 * <li>{@code siege} {owner, by, days}: an army besieges one of the owner's linked colonies (the capital last); its name,
 * or null if there's nothing to besiege.</li>
 * <li>{@code lift} {owner, by}: lifts that rival's sieges; how many (Integer).</li>
 * <li>{@code besieged} {owner, by}: the names of the colonies that rival has under siege (List of String).</li>
 * </ul>
 */
public final class NetworkApi {
	public static final String KEY = "colonycraft:network";
	private static final Random RANDOM = new Random();

	private NetworkApi() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().put(KEY, (BiFunction<String, Map<String, Object>, Object>) NetworkApi::call);
	}

	static @Nullable Object call(String op, Map<String, Object> args) {
		if (!(args.get("owner") instanceof UUID owner)) {
			return null;
		}
		String by = args.get("by") instanceof String s ? s : "";
		switch (op) {
			case "colonies": {
				List<Map<String, Object>> out = new ArrayList<>();
				for (Colony c : Network.colonies(owner)) {
					Network.Siege s = Network.siege(c);
					Map<String, Object> m = new HashMap<>();
					m.put("id", c.id);
					m.put("name", c.name);
					m.put("linked", Network.linked(c));
					m.put("role", Network.role(c).name());
					m.put("besieged", s == null ? "" : s.by);
					m.put("dimension", c.dimension);
					m.put("x", c.centre().getX());
					m.put("y", c.centre().getY());
					m.put("z", c.centre().getZ());
					out.add(m);
				}
				return out;
			}
			case "convoys": {
				Network.Net n = Network.ALL.get(owner.toString());
				return n == null ? 0 : n.convoys.size();
			}
			case "raid_convoy": {
				Network.Convoy cv = Network.raid(owner, by, RANDOM);
				if (cv == null) {
					return null;
				}
				Colony from = Network.colony(cv.from);
				Colony to = Network.colony(cv.to);
				Map<String, Object> m = new HashMap<>();
				m.put("items", cv.items);
				m.put("value", Network.value(cv));
				m.put("from", from == null ? "" : from.name);
				m.put("to", to == null ? "" : to.name);
				return m;
			}
			case "siege": {
				int days = args.get("days") instanceof Number d ? d.intValue() : 5;
				Colony target = null;
				Colony capital = Network.capital(owner);
				for (Colony c : Network.colonies(owner)) {
					if (Network.linked(c) && Network.siege(c) == null && c != capital) {
						target = c; // the furthest down the list: the newest links lie open
					}
				}
				if (target == null && capital != null && Network.siege(capital) == null) {
					target = capital;
				}
				if (target == null || Network.besiege(target, by, days) != null) {
					return null;
				}
				return target.name;
			}
			case "lift":
				return Network.lift(owner, by.isEmpty() ? null : by);
			case "besieged": {
				List<String> out = new ArrayList<>();
				for (Colony c : Colonies.ownedBy(owner)) {
					Network.Siege s = Network.siege(c);
					if (s != null && (by.isEmpty() || s.by.equals(by))) {
						out.add(c.name);
					}
				}
				return out;
			}
			default:
				return null;
		}
	}
}
