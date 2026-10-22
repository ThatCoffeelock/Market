package com.thatcoffeelock.terraincognita;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /terra (help), /terra chart|go|home|raid (ops). */
final class TerraCommands {
	private TerraCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("terra")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("chart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				player.getInventory().placeItemBackInInventory(Charts.chart(), net.minecraft.util.Prediction.SERVER_ONLY);
				ctx.getSource().sendSuccess(() -> Component.literal("A Sea Chart of the Southland. Mind the blank half.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			}))
			.then(literal("go").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> jump(ctx.getSource(), true)))
			.then(literal("home").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> jump(ctx.getSource(), false)))
			.then(literal("raid").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ServerLevel level = (ServerLevel) player.level();
				BlockPos at = Raiders.band(level, player.blockPosition(), TerraConfig.get().maxBandSize);
				ctx.getSource().sendSuccess(() -> Component.literal(at == null ? "No dry ground nearby for a band to stand on."
					: "A band of illagers is coming, from " + at.toShortString() + ".").withStyle(ChatFormatting.RED), false);
				return 1;
			})));
	}

	/** Ops skip the voyage: straight there (or home), to open water near where they stand. */
	private static int jump(CommandSourceStack source, boolean there) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel to = there ? Terra.terra(source.getServer()) : source.getServer().overworld();
		if (to == null) {
			source.sendFailure(Component.literal("Terra Incognita didn't load on this server."));
			return 0;
		}
		BlockPos at = Crossing.arrival(to, player.getBlockX(), player.getBlockZ());
		player.stopRiding();
		Cmd.run(to, "execute in " + Terra.id(to) + " run tp " + player.getUUID() + " " + Cmd.pos(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5));
		return 1;
	}

	private static void help(CommandSourceStack source) {
		TerraConfig c = TerraConfig.get();
		String[] lines = {
			"§6§l—— Terra Incognita ——",
			"§e1. Chart§7: §fMap + Compass + Gold Ingot + Emerald§7 = §6Sea Chart of the Southland§7.",
			"§e2. Sail§7: take it to sea in a boat (or aboard a ship) and right-click it. Hold your course for " + c.voyageSeconds + " seconds.",
			"§7   Your crew (anyone aboard something within " + c.crewRadius + " blocks) comes along. Your own ship stays moored at home.",
			"§e3. Over there§7: islands, mountains, jungles, badlands, and illagers. Lots of illagers.",
			"§7   Every unopened chest holds extra plunder: gold, emeralds, diamonds. Illagers drop purses.",
			"§e4. Home§7: the same chart, the same way: out to sea, right-click, hold your course.",
			"§8High risk, high reward. Bring friends. Bring a spare chart.",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
