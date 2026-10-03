package com.thatcoffeelock.warehouse;

import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The shelves of a warehouse: used for the tabs in the GUI and for a warehouse's "only accepts" filter, so a dock
 * can send iron to the Iron Depot and bread to the Food Hall.
 */
enum Category {
	ALL("Everything", Items.CHEST, ChatFormatting.WHITE),
	MINERALS("Ores & minerals", Items.IRON_INGOT, ChatFormatting.GRAY),
	FOOD("Food", Items.BREAD, ChatFormatting.GOLD),
	GEAR("Tools, weapons & armor", Items.IRON_PICKAXE, ChatFormatting.AQUA),
	BLOCKS("Building blocks", Items.BRICKS, ChatFormatting.RED),
	OTHER("Everything else", Items.STRING, ChatFormatting.LIGHT_PURPLE);

	private static final Pattern MINERAL = Pattern.compile(
		"^(deepslate_|nether_)?(raw_)?(iron|gold|copper|diamond|emerald|lapis|redstone|coal|netherite|quartz|amethyst)"
			+ "(_ore|_ingot|_nugget|_block|_scrap|_shard|_lazuli)?$|^(raw_.*|ancient_debris|charcoal|nether_quartz_ore|.*_ore)$");

	final String title;
	final Item icon;
	final ChatFormatting color;

	Category(String title, Item icon, ChatFormatting color) {
		this.title = title;
		this.icon = icon;
		this.color = color;
	}

	Category next() {
		return values()[(ordinal() + 1) % values().length];
	}

	Category previous() {
		return values()[(ordinal() + values().length - 1) % values().length];
	}

	static Category byName(String name) {
		for (Category c : values()) {
			if (c.name().equalsIgnoreCase(name)) {
				return c;
			}
		}
		return ALL;
	}

	/** Which shelf an item goes on. Never ALL. */
	static Category of(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (stack.get(DataComponents.MAX_DAMAGE) != null) {
			return GEAR;
		}
		if (MINERAL.matcher(id).matches()) {
			return MINERALS;
		}
		if (stack.get(DataComponents.FOOD) != null) {
			return FOOD;
		}
		// a block item that places a block with its own name; string (tripwire) and seeds (crops) don't count
		if (stack.getItem() instanceof BlockItem block && BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath().equals(id)) {
			return BLOCKS;
		}
		return OTHER;
	}

	/** Does this shelf (or filter) take the item? */
	boolean takes(ItemStack stack) {
		return this == ALL || of(stack) == this;
	}
}
