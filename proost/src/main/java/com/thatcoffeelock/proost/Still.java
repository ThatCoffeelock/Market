package com.thatcoffeelock.proost;

import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The still: any cauldron with heat right under it (a lit campfire, fire, lava, magma or a lit furnace). Hold mash
 * and right-click it with a glass bottle in your inventory: {@link #MASH_PER_BOTTLE} mash become one bottle of young
 * spirit. Grain mash makes whisky, or genever with sweet berries (the juniper, near enough) in your other hand.
 * Sneak to distill up to {@link #BATCH} bottles at once.
 */
final class Still {
	static final int MASH_PER_BOTTLE = 8;
	static final int BATCH = 8;
	private static final Set<String> CAULDRONS = Set.of("cauldron", "water_cauldron");
	private static final Set<String> HEAT = Set.of("campfire", "soul_campfire", "fire", "soul_fire", "lava", "magma_block",
		"furnace", "smoker", "blast_furnace");

	/** What distilling would make from these inputs, or why it can't. */
	record Plan(@Nullable String problem, ItemStack bottles, int mashUsed, int berriesUsed, int glassUsed) {
		static Plan no(String problem) {
			return new Plan(problem, ItemStack.EMPTY, 0, 0, 0);
		}

		boolean ok() {
			return problem == null;
		}
	}

	private Still() {
	}

	/**
	 * Up to {@code wanted} bottles from this mash, with these botanicals in the other hand and this many glass
	 * bottles to fill. {@code glass} below 0 means there's no limit (creative).
	 */
	static Plan plan(ItemStack mash, ItemStack other, int glass, int wanted) {
		ProostItems.Base base = ProostItems.isMash(mash) ? ProostItems.base(mash) : null;
		if (base == null) {
			return Plan.no("Hold mash to distill. Ferment wheat, sugar cane or apples in a Mash Tun first.");
		}
		int count = Math.min(wanted, mash.getCount() / MASH_PER_BOTTLE);
		if (count <= 0) {
			return Plan.no("You need " + MASH_PER_BOTTLE + " mash per bottle.");
		}
		if (glass >= 0) {
			count = Math.min(count, glass);
			if (count <= 0) {
				return Plan.no("You need an empty glass bottle to catch it in.");
			}
		}
		ProostItems.Drink drink = switch (base) {
			case CANE -> ProostItems.Drink.RUM;
			case APPLE -> ProostItems.Drink.BRANDY;
			case GRAIN -> other.is(Items.SWEET_BERRIES) && !other.has(DataComponents.CUSTOM_DATA)
				? ProostItems.Drink.GENEVER : ProostItems.Drink.WHISKY;
		};
		int berries = 0;
		if (drink == ProostItems.Drink.GENEVER) {
			count = Math.min(count, other.getCount());
			berries = count;
		}
		return new Plan(null, ProostItems.spirit(drink, false, count), count * MASH_PER_BOTTLE, berries, glass < 0 ? 0 : count);
	}

	static boolean isStill(ServerLevel level, BlockPos pos) {
		return CAULDRONS.contains(Barrels.blockId(level.getBlockState(pos))) && heated(level.getBlockState(pos.below()));
	}

	private static boolean heated(BlockState below) {
		String id = Barrels.blockId(below);
		if (!HEAT.contains(id)) {
			return false;
		}
		// campfires and furnaces have an off state: only lit ones count
		return !(id.contains("campfire") || id.contains("furnace") || id.equals("smoker")) || below.toString().contains("lit=true");
	}

	/** Hold mash and right-click a heated cauldron. */
	static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		ItemStack mash = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || !ProostItems.isMash(mash)
			|| !CAULDRONS.contains(Barrels.blockId(level.getBlockState(pos)))) {
			return InteractionResult.PASS;
		}
		if (!heated(level.getBlockState(pos.below()))) {
			actionBar(player, "A still needs heat: put the cauldron on a lit campfire, fire, lava, magma or a lit furnace.", ChatFormatting.YELLOW);
			return InteractionResult.SUCCESS;
		}
		ItemStack other = player.getOffhandItem();
		boolean creative = player.isCreative();
		Plan plan = plan(mash, other, creative ? -1 : countGlass(player), player.isShiftKeyDown() ? BATCH : 1);
		if (!plan.ok()) {
			actionBar(player, plan.problem(), ChatFormatting.RED);
			return InteractionResult.SUCCESS;
		}
		int mashUsed = plan.mashUsed();
		// Green Leaf: a good run needs only half the mash
		if (SkillsLink.roll(player.getUUID(), "connoisseur/green_leaf")) {
			mashUsed = Math.max(MASH_PER_BOTTLE / 2, mashUsed / 2);
		}
		ItemStack bottles = plan.bottles().copy();
		// Master Roller: a bonus bottle from the dregs
		boolean bonus = SkillsLink.roll(player.getUUID(), "connoisseur/master_roller");
		if (bonus) {
			bottles.grow(1);
		}
		if (!creative) {
			mash.shrink(mashUsed);
			other.shrink(plan.berriesUsed());
			takeGlass(player, plan.glassUsed());
		}
		ProostItems.give(player, bottles);
		SkillsLink.xp(player.getUUID(), "connoisseur", 2.0 * plan.bottles().getCount());

		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		Cmd.sound(level, "minecraft:block.brewing_stand.brew", x, y, z, 0.8f, 0.8f);
		Cmd.sound(level, "minecraft:block.bubble_column.upwards_inside", x, y, z, 0.6f, 1.0f);
		Cmd.particles(level, "minecraft:bubble_pop", x, y, z, 0.25, 0.02, 12);
		Cmd.particles(level, "minecraft:cloud", x, y + 0.3, z, 0.15, 0.01, 4);
		String made = bottles.getCount() + "× " + ProostItems.drink(bottles).title(false);
		actionBar(player, "Distilled " + made + "." + (bonus ? " A bonus bottle from the dregs!" : ""), ChatFormatting.GOLD);
		return InteractionResult.SUCCESS;
	}

	private static int countGlass(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.GLASS_BOTTLE)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void takeGlass(ServerPlayer player, int count) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.GLASS_BOTTLE)) {
				int n = Math.min(count, stack.getCount());
				stack.shrink(n);
				count -= n;
			}
		}
	}

	private static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}
}
