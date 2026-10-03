package com.thatcoffeelock.skills;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/** Mining, Woodcutting, Excavation and Farming: everything that happens when a player breaks a block. */
final class Gathering {
	/** XP per natural block for Mining. Ores not listed here (e.g. from other mods) give 5. */
	private static final Map<String, Double> MINING = Map.ofEntries(
		Map.entry("stone", 0.5), Map.entry("deepslate", 0.5), Map.entry("granite", 0.5), Map.entry("diorite", 0.5),
		Map.entry("andesite", 0.5), Map.entry("tuff", 0.5), Map.entry("calcite", 0.5), Map.entry("dripstone_block", 0.5),
		Map.entry("sandstone", 0.5), Map.entry("red_sandstone", 0.5), Map.entry("terracotta", 0.5),
		Map.entry("netherrack", 0.25), Map.entry("basalt", 0.5), Map.entry("smooth_basalt", 0.5), Map.entry("blackstone", 0.5),
		Map.entry("end_stone", 0.5), Map.entry("magma_block", 1.0), Map.entry("obsidian", 3.0),
		Map.entry("amethyst_block", 1.0), Map.entry("amethyst_cluster", 4.0),
		Map.entry("coal_ore", 6.0), Map.entry("deepslate_coal_ore", 6.0),
		Map.entry("copper_ore", 5.0), Map.entry("deepslate_copper_ore", 5.0),
		Map.entry("iron_ore", 10.0), Map.entry("deepslate_iron_ore", 10.0),
		Map.entry("redstone_ore", 8.0), Map.entry("deepslate_redstone_ore", 8.0),
		Map.entry("lapis_ore", 10.0), Map.entry("deepslate_lapis_ore", 10.0),
		Map.entry("gold_ore", 15.0), Map.entry("deepslate_gold_ore", 15.0), Map.entry("nether_gold_ore", 5.0),
		Map.entry("nether_quartz_ore", 6.0),
		Map.entry("diamond_ore", 40.0), Map.entry("deepslate_diamond_ore", 40.0),
		Map.entry("emerald_ore", 50.0), Map.entry("deepslate_emerald_ore", 50.0),
		Map.entry("ancient_debris", 60.0));

	/** XP per natural block for Excavation. */
	private static final Map<String, Double> DIGGING = Map.ofEntries(
		Map.entry("dirt", 1.0), Map.entry("grass_block", 1.0), Map.entry("coarse_dirt", 1.0), Map.entry("podzol", 1.0),
		Map.entry("mycelium", 1.0), Map.entry("rooted_dirt", 1.0), Map.entry("mud", 1.0), Map.entry("sand", 1.0),
		Map.entry("red_sand", 1.0), Map.entry("gravel", 1.0), Map.entry("clay", 3.0), Map.entry("soul_sand", 1.5),
		Map.entry("soul_soil", 1.5), Map.entry("snow_block", 0.5));

