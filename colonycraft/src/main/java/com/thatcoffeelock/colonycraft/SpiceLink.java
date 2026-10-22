package com.thatcoffeelock.colonycraft;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Optional link to the Specerij mod, through Fabric's ObjectShare ({@code specerij:spice_for_biome} and
 * {@code specerij:plantation}): Spice Plantations grow the spice of the colony's climate, and Specerij decides how
 * much. Without Specerij, there are no plantations for sale.
 */
final class SpiceLink {
	/** Blocks from Headquarters at which a plantation reaches its full "far from home" bonus (double). */
	static final double FAR = 5000;
	private static boolean warned;

	private SpiceLink() {
	}

	static boolean present() {
		var share = FabricLoader.getInstance().getObjectShare();
		return share.get("specerij:spice_for_biome") instanceof Function<?, ?> && share.get("specerij:plantation") instanceof Function<?, ?>;
	}

	/** The spice that grows at this spot ("pepper"), or null if none does (or there's no Specerij). */
	@SuppressWarnings("unchecked")
	static @Nullable String spiceAt(ServerLevel level, BlockPos pos) {
		if (!(FabricLoader.getInstance().getObjectShare().get("specerij:spice_for_biome") instanceof Function<?, ?> f)) {
			return null;
		}
		try {
			return ((Function<String, String>) f).apply(level.getBiome(pos).getRegisteredName());
		} catch (RuntimeException e) {
			warn(e);
			return null;
		}
	}

	/**
	 * How far from home a colony is: 1 next door to Headquarters, rising to 2 at {@link #FAR} blocks (or in another
	 * dimension). Without Headquarters it's measured from the middle of the world.
	 */
	static double distance(Colony.Building building) {
		Colony.Building hq = Network.hq(building.colony.owner);
		if (hq != null && !hq.colony.dimension.equals(building.colony.dimension)) {
			return 2;
		}
		BlockPos home = hq != null ? hq.origin : BlockPos.ZERO;
		double dx = building.origin.getX() - home.getX();
		double dz = building.origin.getZ() - home.getZ();
		return 1 + Math.min(1, Math.sqrt(dx * dx + dz * dz) / FAR);
	}

	/** A plantation's day: the spice of its climate, as Specerij grows it. Empty without Specerij or a spice. */
	@SuppressWarnings("unchecked")
	static List<ItemStack> harvest(@Nullable String spice, int tier, int workers, double distance) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("specerij:plantation");
		if (spice == null || workers <= 0 || !(hook instanceof Function<?, ?> f)) {
			return List.of();
		}
		try {
			List<ItemStack> out = ((Function<Map<String, Object>, List<ItemStack>>) f)
				.apply(Map.of("spice", spice, "tier", tier, "workers", workers, "distance", distance));
			return out == null ? List.of() : out;
		} catch (RuntimeException e) {
			warn(e);
			return List.of();
		}
	}

	private static void warn(RuntimeException e) {
		if (!warned) {
			warned = true;
			ColonycraftMod.LOG.warn("The Specerij link failed; spice plantations grow nothing", e);
		}
	}
}
