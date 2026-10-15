package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jetbrains.annotations.Nullable;

/**
 * De Hooghe Heeren, behind the tab on the Company Desk: your standing at court, the rival houses and their dossiers
 * (diplomacy, war and covert errands), the dispatches waiting for an answer, the errands under way, and your army.
 */
final class CourtMenu extends Ui {
	enum Page {
		COURT, RIVALS, DOSSIER, ERRAND, DISPATCHES, OPS, ARMY
	}

	static final int[] GRID = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
	private static final int[] RIVAL_SLOTS = {20, 22, 24, 30, 32, 21, 23};

	private final Court court;
	private Page page;
	private @Nullable String rivalId;
	private @Nullable Covert.Type errand;
	private int approach = 2;
	private @Nullable String agentId;
	private String other = "";

	static void open(ServerPlayer player, Court court, Page page) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CourtMenu(id, player, court, page, null, new SimpleContainer(SIZE)),
			Component.literal("De Hooghe Heeren · " + court.reputation()).withStyle(ChatFormatting.DARK_PURPLE)));
	}

	static void openDossier(ServerPlayer player, Court court, Rival r) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CourtMenu(id, player, court, Page.DOSSIER, r.id, new SimpleContainer(SIZE)),
			Component.literal(r.name()).withStyle(Names.colour(r.colour).chat())));
	}

	private CourtMenu(int syncId, ServerPlayer viewer, Court court, Page page, @Nullable String rival, SimpleContainer box) {
		super(syncId, viewer, box);
		this.court = court;
		this.page = page;
		this.rivalId = rival;
		render();
	}

	private long today() {
		return Courts.today();
	}

	private void go(Page p) {
		click();
		page = p;
	}

	private void say(String text, ChatFormatting color) {
		viewer.sendSystemMessage(Component.literal(text).withStyle(color));
	}

	/** Shows why something didn't happen (if it didn't). */
	private void nope(@Nullable String why) {
		if (why != null) {
			Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1f);
			say(why, ChatFormatting.RED);
		} else {
			click();
		}
	}

	static List<Component> wrap(String text, ChatFormatting color) {
		List<Component> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() + word.length() + 1 > 42 && line.length() > 0) {
				lines.add(t(line.toString(), color));
				line.setLength(0);
			}
			line.append(line.length() > 0 ? " " : "").append(word);
		}
		if (line.length() > 0) {
			lines.add(t(line.toString(), color));
		}
		return lines;
	}

	static String days(long n) {
		return n + (n == 1 ? " day" : " days");
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void draw() {
		tabs();
		Rival r = rivalId == null ? null : court.rival(rivalId);
		switch (page) {
			case COURT -> court();
			case RIVALS -> rivals();
			case DOSSIER -> {
				if (r == null) {
					page = Page.RIVALS;
					rivals();
				} else {
					dossier(r);
				}
			}
			case ERRAND -> {
				if (r == null || errand == null) {
					page = Page.RIVALS;
					rivals();
				} else {
					errand(r, errand);
				}
			}
			case DISPATCHES -> dispatches();
			case OPS -> ops();
			case ARMY -> army();
		}
	}

	private ItemStack tab(String icon, String name, boolean on, List<Component> lore) {
		ItemStack stack = icon(item(icon, Items.PAPER), t(name, on ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD), lore);
		return on ? glow(stack) : stack;
	}

	private void tabs() {
		button(0, tab("white_banner", "The Court", page == Page.COURT, List.of(t(court.reputation(), ChatFormatting.LIGHT_PURPLE))), () -> go(Page.COURT));
		button(1, tab("player_head", "The Houses", page == Page.RIVALS || page == Page.DOSSIER || page == Page.ERRAND,
			List.of(t(court.rivals.size() + " at the table", ChatFormatting.GRAY))), () -> go(Page.RIVALS));
		int pending = court.dispatches.size();
		ItemStack d = tab("paper", "Dispatches", page == Page.DISPATCHES, List.of(t(pending + " waiting for an answer", pending > 0 ? ChatFormatting.RED : ChatFormatting.GRAY)));
		button(2, pending > 0 ? glow(d) : d, () -> go(Page.DISPATCHES));
		button(3, tab("spyglass", "Errands", page == Page.OPS, List.of(t(court.ops.size() + " under way", ChatFormatting.GRAY))), () -> go(Page.OPS));
		button(4, tab("iron_sword", "Your Army", page == Page.ARMY, List.of(t(court.regiments + " regiments" + (court.atWar() ? " · AT WAR" : ""),
			court.atWar() ? ChatFormatting.RED : ChatFormatting.GRAY))), () -> go(Page.ARMY));
		button(8, icon(item("cartography_table", Items.PAPER), t("Back to the desk", ChatFormatting.YELLOW), List.of()), () -> {
			click();
			HeerenMod.nextTick(() -> CompagnieHooks.openDesk(viewer));
		});
	}

	private static String bar(int value) {
		int filled = Math.round(value / 10f);
		return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, 10 - filled)) + " " + value;
	}

	private void court() {
		List<Component> you = new ArrayList<>();
		you.addAll(wrap(court.reputationText(), ChatFormatting.GRAY));
		you.add(t("Honour " + bar(court.honour), ChatFormatting.AQUA));
		you.add(t("Fear   " + bar(court.fear), ChatFormatting.RED));
		you.add(t("Honour wins you pacts; Fear makes demands stick.", ChatFormatting.DARK_GRAY));
		you.add(t("Regiments: " + court.regiments + " (" + Bank.format(court.regiments * Courts.UPKEEP) + " a day)", ChatFormatting.WHITE));
		you.add(t("Wars won " + court.warsWon + " · lost " + court.warsLost + " · houses fallen " + court.housesFallen, ChatFormatting.GRAY));
		you.add(t("Treasury (your balance): " + Bank.format(Bank.balance(court.uuid())), ChatFormatting.GOLD));
		button(13, glow(icon(item("nether_star", Items.PAPER), t(court.ownerName + ": " + court.reputation(), ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), you)),
			null);

		List<Component> news = new ArrayList<>();
		for (int i = Math.max(0, court.chronicle.size() - 7); i < court.chronicle.size(); i++) {
			news.addAll(wrap(court.chronicle.get(i), ChatFormatting.GRAY));
		}
		if (news.isEmpty()) {
			news.add(t("Nothing yet.", ChatFormatting.GRAY));
		}
		button(21, icon(item("written_book", Items.PAPER), t("The chronicle", ChatFormatting.YELLOW, ChatFormatting.BOLD), news), null);

		List<Component> hall = new ArrayList<>();
		Rival guest = court.rival(court.guest);
		if (!court.hall) {
			hall.add(t("No hall yet. Craft an Audience Bell", ChatFormatting.GRAY));
			hall.add(t("(bell + gold ingot + paper) and hang it.", ChatFormatting.GRAY));
		} else {
			hall.add(t("Your bell hangs at " + court.hallX + ", " + court.hallY + ", " + court.hallZ + ".", ChatFormatting.GRAY));
			hall.add(guest == null ? t("Nobody's visiting. Invite a house from its dossier.", ChatFormatting.GRAY)
				: t(court.guestHeld ? guest.name() + " is your prisoner." : guest.name() + " is your guest.", court.guestHeld ? ChatFormatting.DARK_RED : ChatFormatting.GREEN));
		}
		button(23, icon(item("bell", Items.PAPER), t("Your hall", ChatFormatting.YELLOW, ChatFormatting.BOLD), hall), null);

		button(31, icon(Items.COMPASS, t("How it works", ChatFormatting.YELLOW), List.of(
			t("Rival houses play by your rules: their income", ChatFormatting.GRAY),
			t("pays their regiments, and no treasury means no army.", ChatFormatting.GRAY),
			t("Every dawn each house does one thing it planned.", ChatFormatting.GRAY),
			t("Open a dossier: gifts, pacts, tribute, war, and", ChatFormatting.GRAY),
			t("covert errands run by your Compagnie agents.", ChatFormatting.GRAY),
			t("Visit their seat, or feast them at your hall:", ChatFormatting.GRAY),
			t("in person, everything goes better.", ChatFormatting.GRAY),
			t("At war, ride out at noon and lead your regiments.", ChatFormatting.GRAY))), null);
		Court.Battle b = court.battle;
		if (b != null) {
			Rival r = court.rival(b.rival);
			button(40, glow(icon(Items.IRON_SWORD, t("⚔ Battle at noon" + (b.day == today() ? " today" : " tomorrow"), ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t("Against " + (r == null ? "?" : r.name()), ChatFormatting.WHITE),
				t("Near " + b.x + ", " + b.z + ". Be within 64 blocks at noon.", ChatFormatting.GRAY),
				t("Click for a compass to the field.", ChatFormatting.YELLOW)))), () -> {
				click();
				Seats.give(viewer, fieldCompass(b));
			});
		}
	}

	private ItemStack fieldCompass(Court.Battle b) {
		ItemStack stack = new ItemStack(Items.COMPASS);
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(java.util.Optional.of(GlobalPos.of(viewer.level().dimension(), b.pos())), false));
		stack.set(DataComponents.ITEM_NAME, Component.literal("To the battlefield").withStyle(ChatFormatting.RED));
		stack.set(DataComponents.LORE, new ItemLore(List.of(t("Near " + b.x + ", " + b.z + ". Noon.", ChatFormatting.GRAY))));
		return stack;
	}

	// ---------------------------------------------------------------- the houses

	static String purse(long cents) {
		long marks = cents / 100;
		return marks < 2000 ? "a thin purse" : marks < 8000 ? "a modest treasury" : marks < 25000 ? "a fat treasury" : "a mountain of gold";
	}

	static String army(int regiments) {
		return regiments == 0 ? "no army to speak of" : regiments <= 2 ? "a handful of men" : regiments <= 5 ? "a few regiments"
			: regiments <= 10 ? "a real army" : "a great host";
	}

	/** Everything you know about a house, as lore lines. */
	static List<Component> about(Court c, Rival r, long today, boolean full) {
		List<Component> lore = new ArrayList<>();
		boolean known = r.known(today);
		int op = c.opinion(r, today);
		Court.Tier tier = Court.Tier.of(op);
		lore.add(t((r.nation.isEmpty() ? "" : r.nation + " · ") + r.seatName + (r.generation > 1 ? " · generation " + r.generation : ""), ChatFormatting.GRAY));
		if (known || r.met) {
			lore.add(t(r.temperament().title + " · " + r.quirk().title, ChatFormatting.AQUA));
			if (full) {
				lore.addAll(wrap(r.temperament().text, ChatFormatting.DARK_AQUA));
				lore.add(t(r.quirk().title + ": " + r.quirk().text, ChatFormatting.DARK_AQUA));
			}
		} else {
			lore.add(t("Temperament unknown: spy on them, or meet them.", ChatFormatting.DARK_GRAY));
		}
		lore.add(t("Opinion of you: " + tier.title + " (" + Wars.signed(op) + ")", tier.color));
		if (known) {
			lore.add(t("Treasury " + Bank.format(r.treasury) + " · " + r.regiments + " regiments", ChatFormatting.GOLD));
			lore.add(t("Income " + Bank.format(r.income) + " a day" + (r.sabotagedUntil >= today ? " (sabotaged)" : ""), ChatFormatting.GOLD));
			lore.add(t("Plans: " + Ai.intentText(c, r), ChatFormatting.LIGHT_PURPLE));
			lore.add(t("(Your spies report for " + days(r.intelUntil - today + 1) + " more.)", ChatFormatting.DARK_GRAY));
		} else {
			lore.add(t("Word is they have " + purse(r.treasury) + " and " + army(r.regiments) + ".", ChatFormatting.GOLD));
		}
		List<String> ties = new ArrayList<>();
		if (r.war) {
			ties.add("AT WAR (score " + Wars.signed(r.warScore) + ")");
		}
		if (r.alliance) {
			ties.add("allies");
		} else if (r.peacePact) {
			ties.add("peace pact");
		}
		if (r.tradePact) {
			ties.add("trade pact");
		}
		if (r.tribute > 0 && r.tributeUntil >= today) {
			ties.add((r.tributeToUs ? "pays you " : "you pay ") + Bank.format(r.tribute) + "/day");
		}
		if (!ties.isEmpty()) {
			lore.add(t(String.join(" · ", ties), r.war ? ChatFormatting.RED : ChatFormatting.GREEN));
		}
		if (full) {
			List<Rival.Memory> memories = new ArrayList<>(r.memories);
			memories.removeIf(m -> Math.round(m.now(today)) == 0);
			memories.sort(Comparator.comparingDouble(m -> -Math.abs(m.now(today))));
			if (!memories.isEmpty()) {
				lore.add(t("Remembers:", ChatFormatting.WHITE));
				for (int i = 0; i < Math.min(5, memories.size()); i++) {
					Rival.Memory m = memories.get(i);
					long v = Math.round(m.now(today));
					lore.add(t("  " + m.text + " (" + Wars.signed((int) v) + (m.fade == 0 ? ", never fades" : "") + ")",
						v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
				}
			}
			List<String> rels = new ArrayList<>();
			for (Rival o : c.rivals) {
				if (o != r) {
					String w = r.wars.contains(o.id) ? "at war" : r.partners.contains(o.id) ? "trading" : Court.Tier.of(r.relation(o.id)).title.toLowerCase();
					rels.add(o.House() + ": " + w);
				}
			}
			if (!rels.isEmpty()) {
				lore.add(t("Other houses:", ChatFormatting.WHITE));
				for (String s : rels) {
					lore.add(t("  " + s, ChatFormatting.GRAY));
				}
			}
		}
		return lore;
	}

	static ItemStack portrait(Court c, Rival r, long today, boolean full) {
		Court.Tier tier = Court.Tier.of(c.opinion(r, today));
		ItemStack stack = icon(item("player_head", Items.PAPER), t(r.name(), Names.colour(r.colour).chat(), ChatFormatting.BOLD), about(c, r, today, full));
		return r.war || tier == Court.Tier.SWORN ? glow(stack) : stack;
	}

	private void rivals() {
		long today = today();
		int i = 0;
		for (Rival r : court.rivals) {
			if (i >= RIVAL_SLOTS.length) {
				break;
			}
			ItemStack stack = portrait(court, r, today, false);
			List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
			lore.add(t("Click to open the dossier.", ChatFormatting.YELLOW));
			stack.set(DataComponents.LORE, new ItemLore(lore));
			button(RIVAL_SLOTS[i++], stack, () -> {
				click();
				rivalId = r.id;
				page = Page.DOSSIER;
			});
		}
		button(49, icon(item("clock", Items.PAPER), t("The table", ChatFormatting.GRAY), List.of(
			t("A Charter sits with 3 houses, a Compagnie with 4,", ChatFormatting.DARK_GRAY),
			t("the VOC with 5. Houses fall, and new ones come.", ChatFormatting.DARK_GRAY))), null);
	}

	// ---------------------------------------------------------------- a dossier

	private void dossier(Rival r) {
		long today = today();
		button(4, portrait(court, r, today, true), null);
		// diplomacy
		List<Component> gift = new ArrayList<>();
		gift.add(t("Click: " + Bank.format(Diplomacy.smallGift(court)) + " · right-click: " + Bank.format(Diplomacy.bigGift(court)), ChatFormatting.GOLD));
		gift.add(t("It goes into their treasury. (Yes, they", ChatFormatting.GRAY));
		gift.add(t("may spend it on regiments.)", ChatFormatting.GRAY));
		button(19, icon(item("gold_nugget", Items.PAPER), t("Send a gift", ChatFormatting.YELLOW, ChatFormatting.BOLD), gift),
			() -> nope(Diplomacy.gift(court, r, right, false, today)));
		button(20, pact("Trade pact", "map", r.tradePact, Diplomacy.tradeOdds(court, r, false, today),
			"Both of you earn " + Bank.format(Ai.pactIncome(court)) + " a day.", "Shift-click to tear it up (Honour -5)."),
			() -> nope(r.tradePact ? (shift ? Diplomacy.cancelTrade(court, r, today) : "Shift-click to tear up the pact.")
				: Diplomacy.proposeTrade(court, r, false, today)));
		button(21, pact("Peace pact", "white_banner", r.peacePact, Diplomacy.peaceOdds(court, r, false, today),
			"Neither of you will attack the other.", "Shift-click to break it (Honour -10)."),
			() -> nope(r.peacePact ? (shift ? Diplomacy.cancelPeacePact(court, r, today) : "Shift-click to break the pact.")
				: Diplomacy.proposePeacePact(court, r, false, today)));
		button(22, pact("Alliance", "shield", r.alliance, Diplomacy.allianceOdds(court, r, false, today),
			"You join each other's wars. Needs a peace pact.", "Shift-click to end it (Honour -15)."),
			() -> nope(r.alliance ? (shift ? Diplomacy.cancelAlliance(court, r, today) : "Shift-click to end the alliance.")
				: Diplomacy.proposeAlliance(court, r, false, today)));
		button(23, icon(item("iron_bars", Items.PAPER), t("Demand tribute", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
			t(Bank.format(Diplomacy.tributeAmount(r)) + " a day for ten days, or else.", ChatFormatting.GOLD),
			t("Works on Fear and a bigger army. Never on the Proud.", ChatFormatting.GRAY),
			t("Odds: " + Diplomacy.pct(Diplomacy.tributeOdds(court, r, false, today)) + " (in person: better)", ChatFormatting.WHITE))),
			() -> nope(Diplomacy.demandTribute(court, r, false, today)));
		button(24, icon(item("bell", Items.PAPER), t("Invite to your hall", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
			t("Costs " + Bank.format(Hall.inviteCost(court)) + " (the coach, the welcome).", ChatFormatting.GOLD),
			t("They stay by your Audience Bell until the second dawn.", ChatFormatting.GRAY),
			t("Talk in person, feast them... or bar the doors.", ChatFormatting.GRAY))), () -> {
			String why = Hall.invite(court, r, today);
			nope(why);
			if (why == null) {
				close();
			}
		});
		button(25, icon(Items.COMPASS, t("Directions to " + r.seatName, ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
			t("A compass to their seat, at " + r.seatX + ", " + r.seatZ + ".", ChatFormatting.GRAY),
			t("Walk in and talk to " + (r.woman ? "her" : "him") + " in person.", ChatFormatting.GRAY),
			t(r.war ? "At war: the garrison will come out." : "At peace, the guards bow you in.", r.war ? ChatFormatting.RED : ChatFormatting.DARK_GRAY))), () -> {
			click();
			Seats.give(viewer, Seats.directions(court, r));
		});
		// war and peace
		if (!r.war) {
			button(30, icon(Items.IRON_SWORD, t("Declare war", ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t(r.peacePact ? "Breaks your peace pact: Honour -20, and every house remembers." : r.provoked >= today
					? "They gave you reason: no Honour lost." : "Unprovoked: Honour -5.", ChatFormatting.GRAY),
				t("Their regiments against yours, every dawn.", ChatFormatting.GRAY),
				t("Shift-click to declare.", ChatFormatting.YELLOW))), () -> nope(shift ? Diplomacy.declareWar(court, r, today) : "Shift-click to declare war."));
		} else {
			button(29, icon(item("white_banner", Items.PAPER), t("Offer peace", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
				t("A white peace: likelier the better you're doing.", ChatFormatting.GRAY),
				t("War score " + Wars.signed(r.warScore) + ".", ChatFormatting.WHITE))), () -> nope(Diplomacy.offerPeace(court, r, false, today)));
			long price = Diplomacy.peacePrice(court, r);
			if (price > 0) {
				button(30, icon(Items.GOLD_INGOT, t("Buy peace: " + Bank.format(price), ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
					t("They'll stop for this much. It goes into their treasury.", ChatFormatting.GRAY))), () -> nope(Diplomacy.offerPeace(court, r, true, today)));
			}
			Court.Battle b = court.battle;
			button(32, icon(Items.TARGET, t("Give battle", ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t(b == null ? "Your regiments march out: a pitched battle" : "A battle is already set.", ChatFormatting.GRAY),
				t(b == null ? "at noon, halfway to their seat. Lead it yourself!" : "", ChatFormatting.GRAY))), () -> {
				if (court.battle != null) {
					nope("A battle is already set.");
				} else if (court.regiments <= 0) {
					nope("You have no regiments to march with.");
				} else {
					click();
					Battles.schedule(court, r, today, true);
				}
			});
		}
		// covert errands
		int i = 0;
		for (Covert.Type type : Covert.Type.values()) {
			List<Component> lore = new ArrayList<>(wrap(type.text, ChatFormatting.GRAY));
			lore.add(t("Costs " + Bank.format(Covert.cost(court, type)) + " · takes " + days(type.days), ChatFormatting.GOLD));
			String why = Covert.allowed(court, r, type, today);
			if (why != null) {
				lore.addAll(wrap(why, ChatFormatting.RED));
			} else {
				lore.add(t("Click to plan it.", ChatFormatting.YELLOW));
			}
			button(37 + i++, icon(item(type.icon, Items.PAPER), t(type.title, ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), lore), why != null ? null : () -> {
				click();
				errand = type;
				approach = firstApproach(type);
				agentId = null;
				other = "";
				page = Page.ERRAND;
			});
		}
		button(45, icon(Items.ARROW, t("Back to the houses", ChatFormatting.YELLOW), List.of()), () -> go(Page.RIVALS));
	}

	private ItemStack pact(String name, String icon, boolean have, double odds, String what, String undo) {
		List<Component> lore = new ArrayList<>();
		lore.add(t(what, ChatFormatting.GRAY));
		if (have) {
			lore.add(t("In force.", ChatFormatting.GREEN));
			lore.add(t(undo, ChatFormatting.DARK_GRAY));
		} else {
			lore.add(t("Odds they'll sign: " + Diplomacy.pct(odds) + " (in person: better)", ChatFormatting.WHITE));
			lore.add(t("Click to propose. One proposal a day.", ChatFormatting.YELLOW));
		}
		ItemStack stack = icon(item(icon, Items.PAPER), t(name, have ? ChatFormatting.GREEN : ChatFormatting.YELLOW, ChatFormatting.BOLD), lore);
		return have ? glow(stack) : stack;
	}

	private static int firstApproach(Covert.Type type) {
		for (int a = 2; a >= 0; a--) {
			if (type.odds[a] >= 0) {
				return a;
			}
		}
		return 2;
	}

	// ---------------------------------------------------------------- planning an errand

	private void errand(Rival r, Covert.Type type) {
		long today = today();
		List<Component> about = new ArrayList<>(wrap(type.text, ChatFormatting.GRAY));
		about.add(t("Against " + r.name(), ChatFormatting.WHITE));
		about.add(t("Costs " + Bank.format(Covert.cost(court, type)) + " · takes " + days(type.days), ChatFormatting.GOLD));
		about.add(t("Botched, it may be traced back to you.", ChatFormatting.RED));
		button(4, icon(item(type.icon, Items.PAPER), t(type.title, ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), about), null);
		int[] slots = {11, 13, 15};
		for (int a = 0; a < 3; a++) {
			int which = a;
			if (type.odds[a] < 0) {
				button(slots[a], icon(Items.BARRIER, t(Covert.ADVISORS[a] + " won't touch it", ChatFormatting.DARK_GRAY), List.of()), null);
				continue;
			}
			ItemStack stack = icon(item(Covert.ADVISOR_ICONS[a], Items.PAPER), t(Covert.ADVISORS[a] + " (" + Covert.STATS[a] + ")",
				approach == a ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD),
				List.of(t("Base odds: " + Diplomacy.pct(type.odds[a]) + " · uses the agent's " + Covert.STATS[a], ChatFormatting.WHITE)));
			button(slots[a], approach == a ? glow(stack) : stack, () -> {
				click();
				approach = which;
			});
		}
		// forging: who gets the letter
		if (type == Covert.Type.FORGE) {
			int i = 0;
			for (Rival o : court.rivals) {
				if (o == r || i >= 7) {
					continue;
				}
				ItemStack stack = icon(item("writable_book", Items.PAPER), t("To " + o.name(), other.equals(o.id) ? ChatFormatting.GOLD : ChatFormatting.WHITE),
					List.of(t(r.House() + " and " + o.House() + ": " + Court.Tier.of(r.relation(o.id)).title.toLowerCase(), ChatFormatting.GRAY)));
				button(28 + i++, other.equals(o.id) ? glow(stack) : stack, () -> {
					click();
					other = o.id;
				});
			}
		}
		// agents who are in
		int i = 0;
		for (CompagnieHooks.AgentInfo a : CompagnieHooks.agents(court.uuid())) {
			if (!a.state().equals("READY") || i >= 9) {
				continue;
			}
			List<Component> lore = new ArrayList<>();
			lore.add(t("Force " + a.force() + " · Coin " + a.coin() + " · Guile " + a.guile(), ChatFormatting.WHITE));
			lore.add(t(CompagnieHooks.traitText(a.traits()) + " · level " + a.level(), ChatFormatting.AQUA));
			if (type.odds[approach] >= 0) {
				lore.add(t("Odds this way: " + Diplomacy.pct(Covert.odds(court, r, type, approach, a.id())), ChatFormatting.GOLD));
			}
			ItemStack stack = icon(item("player_head", Items.PAPER), t(a.name(), a.id().equals(agentId) ? ChatFormatting.GOLD : ChatFormatting.WHITE, ChatFormatting.BOLD), lore);
			button(36 + i++, a.id().equals(agentId) ? glow(stack) : stack, () -> {
				click();
				agentId = a.id();
			});
		}
		if (i == 0) {
			button(40, icon(Items.BARRIER, t("Nobody's available", ChatFormatting.RED), List.of(
				t("Hire an agent at the Company Desk, or wait", ChatFormatting.GRAY), t("for one to come home.", ChatFormatting.GRAY))), null);
		}
		button(45, icon(Items.ARROW, t("Back to the dossier", ChatFormatting.YELLOW), List.of()), () -> go(Page.DOSSIER));
		if (agentId != null && type.odds[approach] >= 0) {
			List<Component> lore = new ArrayList<>();
			lore.add(t(Covert.ADVISORS[approach] + "'s way · odds " + Diplomacy.pct(Covert.odds(court, r, type, approach, agentId)), ChatFormatting.GOLD));
			lore.add(t("Costs " + Bank.format(Covert.cost(court, type)), ChatFormatting.GOLD));
			lore.add(t(type == Covert.Type.ASSASSINATE ? "Shift-click to send them. There's no undoing it." : "Click to send them.", ChatFormatting.YELLOW));
			button(49, glow(icon(Items.EMERALD, t("Send them", ChatFormatting.GREEN, ChatFormatting.BOLD), lore)), () -> {
				if (type == Covert.Type.ASSASSINATE && !shift) {
					nope("Shift-click to send an assassin.");
					return;
				}
				String why = Covert.start(court, r, type, approach, agentId, other, today);
				if (why == null) {
					say("Your agent is on their way: " + type.title + " (" + r.House() + "). Back in " + days(type.days) + ".", ChatFormatting.GOLD);
					page = Page.OPS;
				}
				nope(why);
			});
		} else {
			button(49, icon(item("gray_dye", Items.PAPER), t("Pick an advisor and an agent", ChatFormatting.GRAY), List.of()), null);
		}
	}

	// ---------------------------------------------------------------- dispatches, errands, the army

	private void dispatches() {
		int row = 1;
		String[] icons = {"emerald", "redstone", "book"};
		for (Court.Dispatch d : new ArrayList<>(court.dispatches)) {
			if (row > 4) {
				break;
			}
			List<String> options = Dispatches.options(court, d);
			List<Component> lore = new ArrayList<>(wrap(Dispatches.text(court, d), ChatFormatting.WHITE));
			lore.add(t("If nobody answers by day " + d.expires + ": \"" + options.get(options.size() - 1) + "\".", ChatFormatting.DARK_GRAY));
			button(row * 9 + 1, glow(icon(Items.PAPER, t("📜 Dispatch", ChatFormatting.GOLD, ChatFormatting.BOLD), lore)), null);
			for (int o = 0; o < options.size(); o++) {
				int choice = o;
				button(row * 9 + 3 + 2 * o, icon(item(icons[o % icons.length], Items.PAPER), t(options.get(o), ChatFormatting.YELLOW),
					List.of(t("Click to answer.", ChatFormatting.DARK_GRAY))), () -> nope(Dispatches.answer(court, d, choice, today(), false)));
			}
			row++;
		}
		if (row == 1) {
			button(22, icon(Items.PAPER, t("No dispatches waiting", ChatFormatting.GRAY), List.of(t("The houses write at dawn.", ChatFormatting.DARK_GRAY))), null);
		}
	}

	private void ops() {
		long today = today();
		int i = 0;
		for (Court.Op op : court.ops) {
			Covert.Type type = op.type();
			Rival r = court.rival(op.rival);
			if (type == null || r == null || i >= GRID.length) {
				continue;
			}
			button(GRID[i++], icon(item(type.icon, Items.PAPER), t(type.title, ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), List.of(
				t("Against " + r.name(), ChatFormatting.WHITE),
				t(op.agentName + ", " + Covert.ADVISORS[Covert.approach(op.approach)] + "'s way", ChatFormatting.GRAY),
				t("Odds now: " + Diplomacy.pct(Covert.odds(court, r, type, Covert.approach(op.approach), op.agent)), ChatFormatting.GOLD),
				t("Back in " + days(Math.max(1, op.end - today)), ChatFormatting.AQUA))), null);
		}
		if (i == 0) {
			button(22, icon(item("spyglass", Items.PAPER), t("No errands under way", ChatFormatting.GRAY), List.of(
				t("Plan one from a house's dossier.", ChatFormatting.DARK_GRAY))), null);
		}
	}

	private void army() {
		long today = today();
		List<Component> lore = new ArrayList<>();
		lore.add(t(court.regiments + " regiments", ChatFormatting.WHITE));
		lore.add(t("Upkeep " + Bank.format(court.regiments * Courts.UPKEEP) + " a day, at dawn.", ChatFormatting.GOLD));
		lore.add(t("Can't pay? Unpaid regiments desert.", ChatFormatting.GRAY));
		lore.add(t("They beat off raids, fight the skirmishes and", ChatFormatting.GRAY));
		lore.add(t("line up in battle (a soldier per half regiment).", ChatFormatting.GRAY));
		lore.add(t("Balance: " + Bank.format(Bank.balance(court.uuid())), ChatFormatting.GOLD));
		button(13, glow(icon(Items.IRON_SWORD, t("Your army", ChatFormatting.GOLD, ChatFormatting.BOLD), lore)), null);
		button(20, icon(item("orange_banner", Items.PAPER), t("Raise a regiment", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
			t(Bank.format(Courts.REGIMENT) + ", then " + Bank.format(Courts.UPKEEP) + " a day.", ChatFormatting.GOLD),
			t("Shift-click: raise five.", ChatFormatting.DARK_GRAY))), () -> nope(Diplomacy.raise(court, shift ? 5 : 1)));
		button(24, icon(item("white_banner", Items.PAPER), t("Send a regiment home", ChatFormatting.YELLOW, ChatFormatting.BOLD), List.of(
			t("No refunds. Shift-click: five.", ChatFormatting.GRAY))), () -> nope(Diplomacy.disband(court, shift ? 5 : 1)));
		int i = 0;
		for (Rival r : court.rivals) {
			if (!r.war || i >= 5) {
				continue;
			}
			List<Component> war = new ArrayList<>();
			war.add(t("War score " + Wars.signed(r.warScore) + " (100: they're crushed)", ChatFormatting.WHITE));
			war.add(r.known(today) ? t("They have " + r.regiments + " regiments, " + Bank.format(r.treasury), ChatFormatting.GOLD)
				: t("Word is they have " + army(r.regiments) + ".", ChatFormatting.GOLD));
			war.add(t("Click for the dossier.", ChatFormatting.YELLOW));
			button(38 + i++, glow(icon(Items.IRON_SWORD, t("At war with " + r.House(), ChatFormatting.RED, ChatFormatting.BOLD), war)), () -> {
				click();
				rivalId = r.id;
				page = Page.DOSSIER;
			});
		}
		Court.Battle b = court.battle;
		if (b != null) {
			button(31, glow(icon(Items.TARGET, t("⚔ Battle at noon" + (b.day == today ? " today" : " tomorrow"), ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t("Near " + b.x + ", " + b.z + ". Be within 64 blocks at noon", ChatFormatting.GRAY),
				t("to lead it. Click for a compass to the field.", ChatFormatting.GRAY)))), () -> {
				click();
				Seats.give(viewer, fieldCompass(b));
			});
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
