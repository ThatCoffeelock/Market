package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * For other mods, through Fabric's ObjectShare (no compile-time link either way):
 * <ul>
 * <li>{@code "compagnie:add_desk"}: (level, pos) makes the cartography table there a built-in Company Desk (Colonycraft's
 * Company House). Breaking a built-in desk gives nothing back.</li>
 * <li>{@code "compagnie:remove_desk"}: (level, pos) forgets it again.</li>
 * <li>{@code "compagnie:house"}: (owner, tier) the best Company House the owner has, 0 for none: +1 job slot, +1 agent
 * per tier, 10% off wages per tier.</li>
 * <li>{@code "compagnie:is_desk"}: (level, pos) is there a Company Desk?</li>
 * <li>{@code "compagnie:books"}: owner to a list of book lines for one treasury across mods. Each line is a map with
 * {@code label} (String), {@code cents} (Long; negative is a cost) and {@code kind} ("daily": every day, or "asset":
 * worth this much now).</li>
 * </ul>
 */
public final class CompagnieApi {
	public static final String ADD_DESK = "compagnie:add_desk";
	public static final String REMOVE_DESK = "compagnie:remove_desk";
	public static final String HOUSE = "compagnie:house";
	public static final String IS_DESK = "compagnie:is_desk";
	public static final String BOOKS = "compagnie:books";

	private CompagnieApi() {
	}

	static void publish() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put(ADD_DESK, (BiConsumer<ServerLevel, BlockPos>) (level, pos) -> Desks.add(level, pos.immutable(), true));
		share.put(REMOVE_DESK, (BiConsumer<ServerLevel, BlockPos>) Desks::remove);
		share.put(HOUSE, (BiConsumer<UUID, Integer>) (owner, tier) -> {
			Company c = Companies.get(owner);
			int t = tier == null ? 0 : Math.max(0, Math.min(3, tier));
			if (c == null && t > 0) {
				c = Companies.of(owner, "");
			}
			if (c != null && c.house != t) {
				c.house = t;
				Companies.changed();
			}
		});
		share.put(IS_DESK, (BiFunction<ServerLevel, BlockPos, Boolean>) (level, pos) -> Desks.at(level, pos) != null);
		share.put(BOOKS, (Function<UUID, List<Map<String, Object>>>) CompagnieApi::books);
	}

	/** The company's lines in the one treasury: wages every day; stakes in flight, money lent and the strongbox now. */
	static List<Map<String, Object>> books(UUID owner) {
		List<Map<String, Object>> out = new ArrayList<>();
		Company c = Companies.get(owner);
		if (c == null) {
			return out;
		}
		if (!c.agents.isEmpty()) {
			out.add(line("Compagnie agents' wages", -c.dailyWages(), "daily"));
		}
		long staked = 0;
		for (Job j : c.active) {
			staked += j.paid;
		}
		if (staked > 0) {
			out.add(line("Compagnie stakes on " + c.active.size() + " errands", staked, "asset"));
		}
		long lent = 0;
		for (Loan l : c.loans) {
			if (l.state() == Loan.State.ACTIVE && !l.gold) {
				lent += l.owed();
			}
		}
		if (lent > 0) {
			out.add(line("Compagnie loans owed to you", lent, "asset"));
		}
		long box = 0;
		for (net.minecraft.world.item.ItemStack stack : c.strongbox) {
			box += Math.max(0, Bank.sellValue(stack));
		}
		if (box > 0) {
			out.add(line("The Compagnie strongbox (at Market prices)", box, "asset"));
		}
		return out;
	}

	private static Map<String, Object> line(String label, long cents, String kind) {
		return Map.of("label", label, "cents", cents, "kind", kind);
	}
}
