package com.thatcoffeelock.tulpengekte;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * A tulip bulb is paper with custom data, drawn as the tulip it will flower into, so vanilla clients can join. Paper
 * does nothing when you right-click with it, so planting is all ours ({@link Beds}). Crafting one into a book is
 * allowed. It's a very expensive book.
 */
public final class TulpItems {
	static final String KEY = "tulpengekte";
	static final String BULB = "bulb";
	static final String VARIETY = "variety";

	private static final Map<String, Item> ITEMS = new HashMap<>();
	private static final Random RANDOM = new Random();

	private TulpItems() {
	}

	/** Looks an item up by id (the tulips and dyed panes aren't all constants in Items any more). */
	static Item item(String id, Item fallback) {
		if (ITEMS.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				ITEMS.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
			}
		}
		Item item = ITEMS.get(id);
		return item == null || item == Items.AIR ? fallback : item;
	}

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	// ---------------------------------------------------------------- building

	static ItemStack bulb(Variety variety, int count) {
		ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, BULB);
		tag.putString(VARIETY, variety.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(variety.color + "_tulip"));
		stack.set(DataComponents.ITEM_NAME, Component.literal(variety.title).withStyle(variety.tier.format));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Tulip bulb · " + variety.tier.title, variety.tier.format));
		lore.add(text(variety.story, ChatFormatting.GRAY));
		lore.add(text("Plant it on grass, dirt or farmland. It flowers in about a day.", ChatFormatting.DARK_GRAY));
		lore.add(text("Lift (break) the flower for the bulb back, and maybe offsets.", ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		if (variety.tier == Variety.Tier.RARE || variety.tier == Variety.Tier.LEGENDARY) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	/**
	 * Bulbs by name, for other mods ({@link TulpMod#ITEM_HOOK}) and /tulpengekte give: a variety id, or a tier
	 * ("common", "fine", "rare", "legendary") for a random variety of it. Empty for anything else.
	 */
	static ItemStack byKind(String kind, Integer count) {
		int n = count == null ? 1 : Math.max(1, count);
		if (kind == null) {
			return ItemStack.EMPTY;
		}
		Variety variety = Variety.byId(kind);
		if (variety != null) {
			return bulb(variety, n);
		}
		for (Variety.Tier tier : Variety.Tier.values()) {
			if (tier.name().equalsIgnoreCase(kind) || tier.title.toLowerCase(Locale.ROOT).equals(kind.toLowerCase(Locale.ROOT))) {
				return bulb(Variety.random(tier, RANDOM), n);
			}
		}
		return ItemStack.EMPTY;
	}

	// ---------------------------------------------------------------- reading

	static CompoundTag data(ItemStack stack) {
		if (stack.isEmpty()) {
			return new CompoundTag();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** The bulb's variety, or null if this isn't a tulip bulb. */
	static @Nullable Variety variety(ItemStack stack) {
		if (!stack.is(Items.PAPER)) {
			return null;
		}
		CompoundTag tag = data(stack);
		if (!BULB.equals(tag.getStringOr(KEY, ""))) {
			return null;
		}
		return Variety.byId(tag.getStringOr(VARIETY, ""));
	}

	static boolean isBulb(ItemStack stack) {
		return variety(stack) != null;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
