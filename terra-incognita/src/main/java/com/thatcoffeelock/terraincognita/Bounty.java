package com.thatcoffeelock.terraincognita;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * High risk, high reward. Every structure chest nobody has opened yet over there (vanilla's, Terra Incognita's,
 * other mods') gets extra plunder on top of its own loot: gold, emeralds, iron, now and then diamonds, and with
 * Specerij a spice cutting. Illagers killed over there drop emeralds and gold. Both scale with the config.
 */
final class Bounty {
	private static final Random RANDOM = new Random();
	private static final String[] SPICES = {"pepper", "nutmeg", "cloves", "cinnamon", "tea", "saffron"};

	private Bounty() {
	}

	/** About to open a block: if it's an unopened loot chest over there, top it up once vanilla has filled it. */
	static InteractionResult opening(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isCreative() || !Terra.isTerra(level) || TerraConfig.get().chestBounty <= 0) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null && entity instanceof Container box) {
			TerraMod.nextTick(() -> {
				if (level.getBlockEntity(pos) == entity) {
					int added = fill(box, plunder(TerraConfig.get().chestBounty, RANDOM));
					if (added > 0) {
						SkillsLink.xp(player.getUUID(), "treasure_hunting", 5);
					}
				}
			});
		}
		return InteractionResult.PASS;
	}

	/** The extra plunder in one chest. */
	static List<ItemStack> plunder(double multiplier, Random random) {
		List<ItemStack> out = new ArrayList<>();
		add(out, Items.GOLD_INGOT, 2 + random.nextInt(7), multiplier);
		add(out, Items.EMERALD, 1 + random.nextInt(6), multiplier);
		if (random.nextDouble() < 0.5) {
			add(out, Items.IRON_INGOT, 3 + random.nextInt(8), multiplier);
		}
		if (random.nextDouble() < 0.3) {
			add(out, Items.DIAMOND, 1 + random.nextInt(2), multiplier);
		}
		if (random.nextDouble() < 0.15) {
			add(out, item("minecraft:gold_block", Items.GOLD_INGOT), 1, multiplier);
		}
		if (random.nextDouble() < 0.3) {
			ItemStack cutting = spice("cutting:" + SPICES[random.nextInt(SPICES.length)], 1);
			if (!cutting.isEmpty()) {
				out.add(cutting);
			}
		}
		return out;
	}

	private static void add(List<ItemStack> out, Item item, int count, double multiplier) {
		int n = (int) Math.round(count * multiplier);
		if (n > 0) {
			out.add(new ItemStack(item, Math.min(n, item.getDefaultMaxStackSize())));
		}
	}

	private static Item item(String id, Item fallback) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		return item == null || item == Items.AIR ? fallback : item;
	}

	/** A Specerij item ("cutting:pepper"), or empty without Specerij. */
	@SuppressWarnings("unchecked")
	private static ItemStack spice(String kind, int count) {
		Object hook = FabricLoader.getInstance().getObjectShare().get("specerij:item");
		if (!(hook instanceof BiFunction<?, ?, ?> f)) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = ((BiFunction<String, Integer, ItemStack>) f).apply(kind, count);
			return stack == null ? ItemStack.EMPTY : stack;
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	/** Puts the plunder into empty slots, scattered. Returns how many stacks fitted. */
	static int fill(Container box, List<ItemStack> plunder) {
		List<Integer> empty = new ArrayList<>();
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).isEmpty()) {
				empty.add(i);
			}
		}
		int added = 0;
		for (ItemStack stack : plunder) {
			if (empty.isEmpty()) {
				break;
			}
			box.setItem(empty.remove(RANDOM.nextInt(empty.size())), stack);
			added++;
		}
		if (added > 0) {
			box.setChanged();
		}
		return added;
	}

	/** An illager died over there: if a player did it, it drops a purse. */
	static void killed(LivingEntity dead, @Nullable Entity killer) {
		double m = TerraConfig.get().illagerBounty;
		if (!(killer instanceof ServerPlayer) || m <= 0 || !(dead.level() instanceof ServerLevel level) || !Terra.isTerra(level) || !Terra.isIllager(dead)) {
			return;
		}
		boolean big = Terra.typeId(dead).equals("minecraft:ravager") || Terra.typeId(dead).equals("minecraft:evoker");
		List<ItemStack> purse = new ArrayList<>();
		add(purse, Items.EMERALD, big ? 4 + RANDOM.nextInt(5) : 1 + RANDOM.nextInt(3), m);
		if (big || RANDOM.nextDouble() < 0.25) {
			add(purse, Items.GOLD_INGOT, 1 + RANDOM.nextInt(big ? 6 : 3), m);
		}
		for (ItemStack stack : purse) {
			Block.popResource(level, dead.blockPosition(), stack);
		}
	}
}
