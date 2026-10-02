package com.thatcoffeelock.havana;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Tobacco plants. A young plant is a vanilla potato crop at a remembered spot, so it grows with random ticks and
 * bone meal like any crop. Once it's fully grown it shoots up into a two-block large fern. Breaking either is
 * handled here (leaves and seeds instead of potatoes). If something else knocks the plant over (trampling, water,
 * a piston), the potatoes or seeds it drops are swapped for tobacco seeds as they spawn.
 */
final class Crops {
	private static final Random RANDOM = new Random();
	/** Chance that breaking grass or a fern drops tobacco seeds. */
	static final double SEED_CHANCE = 1.0 / 12;
	private static final Set<String> GRASS = Set.of("short_grass", "tall_grass", "fern", "large_fern", "bush");
	private static final Set<String> CROP_DROPS = Set.of("minecraft:potato", "minecraft:poisonous_potato", "minecraft:wheat_seeds");

	private Crops() {
	}

	static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	private static boolean isYoung(BlockState state) {
		return blockId(state).equals("potatoes");
	}

	private static boolean isGrown(BlockState state) {
		return blockId(state).equals("large_fern");
	}

	static boolean isPlant(BlockState state) {
		return isYoung(state) || isGrown(state);
	}

	/** Fully grown: the large fern, or a ripe crop that had no room to shoot up. */
	static boolean isRipe(BlockState state) {
		return isGrown(state) || state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	// ---------------------------------------------------------------- planting

	/** Right-click with Tobacco Seeds: plants on farmland. Anywhere else the seeds do nothing (no beetroot!). */
	static InteractionResult useSeeds(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!HavanaItems.isSeeds(held)) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!blockId(level.getBlockState(target.below())).equals("farmland") || !level.getBlockState(target).canBeReplaced()) {
			if (blockId(level.getBlockState(clicked)).equals("farmland") && hit.getDirection() != Direction.UP) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}
		if (!plant(level, target)) {
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:item.crop.plant", target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1f, 0.8f);
		return InteractionResult.SUCCESS;
	}

	static boolean plant(ServerLevel level, BlockPos pos) {
		Cmd.setblock(level, pos, "minecraft:potatoes[age=0]");
		if (!isYoung(level.getBlockState(pos))) {
			return false;
		}
		HavanaStore.addCrop(level, pos);
		return true;
	}

	// ---------------------------------------------------------------- growing

	/** Every 2 seconds: ripe crops shoot up, plants that are gone are forgotten. */
	static void scan(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (long packed : HavanaStore.crops(level)) {
				BlockPos pos = BlockPos.of(packed);
				if (!level.isLoaded(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (isGrown(state)) {
					continue;
				}
				if (!isYoung(state)) {
					HavanaStore.removeCrop(level, pos);
				} else if (isRipe(state) && level.getBlockState(pos.above()).isAir()) {
					shootUp(level, pos);
				}
			}
		}
	}

	/** A ripe crop becomes a two-block tobacco plant. */
	static void shootUp(ServerLevel level, BlockPos pos) {
		Cmd.setblock(level, pos.above(), "minecraft:large_fern[half=upper]");
		Cmd.setblock(level, pos, "minecraft:large_fern[half=lower]");
		Cmd.particles(level, "minecraft:happy_villager", pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0.4, 0, 6);
		Cmd.sound(level, "minecraft:block.grass.place", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.7f, 0.7f);
	}

	// ---------------------------------------------------------------- harvesting

	/** The block the plant is remembered by (its bottom), if this is a tobacco plant. */
	static @Nullable BlockPos plantAt(ServerLevel level, BlockPos pos, BlockState state) {
		if (isPlant(state) && HavanaStore.hasCrop(level, pos)) {
			return pos;
		}
		if (isGrown(state) && HavanaStore.hasCrop(level, pos.below()) && isGrown(level.getBlockState(pos.below()))) {
			return pos.below();
		}
		return null;
	}

	/** Player breaks a tobacco plant: our drops, not vanilla's. Returns false to cancel the vanilla break. */
	static boolean beforeBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		BlockPos base = plantAt(level, pos, state);
		if (base == null) {
			return true;
		}
		boolean ripe = isRipe(level.getBlockState(base));
		harvest(level, base, !player.isCreative());
		if (!ripe && player instanceof ServerPlayer sp) {
			sp.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("Not ripe yet. You get your seed back, impatient one.").withStyle(ChatFormatting.YELLOW)));
		}
		return false;
	}

	/** Removes the plant and returns (and, if asked, drops) what it gives: leaves and seeds if ripe, else the seed back. */
	static List<ItemStack> harvest(ServerLevel level, BlockPos base, boolean drop) {
		boolean ripe = isRipe(level.getBlockState(base));
		HavanaStore.removeCrop(level, base);
		if (isGrown(level.getBlockState(base.above()))) {
			level.removeBlock(base.above(), false);
		}
		level.removeBlock(base, false);
		List<ItemStack> loot = new ArrayList<>();
		if (ripe) {
			loot.add(HavanaItems.leaf(3 + RANDOM.nextInt(3)));
			loot.add(HavanaItems.seeds(1 + RANDOM.nextInt(2)));
		} else {
			loot.add(HavanaItems.seeds(1));
		}
		if (drop) {
			for (ItemStack stack : loot) {
				Block.popResource(level, base, stack.copy());
			}
		}
		Cmd.sound(level, "minecraft:block.grass.break", base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5, 1f, 0.9f);
		return loot;
	}

	/** Breaking grass now and then turns up tobacco seeds, the way it does wheat seeds. */
	static void afterBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		if (!player.isCreative() && GRASS.contains(blockId(state)) && RANDOM.nextDouble() < SEED_CHANCE) {
			Block.popResource(level, pos, HavanaItems.seeds(1));
		}
	}

	/**
	 * A tobacco plant knocked over by something other than a player (trampled farmland, water, a piston) drops its
	 * vanilla loot: potatoes, or wheat seeds for the fern. Swap those for a tobacco seed as they spawn.
	 */
	static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof ItemEntity item) || !CROP_DROPS.contains(HavanaItems.id(item.getItem().getItem()))) {
			return;
		}
		BlockPos pos = item.blockPosition();
		for (BlockPos at : new BlockPos[] {pos, pos.below()}) {
			if (HavanaStore.hasCrop(level, at) && !isPlant(level.getBlockState(at))) {
				item.setItem(HavanaItems.seeds(1));
				return;
			}
		}
	}
}
