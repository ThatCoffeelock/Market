package com.thatcoffeelock.skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Brewing stands brew on their own, with nobody holding them, so the XP goes to the last player who opened the
 * stand. Called from BrewingStandMixin around vanilla's doBrew.
 */
public final class Brewing {
	/** Dimension + position -> last player to open that brewing stand. */
	private static final Map<String, UUID> BREWERS = new HashMap<>();
	/** Brews seen, for the smoke test (proves the mixin is live). */
	static int brews;

	private static ItemStack ingredient = ItemStack.EMPTY;
	private static int bottles;

	private Brewing() {
	}

	private static String key(Level level, BlockPos pos) {
		return level.dimension().toString() + "@" + pos.asLong();
	}

	static void opened(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BREWERS.put(key(level, pos), player.getUUID());
	}

	public static void beforeBrew(Level level, BlockPos pos, Container items) {
		ingredient = items.getItem(3).copy();
		bottles = 0;
		for (int i = 0; i < 3; i++) {
			if (!items.getItem(i).isEmpty()) {
				bottles++;
			}
		}
	}

	public static void afterBrew(Level level, BlockPos pos, Container items) {
		brews++;
		if (!(level instanceof ServerLevel server) || ingredient.isEmpty()) {
			return;
		}
		UUID uuid = BREWERS.get(key(level, pos));
		ServerPlayer player = uuid == null ? null : server.getServer().getPlayerList().getPlayer(uuid);
		if (player == null) {
			return;
		}
		Skills.award(player, Skill.BREWING, 12.0 * bottles);
		if (!Skills.roll(Skills.passive(player, Skill.BREWING) + Skills.perk(player, Perk.THRIFTY_ALCHEMIST))) {
			return;
		}
		ItemStack now = items.getItem(3);
		if (now.isEmpty()) {
			items.setItem(3, ingredient.copyWithCount(1));
		} else if (ItemStack.isSameItemSameComponents(now, ingredient) && now.getCount() < now.getMaxStackSize()) {
			now.grow(1);
			items.setChanged();
		} else {
			return; // the slot holds something else now (an ingredient's leftover), leave it be
		}
		Cmd.particles(server, "minecraft:witch", pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0.2, 6);
	}
}
