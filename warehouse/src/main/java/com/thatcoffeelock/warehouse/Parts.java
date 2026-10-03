package com.thatcoffeelock.warehouse;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The three things you build a warehouse from. They're vanilla blocks with a tag on the item, so players don't need
 * the mod: the Warehouse Core is a cartography table (the office), a Storage Rack is a barrel, a Loading Dock is a
 * lantern on your pier. Where they stand is remembered when they're placed (see the BlockItem mixin).
 */
final class Parts {
	static final String CORE = "warehouse_core";
	static final String RACK = "warehouse_rack";
	static final String DOCK = "warehouse_dock";
	/** On a packed-up core: which warehouse is inside. */
	static final String ID = "warehouse_id";

	private Parts() {
	}

	static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static ItemStack tagged(ItemStack stack, String key, String name, ChatFormatting color, Component... lore) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(key, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color));
		stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	// ---------------------------------------------------------------- the items (keep them identical to data/warehouse/recipe, so they stack)

	static ItemStack core() {
		return tagged(new ItemStack(Items.CARTOGRAPHY_TABLE), CORE, "Warehouse Core", ChatFormatting.GOLD,
			Gui.text("Place it to start a new warehouse.", ChatFormatting.GRAY),
			Gui.text("Storage Racks touching it add room.", ChatFormatting.GRAY),
			Gui.text("Rename it in an anvil to name the warehouse.", ChatFormatting.DARK_GRAY));
	}

	/** A core that was picked up with its stock still inside. */
	static ItemStack packedCore(Warehouse warehouse) {
		ItemStack stack = core();
		CompoundTag tag = data(stack);
		tag.putString(ID, warehouse.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(warehouse.name).withStyle(style -> style.withItalic(false)).withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Gui.text("Packed up with its stock inside:", ChatFormatting.GRAY),
			Gui.text(Gui.n(warehouse.total()) + " items of " + Gui.n(warehouse.kinds()) + " kinds.", ChatFormatting.AQUA),
			Gui.text("Place it anywhere to unpack.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	static ItemStack rack() {
		return tagged(new ItemStack(Items.BARREL), RACK, "Storage Rack", ChatFormatting.AQUA,
			Gui.text("Adds room to the warehouse it touches.", ChatFormatting.GRAY),
			Gui.text("Place it against a Warehouse Core or another rack.", ChatFormatting.GRAY),
			Gui.text("Hoppers can feed it; it passes everything on.", ChatFormatting.DARK_GRAY));
	}

	static ItemStack dock() {
		return tagged(new ItemStack(Items.LANTERN), DOCK, "Loading Dock", ChatFormatting.YELLOW,
			Gui.text("Put it on your pier. It serves the warehouses nearby.", ChatFormatting.GRAY),
			Gui.text("Sail an Ahoy ship up to it to unload and load.", ChatFormatting.DARK_GRAY));
	}

	static boolean isCore(ItemStack stack) {
		return stack.is(Items.CARTOGRAPHY_TABLE) && data(stack).contains(CORE);
	}

	static boolean isRack(ItemStack stack) {
		return stack.is(Items.BARREL) && data(stack).contains(RACK);
	}

	static boolean isDock(ItemStack stack) {
		return stack.is(Items.LANTERN) && data(stack).contains(DOCK);
	}

	/** The warehouse inside a packed-up core, or "" for a fresh one. */
	static String packedId(ItemStack stack) {
		return data(stack).getStringOr(ID, "");
	}

	// ---------------------------------------------------------------- the blocks

	static boolean isCoreBlock(BlockState state) {
		return state.is(Blocks.CARTOGRAPHY_TABLE);
	}

	static boolean isRackBlock(BlockState state) {
		return state.is(Blocks.BARREL);
	}

	static boolean isDockBlock(BlockState state) {
		return state.is(Blocks.LANTERN);
	}
}
