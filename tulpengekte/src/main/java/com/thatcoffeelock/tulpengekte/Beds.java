package com.thatcoffeelock.tulpengekte;

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
 * Tulip beds. A planted bulb is a short grass sprout at a remembered spot. After {@link TulpConfig#growTicks} it
 * flowers as the vanilla tulip of its color. Lifting it (breaking it) gives the bulb back, and a flowering one gives
 * offsets too, which now and then "break" into a rarer variety. No bone meal: patience is the price.
 *
 * Anything else that knocks a tulip over (water, a piston, an explosion) leaves the bulb lying there, without offsets.
 */
final class Beds {
	private static final Random RANDOM = new Random();
	private static final Set<String> SOIL = Set.of("grass_block", "dirt", "coarse_dirt", "podzol", "farmland", "rooted_dirt");
	private static final String SPROUT = "short_grass";
	/** What a dispenser's bone meal turns a sprout into. We turn it back. */
	private static final String BONE_MEALED = "tall_grass";

	private Beds() {
	}

	static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	/** One of the four vanilla tulips. */
	static boolean isTulip(BlockState state) {
		String id = blockId(state);
		return id.equals("red_tulip") || id.equals("orange_tulip") || id.equals("white_tulip") || id.equals("pink_tulip");
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	// ---------------------------------------------------------------- planting

	/** Right-click with a bulb: plants it on grass, dirt or farmland. */
	static InteractionResult useBulb(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		Variety variety = TulpItems.variety(held);
		if (variety == null) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		if (plantAt(level, clicked, level.getBlockState(clicked)) != null) {
			actionBar(player, "Something's already growing there.", ChatFormatting.YELLOW);
			return InteractionResult.FAIL;
		}
		BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (!SOIL.contains(blockId(level.getBlockState(target.below()))) || !level.getBlockState(target).canBeReplaced()) {
			if (SOIL.contains(blockId(level.getBlockState(clicked))) && hit.getDirection() != Direction.UP) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}
		if (!plant(level, target, variety)) {
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:item.crop.plant", target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1f, 1.1f);
		return InteractionResult.SUCCESS;
	}

	static boolean plant(ServerLevel level, BlockPos pos, Variety variety) {
		Cmd.setblock(level, pos, "minecraft:" + SPROUT);
		if (!blockId(level.getBlockState(pos)).equals(SPROUT)) {
			return false;
		}
		TulpStore.addPlant(level, pos, new TulpStore.Plant(variety, level.getGameTime()));
		return true;
	}

	/** Bone meal on a tulip bed does nothing. Tulips don't do shortcuts. */
	static InteractionResult useBoneMeal(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() || !TulpItems.id(held.getItem()).equals("minecraft:bone_meal")) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (plantAt(level, pos, level.getBlockState(pos)) == null) {
			return InteractionResult.PASS;
		}
		actionBar(player, "Tulips don't do shortcuts. Wait for it.", ChatFormatting.YELLOW);
		return InteractionResult.FAIL;
	}

	// ---------------------------------------------------------------- growing

	/** Every 2 seconds: sprouts that are ready flower, broken tulips sparkle, beds that are gone leave their bulb. */
	static void scan(MinecraftServer server) {
		int grow = TulpConfig.get().growTicks;
		for (ServerLevel level : server.getAllLevels()) {
			for (long packed : TulpStore.plants(level)) {
				BlockPos pos = BlockPos.of(packed);
				TulpStore.Plant plant = TulpStore.plant(level, pos);
				if (plant == null || !level.isLoaded(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				String id = blockId(state);
				if (id.equals(SPROUT)) {
					if (level.getGameTime() - plant.planted >= grow) {
						bloom(level, pos, plant.variety());
					}
				} else if (id.equals(BONE_MEALED)) {
					Cmd.setblock(level, pos, "minecraft:" + SPROUT);
					if (blockId(level.getBlockState(pos.above())).equals(BONE_MEALED)) {
						level.removeBlock(pos.above(), false);
					}
				} else if (isTulip(state)) {
					sparkle(level, pos, plant.variety());
				} else {
					TulpStore.removePlant(level, pos);
					Block.popResource(level, pos, TulpItems.bulb(plant.variety(), 1));
				}
			}
		}
	}

	/** A sprout that's had its day flowers. */
	static void bloom(ServerLevel level, BlockPos pos, Variety variety) {
		Cmd.setblock(level, pos, variety.block());
		Cmd.particles(level, "minecraft:happy_villager", pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0.3, 0, 5);
		Cmd.sound(level, "minecraft:block.grass.place", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.6f, 1.3f);
	}

	/** Broken and legendary tulips show off. */
	private static void sparkle(ServerLevel level, BlockPos pos, Variety variety) {
		String particle = switch (variety.tier) {
			case RARE -> "minecraft:wax_on";
			case LEGENDARY -> "minecraft:end_rod";
			default -> null;
		};
		if (particle != null) {
			Cmd.particles(level, particle, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 0.25, 0.01, variety.tier == Variety.Tier.LEGENDARY ? 3 : 2);
		}
	}

	// ---------------------------------------------------------------- lifting

	/** The spot the plant is remembered by, if this block is (part of) a planted tulip. */
	static @Nullable BlockPos plantAt(ServerLevel level, BlockPos pos, BlockState state) {
		String id = blockId(state);
		if (TulpStore.plant(level, pos) != null && (id.equals(SPROUT) || id.equals(BONE_MEALED) || isTulip(state))) {
			return pos;
		}
		if (id.equals(BONE_MEALED) && TulpStore.plant(level, pos.below()) != null) {
			return pos.below();
		}
		return null;
	}

	/**
	 * A player breaks a tulip: a planted one is lifted (our drops, not vanilla's), and a wild one sometimes turns up a
	 * bulb. Returns false to cancel the vanilla break.
	 */
	static boolean beforeBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		BlockPos base = plantAt(level, pos, state);
		if (base != null) {
			ServerPlayer grower = player instanceof ServerPlayer sp && !player.isCreative() ? sp : null;
			boolean flowering = isTulip(level.getBlockState(base));
			Lift lift = lift(level, base, grower, grower != null);
			if (grower != null && flowering) {
				SkillsLink.xp(grower.getUUID(), "farming", 4);
				for (Variety bred : lift.bred()) {
					announce(level, grower, bred);
				}
			}
			if (!flowering && player instanceof ServerPlayer sp) {
				actionBar(sp, "Not in flower yet. You get your bulb back, no offsets.", ChatFormatting.YELLOW);
			}
			return false;
		}
		if (isTulip(state) && !player.isCreative() && RANDOM.nextDouble() < TulpConfig.get().wildBulbChance) {
			String id = blockId(state);
			level.removeBlock(pos, false);
			Block.popResource(level, pos, TulpItems.bulb(Variety.common(id.substring(0, id.indexOf('_'))), 1));
			Cmd.sound(level, "minecraft:block.rooted_dirt.break", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1f, 1.2f);
			if (player instanceof ServerPlayer sp) {
				actionBar(sp, "You dig up the flower and find a bulb underneath.", ChatFormatting.GREEN);
				SkillsLink.xp(sp.getUUID(), "farming", 1);
			}
			return false;
		}
		return true;
	}

	/** What lifting a tulip gave: the bulbs, and the rarer varieties that broke out of it. */
	record Lift(List<ItemStack> loot, List<Variety> bred) {
	}

	/**
	 * Lifts the tulip at {@code base}: removes it and returns (and, if asked, drops) the bulb, plus offsets if it was
	 * in flower. {@code grower} gets their Green Thumb roll (Skills mod), if there is one.
	 */
	static Lift lift(ServerLevel level, BlockPos base, @Nullable ServerPlayer grower, boolean drop) {
		TulpStore.Plant plant = TulpStore.plant(level, base);
		Variety variety = plant == null ? Variety.GOUDA : plant.variety();
		boolean flowering = isTulip(level.getBlockState(base));
		TulpStore.removePlant(level, base);
		if (blockId(level.getBlockState(base.above())).equals(BONE_MEALED)) {
			level.removeBlock(base.above(), false);
		}
		level.removeBlock(base, false);
		List<ItemStack> loot = new ArrayList<>();
		List<Variety> bred = new ArrayList<>();
		loot.add(TulpItems.bulb(variety, 1));
		if (flowering) {
			// Green Thumb (Farming): another offset now and then, from bulbs that multiply at all
			boolean extra = grower != null && SkillsLink.roll(grower.getUUID(), "farming/green_thumb");
			for (Variety child : offsets(variety, extra, TulpConfig.get(), RANDOM)) {
				loot.add(TulpItems.bulb(child, 1));
				if (child.tier.ordinal() > variety.tier.ordinal()) {
					bred.add(child);
				}
			}
		}
		if (drop) {
			for (ItemStack stack : loot) {
				Block.popResource(level, base, stack.copy());
			}
		}
		Cmd.sound(level, "minecraft:block.rooted_dirt.break", base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5, 1f, 1f);
		return new Lift(loot, bred);
	}

	// ---------------------------------------------------------------- breeding

	/**
	 * The offsets a flowering bulb gives when it's lifted. Common bulbs give 0-2 (about 1.2), Fine ones 0-1, Broken
	 * ones one a quarter of the time and the Semper Augustus one time in ten. {@code extra} adds one, for bulbs that
	 * multiply at all (common and fine).
	 */
	static List<Variety> offsets(Variety parent, boolean extra, TulpConfig c, Random random) {
		double roll = random.nextDouble();
		int n = switch (parent.tier) {
			case COMMON -> roll < 0.2 ? 0 : roll < 0.6 ? 1 : 2;
			case FINE -> roll < 0.5 ? 0 : 1;
			case RARE -> roll < 0.25 ? 1 : 0;
			case LEGENDARY -> roll < 0.1 ? 1 : 0;
		};
		if (extra && (parent.tier == Variety.Tier.COMMON || parent.tier == Variety.Tier.FINE)) {
			n++;
		}
		List<Variety> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(child(parent, c, random));
		}
		return out;
	}

	/**
	 * One offset. A common bulb breaks into a Fine variety 1 time in 20, a Broken one 1 in 100 and the Semper Augustus
	 * 1 in 1,000. A Fine bulb breeds true, with twice the odds of breaking further. Broken and Legendary bulbs are
	 * weak: half their offsets revert to the common tulip of their color.
	 */
	static Variety child(Variety parent, TulpConfig c, Random random) {
		double roll = random.nextDouble();
		return switch (parent.tier) {
			case COMMON -> roll < c.legendaryChance ? Variety.SEMPER_AUGUSTUS
				: roll < c.legendaryChance + c.rareChance ? Variety.random(Variety.Tier.RARE, random)
				: roll < c.legendaryChance + c.rareChance + c.fineChance ? Variety.random(Variety.Tier.FINE, random)
				: parent;
			case FINE -> roll < 2 * c.legendaryChance ? Variety.SEMPER_AUGUSTUS
				: roll < 2 * (c.legendaryChance + c.rareChance) ? Variety.random(Variety.Tier.RARE, random)
				: parent;
			case RARE -> roll < 5 * c.legendaryChance ? Variety.SEMPER_AUGUSTUS
				: random.nextDouble() < c.brokenTrueChance ? parent : Variety.common(parent.color);
			case LEGENDARY -> roll < c.brokenTrueChance ? parent : Variety.common(parent.color);
		};
	}

	/** A rarer tulip broke out of an offset. A Broken one is news to the grower; a Semper Augustus is news to everyone. */
	private static void announce(ServerLevel level, ServerPlayer grower, Variety bred) {
		if (bred.tier == Variety.Tier.FINE) {
			grower.sendSystemMessage(Component.literal("One of the offsets looks different: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(bred.title).withStyle(bred.tier.format)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
			SkillsLink.xp(grower.getUUID(), "farming", 15);
			return;
		}
		if (bred.tier == Variety.Tier.RARE) {
			grower.sendSystemMessage(Component.literal("✿ The petals came up streaked. A broken tulip: ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal(bred.title).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
			SkillsLink.xp(grower.getUUID(), "farming", 40);
			Cmd.sound(level, "minecraft:entity.player.levelup", grower.getX(), grower.getY(), grower.getZ(), 0.7f, 1.4f);
			return;
		}
		int n = TulpStore.semperBred();
		SkillsLink.xp(grower.getUUID(), "farming", 150);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("✿ ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(grower.getName().getString() + " bred a ").withStyle(ChatFormatting.YELLOW))
			.append(Component.literal("Semper Augustus").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
			.append(Component.literal(n == 1 ? ", the first on this server! Hide your canal houses." : " (number " + n + " on this server).")
				.withStyle(ChatFormatting.YELLOW)), false);
		Cmd.sound(level, "minecraft:ui.toast.challenge_complete", grower.getX(), grower.getY() + 1, grower.getZ(), 0.8f, 1.0f);
	}
}
