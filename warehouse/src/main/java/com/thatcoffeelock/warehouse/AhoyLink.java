package com.thatcoffeelock.warehouse;

import java.util.List;

import com.thatcoffeelock.ahoy.AhoyApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The only class that talks to Ahoy, and it's only loaded when Ahoy is installed. Adds a "Loading Dock" button to the
 * captain's menu whenever the ship is moored near a dock.
 */
final class AhoyLink {
	private AhoyLink() {
	}

	static void init() {
		AhoyApi.addMenuButton(new AhoyApi.MenuButton() {
			@Override
			public @Nullable ItemStack icon(ServerPlayer viewer, AhoyApi.ShipView ship) {
				BlockPos dock = dockNear(ship);
				if (dock == null) {
					return null;
				}
				int served = Docks.served(ship.level(), dock).size();
				return Gui.glow(Gui.icon(Items.LANTERN, Gui.text("Loading Dock", ChatFormatting.YELLOW, ChatFormatting.BOLD),
					Gui.text(served + (served == 1 ? " warehouse" : " warehouses") + " ashore.", ChatFormatting.GRAY),
					Gui.text("Unload the cargo, or load up from a warehouse.", ChatFormatting.GRAY),
					Gui.text("Click to open the dock.", ChatFormatting.YELLOW)));
			}

			@Override
			public void click(ServerPlayer viewer, AhoyApi.ShipView ship) {
				BlockPos dock = dockNear(ship);
				if (dock == null) {
					return;
				}
				WarehouseMod.nextTick(() -> DockMenu.open(viewer, ship.level(), dock, target(viewer, ship, dock)));
			}
		});
	}

	private static @Nullable BlockPos dockNear(AhoyApi.ShipView ship) {
		Vec3 p = ship.position();
		return Warehouses.nearestDock(ship.level(), p.x, p.y, p.z, WarehouseConfig.get().shipReach);
	}

	private static ShipTarget target(ServerPlayer viewer, AhoyApi.ShipView ship, BlockPos dock) {
		double reach = WarehouseConfig.get().shipReach + 2;
		Vec3 center = Vec3.atCenterOf(dock);
		return new ShipTarget(ship.name(), ship.holds(),
			() -> !ship.isGone() && ship.position().distanceToSqr(center) <= reach * reach
				&& viewer.position().distanceToSqr(ship.position()) <= 32 * 32,
			() -> ship.mayUse(viewer));
	}

	/** The ship moored nearest to this dock, for when someone right-clicks the dock from the pier. */
	static @Nullable ShipTarget nearestShip(ServerLevel level, BlockPos dock, ServerPlayer viewer) {
		List<AhoyApi.ShipView> ships = AhoyApi.shipsNear(level, Vec3.atCenterOf(dock), WarehouseConfig.get().shipReach);
		return ships.isEmpty() ? null : target(viewer, ships.get(0), dock);
	}
}
