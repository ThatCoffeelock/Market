package com.thatcoffeelock.havana;

import java.util.Locale;

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

/** /havana (help for everyone), /havana give ... (ops). */
final class HavanaCommands {
	private HavanaCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> give = literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (String what : new String[] {"seeds", "leaves", "cured", "aged"}) {
			give.then(literal(what).executes(ctx -> give(ctx.getSource(), HavanaItems.byName(what, 16), "16 " + what)));
		}
		give.then(literal("barrel").executes(ctx -> give(ctx.getSource(), HavanaItems.curingBarrel(), "a Curing Barrel")));
		for (HavanaItems.Grade grade : HavanaItems.Grade.values()) {
			LiteralArgumentBuilder<CommandSourceStack> cigar = literal(grade == HavanaItems.Grade.AGED ? "granreserva" : "cigar");
			cigar.executes(ctx -> give(ctx.getSource(), HavanaItems.cigar(grade, HavanaItems.Flavor.NONE, 4), "4 cigars"));
			for (HavanaItems.Flavor flavor : HavanaItems.Flavor.values()) {
				if (flavor != HavanaItems.Flavor.NONE) {
					cigar.then(literal(flavor.name().toLowerCase(Locale.ROOT))
						.executes(ctx -> give(ctx.getSource(), HavanaItems.cigar(grade, flavor, 4), "4 " + HavanaItems.cigarName(grade, flavor) + "s")));
				}
			}
			give.then(cigar);
		}
		dispatcher.register(literal("havana")
			.executes(ctx -> {
				help(ctx.getSource());
				return 1;
			})
			.then(give));
	}

	private static int give(CommandSourceStack source, ItemStack stack, String what) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HavanaItems.give(player, stack);
		source.sendSuccess(() -> Component.literal("Here's " + what + ". Enjoy responsibly. Or don't, it's a block game.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static void help(CommandSourceStack source) {
		String[] lines = {
			"§6§l—— Havana ——",
			"§e1. Seeds§7: break grass and ferns, now and then you find §eTobacco Seeds§7.",
			"§e2. Grow§7: plant them on farmland. Fully grown, the plant shoots up two blocks tall. Break it for",
			"§7   3-5 §aTobacco Leaves§7 and a seed or two. Bone meal works.",
			"§e3. Cure§7: §fBarrel + Hay Bale§7 = §6Curing Barrel§7 (or name any barrel or chest \"Curing Barrel\" or \"Humidor\").",
			"§7   Leaves cure in a day. Leave §6Cured Tobacco§7 in two more days and it becomes §dAged Tobacco§7.",
			"§e4. Roll§7: hold tobacco and right-click a crafting table. 3 tobacco = 1 cigar. Add a flavor if you like:",
			"§7   honey, cocoa beans, sweet berries, glow berries or blaze powder. Aged tobacco rolls §dGran Reserva§7.",
			"§e5. Smoke§7: light it with flint and steel in your other hand (or on a campfire, torch or lantern),",
			"§7   then right-click to puff. 8 puffs per cigar. Don't chain-smoke, you'll cough.",
			"§8Cigars go out underwater. Obviously.",
		};
		for (String line : lines) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
