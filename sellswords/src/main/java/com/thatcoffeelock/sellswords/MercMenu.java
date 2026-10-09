package com.thatcoffeelock.sellswords;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A mercenary's menu (right-click them with an empty hand). The top is about them and their orders. The middle is
 * the promotion tree: the Ranged path across one row, the Melee path across another, glass between the steps lit up
 * green for ranks they have, yellow for the one they can take next, grey for later, red for the path they didn't take.
 * Click the yellow one to pay the gold and promote them. The first promotion picks the path for good, so it asks twice.
 */
final class MercMenu extends Ui {
	private static final int[] RANGED_SLOTS = {20, 22, 24, 26};
	private static final int[] MELEE_SLOTS = {38, 40, 42, 44};

	private final Merc merc;
	private @Nullable Rank confirming;
	private boolean confirmDismiss;

	static void open(ServerPlayer player, Merc merc) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MercMenu(id, player, merc, new SimpleContainer(SIZE)),
			Component.literal(merc.name).withStyle(ChatFormatting.DARK_RED)));
	}

	private MercMenu(int syncId, ServerPlayer viewer, Merc merc, SimpleContainer box) {
		super(syncId, viewer, box);
		this.merc = merc;
		render();
	}

	@Override
	protected void draw() {
		if (!Mercs.ALL.containsKey(merc.id)) {
			close();
			return;
		}
		Rank rank = merc.rank();
		Station home = Stations.ALL.get(merc.home);
		List<Component> about = new ArrayList<>();
		about.add(t(rank.title + " " + rank.stars(), rank.path.color, ChatFormatting.BOLD));
		about.add(t(rank.blurb(), ChatFormatting.GRAY));
		about.add(t(Ui.health(merc), ChatFormatting.GRAY));
		about.add(t("Kills: " + merc.kills, ChatFormatting.GRAY));
		about.add(t("Orders: " + Ui.ordersText(merc), ChatFormatting.YELLOW));
		about.add(t("Home: " + (home == null ? "none" : home.name + " at " + home.x + " " + home.y + " " + home.z), ChatFormatting.DARK_GRAY));
		about.add(t("Works for " + merc.ownerName, ChatFormatting.DARK_GRAY));
		button(4, glow(icon(Items.NAME_TAG, t(merc.name, ChatFormatting.GOLD, ChatFormatting.BOLD), about)), null);

		// orders
		Merc.Orders orders = merc.orders();
		button(10, order(Items.LEAD, "Follow me", orders == Merc.Orders.FOLLOW, List.of(
			t("Walks with you, fights whatever attacks you", ChatFormatting.GRAY),
			t("and whatever you attack (a hunting party).", ChatFormatting.GRAY),
			t("Boards your ship or airship with you, and", ChatFormatting.GRAY),
			t("follows you through portals.", ChatFormatting.GRAY))), () -> {
			merc.orders(Merc.Orders.FOLLOW);
			done("\"Lead the way.\"");
		});
		button(11, order(item("white_banner", Items.SHIELD), "Hold this spot", orders == Merc.Orders.GUARD, List.of(
			t("Stays right here like a good dog. Fights", ChatFormatting.GRAY),
			t("anything that comes within " + (int) Duty.GUARD_RADIUS + " blocks, protects", ChatFormatting.GRAY),
			t("villagers, then goes back to the spot.", ChatFormatting.GRAY),
			t("On a wall, rangers stay on the wall.", ChatFormatting.DARK_GRAY))), () -> {
			LivingEntity brain = Mercs.brain(merc);
			merc.orders(Merc.Orders.GUARD);
			if (brain != null) {
				merc.post(Cmd.dimId(brain.level()), brain.blockPosition());
			}
			done("\"Nobody gets past. Probably.\"");
		});
		Station back = home != null ? home : Stations.nearest(merc.dim, viewer.blockPosition());
		button(12, order(Items.TARGET, back == null ? "Back to the station" : "Back to the " + back.name, orders == Merc.Orders.STATION, back == null
			? List.of(t("There's no Mercenary Station in this world.", ChatFormatting.RED))
			: List.of(t("Goes back to the station at " + back.x + " " + back.y + " " + back.z, ChatFormatting.GRAY),
				t("and patrols " + SellswordsConfig.get().stationRadius + " blocks around it.", ChatFormatting.GRAY))), back == null ? null : () -> {
			merc.home = back.key();
			merc.orders(Merc.Orders.STATION);
			Duty.state(merc).wander = null;
			done("\"Back to the barracks. Is there coffee?\"");
		});
		button(14, order(Items.BOW, "Hunting: " + (merc.hunting ? "on" : "off"), merc.hunting, List.of(
			t("While following you, also shoots cows, pigs, sheep,", ChatFormatting.GRAY),
			t("chickens and rabbits near you. Never named, leashed", ChatFormatting.GRAY),
			t("or baby ones. They always join in on what you hit.", ChatFormatting.GRAY),
			t("Click to switch " + (merc.hunting ? "off." : "on."), ChatFormatting.YELLOW))), () -> {
			merc.hunting = !merc.hunting;
			Mercs.changed();
			click();
		});
		button(16, icon(Items.BARRIER, t(confirmDismiss ? "Click again to dismiss" : "Dismiss", ChatFormatting.RED, ChatFormatting.BOLD), List.of(
			t("Sends " + merc.firstName() + " away for good. No refunds,", ChatFormatting.GRAY),
			t("and the gold stays spent.", ChatFormatting.GRAY))), () -> {
			if (!confirmDismiss) {
				confirmDismiss = true;
				return;
			}
			viewer.sendSystemMessage(Component.literal(merc.name + " tips their hat and walks off into the sunset.").withStyle(ChatFormatting.GRAY));
			Mercs.dismiss(merc);
			close();
		});

		// the promotion tree
		button(18, icon(Items.CROSSBOW, t("Ranged path", Rank.Path.RANGED.color, ChatFormatting.BOLD), List.of(
			t("Further, harder, faster shots,", ChatFormatting.GRAY), t("ending with a musket.", ChatFormatting.GRAY))), null);
		button(36, icon(Items.IRON_SWORD, t("Melee path", Rank.Path.MELEE.color, ChatFormatting.BOLD), List.of(
			t("Heavier armour, a heavier sword,", ChatFormatting.GRAY), t("ending behind a shield.", ChatFormatting.GRAY))), null);
		path(Rank.Path.RANGED, RANGED_SLOTS);
		path(Rank.Path.MELEE, MELEE_SLOTS);
		button(27, glow(icon(Rank.RECRUIT.icon, t("Recruit", rank == Rank.RECRUIT ? ChatFormatting.YELLOW : ChatFormatting.GREEN), List.of(
			t("Where everyone starts.", ChatFormatting.GRAY),
			t(rank == Rank.RECRUIT ? "Pick a path: the first promotion decides." : "✔ Done that.", ChatFormatting.DARK_GRAY)))), null);
		int have = gold(viewer);
		ItemStack purse = icon(Items.GOLD_INGOT, t("Your gold: " + have + " ingots", ChatFormatting.GOLD), List.of(
			t("Promotions are paid in gold ingots.", ChatFormatting.GRAY),
			t("Each step costs twice the last.", ChatFormatting.GRAY)));
		purse.setCount(Math.max(1, Math.min(64, have)));
		button(31, purse, null);
		button(49, icon(Items.ARROW, t("Close", ChatFormatting.GRAY), List.of()), this::close);
	}

	private ItemStack order(Item item, String name, boolean active, List<Component> lore) {
		List<Component> all = new ArrayList<>(lore);
		all.add(t(active ? "▶ Current orders" : "Click to give this order.", active ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		ItemStack stack = icon(item, t(name, active ? ChatFormatting.GREEN : ChatFormatting.WHITE, ChatFormatting.BOLD), all);
		return active ? glow(stack) : stack;
	}

	private void done(String quote) {
		Mercs.changed();
		click();
		viewer.sendSystemMessage(Component.literal(merc.firstName() + ": " + quote).withStyle(ChatFormatting.GOLD));
		close();
	}

	/** One row of the tree: four ranks with glass between them. */
	private void path(Rank.Path path, int[] slots) {
		Rank current = merc.rank();
		List<Rank> ranks = Rank.of(path);
		boolean closed = current.path != Rank.Path.NONE && current.path != path;
		for (int i = 0; i < ranks.size(); i++) {
			Rank r = ranks.get(i);
			boolean have = current.path == path && current.tier >= r.tier;
			boolean next = current.canBecome(r);
			Item glass = item(closed ? "red_stained_glass_pane" : have ? "lime_stained_glass_pane"
				: next ? "yellow_stained_glass_pane" : "gray_stained_glass_pane", Items.GLASS_PANE);
			button(slots[i] - 1, icon(glass, t(" "), List.of()), null);
			List<Component> lore = new ArrayList<>();
			lore.add(t(r.stars(), path.color));
			lore.add(t(r.blurb(), ChatFormatting.GRAY));
			lore.add(t("Health " + (int) r.health + " · armour " + (int) r.armor + " · sword " + (int) r.melee
				+ (r.shoots() ? " · " + (r.musket() ? "musket " : "crossbow ") + (int) r.ranged + " at " + (int) r.range + " blocks" : ""), ChatFormatting.DARK_GRAY));
			ChatFormatting color;
			Runnable action = null;
			if (have) {
				color = ChatFormatting.GREEN;
				lore.add(t("✔ " + merc.firstName() + " is " + (r == current ? "this rank." : "past this."), ChatFormatting.GREEN));
			} else if (closed) {
				color = ChatFormatting.DARK_RED;
				lore.add(t("✖ " + merc.firstName() + " took the " + current.path.label + " path.", ChatFormatting.DARK_RED));
			} else if (next) {
				color = ChatFormatting.YELLOW;
				int cost = Mercs.promotionCost(r, viewer.getUUID());
				int purse = gold(viewer);
				lore.add(t("Promotion: " + cost + " gold ingots (you have " + purse + ")", purse >= cost || viewer.isCreative() ? ChatFormatting.GOLD : ChatFormatting.RED));
				if (current == Rank.RECRUIT) {
					lore.add(t(confirming == r ? "Click again: there's no going back from the " + path.label + " path."
						: "This picks the " + path.label + " path for good.", ChatFormatting.LIGHT_PURPLE));
				} else {
					lore.add(t("Click to promote.", ChatFormatting.YELLOW));
				}
				action = () -> promote(r);
			} else {
				color = ChatFormatting.GRAY;
				lore.add(t("Promote to " + (r.tier == 1 ? "this path" : Rank.of(path).get(r.tier - 2).title) + " first.", ChatFormatting.DARK_GRAY));
			}
			ItemStack stack = icon(r.icon, t(r.title, color, ChatFormatting.BOLD), lore);
			button(slots[i], have || next ? glow(stack) : stack, action);
		}
	}

	private void promote(Rank to) {
		Rank current = merc.rank();
		if (!current.canBecome(to)) {
			return;
		}
		if (current == Rank.RECRUIT && confirming != to) {
			confirming = to;
			return;
		}
		confirming = null;
		int cost = Mercs.promotionCost(to, viewer.getUUID());
		if (!takeGold(viewer, cost)) {
			viewer.sendSystemMessage(Component.literal(merc.firstName() + " wants " + cost + " gold ingots for that. You have " + gold(viewer) + ".")
				.withStyle(ChatFormatting.RED));
			Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1f);
			return;
		}
		Mercs.promote(merc, to);
		viewer.sendSystemMessage(Component.literal(merc.name + " is now a " + to.title + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}

	@Override
	public boolean stillValid(Player player) {
		if (!Mercs.ALL.containsKey(merc.id)) {
			return false;
		}
		LivingEntity body = Mercs.body(merc);
		LivingEntity brain = Mercs.brain(merc);
		LivingEntity near = body != null ? body : brain;
		return near != null && near.level() == player.level() && player.distanceToSqr(near) < 12 * 12;
	}
}
