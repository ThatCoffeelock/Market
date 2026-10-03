package com.thatcoffeelock.overenchant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;

import static net.minecraft.commands.Commands.literal;

/** /overenchant lists what went up (everyone), /overenchant reload re-reads the config (ops). */
final class OverenchantCommands {
	private OverenchantCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("overenchant")
			.executes(ctx -> {
				show(ctx.getSource());
				return 1;
			})
			.then(literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				OverenchantConfig config = OverenchantConfig.load();
				ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/overenchant.json: x" + config.multiplier
					+ (config.bonusLevels > 0 ? " + " + config.bonusLevels : "") + ", cap " + config.cap + ".").withStyle(ChatFormatting.GREEN), true);
				return 1;
			})));
	}

	/** One line per "old max to new max", listing the enchantments that moved that way. */
	private static void show(CommandSourceStack source) {
		OverenchantConfig config = OverenchantConfig.get();
		Map<Integer, List<Holder.Reference<Enchantment>>> byOriginal = new TreeMap<>(Comparator.reverseOrder());
		source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder -> {
			int original = holder.value().definition().maxLevel();
			if (holder.value().getMaxLevel() > original) {
				byOriginal.computeIfAbsent(original, k -> new ArrayList<>()).add(holder);
			}
		});
		source.sendSystemMessage(Component.literal("§6§l—— Overenchant ——"));
		source.sendSystemMessage(Component.literal("§7Maximum levels are x" + config.multiplier
			+ (config.bonusLevels > 0 ? " + " + config.bonusLevels : "") + ", up to " + roman(config.cap) + ". Combine two books of the same level in an anvil to climb."));
		for (Map.Entry<Integer, List<Holder.Reference<Enchantment>>> entry : byOriginal.entrySet()) {
			List<Holder.Reference<Enchantment>> holders = entry.getValue();
			holders.sort(Comparator.comparing(Holder::getRegisteredName));
			int original = entry.getKey();
			MutableComponent line = Component.literal(roman(original) + " → " + roman(holders.get(0).value().getMaxLevel()) + ": ").withStyle(ChatFormatting.GOLD);
			for (int i = 0; i < holders.size(); i++) {
				if (i > 0) {
					line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
				}
				line.append(holders.get(i).value().description().copy().withStyle(ChatFormatting.WHITE));
			}
			source.sendSystemMessage(line);
		}
	}

	/** I, II, ... for any level (vanilla only knows the names up to X). */
	static String roman(int n) {
		int[] values = {100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] symbols = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (n >= values[i]) {
				out.append(symbols[i]);
				n -= values[i];
			}
		}
		return out.toString();
	}
}
