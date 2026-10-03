package com.thatcoffeelock.flintlock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Guns and ammo as items: vanilla items tagged with custom data, so vanilla clients can join.
 * <ul>
 * <li>A gun is a carrot on a stick (no use of its own, never stacks, not an ingredient in anything) that looks like a
 * crossbow. Its durability is removed. A loaded gun carries an arrow as "charged projectile", so it looks like a
 * loaded crossbow.</li>
 * <li>Ammo is paper that looks like a candle (cartridges) or a bundle (scattershot).</li>
 * </ul>
 * The unloaded gun and the ammo must match the results in data/flintlock/recipe.
 */
public final class GunItems {
	static final String KEY = "flintlock";
	static final String LOADED = "loaded";

	private GunItems() {
	}

	/** Non-italic lore text. */
	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	// ---------------------------------------------------------------- building

	public static ItemStack gun(Gun gun) {
		ItemStack stack = new ItemStack(Items.CARROT_ON_A_STICK);
		stack.remove(DataComponents.MAX_DAMAGE);
		stack.remove(DataComponents.DAMAGE);
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("crossbow"));
		stack.set(DataComponents.ITEM_NAME, Component.literal(gun.title).withStyle(gun.color));
		setLoaded(stack, gun, false);
		return stack;
	}

	public static ItemStack ammo(Gun.Ammo ammo, int count) {
		ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, count));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, ammo.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(ammo.model));
		stack.set(DataComponents.ITEM_NAME, Component.literal(ammo.title).withStyle(ammo.color));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			text(ammo.blurb, ChatFormatting.GRAY),
			text(ammo.usedBy, ChatFormatting.DARK_GRAY))));
		return stack;
	}

	/** Loads or unloads a gun: its custom data, its look and its lore. */
	static void setLoaded(ItemStack stack, Gun gun, boolean loaded) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, gun.id);
		if (loaded) {
			tag.putBoolean(LOADED, true);
		}
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (loaded) {
			stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
		} else {
			stack.remove(DataComponents.CHARGED_PROJECTILES);
		}
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			loaded
				? text("Loaded. Right-click to fire.", ChatFormatting.GREEN)
				: text("Empty. Right-click to reload (1 " + gun.ammo.title + ").", ChatFormatting.GRAY),
			text(stats(gun), ChatFormatting.DARK_GRAY),
			text(gun.blurb, ChatFormatting.DARK_GRAY))));
	}

	/** e.g. "Damage 18 · Reload 4s". */
	static String stats(Gun gun) {
		String damage = gun.pellets > 1 ? gun.pellets + " × " + number(gun.damage) : number(gun.damage);
		String line = "Damage " + damage + " · Reload " + number(gun.reloadTicks / 20.0) + "s";
		return gun == Gun.BLUNDERBUSS ? line + " · Huge knockback" : line;
	}

	private static String number(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	// ---------------------------------------------------------------- reading

	private static String kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(KEY, "");
	}

	/** Which gun this is, or null if it isn't one. */
	static @Nullable Gun gunOf(ItemStack stack) {
		return stack.is(Items.CARROT_ON_A_STICK) ? Gun.byId(kind(stack)) : null;
	}

	/** Which ammo this is, or null if it isn't any. */
	static @Nullable Gun.Ammo ammoOf(ItemStack stack) {
		return stack.is(Items.PAPER) ? Gun.Ammo.byId(kind(stack)) : null;
	}

	static boolean isLoaded(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(LOADED, false);
	}

	static int countAmmo(Player player, Gun.Ammo ammo) {
		Inventory inventory = player.getInventory();
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (ammoOf(stack) == ammo) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Takes one round of this ammo out of the player's inventory. False if they have none. */
	static boolean takeAmmo(Player player, Gun.Ammo ammo) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (ammoOf(stack) == ammo) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	/** Puts the item in the player's inventory, or drops it at their feet if it's full. */
	static void give(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
