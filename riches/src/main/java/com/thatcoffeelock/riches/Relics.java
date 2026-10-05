package com.thatcoffeelock.riches;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Finding relics, remembering who found what, and paying out for complete collections. */
final class Relics {
	/** Who found a relic first, and when. */
	record Found(String uuid, String name, String date) {
	}

	static final Map<String, Found> FOUND = new LinkedHashMap<>();
	/** "uuid:COLLECTION" for every collection reward already paid. */
	static final Set<String> REWARDED = new HashSet<>();

	private Relics() {
	}

	static void reset() {
		FOUND.clear();
		REWARDED.clear();
	}

	static boolean isFound(Relic relic) {
		return FOUND.containsKey(relic.id());
	}

	/**
	 * Rolls for a relic. Returns it (and records the find) if the dice say yes and it's still out there, else null.
	 * {@code force} skips the dice (tests and admins).
	 */
	static @Nullable ItemStack roll(Relic relic, ServerPlayer player, boolean force) {
		RichesConfig c = RichesConfig.get();
		if (!available(relic)) {
			return null;
		}
		// Treasure Hunting (Skills mod): Relic Hunter makes every relic a bit likelier
		double hunter = 1.0 + SkillsLink.bonus(player.getUUID(), "treasure_hunting/relic_hunter");
		if (!force && Math.random() >= relic.chance * c.relicChanceMultiplier * hunter) {
			return null;
		}
		found(relic, player);
		return RichesItems.relic(relic);
	}

	/** Can this relic still turn up? (Always, unless relics are unique and someone already has it.) */
	static boolean available(Relic relic) {
		return !(RichesConfig.get().uniqueRelics && isFound(relic));
	}

	/** Writes down who found it. The first finder stays on record. */
	static void record(Relic relic, String uuid, String name) {
		FOUND.putIfAbsent(relic.id(), new Found(uuid, name, LocalDate.now().toString()));
		Store.changed();
	}

	static void found(Relic relic, ServerPlayer player) {
		record(relic, player.getUUID().toString(), player.getName().getString());
		SkillsLink.xp(player.getUUID(), "treasure_hunting", 200);
		int n = FOUND.size();
		((ServerLevel) player.level()).getServer().getPlayerList().broadcastSystemMessage(Component.literal("★ ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(player.getName().getString() + " found ").withStyle(ChatFormatting.YELLOW))
			.append(Component.literal(relic.title).withStyle(relic.collection.color, ChatFormatting.BOLD))
			.append(Component.literal(" (" + n + " of " + Relic.values().length + " relics found)").withStyle(ChatFormatting.GRAY)), false);
		Cmd.sound((ServerLevel) player.level(), "minecraft:ui.toast.challenge_complete", player.getX(), player.getY() + 1, player.getZ(), 0.8f, 1.0f);
	}

	// ---------------------------------------------------------------- where they turn up

	/** A player killed something. */
	static void killed(LivingEntity dead, @Nullable Entity killer) {
		if (!(killer instanceof ServerPlayer player) || player.isCreative() || !(dead.level() instanceof ServerLevel level)) {
			return;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).getPath();
		for (Relic relic : Relic.values()) {
			if (relic.source == Relic.Source.KILL && relic.what.equals(type)) {
				ItemStack drop = roll(relic, player, false);
				if (drop != null) {
					Block.popResource(level, dead.blockPosition(), drop);
				}
			}
		}
	}

	/** A player mined a block. */
	static void mined(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (player.isCreative()) {
			return;
		}
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		for (Relic relic : Relic.values()) {
			if (relic.source == Relic.Source.MINE && relic.what.equals(id)) {
				ItemStack drop = roll(relic, player, false);
				if (drop != null) {
					Block.popResource(level, pos, drop);
				}
			}
		}
	}

	/** The loot table a chest still holds (it's gone once the chest has been opened), or "" for none. */
	static String lootKey(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof RandomizableContainer container && container.getLootTable() != null) {
			return container.getLootTable().toString();
		}
		return "";
	}

	/** A player is about to open a container. If it's a structure chest nobody has opened yet, maybe there's a relic. */
	static void opening(ServerLevel level, ServerPlayer player, BlockPos pos) {
		String loot = lootKey(level, pos);
		if (loot.isEmpty() || player.isCreative()) {
			return;
		}
		for (Relic relic : Relic.values()) {
			if (relic.source == Relic.Source.CHEST && loot.contains(relic.what)) {
				ItemStack drop = roll(relic, player, false);
				if (drop != null) {
					RichesItems.give(player, drop);
					player.sendSystemMessage(Component.literal("Tucked behind the loot, you find something old. Very old.").withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}

	// ---------------------------------------------------------------- collections

	/** Relics this owner has on show in their own cases. */
	static Set<Relic> onShow(String owner) {
		Set<Relic> shown = EnumSet.noneOf(Relic.class);
		for (Places.Showcase s : Places.SHOWCASES.values()) {
			Relic r = RichesItems.relicOf(s.item);
			if (r != null && s.owner.equals(owner)) {
				shown.add(r);
			}
		}
		return shown;
	}

	/** Pays out (once) for every collection this owner now shows in full. */
	static void checkCollections(ServerLevel level, String owner, String ownerName) {
		if (owner.isEmpty()) {
			return;
		}
		Set<Relic> shown = onShow(owner);
		for (Relic.Collection c : Relic.Collection.values()) {
			if (!shown.containsAll(c.relics()) || !REWARDED.add(owner + ":" + c.name())) {
				continue;
			}
			Store.changed();
			UUID id;
			try {
				id = UUID.fromString(owner);
			} catch (IllegalArgumentException e) {
				continue;
			}
			// Treasure Hunting (Skills mod): Patron of the Arts raises the reward
			long reward = Math.round(Bank.cents(RichesConfig.get().collectionReward) * (1.0 + SkillsLink.bonus(id, "treasure_hunting/patron")));
			Bank.credit(id, ownerName, reward);
			SkillsLink.xp(id, "treasure_hunting", 500);
			level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("🏛 ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(ownerName + " completed ").withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(c.title).withStyle(c.color, ChatFormatting.BOLD))
				.append(Component.literal("! The Royal Society awards " + Bank.format(reward) + ".").withStyle(ChatFormatting.YELLOW)), false);
		}
	}
}
