package com.thatcoffeelock.hoogheheeren;

import java.util.List;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * The banner tab, for a company that hasn't joined the Hooghe Heeren: rivals are optional. This page says what
 * taking a seat means, and seats you only if you ask. Until then, no rival houses exist for you at all.
 */
final class JoinMenu extends Ui {
	static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new JoinMenu(id, player, new SimpleContainer(SIZE)),
			Component.literal("De Hooghe Heeren · an invitation").withStyle(ChatFormatting.DARK_PURPLE)));
	}

	private JoinMenu(int syncId, ServerPlayer viewer, SimpleContainer box) {
		super(syncId, viewer, box);
		render();
	}

	@Override
	protected void draw() {
		button(13, glow(icon(item("white_banner", Items.PAPER), t("De Hooghe Heeren", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), List.of(
			t("An optional way to play: rival houses for your company.", ChatFormatting.WHITE),
			t("Three to five lords with treasuries, regiments and long", ChatFormatting.GRAY),
			t("memories. They play by your rules: no treasury, no army.", ChatFormatting.GRAY),
			t("Trade with them, sign pacts, feast them in your hall,", ChatFormatting.GRAY),
			t("spy on them, rob them, or go to war and lead your", ChatFormatting.GRAY),
			t("regiments in the field at noon.", ChatFormatting.GRAY),
			t("They'll also come for you: tribute demands, raids on", ChatFormatting.RED),
			t("your convoys, spies, saboteurs, declarations of war.", ChatFormatting.RED),
			t("Nothing happens until you take your seat.", ChatFormatting.DARK_GRAY)))), null);
		button(29, glow(icon(Items.EMERALD, t("Take your seat at the table", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
			t("The rival houses are drawn up at once.", ChatFormatting.GRAY),
			t("You can leave again any time you're not at war.", ChatFormatting.DARK_GRAY)))), () -> {
			click();
			Court c = Courts.of(viewer.getUUID(), viewer.getName().getString());
			Courts.tell(c, "You take your seat among the Hooghe Heeren. " + c.rivals.size() + " houses are already whispering about you.",
				ChatFormatting.LIGHT_PURPLE);
			HeerenMod.nextTick(() -> CourtMenu.open(viewer, c, CourtMenu.Page.RIVALS));
		});
		button(33, icon(Items.BARRIER, t("Not now", ChatFormatting.GRAY), List.of(t("Your company carries on without rivals.", ChatFormatting.DARK_GRAY))),
			this::close);
		button(8, icon(item("cartography_table", Items.PAPER), t("Back to the desk", ChatFormatting.YELLOW), List.of()), () -> {
			click();
			HeerenMod.nextTick(() -> CompagnieHooks.openDesk(viewer));
		});
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
