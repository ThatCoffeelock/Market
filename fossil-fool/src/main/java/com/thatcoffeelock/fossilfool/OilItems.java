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
	static final String PIPE = "pipe";
	static final String OVEN = "industrial_oven";
	/** On a picked-up oven: the diesel in its tank. */
	static final String DIESEL_IN = "diesel_in";
	/** On a picked-up tank or refinery: what was inside. */
	static final String FLUID = "fluid";
	/** On a tank: the one fluid it's set to take. */
	static final String SET = "set";
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

	/** Buckets of this fluid. Water and lava come in vanilla buckets, which don't stack: mind the count. */
	static ItemStack bucketOf(Fluid fluid, int count) {
		return switch (fluid) {
			case DIESEL -> diesel(count);
			case WATER -> new ItemStack(Items.WATER_BUCKET, Math.max(1, count));
			case LAVA -> new ItemStack(Items.LAVA_BUCKET, Math.max(1, count));
			case CRUDE, NONE -> crude(count);
		};
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
			text("Riveted iron. Holds " + FossilConfig.get().tankCapacity + " buckets of one fluid:", ChatFormatting.GRAY),
			text("crude, diesel, water or lava.", ChatFormatting.GRAY),
			text("Right-click with buckets to fill or empty it.", ChatFormatting.GRAY),
			text("Sneak + right-click with an empty hand: pick its fluid.", ChatFormatting.DARK_GRAY),
			text("Rigs, Refineries and Pipes nearby connect to it.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A tank set to take only this fluid ("Lava Tank"). */
	static ItemStack tank(Fluid set) {
		return tank(set, Fluid.NONE, 0);
	}

	/** A tank that was picked up: the fluid it's set to, and what was still inside. */
	static ItemStack tank(Fluid set, Fluid fluid, int amount) {
		ItemStack stack = tank();
		boolean full = fluid != Fluid.NONE && amount > 0;
		if (set == Fluid.NONE && !full) {
			return stack;
		}
		CompoundTag tag = data(stack);
		List<Component> lore = new ArrayList<>();
		if (set != Fluid.NONE) {
			tag.putString(SET, set.name());
			stack.set(DataComponents.ITEM_NAME, Component.literal(set.tankName).withStyle(set.color));
			lore.add(text("Takes " + set.title.toLowerCase(java.util.Locale.ROOT) + " only.", ChatFormatting.GRAY));
		}
		if (full) {
			tag.putString(FLUID, fluid.name());
			tag.putInt(AMOUNT, amount);
			stack.set(DataComponents.MAX_STACK_SIZE, 1);
			lore.add(text("Full of " + fluid.title.toLowerCase(java.util.Locale.ROOT) + ": " + amount + " buckets.", ChatFormatting.YELLOW));
		}
		lore.add(text("Place it again to set it back up.", ChatFormatting.GRAY));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack oven() {
		ItemStack stack = make(Items.SMOKER, 1, OVEN);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Industrial Oven").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Burns diesel. Smelts or cooks anything", ChatFormatting.GRAY),
			text("a furnace can, a stack in seconds.", ChatFormatting.GRAY),
			text("Ores and raw metal come out double.", ChatFormatting.YELLOW),
			text("Drinks diesel from Tanks nearby or on a pipeline.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** An oven that was picked up with diesel still in its tank. */
	static ItemStack oven(int diesel) {
		ItemStack stack = oven();
		if (diesel <= 0) {
			return stack;
		}
		CompoundTag tag = data(stack);
		tag.putInt(DIESEL_IN, diesel);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Still holds " + diesel + " buckets of diesel.", ChatFormatting.YELLOW),
			text("Place it again to set it back up.", ChatFormatting.GRAY))));
		return stack;
	}

	/** The block pipes are made of: a copper lightning rod. */
	static Item pipeBase() {
		return Gui.item("minecraft:lightning_rod", Items.END_ROD);
	}

	public static ItemStack pipe(int count) {
		ItemStack stack = make(pipeBase(), count, PIPE);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Pipe").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("Copper pipe. Lay it in a line.", ChatFormatting.GRAY),
			text("Links Drill Rigs, Tanks, Refineries and chests", ChatFormatting.GRAY),
			text("that touch it, however far apart they are.", ChatFormatting.GRAY),
			text("A rig connects through the ring around its shaft.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
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

	public static boolean isOven(ItemStack stack) {
		return stack.is(Items.SMOKER) && OVEN.equals(kind(stack));
	}

	public static boolean isPipe(ItemStack stack) {
		return PIPE.equals(kind(stack));
	}

	/** Which fluid a bucket holds: our crude and diesel, or a vanilla water or lava bucket. NONE for anything else. */
	static Fluid fluidOf(ItemStack stack) {
		if (isCrude(stack)) {
			return Fluid.CRUDE;
		}
		if (isDiesel(stack)) {
			return Fluid.DIESEL;
		}
		if (!kind(stack).isEmpty()) {
			return Fluid.NONE;
		}
		if (stack.is(Items.WATER_BUCKET)) {
			return Fluid.WATER;
		}
		return stack.is(Items.LAVA_BUCKET) ? Fluid.LAVA : Fluid.NONE;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}
}
