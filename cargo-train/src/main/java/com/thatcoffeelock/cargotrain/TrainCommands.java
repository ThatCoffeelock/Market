package com.thatcoffeelock.cargotrain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /train (help for everyone), /train give train|wagon|station (ops). */
final class TrainCommands {
	private TrainCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("train")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("wagon").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TrainItems.give(player, TrainItems.wagon());
					ctx.getSource().sendSuccess(() -> Component.literal("One cargo wagon. Right-click your train with it.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
				.then(literal("train").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TrainItems.give(player, TrainItems.train());
					ctx.getSource().sendSuccess(() -> Component.literal("One cargo train, fresh from the works.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
				.then(literal("station")
					.executes(ctx -> giveStation(ctx.getSource(), Stations.Mode.PICKUP))
					.then(literal("pickup").executes(ctx -> giveStation(ctx.getSource(), Stations.Mode.PICKUP)))
					.then(literal("dropoff").executes(ctx -> giveStation(ctx.getSource(), Stations.Mode.DROPOFF)))
					.then(literal("swap").executes(ctx -> giveStation(ctx.getSource(), Stations.Mode.SWAP))))));
	}

	private static int giveStation(CommandSourceStack source, Stations.Mode mode) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TrainItems.give(player, TrainItems.station(mode));
		source.sendSuccess(() -> Component.literal("One " + mode.title + ". Put it right next to the track.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Cargo Train ——",
			"§eCargo Train§7: §fFurnace Minecart + Chest Minecart + Redstone Block§7 (any shape)",
			"§eCargo Wagon§7: §fChest Minecart + Iron Ingot§7. Right-click your train with it to couple it (up to 4 wagons).",
			"§eStation§7: §fChest + Rail§7 (any shape). Or rename any chest or barrel in an anvil:",
			"§7  §6Pickup Station§7 (the train loads it all), §bDrop-off Station§7 (it unloads), §dSwap Station§7 (both)",
			"§7Put stations right next to the track. Sneak + right-click one with an empty hand to switch its mode.",
			"§7Right-click a rail with the train to put it on (it needs 4 rails in a row). It shuttles to the end of",
			"§7the line, turns around, goes back, and stops at every station on the way. Loops work too.",
			"§7Right-click the locomotive to ride along. Right-click a wagon for its cargo. Sneak + right-click the",
			"§7locomotive for the menu: start/stop, lock, uncouple, pick up. Parked, you can drive it from the cab with W/S.",
			"§8A running train keeps its own chunks loaded, so it keeps hauling while you're far away."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
