package com.thatcoffeelock.skills;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Treasure Hunting. A structure chest (or barrel, or minecart) that still has its loot table hasn't been opened by
 * anyone yet: opening it gives XP, a chance (the passive) that one of its stacks comes out one bigger, and with Gilded
 * Find a few hidden coins. Riches relics add their own XP and use Relic Hunter and Patron of the Arts through the
 * skills API.
 */
final class Treasure {
	private Treasure() {
	}

	/** Does this block still hold an unrolled loot table? */
	static boolean unopened(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof RandomizableContainer c && c.getLootTable() != null;
	}

	/** A player is about to open this block. Called before vanilla opens it (and rolls the loot). */
	static void opening(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (player.isCreative() || !unopened(level, pos)) {
			return;
		}
		Skills.award(player, Skill.TREASURE_HUNTING, 15);
		boolean bonus = Skills.roll(Skills.passive(player, Skill.TREASURE_HUNTING));
		boolean gilded = Skills.roll(Skills.perk(player, Perk.GILDED_FIND));
		if (!bonus && !gilded) {
			return;
		}
		// the loot is rolled as the chest opens, so look at it at the end of the tick
		SkillsMod.nextTick(() -> {
			if (!(level.getBlockEntity(pos) instanceof Container box)) {
				return;
			}
			if (bonus) {
				grow(box);
			}
			if (gilded) {
				ItemStack coins = switch (Skills.random().nextInt(3)) {
					case 0 -> new ItemStack(Items.GOLD_NUGGET, 4 + Skills.random().nextInt(6));
					case 1 -> new ItemStack(Items.GOLD_INGOT, 1 + Skills.random().nextInt(2));
					default -> new ItemStack(Items.EMERALD, 1 + Skills.random().nextInt(3));
				};
				Skills.give(player, coins);
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Gilded Find: "
					+ coins.getCount() + " × " + coins.getHoverName().getString() + " tucked in a corner of the chest.").withStyle(ChatFormatting.GOLD)));
			}
		});
	}

	/** One random stack in the box grows by one (if it can). Returns whether one did. */
	static boolean grow(Container box) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack s = box.getItem(i);
			if (!s.isEmpty() && s.getCount() < s.getMaxStackSize()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return false;
		}
		box.getItem(slots.get(Skills.random().nextInt(slots.size()))).grow(1);
		box.setChanged();
		return true;
	}
}
