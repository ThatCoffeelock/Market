package com.thatcoffeelock.mobilehome;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/** /mobilehome (help for everyone), /mobilehome give|refuel (ops). */
final class MobileHomeCommands {
	private MobileHomeCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (VehicleType type : VehicleType.values()) {
			give.then(literal(type.id).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Kits.give(player, Kits.kit(type));
				ctx.getSource().sendSuccess(() -> Component.literal("Here's a " + type.displayName + ". Drive safe.").withStyle(ChatFormatting.GOLD), false);
				return 1;
			}));
		}

		dispatcher.register(literal("mobilehome")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(give)
			.then(literal("refuel").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Vehicle vehicle = Vehicles.vehicleOf(player);
				if (vehicle == null) {
					ctx.getSource().sendFailure(Component.literal("Get in a vehicle first."));
					return 0;
				}
				vehicle.data.fuel = VehicleType.FUEL_CAPACITY;
				ctx.getSource().sendSuccess(() -> Component.literal("Filled her up.").withStyle(ChatFormatting.GREEN), false);
				return 1;
			})));
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Mobile Home ——",
			"§bCamper Van§7: §fGlass Glass Glass / Iron-block Chest Iron-block / Minecart Furnace Minecart",
			"§2Tank§7: §fIron-block Dispenser Iron-block / Obsidian Chest Obsidian / Minecart Furnace Minecart",
			"§7Right-click the ground with it to park. Right-click the vehicle to get in.",
			"§7Driving: §fW/S§7 gas/brake, §fA/D§7 steer, §fCtrl§7 turbo, §fSpace§7 honk, §fShift§7 get out.",
			"§7Sneak + right-click (or right-click while seated) for storage, workbench, fuel and pack-up.",
			"§7Fuel: anything a furnace burns. Sneak + right-click the vehicle holding it.",
			"§aWhile you're inside, monsters can't hurt you and get zapped away."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
