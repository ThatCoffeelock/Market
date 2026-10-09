package com.thatcoffeelock.sellswords;

import java.util.ArrayList;
import java.util.List;

import com.thatcoffeelock.market.MarketData;
import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The Mercenary Station: hire a recruit at the top, your roster below (click one to call them back here), and
 * "everyone back" at the bottom.
 */
final class StationMenu extends Ui {
	private static final int[] ROSTER = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

	private final ServerLevel level;
	private final Station station;

	static void open(ServerPlayer player, ServerLevel level, Station station) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StationMenu(id, player, level, station, new SimpleContainer(SIZE)),
			Component.literal(station.name).withStyle(ChatFormatting.DARK_RED)));
	}

	private StationMenu(int syncId, ServerPlayer viewer, ServerLevel level, Station station, SimpleContainer box) {
		super(syncId, viewer, box);
		this.level = level;
		this.station = station;
		render();
	}

	@Override
	protected void draw() {
		List<Merc> mine = Mercs.ownedBy(viewer.getUUID());
		int cap = Mercs.squadCap(viewer.getUUID());
		long cost = Stations.hireCost(station);
		List<net.minecraft.network.chat.Component> about = new ArrayList<>();
		about.add(t("Hiring: " + Money.format(cost) + (station.discount > 0 ? " (" + Math.round(station.discount * 100) + "% Guildhouse discount)" : ""), ChatFormatting.GOLD));
		about.add(t("Your squad: " + mine.size() + " / " + cap, ChatFormatting.YELLOW));
		about.add(t("Idle mercenaries patrol " + SellswordsConfig.get().stationRadius + " blocks around", ChatFormatting.GRAY));
		about.add(t("their station and protect it and its villagers.", ChatFormatting.GRAY));
		about.add(t("Right-click one with an empty hand for orders and promotions.", ChatFormatting.DARK_GRAY));
		about.add(t("Blow a goat horn to rally them, or to make them hold.", ChatFormatting.DARK_GRAY));
		button(4, glow(icon(Items.TARGET, t(station.name, ChatFormatting.GOLD, ChatFormatting.BOLD), about)), null);

		// hiring
		long balance = MarketData.balance(viewer);
		Rank r = Rank.RECRUIT;
		if (mine.size() >= cap) {
			button(22, icon(Items.BARRIER, t("Your squad is full", ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t(mine.size() + " / " + cap + " mercenaries.", ChatFormatting.GRAY),
				t("Leadership gives room for one more every 25 levels.", ChatFormatting.DARK_GRAY),
				t("Or dismiss someone. They'll get over it.", ChatFormatting.DARK_GRAY))), null);
		} else {
			boolean afford = balance >= cost;
			button(22, glow(icon(Items.CROSSBOW, t("Hire a Recruit: " + Money.format(cost), ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
				t("Health " + (int) r.health + " · crossbow " + (int) r.ranged + " · sword " + (int) r.melee, ChatFormatting.GRAY),
				t("Comes with their own gear. Promote them with gold ingots:", ChatFormatting.GRAY),
				t("Ranged path → Musketeer, Melee path → Foestopper Bulwark.", ChatFormatting.GRAY),
				t("They're gone for good if they die.", ChatFormatting.DARK_GRAY),
				t("Your balance: " + Money.format(balance), afford ? ChatFormatting.GREEN : ChatFormatting.RED),
				t(afford ? "Click to hire." : "You can't afford one.", afford ? ChatFormatting.YELLOW : ChatFormatting.RED)))), afford ? this::hire : null);
		}

		// the roster
		for (int i = 0; i < ROSTER.length; i++) {
			if (i >= mine.size()) {
				break;
			}
			Merc m = mine.get(i);
			Rank mr = m.rank();
			List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
			lore.add(t(mr.title + " " + mr.stars(), mr.path.color));
			lore.add(t(Ui.health(m), ChatFormatting.GRAY));
			lore.add(t(Ui.ordersText(m), ChatFormatting.GRAY));
			lore.add(t("Kills: " + m.kills + (m.hunting ? " · hunting" : ""), ChatFormatting.GRAY));
			lore.add(t("Last seen at " + (int) Math.floor(m.x) + " " + (int) Math.floor(m.y) + " " + (int) Math.floor(m.z), ChatFormatting.DARK_GRAY));
			boolean here = m.orders() == Merc.Orders.STATION && m.home.equals(station.key());
			lore.add(t(here ? "Already stationed here." : "Click to call them back to this station.", here ? ChatFormatting.DARK_GRAY : ChatFormatting.YELLOW));
			button(ROSTER[i], icon(mr.icon, t(m.name, mr.path.color, ChatFormatting.BOLD), lore), here ? null : () -> {
				recall(m);
				viewer.sendSystemMessage(Component.literal(m.firstName() + " is on the way.").withStyle(ChatFormatting.GOLD));
			});
		}
		if (mine.size() > ROSTER.length) {
			button(44, icon(Items.PAPER, t("+" + (mine.size() - ROSTER.length) + " more", ChatFormatting.GRAY), List.of()), null);
		}

		if (!mine.isEmpty()) {
			button(45, icon(Items.BELL, t("Call everyone back", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
				t("All your mercenaries make for this station", ChatFormatting.GRAY),
				t("and patrol around it.", ChatFormatting.GRAY))), () -> {
				for (Merc m : Mercs.ownedBy(viewer.getUUID())) {
					recall(m);
				}
				Cmd.sound(level, "minecraft:block.bell.use", viewer.getX(), viewer.getY(), viewer.getZ(), 1f, 1f);
			});
		}
		button(53, icon(Items.BOOK, t("How it works", ChatFormatting.YELLOW), List.of(
			t("Right-click a mercenary with an empty hand:", ChatFormatting.GRAY),
			t("follow, hold a spot, back to the station, hunting,", ChatFormatting.GRAY),
			t("dismiss, and promotions for gold ingots.", ChatFormatting.GRAY),
			t("Goat horn: rally everyone close by, or make the", ChatFormatting.GRAY),
			t("ones following you hold. Sneak + horn: Charge!", ChatFormatting.GRAY),
			t("at whatever you're looking at.", ChatFormatting.GRAY),
			t("They board your ships and airships with you, and", ChatFormatting.GRAY),
			t("follow you through portals. They never hurt", ChatFormatting.GRAY),
			t("villagers, players or your pets.", ChatFormatting.GRAY))), null);
		button(49, icon(Items.ARROW, t("Close", ChatFormatting.GRAY), List.of()), this::close);
	}

	private void recall(Merc m) {
		m.home = station.key();
		m.orders(Merc.Orders.STATION);
		Mercs.changed();
		LivingEntity brain = Mercs.brain(m);
		if (brain != null && brain.level() == level && brain.distanceToSqr(station.x, station.y, station.z) > 64 * 64) {
			Duty.state(m).wander = null;
		}
	}

	private void hire() {
		List<Merc> mine = Mercs.ownedBy(viewer.getUUID());
		if (mine.size() >= Mercs.squadCap(viewer.getUUID())) {
			return;
		}
		long cost = Stations.hireCost(station);
		if (!MarketData.withdraw(viewer, cost)) {
			viewer.sendSystemMessage(Component.literal("You can't afford a recruit. Mercenaries don't take IOUs.").withStyle(ChatFormatting.RED));
			return;
		}
		Merc m = Mercs.hire(level, station.pos(), viewer.getUUID(), viewer.getName().getString(), station);
		if (m == null) {
			MarketData.deposit(viewer, cost);
			viewer.sendSystemMessage(Component.literal("Nobody turned up. (Couldn't place a recruit here; you've been refunded.)").withStyle(ChatFormatting.RED));
			return;
		}
		viewer.sendSystemMessage(Component.literal("Hired ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(m.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
			.append(Component.literal(" for " + Money.format(cost) + ". " + greeting(m)).withStyle(ChatFormatting.GOLD)));
	}

	private static String greeting(Merc m) {
		String[] lines = {"\"Goedendag. Who do I stab?\"", "\"I bring my own lunch. Cheese.\"", "\"Payment up front, ja?\"",
			"\"I have killed many zombies. Some of them on purpose.\"", "\"Where I come from, everything is flat. Including the jokes.\"",
			"\"Gezellig. Now point me at something.\"", "\"Do not worry, I am very tall. Arrows hit the top of me first.\""};
		return lines[Mercs.RANDOM.nextInt(lines.length)];
	}

	@Override
	public boolean stillValid(Player player) {
		return Stations.ALL.containsKey(station.key()) && level.getBlockState(station.pos()).is(Blocks.TARGET)
			&& player.distanceToSqr(station.x + 0.5, station.y + 0.5, station.z + 0.5) < 64;
	}
}
