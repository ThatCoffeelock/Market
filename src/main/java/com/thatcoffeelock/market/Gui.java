package com.thatcoffeelock.market;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

/** Small helpers for building chest-GUI icons out of vanilla items. */
public final class Gui {
	private Gui() {
	}

	/** Non-italic text (lore is italic by default, which looks messy). */
	public static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	public static ItemStack icon(ItemLike item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack icon(ItemLike item, Component name, Component... lore) {
		return icon(item, name, List.of(lore));
	}

	public static ItemStack glow(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Adds lore lines to a copy of a real item (used to show prices in the shop). */
	public static ItemStack withLore(ItemStack base, List<Component> lore) {
		ItemStack stack = base.copy();
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack filler() {
		return icon(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "));
	}
}
