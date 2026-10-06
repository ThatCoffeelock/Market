package com.thatcoffeelock.warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Warehouse networks: pick the central warehouse a warehouse should be a branch of, or (on a central warehouse) see
 * its branches and open any of them from here, wherever they are.
 */
final class NetworkMenu extends BaseMenu {
	private static final int LIST = 45;

	enum Mode { PICK, BRANCHES }

	private final Warehouse w;
	private final BooleanSupplier still;
	private final WarehouseMenu.View view;
	private final Mode mode;
	private int page;

	static void open(ServerPlayer player, Warehouse w, BooleanSupplier still, WarehouseMenu.View view, Mode mode) {
		Component title = Component.literal(mode == Mode.PICK ? "Pick a central warehouse" : w.name + " · Branches")
			.withStyle(ChatFormatting.DARK_BLUE);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new NetworkMenu(id, player, w, still, view, mode), title));
	}

	private NetworkMenu(int syncId, ServerPlayer viewer, Warehouse w, BooleanSupplier still, WarehouseMenu.View view, Mode mode) {
		super(syncId, viewer, 6);
		this.w = w;
		this.still = still;
		this.view = view;
		this.mode = mode;
		render();
	}

	/** Warehouses the viewer could make this one a branch of. */
	private List<Warehouse> candidates() {
		List<Warehouse> list = new ArrayList<>();
		for (Warehouse c : Warehouses.all()) {
			if (!c.packed && c.mayManage(viewer) && Warehouses.whyNoLink(w, c) == null) {
				list.add(c);
			}
		}
		return list;
	}

	private static String where(Warehouse c) {
		return c.packed ? "packed up" : c.pos.toShortString() + " in " + c.dimension.replaceAll(".*[:/ ]", "").replace("]", "");
	}

	private void render() {
		clearButtons();
		List<Warehouse> list = mode == Mode.PICK ? candidates() : Warehouses.branchesOf(w);
		int pages = Math.max(1, (list.size() + LIST - 1) / LIST);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < LIST; i++) {
			int index = page * LIST + i;
			if (index >= list.size()) {
				break;
			}
			Warehouse c = list.get(index);
			List<Component> lore = new ArrayList<>();
			lore.add(Gui.text("Stock: " + Gui.n(c.total()) + " / " + Gui.n(c.capacity()) + " items", ChatFormatting.AQUA));
			lore.add(Gui.text("Accepts: " + c.filter.title, ChatFormatting.GRAY));
			lore.add(Gui.text("At " + where(c), ChatFormatting.DARK_GRAY));
			lore.add(Component.empty());
			if (mode == Mode.PICK) {
				lore.add(Gui.text("Click to make " + w.name + " a branch of this one.", ChatFormatting.YELLOW));
				button(i, Gui.icon(Items.CARTOGRAPHY_TABLE, Gui.text(c.name, ChatFormatting.GOLD, ChatFormatting.BOLD), lore), (b, t) -> pick(c));
			} else {
				lore.add(Gui.text(c.forward ? "Sends everything on to " + w.name + "." : "Keeps its own stock.",
					c.forward ? ChatFormatting.GREEN : ChatFormatting.GRAY));
				lore.add(Gui.text("Left-click: open it from here", ChatFormatting.YELLOW));
				if (c.mayManage(viewer)) {
					lore.add(Gui.text("Right-click: switch sending on/off", ChatFormatting.YELLOW));
				}
				ItemStack icon = Gui.icon(Items.CARTOGRAPHY_TABLE, Gui.text(c.name, ChatFormatting.GOLD, ChatFormatting.BOLD), lore);
				button(i, c.forward ? Gui.glow(icon) : icon, (b, t) -> {
					if (b == 1) {
						toggle(c);
					} else {
						openBranch(c);
					}
				});
			}
		}
		if (list.isEmpty()) {
			button(22, Gui.icon(Items.COBWEB, Gui.text(mode == Mode.PICK ? "No warehouse to link to" : "No branches yet", ChatFormatting.GRAY),
				mode == Mode.PICK
					? List.<Component>of(Gui.text("You need another warehouse of your own that", ChatFormatting.DARK_GRAY),
						Gui.text("isn't a branch itself.", ChatFormatting.DARK_GRAY))
					: List.<Component>of(Gui.text("Open another warehouse's settings and link", ChatFormatting.DARK_GRAY),
						Gui.text("it to this one.", ChatFormatting.DARK_GRAY))), null);
		}
		button(45, Gui.icon(Items.ARROW, Gui.text("Back to the settings", ChatFormatting.YELLOW)), (b, t) -> back());
		if (page > 0) {
			button(48, Gui.icon(Items.ARROW, Gui.text("Previous page", ChatFormatting.YELLOW)), (b, t) -> {
				click();
				page--;
				render();
			});
		}
		button(49, Gui.icon(Items.FILLED_MAP, Gui.text(w.name, ChatFormatting.GOLD, ChatFormatting.BOLD),
			Gui.text(mode == Mode.PICK ? "Branches open from their central warehouse," : list.size() + (list.size() == 1 ? " branch." : " branches."), ChatFormatting.GRAY),
			Gui.text(mode == Mode.PICK ? "and can send their stock on to it." : "Branches that send pass their stock on here.", ChatFormatting.GRAY)), null);
		if (page < pages - 1) {
			button(50, Gui.icon(Items.ARROW, Gui.text("Next page", ChatFormatting.YELLOW)), (b, t) -> {
				click();
				page++;
				render();
			});
		}
		button(53, Gui.icon(Items.BARRIER, Gui.text("Close", ChatFormatting.RED)), (b, t) -> WarehouseMod.nextTick(viewer::closeContainer));
		fill(0, size);
	}

	private void pick(Warehouse c) {
		String why = Warehouses.whyNoLink(w, c);
		if (!w.mayManage(viewer) || !c.mayManage(viewer)) {
			why = "You can only link warehouses you own.";
		}
		if (why != null) {
			nope(why);
			render();
			return;
		}
		Warehouses.link(w, c);
		stored();
		viewer.sendSystemMessage(Component.literal(w.name + " is now a branch of " + c.name + ", and sends its stock on to it.")
			.withStyle(ChatFormatting.GOLD));
		back();
	}

	private void toggle(Warehouse c) {
		if (!c.mayManage(viewer)) {
			nope("Only " + c.ownerName + " can change that.");
			return;
		}
		click();
		c.forward = !c.forward;
		c.changed();
		render();
	}

	private void openBranch(Warehouse c) {
		if (c.packed) {
			nope(c.name + " is packed up.");
			return;
		}
		click();
		BooleanSupplier stillHere = () -> still.getAsBoolean() && !w.packed && Warehouses.centralOf(c) == w;
		WarehouseMod.nextTick(() -> WarehouseMenu.open(viewer, c, stillHere, null, new WarehouseMenu.View()));
	}

	private void back() {
		click();
		view.settings = true;
		WarehouseMod.nextTick(() -> WarehouseMenu.open(viewer, w, still, null, view));
	}

	@Override
	public boolean stillValid(Player player) {
		return !w.packed && Warehouses.byId(w.id) == w && still.getAsBoolean();
	}
}
