package com.thatcoffeelock.proost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * Every Proost item is a vanilla item with custom data, so vanilla clients can join:
 * <ul>
 * <li>Mash: paper that looks like a bowl of suspicious stew (it is suspicious, frankly)</li>
 * <li>A bottle of spirit: paper that looks like a bottle. Opened, it's a single item whose durability bar is the
 * swigs left (like a lit Havana cigar)</li>
 * </ul>
 * Using one as plain paper in a recipe only ever turns it into something cheaper, so that's allowed.
 */
public final class ProostItems {
	static final String KEY = "proost";
	static final String MASH = "mash";
	static final String SPIRIT = "spirit";
	static final String BASE = "base";
	static final String DRINK = "drink";
	static final String AGED = "aged";
	static final String OPEN = "open";

	/** Swigs in one bottle. */
	static final int SWIGS = 4;
	static final int BOTTLE_STACK = 16;

	/** What a mash is made from. A Mash Tun turns one crop into one mash. */
	enum Base {
		GRAIN("Grain Mash", "minecraft:wheat"),
		CANE("Cane Mash", "minecraft:sugar_cane"),
		APPLE("Apple Mash", "minecraft:apple");

		final String title;
		final String crop;

		Base(String title, String crop) {
			this.title = title;
			this.crop = crop;
		}

