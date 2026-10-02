package com.thatcoffeelock.havana;

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
 * Every Havana item is a vanilla item with custom data, so vanilla clients can join:
 * <ul>
 * <li>Tobacco Seeds: beetroot seeds (planting is handled by {@link Crops}, so they never grow beetroot)</li>
 * <li>Tobacco Leaf, Cured Tobacco, Aged Tobacco: paper that looks like a fern, a rabbit hide and leather</li>
 * <li>Cigar: a stick (it already looks like one). A lit cigar is a single stick with a durability bar: the puffs left</li>
 * </ul>
 * Using one as its vanilla base in a recipe only ever turns it into something cheaper, so that's allowed.
 */
public final class HavanaItems {
	static final String KEY = "havana";
	static final String SEEDS = "seeds";
	static final String LEAF = "leaf";
	static final String CURED = "cured";
	static final String AGED = "aged";
	static final String CIGAR = "cigar";
	static final String GRADE = "grade";
	static final String FLAVOR = "flavor";
	static final String LIT = "lit";

	/** Puffs in one cigar. */
	static final int PUFFS = 8;
	static final int CIGAR_STACK = 16;

	/** What a cigar is rolled from. */
	enum Grade {
		CURED("Cigar", ChatFormatting.GOLD),
		AGED("Gran Reserva", ChatFormatting.LIGHT_PURPLE);

		final String title;
		final ChatFormatting format;

		Grade(String title, ChatFormatting format) {
			this.title = title;
			this.format = format;
		}
	}

	/** An optional extra rolled into the cigar. Each puff gives its effect on top of the grade's. */
	enum Flavor {
		NONE("", "", null, "", 0, ""),
		HONEY("Honey", "minecraft:honey_bottle", "minecraft:glass_bottle", "absorption", 30, "Sweet and sticky. Like your fingers now."),
		CHOCOLATE("Chocolate", "minecraft:cocoa_beans", null, "haste", 30, "Mine faster. Talk slower."),
		BERRY("Berry", "minecraft:sweet_berries", null, "speed", 30, "Fruity. Your grandad would not approve."),
		GLOW("Glow", "minecraft:glow_berries", null, "night_vision", 45, "See in the dark. Look cool in the dark."),
		BLAZE("Blaze", "minecraft:blaze_powder", null, "fire_resistance", 30, "For when the cigar isn't hot enough.");

		final String title;
		final String ingredient;
		final @Nullable String remainder;
		final String effect;
		final int seconds;
		final String blurb;

		Flavor(String title, String ingredient, @Nullable String remainder, String effect, int seconds, String blurb) {
			this.title = title;
			this.ingredient = ingredient;
			this.remainder = remainder;
			this.effect = effect;
			this.seconds = seconds;
			this.blurb = blurb;
		}

		/** The flavor this ingredient makes, or null if it isn't one. */
		static @Nullable Flavor of(ItemStack stack) {
			if (stack.isEmpty() || isHavana(stack)) {
				return null;
			}
			String id = id(stack.getItem());
			for (Flavor flavor : values()) {
				if (flavor != NONE && flavor.ingredient.equals(id)) {
					return flavor;
				}
			}
			return null;
		}

