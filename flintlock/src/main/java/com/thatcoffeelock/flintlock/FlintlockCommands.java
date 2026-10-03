package com.thatcoffeelock.flintlock;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /flintlock (help for everyone), /flintlock give pistol|musket|blunderbuss|cartridges|scattershot (ops). */
final class FlintlockCommands {
	private FlintlockCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (Gun gun : Gun.values()) {
			give.then(literal(gun.id).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				GunItems.give(player, GunItems.gun(gun));
				ctx.getSource().sendSuccess(() -> Component.literal("One " + gun.title + ". It isn't loaded. Probably.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			}));
		}
		give.then(ammo("cartridges", Gun.Ammo.CARTRIDGE));
		give.then(ammo("scattershot", Gun.Ammo.SCATTERSHOT));
		dispatcher.register(literal("flintlock")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(give));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ammo(String name, Gun.Ammo ammo) {
		return literal(name)
			.executes(ctx -> giveAmmo(ctx.getSource(), ammo, 16))
			.then(argument("count", IntegerArgumentType.integer(1, 64))
				.executes(ctx -> giveAmmo(ctx.getSource(), ammo, IntegerArgumentType.getInteger(ctx, "count"))));
	}

	private static int giveAmmo(CommandSourceStack source, Gun.Ammo ammo, int count) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		GunItems.give(player, GunItems.ammo(ammo, count));
		source.sendSuccess(() -> Component.literal(count + " × " + ammo.title + ". Keep it away from the fireplace.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Flintlock ——",
			"§eFlintlock Pistol§7: §fIron Ingot, Flint / _, Planks §7(2×2)",
			"§6Musket§7: §fIron Ingot, _, _ / _, Iron Ingot, _ / _, Flint, Planks",
			"§cBlunderbuss§7: §fCopper Ingot, _, _ / _, Copper Ingot, _ / _, Flint, Planks",
			"§fPaper Cartridge§7 (×4): §fPaper + Gunpowder + Iron Nugget, any shape",
			"§7Scattershot§7 (×2): §fPaper + Gunpowder + Flint + Gravel, any shape",
			"§7Right-click an empty gun to reload it. Keep holding it until the bar fills.",
			"§7Right-click a loaded gun to fire. Pistol 2s, Blunderbuss 3s, Musket 4s to reload.",
			"§7Pistol and Musket take cartridges. The Blunderbuss takes scattershot.",
			"§cMusket: hits hardest. Blunderbuss: eight pellets, short range, sends things flying. Kicks, too."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
