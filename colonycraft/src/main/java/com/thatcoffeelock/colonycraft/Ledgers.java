package com.thatcoffeelock.colonycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;

/**
 * One treasury for everything: the other mods' books, read through Fabric's ObjectShare (no compile-time link). Each
 * mod publishes {@code "<mod>:books"}, owner to a list of lines: {@code label}, {@code cents} (negative is a cost) and
 * {@code kind} ("daily" or "asset"). A mod that isn't there simply has no lines.
 */
final class Ledgers {
	static final List<String> SOURCES = List.of("compagnie:books", "hoogheheeren:books");
	private static boolean warned;

	private Ledgers() {
	}

	record Line(String label, long cents, boolean daily) {
	}

	@SuppressWarnings("unchecked")
	static List<Line> lines(UUID owner) {
		List<Line> out = new ArrayList<>();
		for (String key : SOURCES) {
			Object hook = FabricLoader.getInstance().getObjectShare().get(key);
			if (!(hook instanceof Function<?, ?> function)) {
				continue;
			}
			try {
				Object got = ((Function<UUID, Object>) function).apply(owner);
				if (!(got instanceof List<?> list)) {
					continue;
				}
				for (Object o : list) {
					if (o instanceof Map<?, ?> m && m.get("label") instanceof String label && m.get("cents") instanceof Number cents) {
						out.add(new Line(label, cents.longValue(), !"asset".equals(m.get("kind"))));
					}
				}
			} catch (RuntimeException e) {
				if (!warned) {
					warned = true;
					ColonycraftMod.LOG.warn("Couldn't read the books at {}", key, e);
				}
			}
		}
		return out;
	}

	/** Every day, from the other mods: income minus costs. */
	static long daily(List<Line> lines) {
		long n = 0;
		for (Line l : lines) {
			if (l.daily()) {
				n += l.cents();
			}
		}
		return n;
	}

	/** What the other mods hold for you now. */
	static long assets(List<Line> lines) {
		long n = 0;
		for (Line l : lines) {
			if (!l.daily()) {
				n += l.cents();
			}
		}
		return n;
	}
}
