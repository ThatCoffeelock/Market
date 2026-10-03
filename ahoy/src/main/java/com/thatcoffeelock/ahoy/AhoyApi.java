package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods (the Warehouse's Loading Dock uses it): find ships, reach into their holds, and add a
 * button to the captain's menu. Ahoy doesn't know or care who's on the other end.
 */
public final class AhoyApi {
	/** A ship, as other mods get to see it. */
	public interface ShipView {
		String name();

		ServerLevel level();

		/** Where the ship is (its stern, at the waterline). */
		Vec3 position();

		/** The cargo holds. Change them like any container; the ship saves them. */
		List<Container> holds();

		/** May this player steer the ship and open its cargo? */
		boolean mayUse(Player player);

		/** True once the ship is bottled up or unloaded. */
		boolean isGone();
	}

	/** An extra button in the captain's menu. */
	public interface MenuButton {
		/** The icon to show, or null to show nothing (e.g. when there's no dock nearby). */
		@Nullable ItemStack icon(ServerPlayer viewer, ShipView ship);

		void click(ServerPlayer viewer, ShipView ship);
	}

	private static final List<MenuButton> BUTTONS = new ArrayList<>();

	private AhoyApi() {
	}

	/** The captain's menu has room for two of these. */
	public static void addMenuButton(MenuButton button) {
		BUTTONS.add(button);
	}

	static List<MenuButton> menuButtons() {
		return BUTTONS;
	}

	/** Ships in this level within {@code radius} blocks of {@code pos}, nearest first. */
	public static List<ShipView> shipsNear(ServerLevel level, Vec3 pos, double radius) {
		List<Ship> found = new ArrayList<>();
		for (Ship ship : Ships.all()) {
			if (ship.level == level && !ship.isRemoved() && ship.root.position().distanceToSqr(pos) <= radius * radius) {
				found.add(ship);
			}
		}
		found.sort((a, b) -> Double.compare(a.root.position().distanceToSqr(pos), b.root.position().distanceToSqr(pos)));
		return new ArrayList<>(found);
	}
}
