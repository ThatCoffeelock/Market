package com.thatcoffeelock.fossilfool;

import java.util.ArrayList;
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

/**
 * Everything Fossil Fool hands out, as vanilla items tagged with custom data, so vanilla clients see them without a
 * resource pack. The buckets are paper wearing a bucket's model (a real bucket would try to scoop water). The ones
 * you can craft must match the results in data/fossilfool/recipe, so crafted and given items stack.
 */
public final class OilItems {
	static final String KEY = "fossilfool";
	static final String CRUDE = "crude";
	static final String DIESEL = "diesel";
	static final String RIG = "drill_rig";
	static final String TANK = "oil_tank";
	static final String REFINERY = "refinery";
	static final String ROD = "dowsing_rod";
	/** On a picked-up tank or refinery: what was inside. */
	static final String FLUID = "fluid";
	static final String AMOUNT = "amount";
	static final String CRUDE_IN = "crude_in";
	static final String DIESEL_OUT = "diesel_out";

	static final int BUCKET_STACK = 16;

	private OilItems() {
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	private static ItemStack make(Item base, int count, String kind) {
		ItemStack stack = new ItemStack(base, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	static String kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		return data(stack).getStringOr(KEY, "");
	}

	// ---------------------------------------------------------------- the oil

	public static ItemStack crude(int count) {
		ItemStack stack = make(Items.PAPER, count, CRUDE);
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("bucket"));
		stack.set(DataComponents.MAX_STACK_SIZE, BUCKET_STACK);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Bucket of Crude Oil").withStyle(ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Black gold. Texas tea.", ChatFormatting.GRAY),
			text("Fuel: " + blocks(Fuel.CRUDE) + " blocks of drilling, at full speed.", ChatFormatting.DARK_GRAY),
			text("Refine 2 into a bucket of diesel.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static ItemStack diesel(int count) {
		ItemStack stack = make(Items.PAPER, count, DIESEL);
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("lava_bucket"));
		stack.set(DataComponents.MAX_STACK_SIZE, BUCKET_STACK);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Bucket of Diesel").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("The good stuff. Refined from crude.", ChatFormatting.GRAY),
			text("Fuel: " + blocks(Fuel.DIESEL) + " blocks of drilling, at 1.5× speed.", ChatFormatting.DARK_GRAY),
			text("Don't drink it. Don't smoke near it.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	/** One bucket of this fluid. */
	static ItemStack bucketOf(Fluid fluid, int count) {
		return fluid == Fluid.DIESEL ? diesel(count) : crude(count);
	}

	private static String blocks(Fuel fuel) {
		double v = fuel.blocks();
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	// ---------------------------------------------------------------- the machines (keep identical to data/fossilfool/recipe)

	public static ItemStack rig() {
		ItemStack stack = make(Items.PISTON, 1, RIG);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Drill Rig").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("A wooden derrick with a steam engine.", ChatFormatting.GRAY),
			text("Right-click the ground: it sinks a 5×5 shaft", ChatFormatting.GRAY),
			text("straight down, slowly, and strikes any oil it finds.", ChatFormatting.GRAY),
			text("Burns coal, lava, crude or diesel. Lots of it.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static ItemStack tank() {
		ItemStack stack = make(Items.CAULDRON, 1, TANK);
		stack.set(DataComponents.MAX_STACK_SIZE, 16);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Oil Tank").withStyle(ChatFormatting.AQUA));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Riveted iron. Holds " + FossilConfig.get().tankCapacity + " buckets of crude or diesel.", ChatFormatting.GRAY),
			text("Right-click with buckets to fill or empty it.", ChatFormatting.GRAY),
			text("Rigs and Refineries nearby pipe into it.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A tank that was picked up with oil still inside. */
	static ItemStack tank(Fluid fluid, int amount) {
		ItemStack stack = tank();
		if (fluid == Fluid.NONE || amount <= 0) {
			return stack;
		}
		CompoundTag tag = data(stack);
		tag.putString(FLUID, fluid.name());
		tag.putInt(AMOUNT, amount);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Full of " + fluid.title.toLowerCase(java.util.Locale.ROOT) + ": " + amount + " buckets.", ChatFormatting.YELLOW),
			text("Place it again to set it back up.", ChatFormatting.GRAY))));
		return stack;
	}

	public static ItemStack refinery() {
		ItemStack stack = make(Items.BLAST_FURNACE, 1, REFINERY);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Refinery").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("A copper-pot still for oil.", ChatFormatting.GRAY),
			text("Cooks 2 buckets of crude into 1 bucket of diesel.", ChatFormatting.GRAY),
			text("Needs fuel in its firebox, like everything else.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A refinery that was picked up with oil still inside. */
	static ItemStack refinery(int crude, int diesel) {
		ItemStack stack = refinery();
		if (crude <= 0 && diesel <= 0) {
			return stack;
		}
		CompoundTag tag = data(stack);
		tag.putInt(CRUDE_IN, crude);
		tag.putInt(DIESEL_OUT, diesel);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Still holds " + crude + " crude and " + diesel + " diesel.", ChatFormatting.YELLOW));
		lore.add(text("Place it again to set it back up.", ChatFormatting.GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack rod() {
		ItemStack stack = make(Items.STICK, 1, ROD);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Dowsing Rod").withStyle(ChatFormatting.YELLOW));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Right-click and it twitches towards oil.", ChatFormatting.GRAY),
			text("Old-timey science. Works suspiciously well.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	// ---------------------------------------------------------------- recognising them

	public static boolean isCrude(ItemStack stack) {
		return CRUDE.equals(kind(stack));
	}

	public static boolean isDiesel(ItemStack stack) {
		return DIESEL.equals(kind(stack));
	}

	public static boolean isRig(ItemStack stack) {
		return stack.is(Items.PISTON) && RIG.equals(kind(stack));
	}

	public static boolean isTank(ItemStack stack) {
		return stack.is(Items.CAULDRON) && TANK.equals(kind(stack));
	}

	public static boolean isRefinery(ItemStack stack) {
		return stack.is(Items.BLAST_FURNACE) && REFINERY.equals(kind(stack));
	}

	public static boolean isRod(ItemStack stack) {
		return ROD.equals(kind(stack));
	}

	/** Which oil a bucket holds, or NONE if it isn't one of ours. */
	static Fluid fluidOf(ItemStack stack) {
		if (isCrude(stack)) {
			return Fluid.CRUDE;
		}
		return isDiesel(stack) ? Fluid.DIESEL : Fluid.NONE;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}
}
