package com.thatcoffeelock.warehouse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Loading Dock screen: which ship is moored, which warehouses the dock serves, and one big "unload everything"
 * button. Click a warehouse to browse it; with a ship moored, what you take goes straight into the ship's holds.
 */
final class DockMenu extends BaseMenu {
	private static final int[] WAREHOUSE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

	private final ServerLevel level;
	private final BlockPos dock;
	private final @Nullable ShipTarget ship;

	static void open(ServerPlayer player, ServerLevel level, BlockPos dock, @Nullable ShipTarget ship) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DockMenu(id, player, level, dock, ship),
			Component.literal("Loading Dock" + (ship != null ? " · " + ship.name() : "")).withStyle(ChatFormatting.DARK_BLUE)));
	}

	private DockMenu(int syncId, ServerPlayer viewer, ServerLevel level, BlockPos dock, @Nullable ShipTarget ship) {
		super(syncId, viewer, 3);
		this.level = level;
		this.dock = dock;
		this.ship = ship;
		render();
	}

	private void render() {
		clearButtons();
		WarehouseConfig config = WarehouseConfig.get();
		List<Warehouse> served = Docks.served(level, dock);

		List<Component> info = new ArrayList<>();
		if (ship != null) {
			info.add(Gui.text("Moored: " + ship.name(), ChatFormatting.AQUA));
		} else if (WarehouseMod.ahoy()) {
			info.add(Gui.text("No ship moored. Sail an Ahoy ship", ChatFormatting.GRAY));
			info.add(Gui.text("within " + config.shipReach + " blocks of this dock.", ChatFormatting.GRAY));
		} else {
			info.add(Gui.text("Install Ahoy to sail ships up to it.", ChatFormatting.GRAY));
		}
		info.add(Gui.text("Serves " + served.size() + (served.size() == 1 ? " warehouse" : " warehouses") + " within " + config.dockReach + " blocks.",
			ChatFormatting.GRAY));
		button(4, Gui.icon(Items.LANTERN, Gui.text("Loading Dock", ChatFormatting.YELLOW, ChatFormatting.BOLD), info), null);

		for (int i = 0; i < served.size() && i < WAREHOUSE_SLOTS.length; i++) {
			Warehouse w = served.get(i);
			List<Component> lore = new ArrayList<>();
			lore.add(Gui.text(Gui.n(w.total()) + " / " + Gui.n(w.capacity()) + " items (" + w.percentFull() + "%)",
				w.space() == 0 ? ChatFormatting.RED : ChatFormatting.AQUA));
			lore.add(Gui.text("Accepts: " + w.filter.title, ChatFormatting.GRAY));
			lore.add(Gui.text(w.locked ? "Locked by " + w.ownerName : "Open to everyone", w.locked ? ChatFormatting.GOLD : ChatFormatting.GREEN));
			lore.add(Gui.text((int) Math.sqrt(w.pos.distSqr(dock)) + " blocks away", ChatFormatting.DARK_GRAY));
			lore.add(Component.empty());
			lore.add(Gui.text(ship != null ? "Click to load the ship from here." : "Click to open it.", ChatFormatting.YELLOW));
			ItemStack icon = Gui.icon(Items.CARTOGRAPHY_TABLE, Gui.text(w.name, ChatFormatting.GOLD, ChatFormatting.BOLD), lore);
			button(WAREHOUSE_SLOTS[i], w.filter != Category.ALL ? Gui.glow(icon) : icon, (b, t) -> {
				click();
				WarehouseMod.nextTick(() -> WarehouseMenu.openFromDock(viewer, w, dock, ship));
			});
		}
		if (served.isEmpty()) {
			button(13, Gui.icon(Items.COBWEB, Gui.text("No warehouses nearby", ChatFormatting.GRAY),
				Gui.text("Build a Warehouse Core within " + config.dockReach + " blocks.", ChatFormatting.DARK_GRAY)), null);
		}

		if (ship != null) {
			button(22, Gui.glow(Gui.icon(Items.CHEST_MINECART, Gui.text("Unload all cargo", ChatFormatting.GREEN, ChatFormatting.BOLD),
				Gui.text("Empties the " + ship.name() + "'s holds into the warehouses.", ChatFormatting.GRAY),
				Gui.text("Specialist warehouses get their kind first,", ChatFormatting.GRAY),
				Gui.text("the rest goes to the nearest general ones.", ChatFormatting.GRAY),
				Gui.text("Whatever doesn't fit stays aboard.", ChatFormatting.DARK_GRAY))), (b, t) -> unload(served));
		} else {
			button(22, Gui.icon(Items.MINECART, Gui.text("Unload all cargo", ChatFormatting.DARK_GRAY),
				Gui.text("No ship moored.", ChatFormatting.GRAY)), null);
		}
		button(26, Gui.icon(Items.BARRIER, Gui.text("Close", ChatFormatting.RED)), (b, t) -> WarehouseMod.nextTick(viewer::closeContainer));
		fill(0, size);
	}

	private void unload(List<Warehouse> served) {
		if (ship == null) {
			return;
		}
		if (!ship.usable()) {
			nope("The " + ship.name() + " has sailed off, or its captain locked the cargo.");
			return;
		}
		Docks.Report report = Docks.unload(served, ship.holds());
		if (report.moved() > 0) {
			stored();
		}
		Docks.tell(viewer, ship.name(), report);
		render();
	}

	@Override
	public boolean stillValid(Player player) {
		return Warehouses.isDock(level, dock) && (WarehouseMenu.near(player, dock) || (ship != null && ship.present().getAsBoolean()));
	}
}
