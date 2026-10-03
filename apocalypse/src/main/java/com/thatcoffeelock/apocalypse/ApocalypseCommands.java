package com.thatcoffeelock.apocalypse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /apocalypse (status for everyone), /apocalypse help, /apocalypse admin ... (ops). */
final class ApocalypseCommands {
	private ApocalypseCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> admin = literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(literal("horde")
				.executes(ctx -> horde(ctx.getSource(), 0))
				.then(argument("size", IntegerArgumentType.integer(1, 64)).executes(ctx -> horde(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size")))))
			.then(literal("hordenight")
				.then(literal("start").executes(ctx -> {
					HordeNight.force(ctx.getSource().getServer());
					return 1;
				}))
				.then(literal("stop").executes(ctx -> {
					HordeNight.stop(ctx.getSource().getServer());
					return 1;
				})))
			.then(literal("infect").then(argument("player", EntityArgument.player()).executes(ctx -> {
				ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
				Infection.infect(p);
				ctx.getSource().sendSuccess(() -> Component.literal("Bit " + p.getName().getString() + ". Rude.").withStyle(ChatFormatting.DARK_GREEN), true);
				return 1;
			})))
			.then(literal("cure").then(argument("player", EntityArgument.player()).executes(ctx -> {
				ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
				Infection.cure(p, "An admin waved their hands at you. Science!");
				ctx.getSource().sendSuccess(() -> Component.literal("Cured " + p.getName().getString() + ".").withStyle(ChatFormatting.GREEN), true);
				return 1;
			})))
			.then(literal("clear").executes(ctx -> {
				int n = Hordes.clear();
				ctx.getSource().sendSuccess(() -> Component.literal("Removed " + n + " horde zombies.").withStyle(ChatFormatting.GREEN), true);
				return 1;
			}))
			.then(literal("reload").executes(ctx -> {
				ApocalypseConfig.load();
				ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/apocalypse.json.").withStyle(ChatFormatting.GREEN), true);
				return 1;
			}));

		LiteralArgumentBuilder<CommandSourceStack> base = literal("base")
			.executes(ctx -> baseInfo(ctx.getSource()))
			.then(literal("set")
				.executes(ctx -> setBase(ctx.getSource(), ApocalypseConfig.get().baseRadius))
				.then(argument("radius", IntegerArgumentType.integer(8, 1024))
					.executes(ctx -> setBase(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
			.then(literal("remove").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				boolean removed = Bases.remove(ctx.getSource().getServer(), player.getUUID());
				ctx.getSource().sendSystemMessage(Component.literal(removed ? "Base abandoned. The dead are welcome again." : "You don't have a base.")
					.withStyle(removed ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
				return removed ? 1 : 0;
			}));

		dispatcher.register(literal("apocalypse")
			.executes(ctx -> status(ctx.getSource()))
			.then(base)
			.then(literal("help").executes(ctx -> {
				help(ctx.getSource());
				return 1;
			}))
			.then(admin));
	}

	private static int status(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		long day = Hordes.day(level);
		long until = HordeNight.daysUntil(day);
		source.sendSystemMessage(Component.literal("☠ Day " + day + " of the apocalypse").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		String night = HordeNight.active() ? "§4§lHORDE NIGHT IS NOW. §cWhy are you reading chat?"
			: until < 0 ? "§7Horde Nights are off. Lucky you."
			: until == 0 ? "§cHorde Night is tonight."
			: "§7Next Horde Night in §f" + until + "§7 day" + (until == 1 ? "" : "s") + ".";
		source.sendSystemMessage(Component.literal(night));
		int[] near = Hordes.census(source.getLevel(), source.getPosition(), 96);
		source.sendSystemMessage(Component.literal(near[0] == 0 ? "§7No hordes within 96 blocks. That you know of."
			: "§c" + near[0] + " horde" + (near[0] == 1 ? "" : "s") + " (" + near[1] + " zombies)§7 within 96 blocks."));
		if (source.getEntity() instanceof ServerPlayer player) {
			int s = Infection.seconds(player);
			if (s >= 0) {
				int left = Math.max(0, Infection.total() - s);
				source.sendSystemMessage(Component.literal("§2☣ You're infected (" + Infection.stage(s, Infection.total()).name().toLowerCase(java.util.Locale.ROOT)
					+ "). §a" + left / 60 + " min left. Eat a golden apple."));
			} else {
				source.sendSystemMessage(Component.literal("§aYou're not infected. Keep it that way."));
			}
		}
		return 1;
	}

	private static int baseInfo(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Bases.Base mine = Bases.of(player.getUUID());
		if (mine == null) {
			source.sendSystemMessage(Component.literal("§7You don't have a base. Stand in the middle of it and use §f/apocalypse base set [radius]§7. "
				+ "No zombies spawn inside (radius " + ApocalypseConfig.get().baseRadius + " by default, up to " + ApocalypseConfig.get().maxBaseRadius + ")."));
		} else {
			double dx = player.getX() - (mine.x + 0.5);
			double dz = player.getZ() - (mine.z + 0.5);
			int away = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			source.sendSystemMessage(Component.literal("§aYour base: §f" + mine.x + " " + mine.y + " " + mine.z + "§a, radius §f" + mine.radius
				+ "§a. " + (away <= mine.radius ? "You're inside." : "You're " + (away - mine.radius) + " blocks outside it.")));
		}
		Bases.Base here = Bases.at(player.level(), player.getX(), player.getZ());
		if (here != null && here != mine) {
			source.sendSystemMessage(Component.literal("§7You're standing in §f" + here.name + "§7's base."));
		}
		return 1;
	}

	private static int setBase(CommandSourceStack source, int radius) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ApocalypseConfig cfg = ApocalypseConfig.get();
		ServerLevel level = (ServerLevel) player.level();
		if (level.dimension() != Level.OVERWORLD) {
			source.sendFailure(Component.literal("Bases only work in the overworld. That's where the zombies are."));
			return 0;
		}
		if (radius > cfg.maxBaseRadius) {
			source.sendFailure(Component.literal("That's a kingdom, not a base. The biggest allowed is " + cfg.maxBaseRadius + "."));
			return 0;
		}
		if (cfg.baseClearRange > 0) {
			int near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(cfg.baseClearRange), Undead::isZombie).size();
			if (near > 0) {
				source.sendFailure(Component.literal("Not with " + near + " zombie" + (near == 1 ? "" : "s") + " within " + cfg.baseClearRange
					+ " blocks. Deal with them first: a base is a home, not a panic button."));
				return 0;
			}
		}
		Bases.Base base = Bases.set(source.getServer(), player.getUUID(), player.getName().getString(), player.blockPosition(), radius);
		source.sendSystemMessage(Component.literal("⌂ Base claimed: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
			.append(Component.literal(base.radius + " blocks around " + base.x + " " + base.y + " " + base.z
				+ ". No zombies spawn here. They can still walk in, so keep the doors shut.").withStyle(ChatFormatting.GREEN)));
		return 1;
	}

	private static int horde(CommandSourceStack source, int size) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		BlockPos spot = Hordes.spotNear(level, player);
		if (spot == null) {
			source.sendFailure(Component.literal("Couldn't find solid ground nearby for them to stand on."));
			return 0;
		}
		int n = size > 0 ? size : Hordes.size(level, HordeNight.active());
		Hordes.Horde h = Hordes.spawn(level, spot, n, false);
		int spawned = h == null ? 0 : h.size();
		source.sendSuccess(() -> Component.literal("Summoned a horde of " + spawned + " at " + spot.toShortString() + ". Good luck.")
			.withStyle(ChatFormatting.DARK_RED), true);
		return spawned;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§4§l—— Zombie Apocalypse ——",
			"§7Zombies roam in §chordes§7 that follow a leader and drift toward survivors. Hordes merge and pick up stragglers.",
			"§7One of them sees you, they all see you. They chew through §fdoors§7, §ftrapdoors§7, §ffence gates§7 and §fglass§7. Iron holds.",
			"§7They hunt by §fsound§7: breaking blocks, fighting and sprinting draw them in. Sneak.",
			"§7Ring a §6bell§7 to lure every zombie within 64 blocks to it. That's how you herd them.",
			"§7Every 7th night is §4Horde Night§7. You get a warning that morning.",
			"§7Bites can §2infect§7 you. A §6golden apple§7 cures it. Die infected and you get back up, wearing your stuff.",
			"§7In daylight only §fhusks§7 come: they don't burn.",
			"§f/apocalypse base set [radius]§7 claims your base: no zombies spawn inside. They can still walk in.",
			"§f/apocalypse§7 shows the day, the next Horde Night and whether you're infected.",
			"§7Ops: §f/apocalypse admin horde [size]|hordenight start|stop|infect|cure <player>|clear|reload"
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
