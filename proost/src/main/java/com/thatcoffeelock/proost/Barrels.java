package com.thatcoffeelock.proost;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Mash Tuns and Oak Casks: any barrel or chest with "mash" or "ferment" (a tun) or "cask" (a cask) in its
 * name. Craft one, or rename one in an anvil.
 * <ul>
 * <li>In a <b>Mash Tun</b>, wheat, sugar cane and apples ferment into mash after {@link #FERMENT_TICKS}, one for one.</li>
 * <li>In an <b>Oak Cask</b>, full bottles of young spirit age after {@link #AGE_TICKS}. Opened bottles don't.</li>
 * </ul>
 * Like Havana's curing barrels: progress is kept per slot in {@link ProostStore} (not on the items, so they still
 * stack), it goes by game time so an unloaded barrel catches up, and topping up a half-done stack waters it down.
 */
final class Barrels {
	/** Half a Minecraft day. */
	static final long FERMENT_TICKS = 12000;
	/** Two days. */
	static final long AGE_TICKS = 48000;

	enum Type {
		TUN("Mash Tun"),
		CASK("Oak Cask");

		final String title;

		Type(String title) {
			this.title = title;
		}
	}

	private Barrels() {
	}

	static @Nullable Type typeOf(String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		if (s.contains("cask")) {
			return Type.CASK;
		}
		if (s.contains("mash") || s.contains("ferment")) {
			return Type.TUN;
		}
		return null;
	}

	static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	private static boolean isBarrelBlock(String id) {
		return id.equals("barrel") || id.equals("chest") || id.equals("trapped_chest");
	}

	/** The tun or cask at pos (its container), or null. Never loads a chunk. */
	static @Nullable Container at(ServerLevel level, BlockPos pos) {
		return typeAt(level, pos) == null ? null : (Container) level.getBlockEntity(pos);
	}

	static @Nullable Type typeAt(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isBarrelBlock(blockId(level.getBlockState(pos)))) {
			return null;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (!(entity instanceof Container) || !(entity instanceof Nameable named)) {
			return null;
		}
		Component name = named.getCustomName();
		return name == null ? null : typeOf(name.getString());
	}

	/** What this stack is as a batch in this kind of barrel ("crop:grain", "young:rum"), or "" if nothing happens to it. */
	static String batchKind(Type type, ItemStack stack) {
		if (type == Type.TUN) {
			ProostItems.Base base = ProostItems.Base.ofCrop(stack);
			return base == null ? "" : "crop:" + base.id();
		}
		if (ProostItems.isSpirit(stack) && !ProostItems.isAged(stack) && !ProostItems.isOpen(stack)) {
			return "young:" + ProostItems.drink(stack).id();
		}
		return "";
	}

	/** What a finished batch of this kind becomes. */
	private static ItemStack finished(String kind, int count) {
		String what = kind.substring(kind.indexOf(':') + 1);
		if (kind.startsWith("crop:")) {
			ProostItems.Base base = ProostItems.Base.byName(what);
			return base == null ? ItemStack.EMPTY : ProostItems.mash(base, count);
		}
		ProostItems.Drink drink = ProostItems.Drink.byName(what);
		return drink == null ? ItemStack.EMPTY : ProostItems.spirit(drink, true, count);
	}

	static long need(Type type) {
		return type == Type.TUN ? FERMENT_TICKS : AGE_TICKS;
	}

	// ---------------------------------------------------------------- interaction

	/**
	 * Opening a tun or cask starts tracking it (and says how it's getting on). Placing a named one does too.
	 * Always passes, so the barrel opens (or gets placed) as normal.
	 */
	static InteractionResult useBarrel(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Type type = typeAt(level, pos);
		if (type != null) {
			if (hand == InteractionHand.MAIN_HAND && !(player.isShiftKeyDown() && !player.getMainHandItem().isEmpty())) {
				Container box = (Container) level.getBlockEntity(pos);
				ProostStore.Barrel barrel = ProostStore.addBarrel(level, pos);
				update(level, pos, type, box, barrel);
				player.sendSystemMessage(report(type, box, barrel));
			}
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		Component name = held.get(DataComponents.CUSTOM_NAME);
		if (name != null && typeOf(name.getString()) != null && isBarrelBlock(ProostItems.id(held.getItem()).replace("minecraft:", ""))) {
			BlockPos beside = pos.relative(hit.getDirection());
			ProostMod.nextTick(() -> {
				for (BlockPos at : new BlockPos[] {pos, beside}) {
					Type placed = typeAt(level, at);
					if (placed != null && ProostStore.barrel(level, at) == null) {
						ProostStore.addBarrel(level, at);
						player.sendSystemMessage(Component.literal(placed == Type.TUN
							? "Mash Tun ready. Fill it with wheat, sugar cane or apples."
							: "Oak Cask ready. Fill it with full bottles of young spirit.").withStyle(ChatFormatting.GOLD));
					}
				}
			});
		}
		return InteractionResult.PASS;
	}

	private static MutableComponent report(Type type, Container box, ProostStore.Barrel barrel) {
		int count = 0;
		long done = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack stack = box.getItem(i);
			if (batchKind(type, stack).isEmpty()) {
				continue;
			}
			ProostStore.Batch batch = barrel.slots.get(Integer.toString(i));
			count += stack.getCount();
			done += batch == null ? 0 : batch.progress * stack.getCount();
		}
		MutableComponent line = Component.literal(type.title + ": ").withStyle(ChatFormatting.GOLD);
		if (count == 0) {
			return line.append(Component.literal(type == Type.TUN
				? "put wheat, sugar cane or apples in here. They ferment into mash in half a day."
				: "put full bottles of young spirit in here. They age in two days.").withStyle(ChatFormatting.GRAY));
		}
		String percent = Math.min(99, done * 100 / (count * need(type))) + "%";
		return line.append(Component.literal(count + (type == Type.TUN ? " fermenting (" : " bottles aging (") + percent + ")")
			.withStyle(type == Type.TUN ? ChatFormatting.YELLOW : ChatFormatting.LIGHT_PURPLE));
	}

	// ---------------------------------------------------------------- fermenting and aging

	/** Every 5 seconds: bring every loaded tun and cask up to date, forget the ones that are gone. */
	static void scan(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (long packed : ProostStore.barrels(level)) {
				BlockPos pos = BlockPos.of(packed);
				if (!level.isLoaded(pos)) {
					continue;
				}
				Type type = typeAt(level, pos);
				ProostStore.Barrel barrel = ProostStore.barrel(level, pos);
				if (type == null || barrel == null) {
					ProostStore.removeBarrel(level, pos);
				} else {
					update(level, pos, type, (Container) level.getBlockEntity(pos), barrel);
				}
			}
		}
	}

	static void update(ServerLevel level, BlockPos pos, Type type, Container box, ProostStore.Barrel barrel) {
		long now = level.getGameTime();
		long elapsed = Math.max(0, now - barrel.last);
		barrel.last = now;
		if (age(type, box, barrel, elapsed) > 0) {
			Cmd.particles(level, type == Type.TUN ? "minecraft:bubble_pop" : "minecraft:happy_villager",
				pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0, 8);
			Cmd.sound(level, type == Type.TUN ? "minecraft:block.brewing_stand.brew" : "minecraft:block.barrel.close",
				pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.6f, 1.2f);
		}
	}

	/** Moves every batch on by {@code elapsed} ticks and swaps finished stacks. Returns how many stacks changed. */
	static int age(Type type, Container box, ProostStore.Barrel barrel, long elapsed) {
		int changed = 0;
		long need = need(type);
		for (int i = 0; i < box.getContainerSize(); i++) {
			String key = Integer.toString(i);
			ItemStack stack = box.getItem(i);
			String kind = batchKind(type, stack);
			if (kind.isEmpty()) {
				if (barrel.slots.remove(key) != null) {
					ProostStore.changed();
				}
				continue;
			}
			ProostStore.Batch batch = barrel.slots.get(key);
			if (batch == null || !batch.kind.equals(kind)) {
				// a new batch: it starts counting from now
				barrel.slots.put(key, new ProostStore.Batch(kind, stack.getCount()));
				ProostStore.changed();
				continue;
			}
			if (stack.getCount() > batch.count) {
				batch.progress = batch.progress * batch.count / stack.getCount();
			}
			batch.count = stack.getCount();
			batch.progress += elapsed;
			if (batch.progress >= need) {
				ItemStack done = finished(kind, batch.count);
				if (!done.isEmpty()) {
					box.setItem(i, done);
					changed++;
				}
				barrel.slots.remove(key);
			}
			ProostStore.changed();
		}
		if (changed > 0) {
			box.setChanged();
		}
		return changed;
	}
}
