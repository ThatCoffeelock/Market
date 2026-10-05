package com.thatcoffeelock.riches;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Everything Riches hands out, as vanilla items tagged with custom data. The blocks must match the results in
 * data/riches/recipe, so crafted and given items stack. Relics are paper wearing another item's model.
 */
public final class RichesItems {
	static final String KEY = "riches";
	static final String LEDGER = "vault_ledger";
	static final String DOOR = "vault_door";
	static final String CASE = "display_case";
	static final String PEDESTAL = "pedestal";
	static final String RELIC = "relic";

	private RichesItems() {
	}

	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	private static ItemStack make(Item base, String kind, String name, ChatFormatting color, int stack, Component... lore) {
		ItemStack item = new ItemStack(base);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		item.set(DataComponents.MAX_STACK_SIZE, stack);
		item.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color));
		item.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return item;
	}

	static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	static String kind(ItemStack stack) {
		return stack.isEmpty() ? "" : data(stack).getStringOr(KEY, "");
	}

	// ---------------------------------------------------------------- the blocks (keep identical to data/riches/recipe)

	public static ItemStack ledger() {
		return make(Items.LODESTONE, LEDGER, "Vault Ledger", ChatFormatting.GOLD, 1,
			text("Put it on the floor of your vault.", ChatFormatting.GRAY),
			text("Your Market balance piles up around it in gold.", ChatFormatting.GRAY),
			text("Walk in. Wade around. You earned it.", ChatFormatting.DARK_GRAY));
	}

	public static ItemStack door() {
		return make(Items.IRON_DOOR, DOOR, "Vault Door", ChatFormatting.GOLD, 16,
			text("Opens for you and the players you trust.", ChatFormatting.GRAY),
			text("Everyone else gets a closed door. Swings shut by itself.", ChatFormatting.DARK_GRAY));
	}

	public static ItemStack displayCase() {
		return make(Items.GLASS, CASE, "Display Case", ChatFormatting.AQUA, 64,
			text("Right-click it with something to put it on show.", ChatFormatting.GRAY),
			text("A brass plaque says what it is and who put it there.", ChatFormatting.DARK_GRAY));
	}

	public static ItemStack pedestal() {
		return make(Items.QUARTZ_PILLAR, PEDESTAL, "Pedestal", ChatFormatting.AQUA, 64,
			text("Right-click it with something to show it off on top.", ChatFormatting.GRAY),
			text("A brass plaque says what it is and who put it there.", ChatFormatting.DARK_GRAY));
	}

	// ---------------------------------------------------------------- relics

	public static ItemStack relic(Relic relic) {
		ItemStack item = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, RELIC);
		tag.putString(RELIC, relic.id());
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		item.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(relic.model));
		item.set(DataComponents.MAX_STACK_SIZE, 1);
		item.set(DataComponents.ITEM_NAME, Component.literal(relic.title).withStyle(relic.collection.color, ChatFormatting.BOLD));
		item.set(DataComponents.LORE, new ItemLore(List.of(
			text("Relic · " + relic.collection.title, relic.collection.color),
			text(relic.story, ChatFormatting.GRAY),
			text(RichesConfig.get().uniqueRelics ? "One of a kind. There is no other." : "A rare find.", ChatFormatting.DARK_GRAY))));
		item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return item;
	}

	static @Nullable Relic relicOf(ItemStack stack) {
		return RELIC.equals(kind(stack)) ? Relic.byId(data(stack).getStringOr(RELIC, "")) : null;
	}

	static boolean isLedger(ItemStack stack) {
		return stack.is(Items.LODESTONE) && LEDGER.equals(kind(stack));
	}

	static boolean isDoor(ItemStack stack) {
		return stack.is(Items.IRON_DOOR) && DOOR.equals(kind(stack));
	}

	static boolean isCase(ItemStack stack) {
		return stack.is(Items.GLASS) && CASE.equals(kind(stack));
	}

	static boolean isPedestal(ItemStack stack) {
		return stack.is(Items.QUARTZ_PILLAR) && PEDESTAL.equals(kind(stack));
	}

	static void give(Player player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}
}
