package com.thatcoffeelock.market;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Market items are vanilla items tagged with custom data, so players don't need the mod installed.
 */
public final class MarketItems {
	public static final String MARKET_BLOCK = "market_block";
	public static final String BANKNOTE = "market_note";
	public static final String VANITY = "market_vanity";

	private MarketItems() {
	}

	public static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	// ---------------------------------------------------------------- market block

	/** Same item the crafting recipe produces (see data/market/recipe/market_block.json). */
	public static ItemStack marketBlock() {
		ItemStack stack = new ItemStack(Items.LECTERN);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARKET_BLOCK, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Market").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Gui.text("Place it, then right-click to trade.", ChatFormatting.GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static boolean isMarketBlock(ItemStack stack) {
		return stack.is(Items.LECTERN) && data(stack).contains(MARKET_BLOCK);
	}

	// ---------------------------------------------------------------- banknotes

	public static ItemStack banknote(long cents) {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putLong(BANKNOTE, cents);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Banknote: " + Money.format(cents)).withStyle(ChatFormatting.GREEN));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Gui.text("Right-click to deposit.", ChatFormatting.GRAY),
			Gui.text("Sneak + right-click to deposit the whole stack.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static long banknoteValue(ItemStack stack) {
		if (!stack.is(Items.PAPER)) {
			return 0;
		}
		return Math.max(0, data(stack).getLongOr(BANKNOTE, 0L));
	}

	// ---------------------------------------------------------------- vanity

	public static ItemStack vanity(Vanity.Type type) {
		ItemStack stack = new ItemStack(type.icon);
		CompoundTag tag = new CompoundTag();
		tag.putString(VANITY, type.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal(type.displayName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Gui.text(type.description, ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Gui.text("Right-click a block to place it.", ChatFormatting.YELLOW));
		lore.add(Gui.text("Sneak + right-click it (empty hand) to pick it up.", ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static @Nullable Vanity.Type vanityType(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String id = data(stack).getStringOr(VANITY, "");
		return id.isEmpty() ? null : Vanity.Type.byId(id);
	}
}
