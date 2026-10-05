package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/**
 * For other mods (the Warehouse uses it): somewhere for a Drill Rig to unload. A rig's ore and stone holds are offered
 * to every registered unloader, every few seconds and when the owner clicks "Unload". Fossil Fool doesn't know or
 * care who's on the other end.
 */
public final class FossilFoolApi {
	/** Takes what it wants out of a rig's holds. */
	@FunctionalInterface
	public interface Unloader {
		/**
		 * @param rig   the middle of the rig's shaft, at ground level
		 * @param holds the ore hold first, then the stone hold; take items out of them directly
		 * @return how many items were taken
		 */
		long unload(ServerLevel level, BlockPos rig, List<Container> holds);
	}

	private static final List<Unloader> UNLOADERS = new ArrayList<>();

	private FossilFoolApi() {
	}

	public static void registerUnloader(Unloader unloader) {
		UNLOADERS.add(unloader);
	}

	/** How far from a rig an unloader should look, in blocks (config "warehouseReach"). */
	public static int reach() {
		return FossilConfig.get().warehouseReach;
	}

	static boolean hasUnloaders() {
		return !UNLOADERS.isEmpty();
	}

	/** Offers these holds to every unloader (what a rig does every few seconds). Returns how many items were taken. */
	public static long unload(ServerLevel level, BlockPos rig, List<Container> holds) {
		long moved = 0;
		for (Unloader unloader : UNLOADERS) {
			try {
				moved += unloader.unload(level, rig, holds);
			} catch (RuntimeException e) {
				FossilFoolMod.LOG.warn("A Drill Rig unloader from another mod failed", e);
			}
		}
		return moved;
	}
}
