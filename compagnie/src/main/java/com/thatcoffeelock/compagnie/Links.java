package com.thatcoffeelock.compagnie;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Optional links to the other mods, through Fabric's ObjectShare: Riches relics for expeditions, Fossil Fool oil
 * pockets for surveys, Fuck Illagers fingers for raids, Burlap Sack villagers for the lost colony. Without the mod,
 * the job just doesn't do that part (or doesn't show up on the board).
 */
final class Links {
	private static boolean warned;

	private Links() {
	}

	static boolean loaded(String mod) {
		return FabricLoader.getInstance().isModLoaded(mod);
	}

	private static Object share(String key) {
		return FabricLoader.getInstance().getObjectShare().get(key);
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			CompagnieMod.LOG.warn("A link to another mod failed; carrying on without it", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Object api(String key, String op, Map<String, Object> args) {
		if (!(share(key) instanceof BiFunction<?, ?, ?> api)) {
			return null;
		}
		try {
			return ((BiFunction<String, Map<String, Object>, Object>) api).apply(op, args);
		} catch (RuntimeException e) {
			warn(e);
			return null;
		}
	}

	/** A Riches relic that's still out there, recorded as found by the owner, or empty. */
	static ItemStack relic(Company c) {
		return api("riches:api", "relic", Map.of("owner", c.owner, "owner_name", c.ownerName)) instanceof ItemStack s ? s : ItemStack.EMPTY;
	}

	/** The nearest Fossil Fool oil pocket: {x, y, z, gusher}, or null. */
	static @Nullable Map<?, ?> pocket(ServerLevel level, BlockPos from) {
		return api("fossilfool:api", "pocket", Map.of("level", level, "pos", from, "radius", 24)) instanceof Map<?, ?> m ? m : null;
	}

	/** Illager Fingers, or empty. */
	static ItemStack fingers(int count) {
		return api("fuckillagers:api", "fingers", Map.of("count", count)) instanceof ItemStack s ? s : ItemStack.EMPTY;
	}

	/** A Burlap Sack with a villager in it, or empty. */
	@SuppressWarnings("unchecked")
	static ItemStack villagerSack(String name) {
		if (!(share("burlapsack:villager") instanceof Function<?, ?> make)) {
			return ItemStack.EMPTY;
		}
		try {
			Object sack = ((Function<String, ItemStack>) make).apply(name);
			return sack instanceof ItemStack s ? s : ItemStack.EMPTY;
		} catch (RuntimeException e) {
			warn(e);
			return ItemStack.EMPTY;
		}
	}
}
