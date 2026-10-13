package com.thatcoffeelock.proost;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.literal;

/** /proost (help for everyone), /proost drunk (how drunk am I?), /proost give ... (ops). */
final class ProostCommands {
	private ProostCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		LiteralArgumentBuilder<CommandSourceStack> mash = literal("mash");
		for (ProostItems.Base base : ProostItems.Base.values()) {
			mash.then(literal(base.id()).executes(ctx -> give(ctx.getSource(), ProostItems.mash(base, 64), "64 " + base.title)));
		}
		give.then(mash);
		for (ProostItems.Drink drink : ProostItems.Drink.values()) {
			give.then(literal(drink.id())
				.executes(ctx -> give(ctx.getSource(), ProostItems.spirit(drink, false, 4), "4 bottles of " + drink.title(false)))
				.then(literal("aged").executes(ctx -> give(ctx.getSource(), ProostItems.spirit(drink, true, 4), "4 bottles of " + drink.title(true)))));
		}
		give.then(literal("tun").executes(ctx -> give(ctx.getSource(), ProostItems.mashTun(), "a Mash Tun")));
		give.then(literal("cask").executes(ctx -> give(ctx.getSource(), ProostItems.oakCask(), "an Oak Cask")));
		dispatcher.register(literal("proost")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(literal("drunk").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				double drunk = Drinking.drunkenness(player.getUUID());
				ctx.getSource().sendSuccess(() -> Component.literal(drunk <= 0 ? "You're stone cold sober. Tragic."
					: String.format(java.util.Locale.ROOT, "You're %s (%.1f). It wears off a point every 30 seconds.", Drinking.describe(drunk), drunk))
					.withStyle(ChatFormatting.GOLD), false);
				return 1;
			}))
			.then(give));
	}

	private static int give(CommandSourceStack source, ItemStack stack, String what) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ProostItems.give(player, stack);
		source.sendSuccess(() -> Component.literal("Here's " + what + ". Proost!").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Proost ——",
			"§e1. Ferment§7: §fBarrel + Water Bucket§7 = §6Mash Tun§7 (or name any barrel or chest \"Mash Tun\").",
			"§7   Wheat, sugar cane or apples in it become §eMash§7 in half a day, one for one.",
			"§e2. Distill§7: put a cauldron on a lit campfire (or fire, lava, magma, a lit furnace). Hold mash, have a",
			"§7   glass bottle on you and right-click it: 8 mash = 1 bottle. Sneak to do up to 8 at once.",
			"§7   Grain mash → §6Whisky§7, or §bJonge Genever§7 with sweet berries in your other hand.",
			"§7   Cane mash → §4White Rum§7. Apple mash → §cBrandy§7.",
			"§e3. Age§7 (optional): §fBarrel + 2 Iron Ingots§7 = §6Oak Cask§7. Full bottles in it age in two days:",
			"§7   §dOude Genever§7, §dAged Whisky§7, §dDark Rum§7, §dBrandy VSOP§7.",
			"§e4. Drink§7: right-click a bottle. 4 swigs each. Every swig has its effects, and makes you a bit drunker.",
			"§7   Tipsy is fine. Drunk, the world spins and you stagger. Keep going and you pass out. §8/proost drunk",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
