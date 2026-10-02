package com.thatcoffeelock.cargotrain;

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

/**
 * The train is a furnace minecart with custom data (vanilla clients see a glowing furnace minecart), an extra
 * wagon is a chest minecart with custom data.
 * A station is just a chest with a station name. Must match the results in data/cargotrain/recipe.
 */
public final class TrainItems {
	static final String KEY = "cargotrain";
	static final String TRAIN = "train";
	static final String WAGON = "wagon";

	private TrainItems() {
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack train() {
		ItemStack stack = new ItemStack(Items.FURNACE_MINECART);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, TRAIN);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Cargo Train").withStyle(ChatFormatting.YELLOW));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("A locomotive and a cargo wagon.", ChatFormatting.GRAY),
			text("Right-click a rail to put it on the track.", ChatFormatting.GRAY),
			text("It shuttles between the ends of the line by itself,", ChatFormatting.DARK_GRAY),
			text("stopping at every Pickup / Drop-off / Swap Station.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static ItemStack wagon() {
		ItemStack stack = new ItemStack(Items.CHEST_MINECART);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, WAGON);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Cargo Wagon").withStyle(ChatFormatting.YELLOW));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("27 more slots for your Cargo Train.", ChatFormatting.GRAY),
			text("Right-click your train (standing still) to couple it.", ChatFormatting.GRAY),
			text("Up to " + TrainData.MAX_WAGONS + " wagons per train.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static String kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(KEY, "");
	}

	public static boolean isTrain(ItemStack stack) {
		return stack.is(Items.FURNACE_MINECART) && TRAIN.equals(kind(stack));
	}

	public static boolean isWagon(ItemStack stack) {
		return stack.is(Items.CHEST_MINECART) && WAGON.equals(kind(stack));
	}

	public static ItemStack station(Stations.Mode mode) {
		ItemStack stack = new ItemStack(Items.CHEST);
		stack.set(DataComponents.CUSTOM_NAME, text(mode.title, mode.format));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text(mode.blurb, ChatFormatting.GRAY),
			text("Place it right next to the track.", ChatFormatting.DARK_GRAY),
			text("Sneak + right-click it (empty hand) to switch mode.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
