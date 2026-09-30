package com.thatcoffeelock.hamlets;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

import static net.minecraft.commands.Commands.literal;

/** /hamlets (help for everyone), /hamlets build <plan> <inhabitants> (ops). */
final class HamletsCommands {
	private HamletsCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> build = literal("build").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (Plan plan : Plan.values()) {
			LiteralArgumentBuilder<CommandSourceStack> node = literal(plan.id);
			for (Folk folk : Folk.values()) {
				node.then(literal(folk.id).executes(ctx -> build(ctx.getSource(), plan, folk)));
			}
			build.then(node);
		}
		dispatcher.register(literal("hamlets")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(build));
	}

	private static int build(CommandSourceStack source, Plan plan, Folk folk) {
		BlockPos origin = BlockPos.containing(source.getPosition()).below();
		Direction ahead = Direction.fromYRot(source.getRotation().y);
		Rotation turn = switch (ahead) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		Canvas canvas = HamletsMod.buildNow(source.getLevel(), plan, folk, origin, turn, source.getLevel().getRandom().nextLong());
		int spawned = canvas.spawned;
		source.sendSuccess(() -> Component.literal("Built a " + plan.id + " for " + folk.id + " (" + spawned + " moved in).")
			.withStyle(ChatFormatting.GOLD), true);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Hamlets & Horrors ——",
			"§7Cottages, castles and dungeons now appear as you explore new land.",
			"§aCottages§7 and §acastles§7 with villagers. Castles have an iron golem on guard.",
			"§cHaunted cottages§7, §cruined castles§7 (bandits in the courtyard) and §cdungeons§7 with monsters.",
			"§7Dungeons keep §fprisoners§7: press the button by a cell door to set them free.",
			"§7Find one: §f/locate structure hamlets:castle§7 (or cottage, haunted_cottage, ruined_castle, dungeon).",
			"§7Ops: §f/hamlets build <cottage|castle|dungeon> <villagers|monsters>§7 builds one where you stand."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
