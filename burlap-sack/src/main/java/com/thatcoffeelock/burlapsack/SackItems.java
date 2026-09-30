package com.thatcoffeelock.burlapsack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * The Burlap Sack as an item: a vanilla bundle with its contents component taken away (so it can't hold items)
 * and custom data on top. A full sack carries the captive's saved entity data. Must match data/burlapsack/recipe.
 */
public final class SackItems {
	static final String KEY = "burlapsack";
	static final String EMPTY = "empty";
	static final String FULL = "full";
	/** The captive's entity type id, e.g. minecraft:villager. */
	static final String TYPE = "type";
	/** The captive's saved entity data (no UUID, position or brain). */
	static final String CAPTIVE = "captive";
	/** The captive's display name, for the item name and messages. */
	static final String NAME = "name";

	private SackItems() {
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack empty() {
		ItemStack stack = base();
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, EMPTY);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Burlap Sack").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Right-click a villager or wandering trader", ChatFormatting.GRAY),
			text("to stuff them in the sack.", ChatFormatting.GRAY),
			text("Iron golems will not approve.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	/** A sack with someone in it. */
	public static ItemStack full(String type, String name, CompoundTag captive) {
		ItemStack stack = base();
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, FULL);
		tag.putString(TYPE, type);
		tag.putString(NAME, name);
		tag.put(CAPTIVE, captive);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Burlap Sack (" + name + ")").withStyle(ChatFormatting.GOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Contains: " + name + ", " + describe(type, captive), ChatFormatting.WHITE));
		lore.add(text("Right-click a block to let them out.", ChatFormatting.GRAY));
		lore.add(text("They are wriggling. And complaining.", ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static ItemStack base() {
		ItemStack stack = new ItemStack(Items.BUNDLE);
		stack.remove(DataComponents.BUNDLE_CONTENTS); // no item storage: it's for people
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		return stack;
	}

	/** "a level 3 farmer", "a baby villager", "a wandering trader". */
	static String describe(String type, CompoundTag captive) {
		if (type.endsWith("wandering_trader")) {
			return "a wandering trader";
		}
		if (captive.getIntOr("Age", 0) < 0) {
			return "a baby villager";
		}
		CompoundTag data = captive.getCompoundOrEmpty("VillagerData");
		String profession = data.getStringOr("profession", "minecraft:none");
		profession = profession.substring(profession.indexOf(':') + 1).replace('_', ' ');
		if (profession.equals("none")) {
			return "an unemployed villager";
		}
		if (profession.equals("nitwit")) {
			return "a nitwit";
		}
		return "a level " + data.getIntOr("level", 1) + " " + profession.toLowerCase(Locale.ROOT);
	}

	private static CompoundTag data(ItemStack stack) {
		if (stack.isEmpty()) {
			return new CompoundTag();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static boolean isEmptySack(ItemStack stack) {
		return EMPTY.equals(data(stack).getStringOr(KEY, ""));
	}

	public static boolean isFullSack(ItemStack stack) {
		return FULL.equals(data(stack).getStringOr(KEY, ""));
	}

	static String type(ItemStack stack) {
		return data(stack).getStringOr(TYPE, "");
	}

	static String name(ItemStack stack) {
		return data(stack).getStringOr(NAME, "Someone");
	}

	static CompoundTag captive(ItemStack stack) {
		return data(stack).getCompoundOrEmpty(CAPTIVE);
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
