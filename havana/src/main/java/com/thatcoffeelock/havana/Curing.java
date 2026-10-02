package com.thatcoffeelock.havana;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A Curing Barrel is any barrel or chest whose name has "curing" or "humidor" in it. Craft one, or rename one in an
 * anvil. Fresh leaves in it turn into Cured Tobacco after {@link #CURE_TICKS}; Cured Tobacco left in it turns into
 * Aged Tobacco after {@link #AGE_TICKS} more.
 *
 * Progress is kept per slot in {@link HavanaStore} (not on the items, so they still stack). It goes by game time, so a
 * barrel in an unloaded chunk catches up the moment it's loaded again. Adding fresh leaves to a stack that's half done
 * waters the progress down accordingly: no topping up a nearly-cured stack.
 */
final class Curing {
	/** One Minecraft day. */
	static final long CURE_TICKS = 24000;
	/** Two more days. */
	static final long AGE_TICKS = 48000;

	private Curing() {
	}

	static boolean isCuringName(String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		return s.contains("curing") || s.contains("humidor");
	}

	private static boolean isCuringBlock(String id) {
		return id.equals("barrel") || id.equals("chest") || id.equals("trapped_chest");
	}

	/** The curing barrel at pos, or null. Never loads a chunk. */
	static @Nullable Container at(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isCuringBlock(Crops.blockId(level.getBlockState(pos)))) {
			return null;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof Container box) || !(entity instanceof Nameable named)) {
			return null;
		}
		Component name = named.getCustomName();
		return name != null && isCuringName(name.getString()) ? box : null;
	}

	/** Ticks a batch of this kind needs to move on, 0 if it's done. */
	static long need(String kind) {
		return switch (kind) {
			case HavanaItems.LEAF -> CURE_TICKS;
			case HavanaItems.CURED -> AGE_TICKS;
			default -> 0;
		};
	}

	private static String next(String kind) {
		return kind.equals(HavanaItems.LEAF) ? HavanaItems.CURED : HavanaItems.AGED;
	}

	// ---------------------------------------------------------------- interaction

	/**
	 * Opening a curing barrel starts tracking it (and says how it's getting on). Placing a named one does too.
	 * Always passes, so the barrel opens (or gets placed) as normal.
	 */
	static InteractionResult useBarrel(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Container box = at(level, pos);
		if (box != null) {
			if (hand == InteractionHand.MAIN_HAND && !(player.isShiftKeyDown() && !player.getMainHandItem().isEmpty())) {
				HavanaStore.Barrel barrel = HavanaStore.addBarrel(level, pos);
				update(level, pos, box, barrel);
				player.sendSystemMessage(report(box, barrel));
			}
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		Component name = held.get(DataComponents.CUSTOM_NAME);
		if (name != null && isCuringName(name.getString()) && isCuringBlock(HavanaItems.id(held.getItem()).replace("minecraft:", ""))) {
			BlockPos beside = pos.relative(hit.getDirection());
			HavanaMod.nextTick(() -> {
				for (BlockPos at : new BlockPos[] {pos, beside}) {
					if (at(level, at) != null && HavanaStore.barrel(level, at) == null) {
						HavanaStore.addBarrel(level, at);
						player.sendSystemMessage(Component.literal("Curing Barrel ready. Fill it with fresh Tobacco Leaves.").withStyle(ChatFormatting.GOLD));
					}
				}
			});
		}
		return InteractionResult.PASS;
	}

	private static MutableComponent report(Container box, HavanaStore.Barrel barrel) {
		int leaves = 0;
		int cured = 0;
		long leafDone = 0;
		long curedDone = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			HavanaStore.Batch batch = barrel.slots.get(Integer.toString(i));
			ItemStack stack = box.getItem(i);
			long progress = batch == null ? 0 : batch.progress * stack.getCount();
			switch (HavanaItems.kind(stack)) {
				case HavanaItems.LEAF -> {
					leaves += stack.getCount();
					leafDone += progress;
				}
				case HavanaItems.CURED -> {
					cured += stack.getCount();
					curedDone += progress;
				}
				default -> {
				}
			}
		}
		MutableComponent line = Component.literal("Curing Barrel: ").withStyle(ChatFormatting.GOLD);
		if (leaves == 0 && cured == 0) {
			return line.append(Component.literal("put fresh Tobacco Leaves in here. They cure in a day, and age into Aged Tobacco in two more.")
				.withStyle(ChatFormatting.GRAY));
		}
		if (leaves > 0) {
			line.append(Component.literal(leaves + " leaves curing (" + percent(leafDone, leaves, CURE_TICKS) + ")").withStyle(ChatFormatting.GREEN));
		}
		if (cured > 0) {
			if (leaves > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(Component.literal(cured + " cured tobacco aging (" + percent(curedDone, cured, AGE_TICKS) + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return line;
	}

	private static String percent(long done, int count, long need) {
		return Math.min(99, done * 100 / (count * need)) + "%";
	}

	// ---------------------------------------------------------------- curing

	/** Every 5 seconds: bring every loaded curing barrel up to date, forget the ones that are gone. */
	static void scan(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (long packed : HavanaStore.barrels(level)) {
				BlockPos pos = BlockPos.of(packed);
				if (!level.isLoaded(pos)) {
					continue;
				}
				Container box = at(level, pos);
				HavanaStore.Barrel barrel = HavanaStore.barrel(level, pos);
				if (box == null || barrel == null) {
					HavanaStore.removeBarrel(level, pos);
				} else {
					update(level, pos, box, barrel);
				}
			}
		}
	}

	static void update(ServerLevel level, BlockPos pos, Container box, HavanaStore.Barrel barrel) {
		long now = level.getGameTime();
		long elapsed = Math.max(0, now - barrel.last);
		barrel.last = now;
		if (age(box, barrel, elapsed) > 0) {
			Cmd.particles(level, "minecraft:composter", pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0, 8);
			Cmd.sound(level, "minecraft:block.composter.ready", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.6f, 1.2f);
		}
	}

	/**
	 * Moves every batch in the barrel on by {@code elapsed} ticks and swaps finished stacks for the next stage
	 * (a long absence can take leaves all the way to aged). Returns how many stacks changed.
	 */
	static int age(Container box, HavanaStore.Barrel barrel, long elapsed) {
		int changed = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			String key = Integer.toString(i);
			ItemStack stack = box.getItem(i);
			String kind = HavanaItems.kind(stack);
			if (need(kind) == 0) {
				if (barrel.slots.remove(key) != null) {
					HavanaStore.changed();
				}
				continue;
			}
			HavanaStore.Batch batch = barrel.slots.get(key);
			if (batch == null || !batch.kind.equals(kind)) {
				// a new batch: it starts counting from now
				barrel.slots.put(key, new HavanaStore.Batch(kind, stack.getCount()));
				HavanaStore.changed();
				continue;
			}
			if (stack.getCount() > batch.count) {
				batch.progress = batch.progress * batch.count / stack.getCount();
			}
			batch.count = stack.getCount();
			batch.progress += elapsed;
			long need = need(kind);
			while (need > 0 && batch.progress >= need) {
				batch.progress -= need;
				kind = next(kind);
				box.setItem(i, HavanaItems.tobacco(kind, batch.count));
				batch.kind = kind;
				need = need(kind);
				changed++;
			}
			if (need == 0) {
				barrel.slots.remove(key);
			}
			HavanaStore.changed();
		}
		if (changed > 0) {
			box.setChanged();
		}
		return changed;
	}
}
