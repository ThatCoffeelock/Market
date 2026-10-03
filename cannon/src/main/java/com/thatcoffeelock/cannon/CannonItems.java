package com.thatcoffeelock.cannon;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The cannon and its cannonballs as items: vanilla items tagged with custom data, so vanilla clients see a
 * glowing dispenser and a grey firework star. Must match the results in data/cannon/recipe.
 */
public final class CannonItems {
	static final String KEY = "cannon";
	static final String CANNON = "cannon";
	static final String CANNONBALL = "cannonball";

	private CannonItems() {
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack cannon() {
		ItemStack stack = new ItemStack(Items.DISPENSER);
		tag(stack, CANNON);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Cannon").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Right-click the ground to place it.", ChatFormatting.GRAY),
			text("Right-click it to man it. Look to aim, Space to fire.", ChatFormatting.DARK_GRAY),
			text("Sneak + right-click to pick it back up.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static ItemStack cannonballs(int count) {
		ItemStack stack = new ItemStack(Items.FIREWORK_STAR, count);
		tag(stack, CANNONBALL);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Cannonball").withStyle(ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Ammunition for a cannon.", ChatFormatting.GRAY),
			text("Keep it in your inventory while you man one.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	private static void tag(ItemStack stack, String kind) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static String kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(KEY, "");
	}

	public static boolean isCannon(ItemStack stack) {
		return CANNON.equals(kind(stack));
	}

	public static boolean isCannonball(ItemStack stack) {
		return CANNONBALL.equals(kind(stack));
	}

	public static int countCannonballs(Player player) {
		Inventory inventory = player.getInventory();
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isCannonball(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Takes one cannonball out of the player's inventory. False if they have none. */
	public static boolean takeCannonball(Player player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isCannonball(stack)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
