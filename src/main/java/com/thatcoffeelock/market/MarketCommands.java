package com.thatcoffeelock.market;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thatcoffeelock.market.gui.HubMenu;
import com.thatcoffeelock.market.gui.MarketGui;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /balance, /pay, /baltop, /withdraw, /worth, /market (+ admin tools for ops).
 */
public final class MarketCommands {
	private MarketCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("balance").executes(MarketCommands::balance));
		dispatcher.register(literal("bal").executes(MarketCommands::balance));

		dispatcher.register(literal("pay")
			.then(argument("player", EntityArgument.player())
				.then(argument("amount", DoubleArgumentType.doubleArg(0.01))
					.executes(MarketCommands::pay))));

		dispatcher.register(literal("baltop").executes(ctx -> {
			HubMenu.showRichList(ctx.getSource().getPlayerOrException());
			return 1;
		}));

		dispatcher.register(literal("withdraw")
			.then(argument("amount", DoubleArgumentType.doubleArg(0.01))
				.executes(MarketCommands::withdraw)));

		dispatcher.register(literal("worth").executes(MarketCommands::worth));

		dispatcher.register(literal("market")
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				if (!MarketConfig.get().marketCommandEnabled) {
					ctx.getSource().sendFailure(Component.literal("Find a Market block to trade. Craft one: 4 obsidian, 4 gold ingots, 1 emerald."));
					return 0;
				}
				MarketGui.openHub(player, null);
				return 1;
			})
			.then(literal("admin")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("reload").executes(ctx -> {
					MarketConfig.load();
					PriceBook.reload();
					ctx.getSource().sendSuccess(() -> Component.literal("Market config and prices reloaded (" + PriceBook.listedCount() + " priced items)."), true);
					return 1;
				}))
				.then(literal("give").then(argument("player", EntityArgument.player()).then(argument("amount", DoubleArgumentType.doubleArg(0.01))
					.executes(ctx -> adjust(ctx, 1)))))
				.then(literal("take").then(argument("player", EntityArgument.player()).then(argument("amount", DoubleArgumentType.doubleArg(0.01))
					.executes(ctx -> adjust(ctx, -1)))))
				.then(literal("set").then(argument("player", EntityArgument.player()).then(argument("amount", DoubleArgumentType.doubleArg(0))
					.executes(ctx -> adjust(ctx, 0)))))
				.then(literal("block").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					MarketItems.give(player, MarketItems.marketBlock());
					return 1;
				}))));
	}

	private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ctx.getSource().sendSuccess(() -> Component.literal("Balance: ").withStyle(ChatFormatting.GRAY).append(Money.text(MarketData.balance(player))), false);
		return 1;
	}

	private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer from = ctx.getSource().getPlayerOrException();
		ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
		long cents = Money.fromDecimal(DoubleArgumentType.getDouble(ctx, "amount"));
		if (from == to) {
			ctx.getSource().sendFailure(Component.literal("Paying yourself? Bold accounting strategy, but no."));
			return 0;
		}
		if (cents <= 0 || !MarketData.withdraw(from, cents)) {
			ctx.getSource().sendFailure(Component.literal("You don't have that much."));
			return 0;
		}
		MarketData.deposit(to, cents);
		from.sendSystemMessage(Component.literal("Sent ").withStyle(ChatFormatting.GREEN).append(Money.text(cents))
			.append(Component.literal(" to " + to.getName().getString() + ".").withStyle(ChatFormatting.GREEN)));
		to.sendSystemMessage(Component.literal(from.getName().getString() + " sent you ").withStyle(ChatFormatting.GREEN).append(Money.text(cents)));
		return 1;
	}

	private static int withdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		long cents = Money.fromDecimal(DoubleArgumentType.getDouble(ctx, "amount"));
		if (cents <= 0 || !MarketData.withdraw(player, cents)) {
			ctx.getSource().sendFailure(Component.literal("You don't have that much."));
			return 0;
		}
		MarketItems.give(player, MarketItems.banknote(cents));
		ctx.getSource().sendSuccess(() -> Component.literal("Printed a banknote worth ").withStyle(ChatFormatting.GREEN).append(Money.text(cents)), false);
		return 1;
	}

	private static int worth(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Hold something first."));
			return 0;
		}
		long value = PriceBook.stackSellValue(stack);
		long buy = PriceBook.unitBuy(stack.getItem());
		Component sell = value < 0 ? Component.literal("the market won't buy this").withStyle(ChatFormatting.RED) : Money.text(value);
		Component buyText = buy < 0 ? Component.literal("not for sale").withStyle(ChatFormatting.DARK_GRAY) : Money.text(buy);
		ctx.getSource().sendSuccess(() -> Component.literal("Sells for: ").withStyle(ChatFormatting.GRAY).append(sell)
			.append(Component.literal("  |  Buy price (each): ").withStyle(ChatFormatting.GRAY)).append(buyText), false);
		return 1;
	}

	private static int adjust(CommandContext<CommandSourceStack> ctx, int mode) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		long cents = Money.fromDecimal(DoubleArgumentType.getDouble(ctx, "amount"));
		if (mode > 0) {
			MarketData.deposit(target, cents);
		} else if (mode < 0) {
			MarketData.setBalance(target, Math.max(0, MarketData.balance(target) - cents));
		} else {
			MarketData.setBalance(target, cents);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " now has ").append(Money.text(MarketData.balance(target))), true);
		return 1;
	}
}
