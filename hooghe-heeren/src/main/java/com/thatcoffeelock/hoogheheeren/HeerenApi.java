package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;

/**
 * For other mods, through Fabric's ObjectShare (no compile-time link either way):
 * <ul>
 * <li>{@code "hoogheheeren:books"}: owner to a list of book lines for one treasury across mods: a map with {@code label}
 * (String), {@code cents} (Long; negative is a cost) and {@code kind} ("daily" or "asset").</li>
 * </ul>
 */
public final class HeerenApi {
	public static final String BOOKS = "hoogheheeren:books";

	private HeerenApi() {
	}

	static void publish() {
		FabricLoader.getInstance().getObjectShare().put(BOOKS, (Function<UUID, List<Map<String, Object>>>) HeerenApi::books);
	}

	/** The court's lines: regiments' upkeep, trade pacts and tribute, every day. */
	static List<Map<String, Object>> books(UUID owner) {
		List<Map<String, Object>> out = new ArrayList<>();
		Court c = Courts.get(owner);
		if (c == null) {
			return out;
		}
		if (c.regiments > 0) {
			out.add(line(c.regiments + (c.regiments == 1 ? " regiment's" : " regiments'") + " upkeep", -c.regiments * Courts.UPKEEP));
		}
		long day = Courts.today();
		int pacts = 0;
		long tributeIn = 0;
		long tributeOut = 0;
		for (Rival r : c.rivals) {
			if (r.tradePact) {
				pacts++;
			}
			if (r.tribute > 0 && r.tributeUntil >= day) {
				if (r.tributeToUs) {
					tributeIn += r.tribute;
				} else {
					tributeOut += r.tribute;
				}
			}
		}
		if (pacts > 0) {
			out.add(line(pacts + (pacts == 1 ? " trade pact" : " trade pacts") + " with the Heeren", pacts * Ai.pactIncome(c)));
		}
		if (tributeIn > 0) {
			out.add(line("Tribute the Heeren pay you", tributeIn));
		}
		if (tributeOut > 0) {
			out.add(line("Tribute you pay the Heeren", -tributeOut));
		}
		return out;
	}

	private static Map<String, Object> line(String label, long cents) {
		return Map.of("label", label, "cents", cents, "kind", "daily");
	}
}