		static Flavor byName(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return NONE;
			}
		}
	}

	private static final Map<String, Item> ITEMS = new HashMap<>();

	private HavanaItems() {
	}

	/** Looks an item up by id (some, like the dyed ones, aren't constants in Items any more). */
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

	private static ItemStack make(Item base, int count, String kind, String model) {
		ItemStack stack = new ItemStack(base, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (!model.isEmpty()) {
			stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(model));
		}
		return stack;
	}

	public static ItemStack seeds(int count) {
		ItemStack stack = make(Items.BEETROOT_SEEDS, count, SEEDS, "");
		stack.set(DataComponents.ITEM_NAME, Component.literal("Tobacco Seeds").withStyle(ChatFormatting.YELLOW));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Plant them on farmland.", ChatFormatting.GRAY),
			text("Fully grown, the plant shoots up two blocks tall.", ChatFormatting.DARK_GRAY),
			text("Bone meal works. Patience works too.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static ItemStack leaf(int count) {
		ItemStack stack = make(Items.PAPER, count, LEAF, "fern");
		stack.set(DataComponents.ITEM_NAME, Component.literal("Tobacco Leaf").withStyle(ChatFormatting.GREEN));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Fresh off the plant. Far too green to smoke.", ChatFormatting.GRAY),
			text("Cure it in a Curing Barrel (about one day).", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static ItemStack cured(int count) {
		ItemStack stack = make(Items.PAPER, count, CURED, "rabbit_hide");
		stack.set(DataComponents.ITEM_NAME, Component.literal("Cured Tobacco").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Golden, dry and fragrant.", ChatFormatting.GRAY),
			text("Hold it and right-click a crafting table to roll cigars.", ChatFormatting.DARK_GRAY),
			text("Or leave it in the barrel two more days to age it.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static ItemStack aged(int count) {
		ItemStack stack = make(Items.PAPER, count, AGED, "leather");
		stack.set(DataComponents.ITEM_NAME, Component.literal("Aged Tobacco").withStyle(ChatFormatting.LIGHT_PURPLE));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Dark, rich, and smug about it.", ChatFormatting.GRAY),
			text("Rolls into Gran Reserva cigars.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** The tobacco of this kind (leaf, cured or aged). */
	static ItemStack tobacco(String kind, int count) {
		return switch (kind) {
			case LEAF -> leaf(count);
			case CURED -> cured(count);
			case AGED -> aged(count);
			default -> throw new IllegalArgumentException(kind);
		};
	}

	static String cigarName(Grade grade, Flavor flavor) {
		String name = flavor == Flavor.NONE ? "Cigar" : flavor.title + " Cigar";
		return grade == Grade.AGED ? "Gran Reserva " + name : name;
	}

	public static ItemStack cigar(Grade grade, Flavor flavor, int count) {
		ItemStack stack = make(Items.STICK, count, CIGAR, "");
		CompoundTag tag = data(stack);
		tag.putString(GRADE, grade.name().toLowerCase(Locale.ROOT));
		tag.putString(FLAVOR, flavor.name().toLowerCase(Locale.ROOT));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, CIGAR_STACK);
		stack.set(DataComponents.ITEM_NAME, Component.literal(cigarName(grade, flavor)).withStyle(grade.format));
		stack.set(DataComponents.LORE, new ItemLore(cigarLore(grade, flavor, false, PUFFS)));
		return stack;
	}

	private static List<Component> cigarLore(Grade grade, Flavor flavor, boolean lit, int puffsLeft) {
		List<Component> lore = new ArrayList<>();
		lore.add(text(grade == Grade.AGED
			? "Puffs: Regeneration and Resistance. Finish it for Hero of the Village."
			: "Puffs: Regeneration.", ChatFormatting.GRAY));
		if (flavor != Flavor.NONE) {
			lore.add(text(flavor.title + ": " + flavor.blurb, ChatFormatting.GRAY));
		}
		if (lit) {
			lore.add(text("Lit. Right-click to puff (" + puffsLeft + " left).", ChatFormatting.GOLD));
			lore.add(text("Don't puff too fast. You'll cough.", ChatFormatting.DARK_GRAY));
		} else {
			if (puffsLeft < PUFFS) {
				lore.add(text("Half-smoked: " + puffsLeft + " puffs left.", ChatFormatting.DARK_GRAY));
			}
			lore.add(text("Light it: flint and steel (or a fire charge) in your", ChatFormatting.DARK_GRAY));
			lore.add(text("other hand, or right-click a campfire, torch or lantern.", ChatFormatting.DARK_GRAY));
		}
		return lore;
	}

	/** One lit cigar made from (one of) these. Keeps how far it was already smoked. */
	static ItemStack lit(ItemStack cigar) {
		return relight(cigar, true);
	}

	/** A lit cigar that went out: still single, still half-smoked. */
	static ItemStack extinguished(ItemStack cigar) {
		return relight(cigar, false);
	}

	private static ItemStack relight(ItemStack cigar, boolean lit) {
		ItemStack stack = cigar.copyWithCount(1);
		int smoked = isSingle(cigar) ? cigar.getDamageValue() : 0;
		CompoundTag tag = data(stack);
		tag.putInt(LIT, lit ? 1 : 0);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.MAX_DAMAGE, PUFFS);
		stack.set(DataComponents.DAMAGE, smoked);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, lit);
		stack.set(DataComponents.LORE, new ItemLore(cigarLore(grade(stack), flavor(stack), lit, PUFFS - smoked)));
		return stack;
	}

	/** Refreshes the "puffs left" line after a puff. */
	static void updateLore(ItemStack cigar) {
		cigar.set(DataComponents.LORE, new ItemLore(cigarLore(grade(cigar), flavor(cigar), isLit(cigar), PUFFS - cigar.getDamageValue())));
	}

	/** Lit, or lit once and put out: carries its own puff count. */
	private static boolean isSingle(ItemStack stack) {
		return stack.has(DataComponents.MAX_DAMAGE);
	}

	/** Gives the player a stack of this kind. Used by /havana give. */
	static @Nullable ItemStack byName(String what, int count) {
		return switch (what) {
			case SEEDS -> seeds(count);
			case "leaves", LEAF -> leaf(count);
			case CURED -> cured(count);
			case AGED -> aged(count);
			default -> null;
		};
	}

	public static ItemStack curingBarrel() {
		ItemStack stack = new ItemStack(item("minecraft:barrel", Items.CHEST));
		stack.set(DataComponents.CUSTOM_NAME, text("Curing Barrel", ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Fresh tobacco leaves cure in here (about one day).", ChatFormatting.GRAY),
			text("Leave cured tobacco in two more days and it ages.", ChatFormatting.GRAY),
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

	/** "seeds", "leaf", "cured", "aged", "cigar", or "" for anything else. */
	static String kind(ItemStack stack) {
		return data(stack).getStringOr(KEY, "");
	}

	static boolean isHavana(ItemStack stack) {
		return !kind(stack).isEmpty();
	}

	public static boolean isSeeds(ItemStack stack) {
		return stack.is(Items.BEETROOT_SEEDS) && SEEDS.equals(kind(stack));
	}

	/** Leaf, cured or aged tobacco. */
	static boolean isTobacco(ItemStack stack) {
		String kind = kind(stack);
		return kind.equals(LEAF) || kind.equals(CURED) || kind.equals(AGED);
	}

	public static boolean isCigar(ItemStack stack) {
		return stack.is(Items.STICK) && CIGAR.equals(kind(stack));
	}

	static boolean isLit(ItemStack stack) {
		return isCigar(stack) && data(stack).getIntOr(LIT, 0) == 1;
	}

	static Grade grade(ItemStack stack) {
		return AGED.equals(data(stack).getStringOr(GRADE, "")) ? Grade.AGED : Grade.CURED;
	}

	static Flavor flavor(ItemStack stack) {
		return Flavor.byName(data(stack).getStringOr(FLAVOR, ""));
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
