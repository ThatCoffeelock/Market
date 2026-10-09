package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Everything Blimey hands out, as vanilla items tagged with custom data so vanilla clients see them without a
 * resource pack. The crafted ones must match the results in data/blimey/recipe, so crafted and given items stack.
 *
 * Diesel isn't ours: it's Fossil Fool's Bucket of Diesel (paper tagged {fossilfool:"diesel"}). We only read the tag,
 * so Blimey builds and runs without Fossil Fool; you just can't get fuel without it.
 */
public final class BlimeyItems {
	static final String KEY = "blimey";
	static final String AIRSHIP = "airship";
	static final String SAVED = "blimey_saved";

	private BlimeyItems() {
	}

	/** The three bombs. {@code model} is what the item looks like, and what falls out of the sky. */
	public enum BombKind {
		SMALL("small_bomb", "Small Bomb", ChatFormatting.RED, "minecraft:tnt", 0.6f,
			"About as much bang as a block of TNT."),
		BIG("big_bomb", "Big Bomb", ChatFormatting.DARK_RED, "minecraft:coal_block", 1.0f,
			"Takes the roof off. And the walls. And the floor."),
		HUGE("huge_bomb", "Huge Bomb", ChatFormatting.LIGHT_PURPLE, "minecraft:respawn_anchor", 1.6f,
			"Leaves a crater you could build a lake in.");

		final String id;
		final String title;
		final ChatFormatting color;
		final String model;
		final float scale;
		final String blurb;

		BombKind(String id, String title, ChatFormatting color, String model, float scale, String blurb) {
			this.id = id;
			this.title = title;
			this.color = color;
			this.model = model;
			this.scale = scale;
			this.blurb = blurb;
		}

		float power() {
			BlimeyConfig c = BlimeyConfig.get();
			return switch (this) {
				case SMALL -> c.smallBombPower;
				case BIG -> c.bigBombPower;
				case HUGE -> c.hugeBombPower;
			};
		}

		static @Nullable BombKind byId(String id) {
			for (BombKind kind : values()) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}
			return null;
		}
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	private static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static String kind(ItemStack stack) {
		return stack.isEmpty() ? "" : data(stack).getStringOr(KEY, "");
	}

	private static void tag(ItemStack stack, String kind) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	// ---------------------------------------------------------------- the airship (keep identical to data/blimey/recipe/airship.json)

	public static ItemStack airship() {
		ItemStack stack = new ItemStack(Items.PAPER);
		tag(stack, AIRSHIP);
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("elytra"));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Flat-Pack Airship").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text("A riveted iron dirigible, some assembly required.", ChatFormatting.GRAY),
			text("Right-click the ground to unfold it.", ChatFormatting.GRAY),
			text("Needs about 9 × 26 blocks of clear ground and 14 up.", ChatFormatting.DARK_GRAY),
			text("Runs on diesel. Lots of diesel.", ChatFormatting.DARK_GRAY),
			text("Rename it in an anvil to name your airship.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** A folded-up airship with its name, refits, fuel and cargo inside. */
	public static ItemStack packed(ServerLevel level, AirshipData data) {
		ItemStack stack = airship();
		CompoundTag tag = data(stack);
		AirshipData.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), data)
			.resultOrPartial(error -> BlimeyMod.LOG.error("Could not pack airship: {}", error))
			.ifPresent(saved -> tag.put(SAVED, saved));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Flat-Pack Airship: " + data.name).withStyle(ChatFormatting.GOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(text("Right-click the ground to unfold it.", ChatFormatting.GRAY));
		lore.add(text("Cargo: " + data.usedSlots() + " / " + data.capacity() + " slots used", ChatFormatting.YELLOW));
		lore.add(text("Diesel aboard: " + data.dieselAboard() + " buckets", ChatFormatting.YELLOW));
		lore.add(text("Engines " + Engineer.roman(data.speedLevel) + " · Economy " + Engineer.roman(data.efficiencyLevel)
			+ " · Holds " + data.holds().size(), ChatFormatting.GOLD));
		lore.add(text("Captain: " + (data.ownerName.isEmpty() ? "nobody yet" : data.ownerName), ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static boolean isAirship(ItemStack stack) {
		return stack.is(Items.PAPER) && AIRSHIP.equals(kind(stack));
	}

	public static @Nullable AirshipData savedData(ItemStack stack, ServerLevel level) {
		CompoundTag tag = data(stack);
		if (!tag.contains(SAVED)) {
			return null;
		}
		return AirshipData.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get(SAVED))
			.resultOrPartial(error -> BlimeyMod.LOG.error("Could not unpack airship: {}", error))
			.orElse(null);
	}

	/** The name given in an anvil, if any. */
	public static @Nullable String customName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}

	// ---------------------------------------------------------------- bombs (keep identical to data/blimey/recipe/*_bomb.json)

	public static ItemStack bomb(BombKind kind, int count) {
		ItemStack stack = new ItemStack(Items.FIREWORK_STAR, Math.max(1, count));
		tag(stack, kind.id);
		stack.set(DataComponents.ITEM_MODEL, Identifier.parse(kind.model));
		stack.set(DataComponents.MAX_STACK_SIZE, 16);
		stack.set(DataComponents.ITEM_NAME, Component.literal(kind.title).withStyle(kind.color));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text(kind.blurb, ChatFormatting.GRAY),
			text("Aboard an airship: right-click to drop it.", ChatFormatting.YELLOW),
			text("On foot: right-click a block to light the fuse.", ChatFormatting.DARK_GRAY),
			text("Unlike a cannonball, it wrecks buildings.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	public static @Nullable BombKind bombKind(ItemStack stack) {
		return stack.is(Items.FIREWORK_STAR) ? BombKind.byId(kind(stack)) : null;
	}

	// ---------------------------------------------------------------- fuel

	/** Fossil Fool's Bucket of Diesel. */
	public static boolean isDiesel(ItemStack stack) {
		return !stack.isEmpty() && "diesel".equals(data(stack).getStringOr("fossilfool", ""));
	}

	/** A Bucket of Diesel the engines accept (smoke test only: real ones come from a Fossil Fool Refinery). */
	public static ItemStack diesel(int count) {
		ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString("fossilfool", "diesel");
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("lava_bucket"));
		stack.set(DataComponents.MAX_STACK_SIZE, 16);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Bucket of Diesel").withStyle(ChatFormatting.GOLD));
		return stack;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}
}
