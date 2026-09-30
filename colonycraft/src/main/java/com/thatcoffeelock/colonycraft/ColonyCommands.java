package com.thatcoffeelock.colonycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /colonycraft (help), /colonycraft charter (buy your first charter), give / payday for ops. */
final class ColonyCommands {
	private ColonyCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (BuildingType type : BuildingType.values()) {
			give.then(literal(type.id).executes(ctx -> {
				Blueprints.give(ctx.getSource().getPlayerOrException(), Blueprints.of(type));
				return 1;
			}));
		}
		dispatcher.register(literal("colonycraft")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("charter").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				long price = Bank.cents(BuildingType.TOWN_HALL.price);
				if (!Bank.pay(player, price)) {
					ctx.getSource().sendFailure(Component.literal("A Colony Charter costs " + Bank.format(price) + ". Keep grinding."));
					return 0;
				}
				Blueprints.give(player, Blueprints.of(BuildingType.TOWN_HALL));
				ctx.getSource().sendSuccess(() -> Component.literal("Bought a Colony Charter for " + Bank.format(price)
					+ ". Right-click the ground to found your colony.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			}))
			.then(give)
			.then(literal("payday").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				Colonies.payday();
				ctx.getSource().sendSuccess(() -> Component.literal("Payday! Every colony worked a day.").withStyle(ChatFormatting.GOLD), true);
				return 1;
			})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Colonycraft ——",
			"§7Buy a §fColony Charter§7 with §f/colonycraft charter§7 (or at any Town Hall) and right-click the ground.",
			"§7A Town Hall is built. Right-click its §flectern§7 to buy blueprints, upgrades and replacement workers.",
			"§7Buildings: Residence (beds), Farm, Lumber Camp, Mine, Workshop (makes goods better), Storehouse.",
			"§7Every morning your workers are paid from your balance and everything they gather goes to your storehouses.",
			"§7Storehouses can auto-sell to the Market. §cNo wages, no work.",
			"§7Found as many colonies as you can afford."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
