package com.thatcoffeelock.colonycraft;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/** Blueprints (and the Colony Charter): paper with custom data, so vanilla clients see glowing paper. */
public final class Blueprints {
	static final String KEY = "colonycraft_blueprint";

	private Blueprints() {
	}

	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack of(BuildingType type) {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, type.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, 16);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		if (type == BuildingType.TOWN_HALL) {
			stack.set(DataComponents.ITEM_NAME, Component.literal("Colony Charter").withStyle(ChatFormatting.GOLD));
			stack.set(DataComponents.LORE, new ItemLore(List.of(
				text("Right-click the ground to found a new colony.", ChatFormatting.GRAY),
				text("A Town Hall gets built there. Its counter sells", ChatFormatting.DARK_GRAY),
				text("everything else. Rename this in an anvil to name the colony.", ChatFormatting.DARK_GRAY))));
		} else {
			stack.set(DataComponents.ITEM_NAME, Component.literal("Blueprint: " + type.displayName).withStyle(ChatFormatting.AQUA));
			stack.set(DataComponents.LORE, new ItemLore(List.of(
				text("Right-click the ground inside your colony to build.", ChatFormatting.GRAY),
				text(type.housing(1) > 0 ? "Sleeps " + type.housing(1) + " workers." : "Comes with " + type.workers(1) + " workers.", ChatFormatting.DARK_GRAY))));
		}
		return stack;
	}

	public static @Nullable BuildingType type(ItemStack stack) {
		if (!stack.is(Items.PAPER)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String id = data.copyTag().getStringOr(KEY, "");
		return id.isEmpty() ? null : BuildingType.byId(id);
	}

	public static @Nullable String customName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}

	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
