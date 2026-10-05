package com.thatcoffeelock.fossilfool;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

/** Small helpers for chest-GUI icons and chat text. */
final class Gui {
	private Gui() {
	}

	/** Non-italic text (lore is italic by default, which looks messy). */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	static ItemStack icon(ItemLike item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	static ItemStack glow(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static final Map<String, Item> ITEMS = new HashMap<>();

	/** An item by id, for the ones that aren't constants in Items (dyed glass panes, say). */
	static Item item(String id, Item fallback) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id);
		return item == null || item == Items.AIR ? fallback : item;
	}

	static ItemStack filler() {
		return icon(item("minecraft:gray_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of());
	}

	/** 48213 -> "48,213". */
	static String n(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	/** "■■■■□□□□□□" for a gauge. */
	static String bar(double fraction, int width) {
		int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
		return "■".repeat(filled) + "□".repeat(width - filled);
	}
}
