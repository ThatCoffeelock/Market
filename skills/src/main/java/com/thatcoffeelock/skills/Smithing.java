package com.thatcoffeelock.skills;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Blacksmithing's durability saving, called from ItemStackMixin whenever a player's item would take wear. */
public final class Smithing {
	private Smithing() {
	}

	public static int wear(@Nullable ServerPlayer player, int amount) {
		if (player == null || amount <= 0) {
			return amount;
		}
		double save = Math.min(0.5, Skills.passive(player, Skill.BLACKSMITHING) + Skills.perk(player, Perk.TEMPERED));
		if (save <= 0) {
			return amount;
		}
		int kept = 0;
		for (int i = 0; i < amount; i++) {
			if (!Skills.roll(save)) {
				kept++;
			}
		}
		return kept;
	}
}