		String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The base this crop ferments into, or null. Only the plain vanilla crop counts. */
		static @Nullable Base ofCrop(ItemStack stack) {
			if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_DATA)) {
				return null;
			}
			String id = ProostItems.id(stack.getItem());
			for (Base base : values()) {
				if (base.crop.equals(id)) {
					return base;
				}
			}
			return null;
		}

		static @Nullable Base byName(String name) {
			for (Base base : values()) {
				if (base.id().equalsIgnoreCase(name)) {
					return base;
				}
			}
			return null;
		}
	}

	/** A spirit: what it's distilled from, what each swig does, and what it's called young and aged. */
	enum Drink {
		GENEVER("Jonge Genever", "Oude Genever", Base.GRAIN, "glass_bottle", ChatFormatting.AQUA,
			"Dutch Courage: Strength.", "Strength and Resistance. Your great-grandad's favourite."),
		WHISKY("Whisky", "Aged Whisky", Base.GRAIN, "honey_bottle", ChatFormatting.GOLD,
			"Warms the bones: Resistance.", "Resistance and Regeneration. Peaty. Smug."),
		RUM("White Rum", "Dark Rum", Base.CANE, "ominous_bottle", ChatFormatting.DARK_RED,
			"A sailor's friend: Water Breathing.", "Water Breathing and Dolphin's Grace. Yo ho."),
		BRANDY("Brandy", "Brandy VSOP", Base.APPLE, "dragon_breath", ChatFormatting.RED,
			"Puts a spring in your pick: Haste.", "Haste and Absorption. Very Superior, Old, Pretentious.");

		final String young;
		final String old;
		final Base base;
		final String model;
		final ChatFormatting format;
		final String youngBlurb;
		final String oldBlurb;

		Drink(String young, String old, Base base, String model, ChatFormatting format, String youngBlurb, String oldBlurb) {
			this.young = young;
			this.old = old;
			this.base = base;
			this.model = model;
			this.format = format;
			this.youngBlurb = youngBlurb;
			this.oldBlurb = oldBlurb;
		}

		String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		String title(boolean aged) {
			return aged ? old : young;
		}

		static @Nullable Drink byName(String name) {
			for (Drink drink : values()) {
				if (drink.id().equalsIgnoreCase(name)) {
					return drink;
				}
			}
			return null;
		}
	}

	private static final Map<String, Item> ITEMS = new HashMap<>();

	private ProostItems() {
	}

	/** Looks an item up by id. */
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

	private static ItemStack make(int count, String kind, String model) {
		ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(model));
		return stack;
	}

	public static ItemStack mash(Base base, int count) {
		ItemStack stack = make(count, MASH, "suspicious_stew");
		CompoundTag tag = data(stack);
		tag.putString(BASE, base.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal(base.title).withStyle(ChatFormatting.YELLOW));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Bubbling, sour and full of promise.", ChatFormatting.GRAY));
		lore.add(text(switch (base) {
			case GRAIN -> "Distills into whisky, or genever with sweet berries.";
			case CANE -> "Distills into rum.";
			case APPLE -> "Distills into brandy.";
		}, ChatFormatting.DARK_GRAY));
		lore.add(text("Hold it and right-click a cauldron over a fire.", ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack spirit(Drink drink, boolean aged, int count) {
		ItemStack stack = make(count, SPIRIT, drink.model);
		CompoundTag tag = data(stack);
		tag.putString(DRINK, drink.id());
		tag.putInt(AGED, aged ? 1 : 0);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, BOTTLE_STACK);
		stack.set(DataComponents.ITEM_NAME, Component.literal(drink.title(aged)).withStyle(aged ? ChatFormatting.LIGHT_PURPLE : drink.format));
		stack.set(DataComponents.LORE, new ItemLore(spiritLore(drink, aged, false, SWIGS)));
		if (aged) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	private static List<Component> spiritLore(Drink drink, boolean aged, boolean open, int swigsLeft) {
		List<Component> lore = new ArrayList<>();
		lore.add(text(aged ? drink.oldBlurb : drink.youngBlurb, ChatFormatting.GRAY));
		if (open) {
			lore.add(text("Opened: " + swigsLeft + " swigs left. Right-click to drink.", ChatFormatting.GOLD));
		} else {
			lore.add(text(SWIGS + " swigs. Right-click to drink.", ChatFormatting.DARK_GRAY));
			if (!aged) {
				lore.add(text("Leave it in an Oak Cask for two days to age it.", ChatFormatting.DARK_GRAY));
			}
		}
		lore.add(text("Drink too much and the world starts to spin.", ChatFormatting.DARK_GRAY));
		return lore;
	}

	/** One opened bottle made from (one of) these. Keeps how much was already drunk. */
	static ItemStack opened(ItemStack bottle) {
		ItemStack stack = bottle.copyWithCount(1);
		int drunk = isOpen(bottle) ? bottle.getDamageValue() : 0;
		CompoundTag tag = data(stack);
		tag.putInt(OPEN, 1);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.MAX_DAMAGE, SWIGS);
		stack.set(DataComponents.DAMAGE, drunk);
		stack.set(DataComponents.LORE, new ItemLore(spiritLore(drink(stack), isAged(stack), true, SWIGS - drunk)));
		return stack;
	}

	/** Refreshes the "swigs left" line after a swig. */
	static void updateLore(ItemStack bottle) {
		bottle.set(DataComponents.LORE, new ItemLore(spiritLore(drink(bottle), isAged(bottle), true, SWIGS - bottle.getDamageValue())));
	}

	public static ItemStack mashTun() {
		ItemStack stack = new ItemStack(item("minecraft:barrel", Items.CHEST));
		stack.set(DataComponents.CUSTOM_NAME, text("Mash Tun", ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Wheat, sugar cane or apples ferment into mash in here.", ChatFormatting.GRAY),
			text("Takes about half a day. Keeps working while you're away.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static ItemStack oakCask() {
		ItemStack stack = new ItemStack(item("minecraft:barrel", Items.CHEST));
		stack.set(DataComponents.CUSTOM_NAME, text("Oak Cask", ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Full bottles of young spirit age in here (about two days).", ChatFormatting.GRAY),
			text("Keeps working while you're away.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	// ---------------------------------------------------------------- reading

	static CompoundTag data(ItemStack stack) {
		if (stack.isEmpty()) {
			return new CompoundTag();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** "mash", "spirit", or "" for anything else. */
	static String kind(ItemStack stack) {
		return data(stack).getStringOr(KEY, "");
	}

	public static boolean isMash(ItemStack stack) {
		return stack.is(Items.PAPER) && MASH.equals(kind(stack));
	}

	public static boolean isSpirit(ItemStack stack) {
		return stack.is(Items.PAPER) && SPIRIT.equals(kind(stack));
	}

	static @Nullable Base base(ItemStack stack) {
		return Base.byName(data(stack).getStringOr(BASE, ""));
	}

	static Drink drink(ItemStack stack) {
		Drink drink = Drink.byName(data(stack).getStringOr(DRINK, ""));
		return drink == null ? Drink.WHISKY : drink;
	}

	static boolean isAged(ItemStack stack) {
		return data(stack).getIntOr(AGED, 0) == 1;
	}

	/** Opened once: carries its own swig count, and doesn't stack or age any more. */
	static boolean isOpen(ItemStack stack) {
		return isSpirit(stack) && stack.has(DataComponents.MAX_DAMAGE);
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	/**
	 * Makes Proost items by name, for /proost give and other mods (Colonycraft's distillery and tavern):
	 * "mash:grain", "genever", "genever:aged", "tun", "cask". Empty if the name means nothing.
	 */
	static ItemStack byName(String what, int count) {
		if (what == null) {
			return ItemStack.EMPTY;
		}
		String[] parts = what.toLowerCase(Locale.ROOT).split(":");
		if (parts[0].equals("tun")) {
			return mashTun();
		}
		if (parts[0].equals("cask")) {
			return oakCask();
		}
		if (parts[0].equals(MASH)) {
			Base base = parts.length > 1 ? Base.byName(parts[1]) : null;
			return base == null ? ItemStack.EMPTY : mash(base, count);
		}
		Drink drink = Drink.byName(parts[0]);
		if (drink == null) {
			return ItemStack.EMPTY;
		}
		return spirit(drink, parts.length > 1 && parts[1].equals(AGED), count);
	}
}
