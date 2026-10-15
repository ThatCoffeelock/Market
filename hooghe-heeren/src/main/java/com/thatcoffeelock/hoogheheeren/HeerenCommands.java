package com.thatcoffeelock.hoogheheeren;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /heeren: how it works. /heeren court: where you stand. /heeren dispatches and /heeren answer N 1-3: deal with
 * letters from anywhere. Ops: /heeren give bell, /heeren battle (start the pending battle now), /heeren intel (know
 * everything about every house for a week).
 */
final class HeerenCommands {
	private HeerenCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("heeren")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("court").executes(ctx -> court(ctx.getSource())))
			.then(literal("dispatches").executes(ctx -> dispatches(ctx.getSource())))
			.then(literal("answer")
				.then(argument("dispatch", IntegerArgumentType.integer(1, 20))
					.then(argument("answer", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> answer(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "dispatch"),
							IntegerArgumentType.getInteger(ctx, "answer"))))))
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("bell").executes(ctx -> {
					Seats.give(ctx.getSource().getPlayerOrException(), Hall.item());
					ctx.getSource().sendSuccess(() -> Component.literal("Here's an Audience Bell. Hang it somewhere grand.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})))
			.then(literal("battle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					Court c = court(ctx.getSource().getPlayerOrException());
					Court.Battle b = c.battle;
					if (b == null) {
						ctx.getSource().sendFailure(Component.literal("No battle is set."));
						return 0;
					}
					b.day = Courts.today();
					long noon = b.day * Battles.DAY + Battles.NOON;
					ctx.getSource().sendSuccess(() -> Component.literal("The battle near " + b.x + ", " + b.z + " is set for game time " + noon
						+ " (now " + ctx.getSource().getLevel().getGameTime() + ").").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
			.then(literal("intel").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					Court c = court(ctx.getSource().getPlayerOrException());
					for (Rival r : c.rivals) {
						r.intelUntil = Courts.today() + 7;
						r.met = true;
					}
					Courts.changed();
					ctx.getSource().sendSuccess(() -> Component.literal("Every house's letters are an open book for a week.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})));
	}

	private static Court court(ServerPlayer player) {
		return Courts.of(player.getUUID(), player.getName().getString());
	}

	private static void help(CommandSourceStack source) {
		for (String line : List.of(
			"⚜ De Hooghe Heeren: rival houses for your Compagnie.",
			"Open the banner tab in the top-right corner of your Company Desk.",
			"Each house has a treasury, regiments and an income, and plays by your rules: no treasury, no army.",
			"Diplomacy: gifts, trade and peace pacts, alliances, tribute. Honour wins you pacts; Fear makes demands stick.",
			"Errands: spy, sabotage, rob, bribe, forge letters, assassinate, run by your Compagnie agents.",
			"In person: visit their seat (ask for directions), or hang an Audience Bell (bell + gold ingot + paper) and invite them.",
			"War: raise regiments, skirmish every dawn, and ride out at noon to lead your men in battle.",
			"/heeren court · /heeren dispatches · /heeren answer <n> <1-3>")) {
			source.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	private static int court(CommandSourceStack source) throws CommandSyntaxException {
		Court c = court(source.getPlayerOrException());
		long today = Courts.today();
		source.sendSystemMessage(Component.literal(c.reputation() + ": Honour " + c.honour + ", Fear " + c.fear + ", " + c.regiments + " regiments.")
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		for (Rival r : c.rivals) {
			int op = c.opinion(r, today);
			Court.Tier tier = Court.Tier.of(op);
			source.sendSystemMessage(Component.literal(" " + r.name() + " (" + r.seatName + "): ").withStyle(Names.colour(r.colour).chat())
				.append(Component.literal(tier.title + " " + Wars.signed(op) + (r.war ? ", AT WAR (" + Wars.signed(r.warScore) + ")" : "")
					+ (r.tradePact ? ", trade pact" : "") + (r.alliance ? ", allies" : r.peacePact ? ", peace pact" : "")).withStyle(tier.color)));
		}
		return 1;
	}

	private static int dispatches(CommandSourceStack source) throws CommandSyntaxException {
		Court c = court(source.getPlayerOrException());
		if (c.dispatches.isEmpty()) {
			source.sendSystemMessage(Component.literal("No dispatches waiting.").withStyle(ChatFormatting.GRAY));
			return 1;
		}
		int n = 1;
		for (Court.Dispatch d : c.dispatches) {
			source.sendSystemMessage(Component.literal(n + ". " + Dispatches.text(c, d)).withStyle(ChatFormatting.GOLD));
			List<String> options = Dispatches.options(c, d);
			for (int i = 0; i < options.size(); i++) {
				source.sendSystemMessage(Component.literal("   /heeren answer " + n + " " + (i + 1) + ": " + options.get(i)).withStyle(ChatFormatting.YELLOW));
			}
			n++;
		}
		return 1;
	}

	private static int answer(CommandSourceStack source, int which, int choice) throws CommandSyntaxException {
		Court c = court(source.getPlayerOrException());
		if (which > c.dispatches.size()) {
			source.sendFailure(Component.literal("There's no dispatch " + which + "."));
			return 0;
		}
		Court.Dispatch d = c.dispatches.get(which - 1);
		if (choice > Dispatches.options(c, d).size()) {
			source.sendFailure(Component.literal("That letter only has " + Dispatches.options(c, d).size() + " answers."));
			return 0;
		}
		String why = Dispatches.answer(c, d, choice - 1, Courts.today(), false);
		if (why != null) {
			source.sendFailure(Component.literal(why));
			return 0;
		}
		return 1;
	}
}
