package com.thatcoffeelock.skills;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /skills (menu), /skills top <skill>, /skills admin ... (ops). */
final class SkillsCommands {
	private SkillsCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> top = literal("top");
		LiteralArgumentBuilder<CommandSourceStack> setlevel = literal("setlevel");
		LiteralArgumentBuilder<CommandSourceStack> addxp = literal("addxp");
		var setTarget = argument("player", EntityArgument.player());
		var addTarget = argument("player", EntityArgument.player());
		for (Skill skill : Skill.values()) {
			top.then(literal(skill.id()).executes(ctx -> top(ctx.getSource(), skill)));
			setTarget.then(literal(skill.id()).then(argument("level", IntegerArgumentType.integer(0, Skill.MAX_LEVEL))
				.executes(ctx -> setLevel(ctx, skill, IntegerArgumentType.getInteger(ctx, "level")))));
			addTarget.then(literal(skill.id()).then(argument("xp", IntegerArgumentType.integer(1))
				.executes(ctx -> addXp(ctx, skill, IntegerArgumentType.getInteger(ctx, "xp")))));
		}
		setlevel.then(setTarget);
		addxp.then(addTarget);

		LiteralArgumentBuilder<CommandSourceStack> admin = literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(setlevel)
			.then(addxp)
			.then(literal("reset").then(argument("player", EntityArgument.player()).executes(SkillsCommands::reset)))
			.then(literal("reload").executes(ctx -> {
				SkillsConfig.load();
				ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/skills.json.").withStyle(ChatFormatting.GREEN), true);
				return 1;
			}));

		dispatcher.register(literal("skills")
			.executes(ctx -> {
				SkillsMenu.open(ctx.getSource().getPlayerOrException(), null);
				return 1;
			})
			.then(literal("help").executes(ctx -> {
				help(ctx.getSource());
				return 1;
			}))
			.then(top)
			.then(admin));
	}

	private static int top(CommandSourceStack source, Skill skill) {
		List<SkillsStore.Profile> best = SkillsStore.top(skill, 10);
		source.sendSystemMessage(Component.literal("—— Best at " + skill.title + " ——").withStyle(skill.color, ChatFormatting.BOLD));
		if (best.isEmpty()) {
			source.sendSystemMessage(Component.literal("Nobody yet. Be the first.").withStyle(ChatFormatting.GRAY));
		}
		int place = 1;
		for (SkillsStore.Profile profile : best) {
			source.sendSystemMessage(Component.literal(place++ + ". ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(profile.name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  Lv " + profile.level(skill)).withStyle(ChatFormatting.YELLOW)));
		}
		return 1;
	}

	private static int setLevel(CommandContext<CommandSourceStack> ctx, Skill skill, int level) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		SkillsStore.Profile profile = SkillsStore.of(player);
		profile.xp.put(skill.id(), Skill.totalFor(level));
		// refund perks that the new level can't pay for
		if (profile.pointsSpent(skill) > profile.pointsEarned(skill)) {
			for (Perk perk : Perk.of(skill)) {
				profile.perks.remove(perk.id());
			}
		}
		SkillsStore.changed();
		Boosts.refresh(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + "'s " + skill.title + " is now level " + level + ".")
			.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int addXp(CommandContext<CommandSourceStack> ctx, Skill skill, int xp) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		SkillsStore.Profile profile = SkillsStore.of(player);
		double before = profile.xp(skill);
		int oldLevel = Skill.levelFor(before);
		profile.xp.put(skill.id(), Math.min(before + xp, Skill.totalFor(Skill.MAX_LEVEL)));
		SkillsStore.changed();
		Boosts.refresh(player);
		int newLevel = profile.level(skill);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + xp + " " + skill.title + " XP to " + player.getName().getString()
			+ " (level " + oldLevel + " -> " + newLevel + ").").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		SkillsStore.Profile profile = SkillsStore.of(player);
		profile.xp.clear();
		profile.perks.clear();
		SkillsStore.changed();
		Boosts.refresh(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Reset all of " + player.getName().getString() + "'s skills.")
			.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§5§l—— Skills ——",
			"§7Do things to get better at them. Mine to level §fMining§7, chop to level §fWoodcutting§7, and so on.",
			"§7Every level gives a small passive bonus. Level 100 is about twice as good as a beginner.",
			"§7Every §e10 levels§7 you earn a §eperk point§7 for that skill. Each skill has 3 perks with 5 ranks:",
			"§7that's 15 ranks for 10 points, so pick what suits you.",
			"§e/skills§7: your skills and perks. §e/skills top <skill>§7: the leaderboard.",
			"§8Ores, logs and dirt you placed yourself give no XP. Nice try though.",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
