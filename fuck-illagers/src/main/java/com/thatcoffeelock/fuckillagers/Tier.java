package com.thatcoffeelock.fuckillagers;

import java.util.List;

import net.minecraft.ChatFormatting;

/** How hard a contract is: what the target hides in, how tough the boss is, and what the skull is worth. */
enum Tier {
	EASY("Easy", 150, 40, ChatFormatting.GREEN, List.of(Site.WAGON, Site.TOWER)),
	MEDIUM("Medium", 400, 80, ChatFormatting.GOLD, List.of(Site.CAMP, Site.FORTRESS)),
	HARD("Hard", 1000, 140, ChatFormatting.RED, List.of(Site.DUNGEON, Site.CASTLE));

	final String label;
	/** Reward for the skull, in Marks. */
	final double reward;
	/** The boss's health (a player has 20). */
	final double bossHealth;
	final ChatFormatting color;
	final List<Site> sites;

	Tier(String label, double reward, double bossHealth, ChatFormatting color, List<Site> sites) {
		this.label = label;
		this.reward = reward;
		this.bossHealth = bossHealth;
		this.color = color;
		this.sites = sites;
	}

	static Tier byName(String name) {
		for (Tier tier : values()) {
			if (tier.name().equalsIgnoreCase(name)) {
				return tier;
			}
		}
		return EASY;
	}

	/** Where targets hide. Each builds itself in {@link Sites}. */
	enum Site {
		WAGON("a wagon", "A caravan wagon on the road, a few pillagers riding guard."),
		TOWER("a watchtower", "A lookout tower. The target likes the view from the top."),
		CAMP("a war camp", "Tents, a campfire, a prisoner in a cage, and a lot of axes."),
		FORTRESS("a fortress", "Stone walls, corner towers and a keep. Crossbows on the walls."),
		DUNGEON("a dungeon", "A crypt on the surface, a ladder down, and a hall full of illagers. Bring torches."),
		CASTLE("a castle", "Curtain walls, four towers, a keep, a ravager in the yard. The boss waits upstairs.");

		final String what;
		final String blurb;

		Site(String what, String blurb) {
			this.what = what;
			this.blurb = blurb;
		}

		static Site byName(String name) {
			for (Site site : values()) {
				if (site.name().equalsIgnoreCase(name)) {
					return site;
				}
			}
			return WAGON;
		}
	}
}
