package com.thatcoffeelock.hoogheheeren;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Talking to a rival in person: as your guest at your hall, or in their own great hall at their seat. Every proposal
 * is 15% likelier to land face to face, and gifts count for more. At your hall you can also lay on a feast, see them
 * off, or bar the doors; with the doors barred, decide what happens to your prisoner.
 */
final class AudienceMenu extends Ui {
	enum Where {
		HALL, SEAT
	}

	private final Court court;
	private final String rivalId;
	private final Where where;
	private boolean confirmBar;

	static void open(ServerPlayer player, Court court, Rival r, Where where) {
		r.met = true;
		Courts.changed();
		String title = where == Where.HALL ? (court.guestHeld ? "Your prisoner: " : "Your guest: ") + r.name() : "Audience at " + r.seatName;
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AudienceMenu(id, player, court, r.id, where, new SimpleContainer(SIZE)),
			Component.literal(title).withStyle(Names.colour(r.colour).chat())));
	}

	private AudienceMenu(int syncId, ServerPlayer viewer, Court court, String rivalId, Where where, SimpleContainer box) {
		super(syncId, viewer, box);
		this.court = court;
		this.rivalId = rivalId;
		this.where = where;
		render();
	}

	private void nope(@Nullable String why) {
		if (why != null) {
			Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1f);
			viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
		} else {
			click();
		}
	}

	@Override
	protected void draw() {
		Rival r = court.rival(rivalId);
		long today = Courts.today();
		if (r == null || (where == Where.HALL && !r.id.equals(court.guest))) {
			button(22, icon(Items.BARRIER, t("They've gone.", ChatFormatting.GRAY), List.of()), this::close);
			return;
		}
		button(4, CourtMenu.portrait(court, r, today, true), null);
		if (where == Where.HALL && court.guestHeld) {
			prisoner(r, today);
			return;
		}
		button(19, icon(item("gold_nugget", Items.PAPER), t("Give a gift, in person", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
			t("Click: " + Bank.format(Diplomacy.smallGift(court)) + " · right-click: " + Bank.format(Diplomacy.bigGift(court)), ChatFormatting.GOLD),
			t("Counts half again in person (double if they're Vain).", ChatFormatting.GRAY))),
			() -> nope(Diplomacy.gift(court, r, right, true, today)));
		if (!r.war) {
			button(20, offer("Propose a trade pact", "map", r.tradePact, Diplomacy.tradeOdds(court, r, true, today)),
				r.tradePact ? null : () -> nope(Diplomacy.proposeTrade(court, r, true, today)));
			button(21, offer("Propose a peace pact", "white_banner", r.peacePact, Diplomacy.peaceOdds(court, r, true, today)),
				r.peacePact ? null : () -> nope(Diplomacy.proposePeacePact(court, r, true, today)));
			button(22, offer("Propose an alliance", "shield", r.alliance, Diplomacy.allianceOdds(court, r, true, today)),
				r.alliance ? null : () -> nope(Diplomacy.proposeAlliance(court, r, true, today)));
			button(23, icon(item("iron_bars", Items.PAPER), t("Demand tribute, to their face", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
				t(Bank.format(Diplomacy.tributeAmount(r)) + " a day for ten days.", ChatFormatting.GOLD),
				t("Odds: " + Diplomacy.pct(Diplomacy.tributeOdds(court, r, true, today)), ChatFormatting.WHITE))),
				() -> nope(Diplomacy.demandTribute(court, r, true, today)));
		} else {
			button(21, icon(item("white_banner", Items.PAPER), t("Offer peace, face to face", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
				t("War score " + Wars.signed(r.warScore) + ".", ChatFormatting.WHITE))), () -> nope(Diplomacy.offerPeace(court, r, false, today)));
			long price = Diplomacy.peacePrice(court, r);
			if (price > 0) {
				button(23, icon(Items.GOLD_INGOT, t("Buy peace: " + Bank.format(price), ChatFormatting.GOLD, ChatFormatting.BOLD), List.of()),
					() -> nope(Diplomacy.offerPeace(court, r, true, today)));
			}
		}
		if (where == Where.HALL) {
			button(24, icon(item("cake", Items.PAPER), t("Lay on a feast", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
				t("From what you carry: up to 16 food, 6 Proost spirits", ChatFormatting.GRAY),
				t("and 4 Havana cigars. Once a day.", ChatFormatting.GRAY),
				t(r.met ? feastHint(r) : "", ChatFormatting.DARK_AQUA))), () -> nope(Hall.feast(viewer, court, r, today)));
			button(31, icon(item("oak_door", Items.PAPER), t("See them off", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
				t("The visit's over. Polite, and free.", ChatFormatting.GRAY))), () -> {
				click();
				Hall.sendGuestHome(court, true);
				close();
			});
			button(33, icon(item("iron_door", Items.PAPER), t(confirmBar ? "Really bar the doors?" : "Bar the doors", ChatFormatting.DARK_RED, ChatFormatting.BOLD), List.of(
				t("Seize your guest. Honour -40, Fear +30.", ChatFormatting.RED),
				t("Every house remembers, forever. It means war.", ChatFormatting.RED),
				t(confirmBar ? "Shift-click to do it." : "Click to think about it.", ChatFormatting.DARK_GRAY))), () -> {
				if (!confirmBar) {
					click();
					confirmBar = true;
				} else if (shift) {
					Hall.barDoors(court, r, today);
					Cmd.sound((ServerLevel) viewer.level(), "minecraft:block.iron_door.close", viewer.getX(), viewer.getY(), viewer.getZ(), 1f, 0.6f);
				} else {
					nope("Shift-click to bar the doors.");
				}
			});
		} else {
			button(24, icon(item("bread", Items.PAPER), t("Accept their hospitality", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
				t("You came all this way: a meal at their table.", ChatFormatting.GRAY),
				t("A small warmth, once a day.", ChatFormatting.GRAY))), () -> {
				boolean already = false;
				for (Rival.Memory m : r.memories) {
					if (m.day == today && m.text.startsWith("Visited ")) {
						already = true;
					}
				}
				if (already || r.war) {
					nope(r.war ? "Not while you're at war." : "You've eaten at their table today already.");
					return;
				}
				r.remember("Visited " + (r.woman ? "her" : "him") + " at " + r.seatName, 5, today, 20);
				Courts.tell(court, "You dine with " + r.name() + " at " + r.seatName + ". " + Ai.capital(r.they()) + " seems pleased you came.", ChatFormatting.GREEN);
				click();
			});
		}
		button(49, icon(Items.BARRIER, t("Take your leave", ChatFormatting.GRAY), List.of()), this::close);
	}

	private static String feastHint(Rival r) {
		if (r.is(Quirk.DRUNKARD)) {
			return "Drunkard: bring spirits. Lots.";
		}
		if (r.is(Quirk.PIOUS) || r.is(Temperament.ZEALOT)) {
			return "Keep the spirits off the table.";
		}
		return r.is(Temperament.SPENDTHRIFT) ? "A Spendthrift loves a big table." : "";
	}

	private net.minecraft.world.item.ItemStack offer(String name, String icon, boolean have, double odds) {
		return icon(item(icon, Items.PAPER), t(name, have ? ChatFormatting.GREEN : ChatFormatting.YELLOW, ChatFormatting.BOLD), have
			? List.of(t("Already in force.", ChatFormatting.GREEN))
			: List.of(t("Odds, face to face: " + Diplomacy.pct(odds), ChatFormatting.WHITE), t("One proposal a day.", ChatFormatting.DARK_GRAY)));
	}

	private void prisoner(Rival r, long today) {
		button(20, icon(Items.GOLD_INGOT, t("Ransom", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			t("Their house pays 40% of its treasury to get them back.", ChatFormatting.GRAY))), () -> {
			click();
			Hall.ransom(court, r, today);
			close();
		});
		button(22, icon(item("white_banner", Items.PAPER), t("Let them go", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
			t("Honour +5. It won't undo it, but it's something.", ChatFormatting.GRAY))), () -> {
			click();
			Hall.release(court, r, today);
			close();
		});
		button(24, icon(item("wither_rose", Items.PAPER), t("Execute", ChatFormatting.DARK_RED, ChatFormatting.BOLD), List.of(
			t("The worst thing you can do at court. Fear +15,", ChatFormatting.RED),
			t("Honour -20, and their heir will never forgive you.", ChatFormatting.RED),
			t("Shift-click to do it.", ChatFormatting.DARK_GRAY))), () -> {
			if (!shift) {
				nope("Shift-click. If you're sure.");
				return;
			}
			Hall.execute(court, r, today);
			close();
		});
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
