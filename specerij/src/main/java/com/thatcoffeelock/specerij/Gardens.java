package com.thatcoffeelock.specerij;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Spice gardens. A cutting planted in its home biome (or a {@link Hortus}) is a short grass sprout at a remembered
 * spot. Once it has grown {@link SpiceConfig#growTicks} it turns into its ripe plant. Right-click a ripe plant to pick
 * it: it gives fresh spice and grows back. Breaking it digs it up: the cutting back, plus the harvest if it was ripe.
 * No bone meal.
 *
 * Wild cuttings: breaking grass, ferns and bushes in a spice's home biome now and then turns one up.
 */
final class Gardens {
	private static final Random RANDOM = new Random();
	private static final Set<String> SOIL = Set.of("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "moss_block", "mud");
	private static final Set<String> WILD = Set.of("short_grass", "tall_grass", "fern", "large_fern", "bush", "dead_bush");
	private static final String SPROUT = "short_grass";
	/** What a dispenser's bone meal turns grass and ferns into. We turn them back. */
	private static final Set<String> BONE_MEALED = Set.of("tall_grass", "large_fern");

	private Gardens() {
	}

	static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	static boolean isRipe(BlockState state, Spice spice) {
		return ("minecraft:" + blockId(state)).equals(spice.ripeBlock());
	}

	// ---------------------------------------------------------------- planting

	/** Right-click with a cutting: plants it, if the climate is right. */
	static InteractionResult useCutting(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		Spice spice = SpiceItems.cuttingOf(held);
		if (spice == null) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		if (plantAt(level, clicked, level.getBlockState(clicked)) != null) {
			return InteractionResult.PASS; // picking, handled by usePlant
		}
		BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!SOIL.contains(blockId(level.getBlockState(target.below()))) || !level.getBlockState(target).canBeReplaced()) {
			if (SOIL.contains(blockId(level.getBlockState(clicked))) && hit.getDirection() != Direction.UP) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}
		if (Hortus.rate(level, target, spice) <= 0) {
			String here = Hortus.biome(level, target);
			actionBar(player, spice.title + " won't grow in " + here.substring(here.indexOf(':') + 1).replace('_', ' ')
				+ ". It wants " + spice.homeNames() + ", or a Hortus.", ChatFormatting.YELLOW);
			return InteractionResult.FAIL;
		}
		if (!plant(level, target, spice)) {
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		if (!Hortus.isHome(level, target, spice)) {
			actionBar(player, "Planted in the Hortus. Far from home, it grows at half speed.", ChatFormatting.GREEN);
		}
		Cmd.sound(level, "minecraft:item.crop.plant", target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1f, 1f);
		return InteractionResult.SUCCESS;
	}

	static boolean plant(ServerLevel level, BlockPos pos, Spice spice) {
		Cmd.setblock(level, pos, "minecraft:" + SPROUT);
		if (!blockId(level.getBlockState(pos)).equals(SPROUT)) {
			return false;
		}
		SpiceStore.addPlant(level, pos, new SpiceStore.Plant(spice, level.getGameTime()));
		return true;
	}

	/** Bone meal on a spice plant does nothing: no shortcuts to the Spice Islands. */
	static InteractionResult useBoneMeal(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() || !SpiceItems.id(held.getItem()).equals("minecraft:bone_meal")) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (plantAt(level, pos, level.getBlockState(pos)) == null) {
			return InteractionResult.PASS;
		}
		actionBar(player, "Spices don't do shortcuts. Wait for it.", ChatFormatting.YELLOW);
		return InteractionResult.FAIL;
	}

	/** Right-click a ripe plant (with anything but bone meal): pick it. It grows back. */
	static InteractionResult usePlant(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos base = plantAt(level, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()));
		if (base == null) {
			return InteractionResult.PASS;
		}
		SpiceStore.Plant plant = SpiceStore.plant(level, base);
		if (plant == null) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (!isRipe(level.getBlockState(base), plant.spice())) {
			double rate = Hortus.rate(level, base, plant.spice());
			int percent = (int) Math.min(99, plant.progress * 100 / SpiceConfig.get().growTicks);
			actionBar(player, plant.spice().title + ": " + percent + "% grown" + (rate <= 0 ? ", but it isn't growing here (the Hortus went cold?)"
				: rate < 1 ? " (in the Hortus, at half speed)" : "."), ChatFormatting.YELLOW);
			return InteractionResult.SUCCESS;
		}
		List<ItemStack> crop = pick(level, base, plant, player.getUUID());
		for (ItemStack stack : crop) {
			Block.popResource(level, base, stack);
		}
		SkillsLink.xp(player.getUUID(), "farming", 3);
		return InteractionResult.SUCCESS;
	}

	/** Picks a ripe plant: the fresh spice, and the plant starts growing again. */
	static List<ItemStack> pick(ServerLevel level, BlockPos base, SpiceStore.Plant plant, @Nullable java.util.UUID picker) {
		Spice spice = plant.spice();
		int n = spice.harvestMin + RANDOM.nextInt(spice.harvestMax - spice.harvestMin + 1);
		// Green Thumb (Farming): a little extra in the basket
		if (picker != null && SkillsLink.roll(picker, "farming/green_thumb")) {
			n += 1;
		}
		plant.progress = 0;
		plant.last = level.getGameTime();
		SpiceStore.changed();
		Cmd.setblock(level, base, "minecraft:" + SPROUT);
		Cmd.sound(level, "minecraft:block.sweet_berry_bush.pick_berries", base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5, 1f, 1f);
		List<ItemStack> out = new ArrayList<>();
		out.add(SpiceItems.fresh(spice, n));
		return out;
	}

	// ---------------------------------------------------------------- growing

	/** Every 2 seconds: plants grow by the climate they stand in, and beds that are gone leave their cutting. */
	static void scan(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (long packed : SpiceStore.plants(level)) {
				BlockPos pos = BlockPos.of(packed);
				SpiceStore.Plant plant = SpiceStore.plant(level, pos);
				if (plant == null || !level.isLoaded(pos)) {
					continue;
				}
				update(level, pos, plant);
			}
		}
	}

	static void update(ServerLevel level, BlockPos pos, SpiceStore.Plant plant) {
		Spice spice = plant.spice();
		BlockState state = level.getBlockState(pos);
		String id = blockId(state);
		long now = level.getGameTime();
		long elapsed = Math.max(0, now - plant.last);
		plant.last = now;
		if (BONE_MEALED.contains(id)) {
			// a dispenser got at it: trim it back to what it was
			Cmd.setblock(level, pos, plant.progress >= SpiceConfig.get().growTicks ? spice.ripe : "minecraft:" + SPROUT);
			if (BONE_MEALED.contains(blockId(level.getBlockState(pos.above())))) {
				level.removeBlock(pos.above(), false);
			}
			return;
		}
		if (id.equals(SPROUT)) {
			if (elapsed > 0) {
				plant.progress += elapsed * Hortus.rate(level, pos, spice);
				SpiceStore.changed();
			}
			if (plant.progress >= SpiceConfig.get().growTicks) {
				ripen(level, pos, spice);
			}
		} else if (!isRipe(state, spice)) {
			SpiceStore.removePlant(level, pos);
			Block.popResource(level, pos, SpiceItems.cutting(spice, 1));
		}
	}

	static void ripen(ServerLevel level, BlockPos pos, Spice spice) {
		Cmd.setblock(level, pos, spice.ripe);
		Cmd.particles(level, "minecraft:happy_villager", pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0.3, 0, 5);
	}

	// ---------------------------------------------------------------- digging up

	/** The spot the plant is remembered by, if this block is (part of) a spice plant. */
	static @Nullable BlockPos plantAt(ServerLevel level, BlockPos pos, BlockState state) {
		SpiceStore.Plant plant = SpiceStore.plant(level, pos);
		String id = blockId(state);
		if (plant != null && (id.equals(SPROUT) || BONE_MEALED.contains(id) || isRipe(state, plant.spice()))) {
			return pos;
		}
		if (BONE_MEALED.contains(id) && SpiceStore.plant(level, pos.below()) != null) {
			return pos.below();
		}
		return null;
	}

	/**
	 * A player breaks a block. A spice plant is dug up: the cutting back, and the harvest if it was ripe. Grass in a
	 * spice's home biome sometimes turns up a wild cutting. Returns false to cancel the vanilla break.
	 */
	static boolean beforeBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		BlockPos base = plantAt(level, pos, state);
		if (base == null) {
			return true;
		}
		SpiceStore.Plant plant = SpiceStore.plant(level, base);
		List<ItemStack> loot = dig(level, base, plant, player.getUUID());
		if (!player.isCreative()) {
			for (ItemStack stack : loot) {
				Block.popResource(level, base, stack);
			}
		}
		return false;
	}

	/** Digs the plant up. Returns the cutting, plus the fresh spice if it was ripe. */
	static List<ItemStack> dig(ServerLevel level, BlockPos base, SpiceStore.Plant plant, @Nullable java.util.UUID picker) {
		List<ItemStack> loot = new ArrayList<>();
		if (isRipe(level.getBlockState(base), plant.spice())) {
			loot.addAll(pick(level, base, plant, picker));
		}
		loot.add(SpiceItems.cutting(plant.spice(), 1));
		SpiceStore.removePlant(level, base);
		if (BONE_MEALED.contains(blockId(level.getBlockState(base.above())))) {
			level.removeBlock(base.above(), false);
		}
		level.removeBlock(base, false);
		return loot;
	}

	/** After a player breaks grass, a fern or a bush: in a spice's home biome, now and then a wild cutting. */
	static void afterBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		if (player.isCreative() || !WILD.contains(blockId(state)) || RANDOM.nextDouble() >= SpiceConfig.get().wildCuttingChance) {
			return;
		}
		Spice spice = Spice.forBiome(Hortus.biome(level, pos));
		if (spice == null) {
			return;
		}
		Block.popResource(level, pos, SpiceItems.cutting(spice, 1));
		if (player instanceof ServerPlayer sp) {
			actionBar(sp, "Among the undergrowth: a wild " + spice.title.toLowerCase(java.util.Locale.ROOT) + " cutting!", ChatFormatting.GREEN);
			SkillsLink.xp(sp.getUUID(), "farming", 5);
		}
	}
}
