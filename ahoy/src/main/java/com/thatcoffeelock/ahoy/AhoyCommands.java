package com.thatcoffeelock.ahoy;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /ahoy (help for everyone), /ahoy give (ops). */
final class AhoyCommands {
	private AhoyCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("ahoy")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Bottle.give(player, Bottle.empty());
				ctx.getSource().sendSuccess(() -> Component.literal("One Ship in a Bottle. Mind the glass.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Ahoy ——",
			"§bShip in a Bottle§7: §fWool Glass-bottle Wool / Chest Boat Chest / Planks Planks Planks",
			"§7Right-click open water with it. Rename the bottle in an anvil to name your ship.",
			"§7Anchored, the ship is real blocks: walk around, sleep in the bunks, build on it.",
			"§7Right-click the §fwheel§7 (grindstone at the back) to set sail. Cargo barrels are in the hold.",
			"§7Sailing: §fW/S§7 sails up/down, §fA/D§7 rudder, §fSpace§7 bell, §fShift§7 drop anchor.",
			"§7The wind matters: sail with it for full speed, against it for half.",
			"§aAt sea, nobody aboard can be hurt, and sea monsters get zapped away."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
