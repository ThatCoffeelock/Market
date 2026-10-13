package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * What some jobs carry: Luxury Export ships the sealed Proost bottles and unlit Havana cigars in your inventory,
 * Charter Your Ship sails off in your packed Ahoy ship or Blimey airship (and brings it back, unless it sinks).
 */
final class Cargo {
	private Cargo() {
	}

	private static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** A sealed Proost bottle, or an unlit, unsmoked Havana cigar. */
	static boolean isLuxury(ItemStack stack) {
		if (stack.isEmpty() || stack.has(DataComponents.MAX_DAMAGE)) {
			return false;
		}
		CompoundTag tag = data(stack);
		return "spirit".equals(tag.getStringOr("proost", "")) || "cigar".equals(tag.getStringOr("havana", ""));
	}

	/** An Ahoy ship in a bottle, or a Blimey airship. */
	static boolean isShip(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CompoundTag tag = data(stack);
		return (stack.is(Items.GLASS_BOTTLE) && tag.contains("ahoy_ship")) || (stack.is(Items.PAPER) && "airship".equals(tag.getStringOr("blimey", "")));
	}

	/** The cargo's Market value, in cents. */
	static long value(List<ItemStack> items) {
		long n = 0;
		for (ItemStack stack : items) {
			n += Math.max(0, Bank.sellValue(stack));
		}
		return n;
	}

	/**
	 * Loads the job's cargo from the player's inventory (taking it). Returns why not, or null if loaded (or it needs
	 * none).
	 */
	static @Nullable String load(Job job, JobType type, ServerPlayer player) {
		Inventory inv = player.getInventory();
		if (type.special == JobType.Special.EXPORT) {
			List<ItemStack> goods = new ArrayList<>();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (isLuxury(inv.getItem(i))) {
					goods.add(inv.getItem(i));
				}
			}
			long value = value(goods);
			if (goods.isEmpty() || value <= 0) {
				return "Luxury Export needs cargo: carry sealed bottles (Proost) or cigars (Havana).";
			}
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (isLuxury(inv.getItem(i))) {
					inv.setItem(i, ItemStack.EMPTY);
				}
			}
			inv.setChanged();
			job.cargo = value;
			return null;
		}
		if (type.special == JobType.Special.CHARTER) {
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (isShip(stack)) {
					JsonElement json = encode((ServerLevel) player.level(), stack);
					if (json == null) {
						return "That ship won't go in the books.";
					}
					job.cargoItem = json;
					inv.setItem(i, ItemStack.EMPTY);
					inv.setChanged();
					return null;
				}
			}
			return "Charter Your Ship needs a ship: carry a packed Ahoy ship in a bottle or a Blimey airship.";
		}
		return null;
	}

	/** Gives back what {@link #load} took, when the job couldn't start after all. */
	static void unload(Job job, JobType type, ServerPlayer player, List<ItemStack> taken) {
		for (ItemStack stack : taken) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
		job.cargo = 0;
		job.cargoItem = null;
	}

	/** The luxury goods {@link #load} would take, copied (so they can be handed back if the job doesn't start). */
	static List<ItemStack> peek(JobType type, ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if ((type.special == JobType.Special.EXPORT && isLuxury(stack)) || (type.special == JobType.Special.CHARTER && isShip(stack) && out.isEmpty())) {
				out.add(stack.copy());
			}
		}
		return out;
	}

	private static DynamicOps<JsonElement> ops(ServerLevel level) {
		return level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	static @Nullable JsonElement encode(ServerLevel level, ItemStack stack) {
		return ItemStack.CODEC.encodeStart(ops(level), stack).result().orElse(null);
	}

	static ItemStack decode(ServerLevel level, @Nullable JsonElement json) {
		if (json == null) {
			return ItemStack.EMPTY;
		}
		return ItemStack.CODEC.parse(ops(level), json).result().orElse(ItemStack.EMPTY);
	}
}
