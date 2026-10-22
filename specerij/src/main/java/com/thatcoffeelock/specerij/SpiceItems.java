package com.thatcoffeelock.specerij;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Every Specerij item is paper with custom data, drawn as something that looks the part, so vanilla clients can join.
 * Four stages per spice: a cutting (plant it), fresh (just picked), dried (out of the Drying Rack) and, for tea only,
 * aged (black tea).
 */
public final class SpiceItems {
	static final String KEY = "specerij";
	static final String SPICE = "spice";
	static final String CUTTING = "cutting";
	static final String FRESH = "fresh";
	static final String DRIED = "dried";
	static final String AGED = "aged";

	private static final Map<String, Item> ITEMS = new HashMap<>();

	private SpiceItems() {
	}

	/** Looks an item up by id (some aren't constants in Items any more). */
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

	private static ItemStack make(Spice spice, String kind, int count, String model, String name, ChatFormatting color, List<Component> lore) {
		ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		tag.putString(SPICE, spice.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(model));
		stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	static ItemStack cutting(Spice spice, int count) {
		return make(spice, CUTTING, count, spice.cuttingModel, spice.title + " Cutting", ChatFormatting.GREEN, List.of(
			text(spice.story, ChatFormatting.GRAY),
			text("Grows in: " + spice.homeNames() + ".", ChatFormatting.YELLOW),
			text("Anywhere else only in a Hortus: under a glass roof,", ChatFormatting.DARK_GRAY),
			text("with a fire going nearby. Half as fast.", ChatFormatting.DARK_GRAY)));
	}

	static ItemStack fresh(Spice spice, int count) {
		return make(spice, FRESH, count, spice.freshModel, spice.freshName, ChatFormatting.WHITE, List.of(
			text("Fresh " + spice.title.toLowerCase(java.util.Locale.ROOT) + ". Too wet to sell for much.", ChatFormatting.GRAY),
			text("Dry it in a Drying Rack (about one day).", ChatFormatting.DARK_GRAY)));
	}

	static ItemStack dried(Spice spice, int count) {
		List<Component> lore = new ArrayList<>();
		lore.add(text(spice.story, ChatFormatting.GRAY));
		if (spice == Spice.TEA) {
			lore.add(text("Leave it in the Drying Rack two more days for black tea.", ChatFormatting.DARK_GRAY));
		}
		return make(spice, DRIED, count, spice.driedModel, spice.driedName, ChatFormatting.GOLD, lore);
	}

	static ItemStack blackTea(int count) {
		ItemStack stack = make(Spice.TEA, AGED, count, "dried_kelp", "Black Tea", ChatFormatting.LIGHT_PURPLE, List.of(
			text("Dark, strong, and worth twice the green.", ChatFormatting.GRAY)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A spice item of this stage. */
	static ItemStack of(String kind, Spice spice, int count) {
		return switch (kind) {
			case CUTTING -> cutting(spice, count);
			case FRESH -> fresh(spice, count);
			case DRIED -> dried(spice, count);
			case AGED -> spice == Spice.TEA ? blackTea(count) : dried(spice, count);
			default -> ItemStack.EMPTY;
		};
	}

	/**
	 * Items by name, for other mods ({@link SpiceMod#ITEM_HOOK}) and /specerij give: "dried:pepper", "fresh:tea",
	 * "cutting:saffron", "aged:tea". Empty for anything else.
	 */
	static ItemStack byKind(String what, Integer count) {
		if (what == null || what.indexOf(':') < 0) {
			return ItemStack.EMPTY;
		}
		Spice spice = Spice.byId(what.substring(what.indexOf(':') + 1));
		if (spice == null) {
			return ItemStack.EMPTY;
		}
		return of(what.substring(0, what.indexOf(':')), spice, count == null ? 1 : Math.max(1, count));
	}

	public static ItemStack dryingRack() {
		ItemStack stack = new ItemStack(item("minecraft:barrel", Items.CHEST));
		stack.set(DataComponents.CUSTOM_NAME, text("Drying Rack", ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Fresh spices dry in here (about one day).", ChatFormatting.GRAY),
			text("Leave green tea in two more days and it turns black.", ChatFormatting.GRAY),
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

	/** "cutting", "fresh", "dried", "aged", or "" for anything that isn't ours. */
	static String kind(ItemStack stack) {
		return stack.is(Items.PAPER) ? data(stack).getStringOr(KEY, "") : "";
	}

	static @Nullable Spice spice(ItemStack stack) {
		return kind(stack).isEmpty() ? null : Spice.byId(data(stack).getStringOr(SPICE, ""));
	}

	static @Nullable Spice cuttingOf(ItemStack stack) {
		return CUTTING.equals(kind(stack)) ? spice(stack) : null;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
