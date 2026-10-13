package com.thatcoffeelock.compagnie;

import java.util.List;
import java.util.Random;

import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Charts from exploring jobs: the surveyors find the nearest real structure of a kind (a shipwreck, a buried treasure,
 * a Hamlets castle...) and bring back a Company Chart with its coordinates. The treasure's real; go and get it.
 */
final class Charts {
	static final String KEY = "compagnie_chart";
	/** How far to look, in chunks. */
	static final int RADIUS = 64;

	private Charts() {
	}

	/** Where the nearest structure with this id is, from {@code from}, or null (no such structure, or none near). */
	static @Nullable BlockPos find(ServerLevel level, String id, BlockPos from) {
		try {
			int colon = id.indexOf(':');
			Identifier key = Identifier.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1));
			Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			var found = structures.get(ResourceKey.create(Registries.STRUCTURE, key));
			if (found.isEmpty()) {
				return null;
			}
			Holder<Structure> holder = found.get();
			Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(holder), from, RADIUS, false);
			return nearest == null ? null : nearest.getFirst();
		} catch (RuntimeException e) {
			CompagnieMod.LOG.warn("Couldn't look for {}", id, e);
			return null;
		}
	}

	/** A chart to the nearest structure this job looks for (trying them in a random order), or empty if none is near. */
	static ItemStack chart(ServerLevel level, JobType type, Company c, Random random) {
		List<String> ids = new java.util.ArrayList<>(type.charts);
		java.util.Collections.shuffle(ids, random);
		BlockPos from = Jobs.home(c);
		for (String id : ids) {
			BlockPos at = find(level, id, from);
			if (at != null) {
				return item(id, at);
			}
		}
		return ItemStack.EMPTY;
	}

	/** "Shipwreck" from "minecraft:shipwreck". */
	static String title(String id) {
		String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
		StringBuilder sb = new StringBuilder();
		for (String word : path.split(" ")) {
			if (!word.isEmpty()) {
				sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return sb.toString();
	}

	static ItemStack item(String id, BlockPos at) {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, id);
		tag.putInt("x", at.getX());
		tag.putInt("z", at.getZ());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("filled_map"));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Company Chart: " + title(id)).withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Ui.t("Marked at X " + at.getX() + ", Z " + at.getZ() + ".", ChatFormatting.YELLOW),
			Ui.t("Drawn by a Compagnie surveyor. The treasure's real.", ChatFormatting.GRAY),
			Ui.t("Go and get it.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** "A chart to a Shipwreck at X 120, Z -340." */
	static String describe(ItemStack chart) {
		CustomData data = chart.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		CompoundTag tag = data.copyTag();
		return "A chart to a " + title(tag.getStringOr(KEY, "")) + " at X " + tag.getIntOr("x", 0) + ", Z " + tag.getIntOr("z", 0) + ".";
	}
}
