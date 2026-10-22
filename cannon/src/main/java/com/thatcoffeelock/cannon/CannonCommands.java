package com.thatcoffeelock.cannon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /cannon (help for everyone), /cannon give cannon|cannonballs and /cannon blockdamage on|off (ops). */
final class CannonCommands {
	private CannonCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("cannon")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("blockdamage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					boolean on = CannonConfig.get().cannonballsBreakBlocks;
					ctx.getSource().sendSuccess(() -> Component.literal("Cannonballs " + (on ? "break blocks." : "don't break blocks.")
						+ " /cannon blockdamage on|off to change it.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})
				.then(literal("on").executes(ctx -> blockDamage(ctx.getSource(), true)))
				.then(literal("off").executes(ctx -> blockDamage(ctx.getSource(), false))))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("cannon").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					CannonItems.give(player, CannonItems.cannon());
					ctx.getSource().sendSuccess(() -> Component.literal("One cannon, fresh from the foundry.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
				.then(literal("cannonballs")
					.executes(ctx -> giveBalls(ctx.getSource(), 16))
					.then(argument("count", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> giveBalls(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))));
	}

	private static int blockDamage(CommandSourceStack source, boolean on) {
		CannonConfig.get().cannonballsBreakBlocks = on;
		CannonConfig.save();
		source.sendSuccess(() -> Component.literal(on ? "Cannonballs now break blocks. Mind the walls." : "Cannonballs no longer break blocks.")
			.withStyle(on ? ChatFormatting.RED : ChatFormatting.GREEN), true);
		return 1;
	}

	private static int giveBalls(CommandSourceStack source, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		CannonItems.give(player, CannonItems.cannonballs(count));
		source.sendSuccess(() -> Component.literal(count + " cannonballs. Try not to aim at your own house.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Cannon ——",
			"§6Cannon§7: §fIron-block Iron-block Iron-block / Log Dispenser Log / Log _ Log",
			"§8Cannonball§7 (×2): §f1 Iron Ingot + 1 Gunpowder, any shape",
			"§7Right-click the ground with the cannon to place it. It faces where you look.",
			"§7Right-click the cannon to man it: §flook§7 to aim, §fSpace§7 to fire, §fShift§7 to get off.",
			"§7Each shot uses one cannonball from your inventory. Reloading takes 2 seconds.",
			"§7Sneak + right-click the cannon to pick it back up (owner only).",
			CannonConfig.get().cannonballsBreakBlocks
				? "§cThe balls explode like TNT: they hurt mobs and players, and break blocks."
				: "§cThe balls explode like a creeper: they hurt mobs and players but don't break blocks."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
