package com.thatcoffeelock.skills;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Earning XP, levelling up, and looking up how good someone is at something. */
public final class Skills {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<String, Item> ITEMS = new HashMap<>();
	/** Last action-bar popup per player, so a fast miner doesn't get 20 packets a second. */
	private static final Map<UUID, Integer> LAST_POPUP = new HashMap<>();

	private Skills() {
	}

	// ---------------------------------------------------------------- lookups

	public static int level(ServerPlayer player, Skill skill) {
		return SkillsStore.of(player).level(skill);
	}

	public static int rank(ServerPlayer player, Perk perk) {
		return SkillsStore.of(player).rank(perk);
	}

	/** The passive bonus of a skill, as a fraction. */
	public static double passive(ServerPlayer player, Skill skill) {
		return skill.passive(level(player, skill));
	}

	/** A perk's effect at the player's rank (0 if they don't have it). */
	public static double perk(ServerPlayer player, Perk perk) {
		return perk.value(rank(player, perk));
	}

	public static boolean roll(double chance) {
		return chance > 0 && RANDOM.nextDouble() < chance;
	}

	public static RandomSource random() {
		return RANDOM;
	}

	// ---------------------------------------------------------------- XP

	/** Gives skill XP (after the config multipliers) and handles level-ups. */
	public static void award(ServerPlayer player, Skill skill, double amount) {
		if (amount <= 0 || player.isSpectator() || player.isCreative()) {
			return;
		}
		double gained = amount * SkillsConfig.get().multiplier(skill);
		if (gained <= 0) {
			return;
		}
		SkillsStore.Profile profile = SkillsStore.of(player);
		double before = profile.xp(skill);
		if (before >= Skill.totalFor(Skill.MAX_LEVEL)) {
			return;
		}
		int oldLevel = Skill.levelFor(before);
		double after = Math.min(before + gained, Skill.totalFor(Skill.MAX_LEVEL));
		profile.xp.put(skill.id(), after);
		SkillsStore.changed();
		int newLevel = Skill.levelFor(after);
		if (newLevel > oldLevel) {
			levelUp(player, skill, oldLevel, newLevel);
		} else {
			popup(player, skill, gained, after);
		}
	}

	private static void popup(ServerPlayer player, Skill skill, double gained, double total) {
		if (!SkillsConfig.get().xpPopups) {
			return;
		}
		int now = SkillsMod.ticks();
		Integer last = LAST_POPUP.get(player.getUUID());
		if (last != null && now - last < 4) {
			return;
		}
		LAST_POPUP.put(player.getUUID(), now);
		int level = Skill.levelFor(total);
		String bar = bar(Skill.progress(total), 10);
		Component line = Component.literal("+" + fmt(gained) + " " + skill.title).withStyle(skill.color)
			.append(Component.literal("  Lv " + level + " ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(bar).withStyle(ChatFormatting.DARK_GRAY));
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	private static void levelUp(ServerPlayer player, Skill skill, int oldLevel, int newLevel) {
		ServerLevel level = (ServerLevel) player.level();
		player.sendSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(skill.title + " ").withStyle(skill.color, ChatFormatting.BOLD))
			.append(Component.literal("is now level " + newLevel + ". ").withStyle(ChatFormatting.GOLD))
			.append(Component.literal("(" + skill.passiveText(newLevel) + ")").withStyle(ChatFormatting.GRAY)));
		Cmd.sound(player, "minecraft:entity.player.levelup", 0.6f, 1.2f);
		int points = newLevel / Skill.LEVELS_PER_POINT - oldLevel / Skill.LEVELS_PER_POINT;
		if (points > 0) {
			player.sendSystemMessage(Component.literal("  ➜ You earned a " + skill.title + " perk point! ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("Spend it with /skills").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)));
			Cmd.sound(player, "minecraft:ui.toast.challenge_complete", 0.5f, 1.4f);
		}
		if (SkillsConfig.get().announceMilestones && (oldLevel < 50 && newLevel >= 50 || oldLevel < 100 && newLevel >= 100)) {
			String text = newLevel >= 100
				? player.getName().getString() + " reached " + skill.title + " 100. Bow before the demigod."
				: player.getName().getString() + " reached " + skill.title + " 50. Halfway to legend.";
			level.getServer().getPlayerList().broadcastSystemMessage(
				Component.literal("★ " + text).withStyle(ChatFormatting.GOLD), false);
		}
		Boosts.refresh(player);
	}

	static String bar(double progress, int width) {
		int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * width);
		return "■".repeat(filled) + "□".repeat(width - filled);
	}

	static String fmt(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	// ---------------------------------------------------------------- items

	/** Item by id ("iron_ingot" or "minecraft:iron_ingot"), or paper if it doesn't exist in this version. */
	public static Item item(String id) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id.contains(":") ? id : "minecraft:" + id);
		return item == null || item == Items.AIR ? Items.PAPER : item;
	}

	public static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	/** Into the inventory, or dropped at their feet if it's full. */
	public static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}

	static void forget(UUID player) {
		LAST_POPUP.remove(player);
	}
}
