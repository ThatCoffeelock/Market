package com.thatcoffeelock.fuckillagers;

import java.util.ArrayList;
import java.util.List;

import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The mod's items. All vanilla items with a tag, so players don't need the mod: the Bounty Station is a fletching
 * table, an Illager Finger is a bone, a boss skull is a skeleton skull and a Wanted Poster is a sheet of paper.
 */
final class Trophies {
	static final String STATION = "bounty_station";
	static final String FINGER = "illager_finger";
	static final String SKULL = "bounty_skull";
	static final String POSTER = "bounty_poster";
	/** On skulls and posters: which contract. */
	static final String CONTRACT = "bounty_contract";
	/** On skulls: what the station pays for it, in cents. */
	static final String REWARD = "bounty_reward";

	private Trophies() {
	}

	static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	private static ItemStack tagged(ItemStack stack, CompoundTag tag, Component name, List<Component> lore, boolean glint) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		if (glint) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	private static CompoundTag flag(String key) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(key, true);
		return tag;
	}

	// ---------------------------------------------------------------- the items (the station matches data/fuckillagers/recipe, so they stack)

	static ItemStack station() {
		return tagged(new ItemStack(Items.FLETCHING_TABLE), flag(STATION),
			Component.literal("Bounty Station").withStyle(ChatFormatting.GOLD), List.of(
				text("Place it, then right-click it to sell", ChatFormatting.GRAY),
				text("illager trophies and take on contracts.", ChatFormatting.GRAY),
				text("Wanted: dead, not alive.", ChatFormatting.DARK_GRAY)), true);
	}

	static ItemStack finger(int count) {
		ItemStack stack = tagged(new ItemStack(Items.BONE), flag(FINGER),
			Component.literal("Illager Finger").withStyle(ChatFormatting.YELLOW), List.of(
				text("Proof of a dead illager.", ChatFormatting.GRAY),
				text("A Bounty Station pays " + Money.format(Bounties.fingerPrice()) + " each.", ChatFormatting.GOLD),
				text("Still twitching a bit.", ChatFormatting.DARK_GRAY)), false);
		stack.setCount(Math.max(1, count));
		return stack;
	}

	static ItemStack skull(Contract contract) {
		CompoundTag tag = flag(SKULL);
		tag.putString(CONTRACT, contract.id);
		tag.putLong(REWARD, Bounties.reward(contract.tier()));
		return tagged(new ItemStack(Items.SKELETON_SKULL), tag,
			Component.literal("Skull of " + contract.target).withStyle(ChatFormatting.RED), List.of(
				text(contract.tier().label + " contract, fulfilled.", contract.tier().color),
				text("A Bounty Station pays " + Money.format(Bounties.reward(contract.tier())) + " for it.", ChatFormatting.GOLD),
				text("Don't put it on a fence post. Okay, maybe once.", ChatFormatting.DARK_GRAY)), true);
	}

	static ItemStack poster(Contract contract) {
		CompoundTag tag = flag(POSTER);
		tag.putString(CONTRACT, contract.id);
		List<Component> lore = new ArrayList<>();
		lore.add(text(contract.tier().label + " contract", contract.tier().color));
		lore.add(text("Hiding in " + contract.site().what + " near", ChatFormatting.GRAY));
		lore.add(text("X " + contract.x + ", Z " + contract.z, ChatFormatting.WHITE));
		lore.add(text("Reward: " + Money.format(Bounties.reward(contract.tier())) + " for the skull", ChatFormatting.GOLD));
		lore.add(text("Hold it to see how far away the target is.", ChatFormatting.DARK_GRAY));
		return tagged(new ItemStack(Items.PAPER), tag,
			Component.literal("Wanted: " + contract.target).withStyle(ChatFormatting.RED), lore, false);
	}

	static boolean isStation(ItemStack stack) {
		return stack.is(Items.FLETCHING_TABLE) && data(stack).contains(STATION);
	}

	static boolean isFinger(ItemStack stack) {
		return stack.is(Items.BONE) && data(stack).contains(FINGER);
	}

	static boolean isSkull(ItemStack stack) {
		return stack.is(Items.SKELETON_SKULL) && data(stack).contains(SKULL);
	}

	static boolean isPoster(ItemStack stack) {
		return stack.is(Items.PAPER) && data(stack).contains(POSTER);
	}

	static String contractOf(ItemStack stack) {
		return data(stack).getStringOr(CONTRACT, "");
	}

	static long rewardOf(ItemStack stack) {
		return data(stack).getLongOr(REWARD, 0L);
	}
}
