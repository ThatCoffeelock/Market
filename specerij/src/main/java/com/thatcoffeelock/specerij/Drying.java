package com.thatcoffeelock.specerij;

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
 * A Drying Rack is any barrel or chest whose name has "drying" or "spice rack" in it. Craft one, or rename one in an
 * anvil. Fresh spice in it dries after {@link SpiceConfig#dryTicks}; green tea left in it turns black after
 * {@link SpiceConfig#blackTeaTicks} more. The same way as Havana's Curing Barrel, on purpose.
 *
 * Progress is kept per slot in {@link SpiceStore} (not on the items, so they still stack). It goes by game time, so a
 * rack in an unloaded chunk catches up the moment it's loaded again. Topping up a half-dried stack waters the progress
 * down accordingly.
 */
final class Drying {
	private Drying() {
	}

	static boolean isRackName(String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		return s.contains("drying") || s.contains("spicerack");
	}

	private static boolean isRackBlock(String id) {
		return id.equals("barrel") || id.equals("chest") || id.equals("trapped_chest");
	}

	/** The drying rack at pos, or null. Never loads a chunk. */
	static @Nullable Container at(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isRackBlock(Gardens.blockId(level.getBlockState(pos)))) {
			return null;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof Container box) || !(entity instanceof Nameable named)) {
			return null;
		}
		Component name = named.getCustomName();
		return name != null && isRackName(name.getString()) ? box : null;
	}

	/** What a batch is: its stage and spice, e.g. "fresh:pepper". Empty for anything that doesn't dry. */
	static String batchKind(ItemStack stack) {
		Spice spice = SpiceItems.spice(stack);
		return spice == null ? "" : SpiceItems.kind(stack) + ":" + spice.id();
	}

	/** Ticks a batch of this kind needs to move on, 0 if it's done. */
	static long need(String kind) {
		if (kind.startsWith(SpiceItems.FRESH + ":")) {
			return SpiceConfig.get().dryTicks;
		}
		if (kind.equals(SpiceItems.DRIED + ":" + Spice.TEA.id())) {
			return SpiceConfig.get().blackTeaTicks;
		}
		return 0;
	}

	private static String next(String kind) {
		return kind.startsWith(SpiceItems.FRESH + ":") ? SpiceItems.DRIED + kind.substring(kind.indexOf(':')) : SpiceItems.AGED + kind.substring(kind.indexOf(':'));
	}

	// ---------------------------------------------------------------- interaction

	/**
	 * Opening a drying rack starts tracking it (and says how it's getting on). Placing a named one does too.
	 * Always passes, so the rack opens (or gets placed) as normal.
	 */
	static InteractionResult useRack(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Container box = at(level, pos);
		if (box != null) {
			if (hand == InteractionHand.MAIN_HAND && !(player.isShiftKeyDown() && !player.getMainHandItem().isEmpty())) {
				SpiceStore.Rack rack = SpiceStore.addRack(level, pos);
				update(level, pos, box, rack);
				player.sendSystemMessage(report(box, rack));
			}
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		Component name = held.get(DataComponents.CUSTOM_NAME);
		if (name != null && isRackName(name.getString()) && isRackBlock(SpiceItems.id(held.getItem()).replace("minecraft:", ""))) {
			BlockPos beside = pos.relative(hit.getDirection());
			SpiceMod.nextTick(() -> {
				for (BlockPos at : new BlockPos[] {pos, beside}) {
					if (at(level, at) != null && SpiceStore.rack(level, at) == null) {
						SpiceStore.addRack(level, at);
						player.sendSystemMessage(Component.literal("Drying Rack ready. Fill it with fresh spices.").withStyle(ChatFormatting.GOLD));
					}
				}
			});
		}
		return InteractionResult.PASS;
	}

	private static MutableComponent report(Container box, SpiceStore.Rack rack) {
		int fresh = 0;
		int green = 0;
		long freshDone = 0;
		long greenDone = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			SpiceStore.Batch batch = rack.slots.get(Integer.toString(i));
			ItemStack stack = box.getItem(i);
			long progress = batch == null ? 0 : batch.progress * stack.getCount();
			String kind = batchKind(stack);
			if (kind.startsWith(SpiceItems.FRESH + ":")) {
				fresh += stack.getCount();
				freshDone += progress;
			} else if (need(kind) > 0) {
				green += stack.getCount();
				greenDone += progress;
			}
		}
		MutableComponent line = Component.literal("Drying Rack: ").withStyle(ChatFormatting.GOLD);
		if (fresh == 0 && green == 0) {
			return line.append(Component.literal("put fresh spices in here. They dry in a day; green tea turns black in two more.")
				.withStyle(ChatFormatting.GRAY));
		}
		if (fresh > 0) {
			line.append(Component.literal(fresh + " fresh drying (" + percent(freshDone, fresh, SpiceConfig.get().dryTicks) + ")").withStyle(ChatFormatting.GREEN));
		}
		if (green > 0) {
			if (fresh > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(Component.literal(green + " green tea turning black (" + percent(greenDone, green, SpiceConfig.get().blackTeaTicks) + ")")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return line;
	}

	private static String percent(long done, int count, long need) {
		return Math.min(99, done * 100 / (count * need)) + "%";
	}

	// ---------------------------------------------------------------- curing

	/** Every 5 seconds: bring every loaded drying rack up to date, forget the ones that are gone. */
	static void scan(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (long packed : SpiceStore.racks(level)) {
				BlockPos pos = BlockPos.of(packed);
				if (!level.isLoaded(pos)) {
					continue;
				}
				Container box = at(level, pos);
				SpiceStore.Rack rack = SpiceStore.rack(level, pos);
				if (box == null || rack == null) {
					SpiceStore.removeRack(level, pos);
				} else {
					update(level, pos, box, rack);
				}
			}
		}
	}

	static void update(ServerLevel level, BlockPos pos, Container box, SpiceStore.Rack rack) {
		long now = level.getGameTime();
		long elapsed = Math.max(0, now - rack.last);
		rack.last = now;
		if (age(box, rack, elapsed) > 0) {
			Cmd.particles(level, "minecraft:composter", pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0, 8);
			Cmd.sound(level, "minecraft:block.composter.ready", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.6f, 1.2f);
		}
	}

	/**
	 * Moves every batch in the rack on by {@code elapsed} ticks and swaps finished stacks for the next stage
	 * (a long absence can take fresh tea all the way to black). Returns how many stacks changed.
	 */
	static int age(Container box, SpiceStore.Rack rack, long elapsed) {
		int changed = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			String key = Integer.toString(i);
			ItemStack stack = box.getItem(i);
			String kind = batchKind(stack);
			if (need(kind) == 0) {
				if (rack.slots.remove(key) != null) {
					SpiceStore.changed();
				}
				continue;
			}
			SpiceStore.Batch batch = rack.slots.get(key);
			if (batch == null || !batch.kind.equals(kind)) {
				// a new batch: it starts counting from now
				rack.slots.put(key, new SpiceStore.Batch(kind, stack.getCount()));
				SpiceStore.changed();
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
				box.setItem(i, SpiceItems.byKind(kind, batch.count));
				batch.kind = kind;
				need = need(kind);
				changed++;
			}
			if (need == 0) {
				rack.slots.remove(key);
			}
			SpiceStore.changed();
		}
		if (changed > 0) {
			box.setChanged();
		}
		return changed;
	}
}
