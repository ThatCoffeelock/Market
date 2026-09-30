package com.thatcoffeelock.mobilehome;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.util.Prediction;
import org.jetbrains.annotations.Nullable;

/**
 * The vehicle as an item: a vanilla minecart tagged with custom data (vanilla clients see a glowing
 * minecart). A packed-up vehicle carries its fuel and storage inside the item.
 */
public final class Kits {
	static final String KEY = "mobile_home";
	static final String SAVED = "mobile_home_saved";

	private Kits() {
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack kit(VehicleType type) {
		ItemStack stack = new ItemStack(type.kitItem());
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, type.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal(type.displayName)
			.withStyle(type == VehicleType.TANK ? ChatFormatting.DARK_GREEN : ChatFormatting.AQUA));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Right-click the ground to park it.", ChatFormatting.GRAY),
			text(type == VehicleType.TANK ? "3 seats, 54 slots of storage, climbs 2-block walls." : "4 seats, 54 slots of storage, a bed you can't sleep in.",
				ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A kit that remembers its fuel, lock and storage. */
	public static ItemStack packed(ServerLevel level, VehicleData data) {
		ItemStack stack = kit(data.type);
		CompoundTag tag = customData(stack);
		VehicleData.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), data)
			.resultOrPartial(error -> MobileHomeMod.LOG.error("Could not pack vehicle: {}", error))
			.ifPresent(saved -> tag.put(SAVED, saved));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Right-click the ground to park it.", ChatFormatting.GRAY));
		lore.add(text("Fuel: " + data.fuelPercent() + "%", ChatFormatting.GOLD));
		lore.add(text("Storage: " + data.usedSlots() + " / " + VehicleData.SLOTS + " slots used", ChatFormatting.YELLOW));
		if (data.locked) {
			lore.add(text("Driver's seat and storage locked", ChatFormatting.DARK_GRAY));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static CompoundTag customData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static @Nullable VehicleType type(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String id = customData(stack).getStringOr(KEY, "");
		return id.isEmpty() ? null : VehicleType.byId(id);
	}

	public static @Nullable VehicleData savedData(ItemStack stack, ServerLevel level) {
		CompoundTag tag = customData(stack);
		if (!tag.contains(SAVED)) {
			return null;
		}
		return VehicleData.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get(SAVED))
			.resultOrPartial(error -> MobileHomeMod.LOG.error("Could not unpack vehicle: {}", error))
			.orElse(null);
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
