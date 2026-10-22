package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Voyages to other worlds (Terra Incognita's Sea Chart): the ship is bottled up on one side and launched again on the
 * other, with its name, owner, cargo, refits, guns and bunks. Published through Fabric's ObjectShare, so neither mod
 * needs the other:
 * <ul>
 * <li>{@code ahoy:ship_aboard} {@code Function<ServerPlayer, Map<String, Object>>}: the ship a player is aboard
 * ({@code name}, {@code rigging} level, {@code mayUse} for that player, the {@code crew} of players aboard), or null</li>
 * <li>{@code ahoy:pack_ship} {@code Function<ServerPlayer, ItemStack>}: bottles up that ship (everyone aboard steps
 * off first), or EMPTY</li>
 * <li>{@code ahoy:launch_ship} {@code BiFunction<ItemStack, List<ServerPlayer>, Boolean>}: launches a bottled ship on
 * open water near the first player, puts them at the wheel and the rest aboard. False if there's no room for it.</li>
 * </ul>
 */
final class Voyages {
	/** How far from the spot to look for water roomy enough for the hull. */
	static final int SEARCH = 48;

	private Voyages() {
	}

	static void share() {
		var share = FabricLoader.getInstance().getObjectShare();
		share.put("ahoy:ship_aboard", (Function<ServerPlayer, Map<String, Object>>) Voyages::aboard);
		share.put("ahoy:pack_ship", (Function<ServerPlayer, ItemStack>) Voyages::pack);
		share.put("ahoy:launch_ship", (BiFunction<ItemStack, List<ServerPlayer>, Boolean>) Voyages::launch);
	}

	static @Nullable Map<String, Object> aboard(ServerPlayer player) {
		Ship ship = Ships.shipOf(player);
		if (ship == null) {
			return null;
		}
		Map<String, Object> info = new HashMap<>();
		info.put("name", ship.data.name);
		info.put("rigging", ship.data.speedLevel);
		info.put("mayUse", ship.mayUse(player));
		info.put("crew", ship.players());
		return info;
	}

	static ItemStack pack(ServerPlayer player) {
		Ship ship = Ships.shipOf(player);
		return ship == null ? ItemStack.EMPTY : ship.bottleUp();
	}

	static boolean launch(ItemStack bottle, List<ServerPlayer> crew) {
		if (crew.isEmpty() || !Bottle.isBottle(bottle)) {
			return false;
		}
		ServerPlayer captain = crew.get(0);
		ServerLevel level = (ServerLevel) captain.level();
		ShipData data = Bottle.savedData(bottle, level);
		if (data == null) {
			return false;
		}
		Ship ship = launchNear(level, data, captain.getX(), captain.getY(), captain.getZ(), captain.getYRot());
		if (ship == null) {
			return false;
		}
		// the ship makes its seats on its first ticks
		List<ServerPlayer> aboard = new ArrayList<>(crew);
		AhoyMod.later(2, () -> {
			int seat = 0;
			for (ServerPlayer sailor : aboard) {
				while (seat < ShipModel.SPOTS.size() && !ship.seatFree(seat)) {
					seat++;
				}
				if (seat >= ShipModel.SPOTS.size() || sailor.level() != level) {
					continue; // no room, or they've gone elsewhere: they stay where they are
				}
				ship.seat(sailor, seat++);
			}
		});
		return true;
	}

	/**
	 * Launches a ship on the open water nearest to (x, z), searching outwards up to {@link #SEARCH} blocks and trying
	 * four headings at each spot, starting with the one given. Null if nowhere nearby has room for the hull.
	 */
	static @Nullable Ship launchNear(ServerLevel level, ShipData data, double x, double y, double z, float yaw) {
		for (int r = 0; r <= SEARCH; r += 8) {
			for (int dx = -r; dx <= r; dx += 8) {
				for (int dz = -r; dz <= r; dz += 8) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue; // only the ring at this distance
					}
					double cx = x + dx;
					double cz = z + dz;
					Double surface = surface(level, cx, y, cz);
					if (surface == null) {
						continue;
					}
					for (int turn = 0; turn < 4; turn++) {
						float heading = yaw + turn * 90;
						if (Ship.hullFits(level, surface, cx, cz, heading) && Ship.afloat(level, surface, cx, cz, heading)) {
							return Ships.launch(level, data, cx, surface, cz, heading);
						}
					}
				}
			}
		}
		return null;
	}

	/** The water surface at (x, z) a few blocks either side of y, or null if there's no water there. */
	private static @Nullable Double surface(ServerLevel level, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y + 3, z);
		for (int i = 0; i < 10; i++, at = at.below()) {
			if (level.getFluidState(at).is(FluidTags.WATER)) {
				while (level.getFluidState(at.above()).is(FluidTags.WATER)) {
					at = at.above();
				}
				return at.getY() + 0.9;
			}
		}
		return null;
	}
}
