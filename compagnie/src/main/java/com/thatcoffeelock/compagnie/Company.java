package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

/** One player's company: its agents, offers, jobs under way, the Ledger, the strongbox and its standing. */
final class Company {
	String owner = "";
	String ownerName = "";
	long rep;
	/** Marks made (or lost) on jobs and loans, all time, in cents. */
	long profit;
	int jobsDone;
	int agentsLost;
	int nextId = 1;
	long candidatesDay = -1;
	/** The best Colonycraft Company House the owner has (its tier), or 0. */
	int house;
	/** Where the owner last used a Company Desk: explorers search from here. */
	int homeX;
	int homeZ;

	List<Agent> agents = new ArrayList<>();
	List<Agent> candidates = new ArrayList<>();
	List<Job> board = new ArrayList<>();
	List<Job> active = new ArrayList<>();
	List<Loan> loans = new ArrayList<>();
	/** What happened while the owner was away, shown at the desk (and on joining). */
	List<String> news = new ArrayList<>();
	/** News lines the owner hasn't seen yet (they happened while away). */
	int unread;
	/** Buff name -> last day it's in effect. */
	Map<String, Long> buffs = new HashMap<>();

	/** Saved form of the strongbox (ItemStack codec JSON). */
	List<JsonElement> strongboxJson = new ArrayList<>();
	transient List<ItemStack> strongbox = new ArrayList<>();

	UUID uuid() {
		return UUID.fromString(owner);
	}

	Rank rank() {
		return Rank.of(rep);
	}

	String newId(String prefix) {
		return prefix + (nextId++);
	}

	Agent agent(String id) {
		for (Agent a : agents) {
			if (a.id.equals(id)) {
				return a;
			}
		}
		return null;
	}

	Loan loan(String id) {
		for (Loan l : loans) {
			if (l.id.equals(id)) {
				return l;
			}
		}
		return null;
	}

	boolean buff(String name, long today) {
		Long until = buffs.get(name);
		return until != null && until >= today;
	}

	/** Adds a line of news (keeps the last 30). */
	void news(String line) {
		news.add(line);
		while (news.size() > 30) {
			news.remove(0);
		}
	}

	/** Wages for everyone on the books, in cents: a Company House takes 10% off per tier. */
	long dailyWages() {
		long n = 0;
		for (Agent a : agents) {
			n += a.wage;
		}
		return Math.round(n * (1 - 0.1 * Math.max(0, Math.min(3, house))));
	}

	/** Jobs at once: the rank's, plus one with a Company House. */
	int slots() {
		return rank().slots + (house > 0 ? 1 : 0);
	}

	/** Agents on the books: the rank's, plus one per Company House tier. */
	int maxAgents() {
		return rank().agents + Math.max(0, Math.min(3, house));
	}

	int pendingTelegrams() {
		int n = 0;
		for (Job j : active) {
			if (j.pendingTelegram()) {
				n++;
			}
		}
		return n;
	}
}