	/** Crop id -> its age when ripe isn't needed: we read the "age" property's max. */
	private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa",
		"torchflower_crop");
	private static final Set<String> GOURDS = Set.of("melon", "pumpkin");
	/** Crops Green Thumb can replant by setting the block back to age 0 (cocoa needs a facing, so not cocoa). */
	private static final Set<String> REPLANTABLE = Set.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart",
		"torchflower_crop");
	private static final Set<String> SOIL = Set.of("dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt", "mud",
		"mycelium", "moss_block", "muddy_mangrove_roots");

	/** Excavation finds: item id and weight. */
	private static final String[][] FINDS = {
		{"flint", "30"}, {"clay_ball", "20"}, {"bone", "15"}, {"gold_nugget", "12"}, {"iron_nugget", "10"},
		{"amethyst_shard", "5"}, {"emerald", "4"}, {"name_tag", "2"}, {"diamond", "1"}, {"music_disc_13", "1"},
	};

	/** True while Vein Miner or Timber is breaking extra blocks, so they don't chain off each other. */
	private static boolean chaining;

	private Gathering() {
	}

	static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	private static boolean isOre(String id) {
		return id.endsWith("_ore") || id.equals("ancient_debris");
	}

	private static double miningXp(String id) {
		Double xp = MINING.get(id);
		if (xp != null) {
			return xp;
		}
		return isOre(id) ? 5.0 : 0.0;
	}

	private static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	/** Blocks worth remembering when a player places them: the ones that give gathering XP. */
	static boolean tracked(BlockState state) {
		String id = id(state);
		return miningXp(id) > 0 || DIGGING.containsKey(id) || GOURDS.contains(id) || isLog(state);
	}

	// ---------------------------------------------------------------- breaking

	static void afterBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		boolean placed = Placed.remove(level, pos);
		if (placed || player.isCreative() || player.isSpectator()) {
			return;
		}
		String id = id(state);

		double mining = miningXp(id);
		if (mining > 0) {
			Skills.award(player, Skill.MINING, mining);
			if (isOre(id)) {
				if (Skills.roll(Skills.perk(player, Perk.PROSPECTOR))) {
					extraDrops(level, player, pos, state, blockEntity, true);
				}
				if (!chaining && player.isShiftKeyDown()) {
					int max = (int) Skills.perk(player, Perk.VEIN_MINER);
					if (max > 0) {
						chain(level, player, pos, max, false, s -> id(s).equals(id));
					}
				}
			}
			return;
		}

		if (isLog(state)) {
			Skills.award(player, Skill.WOODCUTTING, 4);
			if (Skills.roll(Skills.perk(player, Perk.LUMBERJACK))) {
				extraDrops(level, player, pos, state, blockEntity, false);
			}
			if (!chaining) {
				replantSapling(level, player, pos, id);
				if (player.isShiftKeyDown()) {
					int max = (int) Skills.perk(player, Perk.TIMBER);
					if (max > 0) {
						chain(level, player, pos, max, true, Gathering::isLog);
					}
				}
			}
			return;
		}

		Double digging = DIGGING.get(id);
		if (digging != null) {
			Skills.award(player, Skill.EXCAVATION, digging);
			if (Skills.roll(Skills.perk(player, Perk.BULK_DIG))) {
				extraDrops(level, player, pos, state, blockEntity, false);
			}
			if (Skills.roll(Skills.perk(player, Perk.TREASURE_HUNTER))) {
				Item find = Skills.item(pickFind());
				Block.popResource(level, pos, new ItemStack(find));
				Cmd.sound(player, "minecraft:entity.experience_orb.pickup", 0.5f, 0.7f);
				Cmd.particles(level, "minecraft:happy_villager", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.3, 8);
			}
			return;
		}

		boolean ripeCrop = CROPS.contains(id) && ripe(state);
		if (ripeCrop || GOURDS.contains(id)) {
			Skills.award(player, Skill.FARMING, ripeCrop ? 4 : 5);
			if (Skills.roll(Skills.passive(player, Skill.FARMING))) {
				extraDrops(level, player, pos, state, blockEntity, false);
			}
			if (ripeCrop && REPLANTABLE.contains(id) && Skills.roll(Skills.perk(player, Perk.GREEN_THUMB))
				&& !(id.equals("potatoes") && FabricLoader.getInstance().isModLoaded("havana"))) {
				// a tick later, so we don't fight vanilla (or Havana) over the block
				SkillsMod.nextTick(() -> {
					if (level.getBlockState(pos).isAir()) {
						Cmd.setblock(level, pos, "minecraft:" + id);
					}
				});
			}
		}
	}

	/** Whether the block's "age" property is at its maximum. */
	private static boolean ripe(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("age")) {
				int max = 0;
				for (Object value : property.getPossibleValues()) {
					if (value instanceof Integer i) {
						max = Math.max(max, i);
					}
				}
				Object now = state.getValue(property);
				return now instanceof Integer i && i >= max;
			}
		}
		return false;
	}

	/** Drops the block's loot a second time. For ores, never the ore block itself (no silk-touch duplication). */
	private static void extraDrops(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
								   @Nullable BlockEntity blockEntity, boolean ore) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, player, player.getMainHandItem());
		Item self = state.getBlock().asItem();
		for (ItemStack drop : drops) {
			if (ore && drop.is(self)) {
				continue;
			}
			Block.popResource(level, pos, drop.copy());
		}
	}

	private static String pickFind() {
		int total = 0;
		for (String[] find : FINDS) {
			total += Integer.parseInt(find[1]);
		}
		int roll = Skills.random().nextInt(total);
		for (String[] find : FINDS) {
			roll -= Integer.parseInt(find[1]);
			if (roll < 0) {
				return find[0];
			}
		}
		return FINDS[0][0];
	}

	/** Forester: chopping the bottom log of a tree can put a sapling back. */
	private static void replantSapling(ServerLevel level, ServerPlayer player, BlockPos pos, String id) {
		if (!id.endsWith("_log") || id.startsWith("stripped_")) {
			return;
		}
		if (!SOIL.contains(id(level.getBlockState(pos.below())))) {
			return;
		}
		if (!Skills.roll(Skills.perk(player, Perk.FORESTER))) {
			return;
		}
		String wood = id.substring(0, id.length() - "_log".length());
		String sapling = wood.equals("mangrove") ? "mangrove_propagule" : wood + "_sapling";
		if (!BuiltInRegistries.BLOCK.containsKey(Identifier.withDefaultNamespace(sapling))) {
			return;
		}
		SkillsMod.nextTick(() -> {
			if (level.getBlockState(pos).isAir()) {
				Cmd.setblock(level, pos, "minecraft:" + sapling);
			}
		});
	}

	/**
	 * Vein Miner and Timber: breaks up to max more matching blocks connected to the first one, as if the player broke
	 * them (so the tool wears, and each block gives its own XP and drops). Timber only goes up, never sideways into
	 * the next tree's trunk below you.
	 */
	private static void chain(ServerLevel level, ServerPlayer player, BlockPos start, int max, boolean upOnly,
							  java.util.function.Predicate<BlockState> matches) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty() && found.size() < max) {
			BlockPos at = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = upOnly ? 0 : -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = at.offset(dx, dy, dz);
						if (upOnly && next.getY() <= start.getY() || !seen.add(next) || found.size() >= max) {
							continue;
						}
						if (matches.test(level.getBlockState(next))) {
							found.add(next);
							queue.add(next);
						}
					}
				}
			}
		}
		if (found.isEmpty()) {
			return;
		}
		Item tool = player.getMainHandItem().getItem();
		chaining = true;
		try {
			for (BlockPos pos : found) {
				// stop when the tool breaks (or the player swapped it)
				if (player.getMainHandItem().isEmpty() || player.getMainHandItem().getItem() != tool) {
					break;
				}
				player.gameMode.destroyBlock(pos);
			}
		} finally {
			chaining = false;
		}
	}
}
