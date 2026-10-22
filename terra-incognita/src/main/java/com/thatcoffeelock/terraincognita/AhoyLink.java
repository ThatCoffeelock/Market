package com.thatcoffeelock.terraincognita;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Optional link to Ahoy, through Fabric's ObjectShare: neither mod needs the other. An Ahoy ship makes the crossing
 * in its bottle, cargo and all, and comes out on open water on the other side.
 */
final class AhoyLink {
	/** Rigging II, Copper sheathing: the hull needs it for those waters. */
	static final String RIGGING_II = "Copper sheathing (rigging II)";

	private AhoyLink() {
	}

	/** The Ahoy ship this player is aboard: name, rigging, mayUse, crew. Null when not aboard one (or no Ahoy). */
	@SuppressWarnings("unchecked")
	static @Nullable Map<String, Object> aboard(ServerPlayer player) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("ahoy:ship_aboard");
		if (!(hook instanceof Function<?, ?> f)) {
			return null;
		}
		try {
			return ((Function<ServerPlayer, Map<String, Object>>) f).apply(player);
		} catch (RuntimeException e) {
			TerraMod.LOG.warn("Ahoy link failed", e);
			return null;
		}
	}

	static String name(Map<String, Object> ship) {
		return String.valueOf(ship.get("name"));
	}

	static int rigging(Map<String, Object> ship) {
		return ship.get("rigging") instanceof Integer i ? i : 0;
	}

	static boolean mayUse(Map<String, Object> ship) {
		return Boolean.TRUE.equals(ship.get("mayUse"));
	}

	@SuppressWarnings("unchecked")
	static List<ServerPlayer> crew(Map<String, Object> ship) {
		return ship.get("crew") instanceof List<?> list ? (List<ServerPlayer>) list : List.of();
	}

	/** Why this ship can't make the crossing, or null if it can. */
	static @Nullable String refusal(Map<String, Object> ship) {
		if (!mayUse(ship)) {
			return "The " + name(ship) + " isn't yours to take across. Its captain has to set the course.";
		}
		int need = TerraConfig.get().shipRigging;
		if (rigging(ship) < need) {
			return "The " + name(ship) + " won't survive those waters. The shipwright needs to fit "
				+ (need == 2 ? RIGGING_II : "rigging level " + need) + " first.";
		}
		return null;
	}

	/** Bottles up the ship this player is aboard (everyone steps off). EMPTY if there's none. */
	@SuppressWarnings("unchecked")
	static ItemStack pack(ServerPlayer player) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("ahoy:pack_ship");
		if (!(hook instanceof Function<?, ?> f)) {
			return ItemStack.EMPTY;
		}
		ItemStack bottle = ((Function<ServerPlayer, ItemStack>) f).apply(player);
		return bottle == null ? ItemStack.EMPTY : bottle;
	}

	/** Launches a bottled ship on open water near the first of the crew and seats them all. False if there's no room. */
	@SuppressWarnings("unchecked")
	static boolean launch(ItemStack bottle, List<ServerPlayer> crew) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("ahoy:launch_ship");
		if (!(hook instanceof BiFunction<?, ?, ?> f)) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(((BiFunction<ItemStack, List<ServerPlayer>, Boolean>) f).apply(bottle, crew));
		} catch (RuntimeException e) {
			TerraMod.LOG.warn("Ahoy link failed", e);
			return false;
		}
	}
}
