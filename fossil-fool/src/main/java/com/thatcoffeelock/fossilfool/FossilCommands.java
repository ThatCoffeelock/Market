package com.thatcoffeelock.fossilfool;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /fossilfool (help for everyone), /fossilfool give ... and /fossilfool admin ... (ops). */
final class FossilCommands {
	private FossilCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(literal("fossilfool")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("rig").executes(ctx -> give(ctx.getSource(), OilItems.rig(), "A Drill Rig. Mind the hole.")))
				.then(literal("tank").executes(ctx -> give(ctx.getSource(), OilItems.tank(), "An Oil Tank. Takes any fluid."))
					.then(literal("crude").executes(ctx -> give(ctx.getSource(), OilItems.tank(Fluid.CRUDE), "An Oil Tank, crude only.")))
					.then(literal("diesel").executes(ctx -> give(ctx.getSource(), OilItems.tank(Fluid.DIESEL), "A Diesel Tank.")))
					.then(literal("water").executes(ctx -> give(ctx.getSource(), OilItems.tank(Fluid.WATER), "A Water Tank.")))
					.then(literal("lava").executes(ctx -> give(ctx.getSource(), OilItems.tank(Fluid.LAVA), "A Lava Tank. Don't sit in it."))))
				.then(literal("pipe")
					.executes(ctx -> give(ctx.getSource(), OilItems.pipe(64), "64 Pipes. Mind the lightning."))
					.then(argument("count", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> give(ctx.getSource(), OilItems.pipe(IntegerArgumentType.getInteger(ctx, "count")), "Pipes."))))
				.then(literal("refinery").executes(ctx -> give(ctx.getSource(), OilItems.refinery(), "A Refinery.")))
				.then(literal("rod").executes(ctx -> give(ctx.getSource(), OilItems.rod(), "A Dowsing Rod. Science!")))
				.then(literal("crude")
					.executes(ctx -> give(ctx.getSource(), OilItems.crude(16), "16 buckets of crude."))
					.then(argument("count", IntegerArgumentType.integer(1, 16))
						.executes(ctx -> give(ctx.getSource(), OilItems.crude(IntegerArgumentType.getInteger(ctx, "count")), "Crude, fresh from the ground."))))
				.then(literal("diesel")
					.executes(ctx -> give(ctx.getSource(), OilItems.diesel(16), "16 buckets of diesel."))
					.then(argument("count", IntegerArgumentType.integer(1, 16))
						.executes(ctx -> give(ctx.getSource(), OilItems.diesel(IntegerArgumentType.getInteger(ctx, "count")), "Diesel. Smells great.")))))
			.then(literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("reload").executes(ctx -> {
					FossilConfig.load();
					ctx.getSource().sendSuccess(() -> Component.literal("Fossil Fool config reloaded.").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
				.then(literal("pocket").executes(ctx -> pocket(ctx.getSource())))));
	}

	private static int give(CommandSourceStack source, ItemStack stack, String text) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		OilItems.give(player, stack);
		source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	/** Tells an admin where the nearest pocket is, exactly. For testing, and for settling arguments. */
	private static int pocket(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		Pockets.Reading r = Pockets.dowse(level, player.blockPosition(), 256);
		if (r == null) {
			source.sendFailure(Component.literal("No oil pocket within 256 blocks."));
			return 0;
		}
		Pockets.Pocket p = r.pocket();
		source.sendSuccess(() -> Component.literal("Nearest pocket: " + p.x() + " " + p.y() + " " + p.z() + " (" + (p.gusher() ? "gusher, " : "")
			+ (2 * p.rx() + 1) + "×" + (2 * p.ry() + 1) + "×" + (2 * p.rz() + 1) + (Pockets.isOpened(p) ? ", opened, " + Pockets.left(p) + " buckets left" : "")
			+ ")").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Fossil Fool ——",
			"§eDowsing Rod§7: right-click anywhere. It twitches towards hidden oil pockets.",
			"§eDrill Rig§7: right-click the ground. It sinks a §f5×5 shaft§7 straight down, one block at a time.",
			"§7  Right-click it for its screen: §ffirebox§7, §fore hold§7, §fstone hold§7, crude tank.",
			"§7  It strikes oil when it hits a pocket, pumps it dry, then keeps drilling to bedrock.",
			"§eFuel§7, each better than the last: §8coal§7 → §clava§7 → §7crude → §6diesel",
			"§eOil Tank§7: right-click with buckets: crude, diesel, water or lava. Sneak + empty hand picks its fluid.",
			"§ePipe§7: a line of them links rigs, tanks, refineries and chests, however far apart.",
			"§7  Rigs and refineries burn diesel, crude or lava from the tanks they reach when their firebox is empty.",
			"§eRefinery§7: 2 crude → 1 diesel, with fuel in its firebox.",
			"§7Found oil by hand? Right-click it with an §fempty bucket§7.",
			"§7Sell crude and diesel at the §fMarket§7. Level §fWildcatting§7 in /skills."
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
