package com.thatcoffeelock.burlapsack;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /burlapsack (help for everyone), /burlapsack give (ops). */
final class SackCommands {
	private SackCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("burlapsack")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					SackItems.give(player, SackItems.empty());
					ctx.getSource().sendSuccess(() -> Component.literal("One Burlap Sack. We don't ask what it's for.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Burlap Sack ——",
			"§6Burlap Sack§7: §f_ String _ / Leather _ Leather / Leather Leather Leather",
			"§7Right-click a §fvillager§7 or §fwandering trader§7 to put them in the sack.",
			"§7Right-click a block to let them out. Trades, level, name and skin come along.",
			"§7Villagers who have traded keep their job. Fresh ones need a workstation in their new home.",
			"§7Wandering traders you move never leave again.",
			"§cIron golems within 24 blocks will attack you, and the captive charges you more for a few days.",
			"§8Colony workers can't be taken."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
