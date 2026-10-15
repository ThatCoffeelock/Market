package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Company Desk: the war table. Tabs along the top (the company, the job board, jobs under way, agents, the hiring
 * board, the Ledger, telegrams and the strongbox), the page below. Planning a job: pick an advisor, a stake and an
 * agent, then send them.
 */
final class DeskMenu extends Ui {
	enum Page {
		HOME, BOARD, PLAN, ACTIVE, AGENTS, HIRING, LEDGER, TELEGRAMS, STRONGBOX
	}

	/** The body's slots, row by row, leaving the edges free: 14 of them. */
	private static final int[] GRID = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

	private final Company company;
	private Page page = Page.HOME;
	/** Planning: the job (an offer, or a collect/rescue made up on the spot), and the choices so far. */
	private @Nullable Job plan;
	private @Nullable Approach approach;
	private int stake = 1;
	private @Nullable String agentId;
	private int boxPage;

	static void open(ServerPlayer player, Company company) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DeskMenu(id, player, company, new SimpleContainer(SIZE)),
			Component.literal("Company Desk · " + company.rank().title).withStyle(ChatFormatting.DARK_GREEN)));
	}

	private DeskMenu(int syncId, ServerPlayer viewer, Company company, SimpleContainer box) {
		super(syncId, viewer, box);
		this.company = company;
		if (company.unread > 0 || company.pendingTelegrams() > 0) {
			page = company.pendingTelegrams() > 0 ? Page.TELEGRAMS : Page.HOME;
		}
		company.unread = 0;
		render();
	}

	private long today() {
		return Companies.today();
	}

	private void go(Page p) {
		click();
		page = p;
	}

	private void say(String text, ChatFormatting color) {
		viewer.sendSystemMessage(Component.literal(text).withStyle(color));
	}

	private void nope(@Nullable String why) {
		if (why != null) {
			Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1f);
			say(why, ChatFormatting.RED);
		}
	}

	private void kaching() {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.experience_orb.pickup", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.2f);
	}

	/** Splits a long text into lore lines of about {@code width} characters. */
	static List<Component> wrap(String text, ChatFormatting color) {
		List<Component> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() + word.length() + 1 > 40 && line.length() > 0) {
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

	private static String pct(double p) {
		return Math.round(p * 100) + "%";
	}

	private static String days(long n) {
		return n + (n == 1 ? " day" : " days");
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void draw() {
		tabs();
		switch (page) {
			case HOME -> home();
			case BOARD -> board();
			case PLAN -> plan();
			case ACTIVE -> active();
			case AGENTS -> agents();
			case HIRING -> hiring();
			case LEDGER -> ledger();
			case TELEGRAMS -> telegrams();
			case STRONGBOX -> strongbox();
		}
	}

	private ItemStack tab(String icon, String name, Page p, List<Component> lore) {
		ItemStack stack = icon(item(icon, Items.PAPER), t(name, page == p ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD), lore);
		return page == p ? glow(stack) : stack;
	}

	private void tabs() {
		Rank rank = company.rank();
		button(0, tab("writable_book", "The Company", Page.HOME, List.of(t(rank.title, ChatFormatting.GRAY))), () -> go(Page.HOME));
		button(1, tab("map", "Job Board", Page.BOARD, List.of(t(company.board.size() + " offers", ChatFormatting.GRAY))), () -> go(Page.BOARD));
		button(2, tab("clock", "Under Way", Page.ACTIVE, List.of(t(company.active.size() + " / " + company.slots() + " slots", ChatFormatting.GRAY))),
			() -> go(Page.ACTIVE));
		button(3, tab("iron_helmet", "Agents", Page.AGENTS, List.of(t(company.agents.size() + " / " + company.maxAgents() + " on the books", ChatFormatting.GRAY))),
			() -> go(Page.AGENTS));
		button(4, tab("emerald", "Hiring Board", Page.HIRING, List.of(t(company.candidates.size() + " looking for work", ChatFormatting.GRAY))),
			() -> go(Page.HIRING));
		int defaulted = 0;
		for (Loan l : company.loans) {
			if (l.state() == Loan.State.DEFAULTED) {
				defaulted++;
			}
		}
		button(5, tab("book", "The Ledger", Page.LEDGER, List.of(t(company.loans.size() + " borrowers" + (defaulted > 0 ? ", " + defaulted + " in default" : ""),
			defaulted > 0 ? ChatFormatting.RED : ChatFormatting.GRAY))), () -> go(Page.LEDGER));
		int pending = company.pendingTelegrams();
		ItemStack telegrams = tab("paper", "Telegrams", Page.TELEGRAMS, List.of(t(pending + " waiting for an answer",
			pending > 0 ? ChatFormatting.RED : ChatFormatting.GRAY)));
		button(6, pending > 0 ? glow(telegrams) : telegrams, () -> go(Page.TELEGRAMS));
		button(7, tab("chest", "Strongbox", Page.STRONGBOX, List.of(t(Strongbox.count(company) + " stacks of spoils", ChatFormatting.GRAY))),
			() -> go(Page.STRONGBOX));
		if (CompagnieHooks.extensions().isEmpty()) {
			button(8, icon(Items.BARRIER, t("Close", ChatFormatting.RED), List.of()), this::close);
		} else {
			// another mod's page (De Hooghe Heeren): the tab in the corner
			CompagnieHooks.Extension ext = CompagnieHooks.extensions().get(0);
			button(8, ext.tab(company.uuid()), () -> {
				click();
				CompagnieMod.nextTick(() -> ext.open(viewer));
			});
		}
	}

	private void home() {
		Rank rank = company.rank();
		Rank next = rank.next();
		List<Component> info = new ArrayList<>();
		info.add(t("Reputation: " + company.rep + (next == null ? "" : " / " + next.rep + " for " + next.title), ChatFormatting.YELLOW));
		info.add(t("Profit, all time: " + Bank.format(company.profit), company.profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		info.add(t("Jobs done well: " + company.jobsDone + " · agents lost: " + company.agentsLost, ChatFormatting.GRAY));
		info.add(t("Job slots " + company.slots() + " · agents " + company.maxAgents() + " · board " + rank.board, ChatFormatting.GRAY));
		if (company.house > 0) {
			info.add(t("Company House (tier " + company.house + "): +1 slot, +" + company.house + " agents, wages -" + 10 * company.house + "%", ChatFormatting.AQUA));
		}
		info.add(t("Wages: " + Bank.format(company.dailyWages()) + " a day", ChatFormatting.GRAY));
		info.add(t("Balance: " + Bank.format(Bank.balance(company.uuid())), ChatFormatting.GOLD));
		button(13, glow(icon(Items.NETHER_STAR, t(company.ownerName + "'s " + rank.title, ChatFormatting.GOLD, ChatFormatting.BOLD), info)), null);

		List<Component> buffs = new ArrayList<>();
		long today = today();
		for (Map.Entry<String, Long> e : company.buffs.entrySet()) {
			if (e.getValue() >= today) {
				String what = switch (e.getKey()) {
					case Jobs.HARBOUR -> "The harbourmaster is ours: sea jobs +10%";
					case Jobs.RIVAL_BURNED -> "The English are reeling: trade jobs +10%";
					case Jobs.RIVAL_ANGRY -> "The English want revenge: trade jobs -10%";
					case Jobs.CORNER -> "We've cornered the market: trade jobs pay 20% more";
					default -> e.getKey();
				};
				buffs.add(t(what + " (" + days(e.getValue() - today + 1) + ")", ChatFormatting.AQUA));
			}
		}
		if (buffs.isEmpty()) {
			buffs.add(t("Nothing in our favour right now.", ChatFormatting.GRAY));
		}
		button(21, icon(item("beacon", Items.PAPER), t("In our favour", ChatFormatting.AQUA, ChatFormatting.BOLD), buffs), null);

		List<Component> news = new ArrayList<>();
		for (int i = Math.max(0, company.news.size() - 8); i < company.news.size(); i++) {
			news.addAll(wrap(company.news.get(i), ChatFormatting.GRAY));
		}
		if (news.isEmpty()) {
			news.add(t("Nothing yet.", ChatFormatting.GRAY));
		}
		button(23, icon(item("written_book", Items.PAPER), t("The latest news", ChatFormatting.YELLOW, ChatFormatting.BOLD), news), null);

		button(31, icon(Items.COMPASS, t("How it works", ChatFormatting.YELLOW), List.of(
			t("1. Hire an agent at the Hiring Board.", ChatFormatting.GRAY),
			t("2. Pick a job on the Job Board, choose an advisor", ChatFormatting.GRAY),
			t("   (Kapitein, Koopman or Spion), a stake and an agent.", ChatFormatting.GRAY),
			t("3. Every dawn (a Minecraft day) jobs come home:", ChatFormatting.GRAY),
			t("   Marks to your balance, spoils to the strongbox.", ChatFormatting.GRAY),
			t("4. Telegrams arrive on the road: answer them.", ChatFormatting.GRAY),
			t("5. Lend money on the Ledger. Collect it, one way or another.", ChatFormatting.GRAY))), null);
	}

	private void board() {
		long today = today();
		Rank rank = company.rank();
		int i = 0;
		for (Job offer : company.board) {
			JobType type = offer.type();
			if (type == null || i >= GRID.length) {
				continue;
			}
			List<Component> lore = new ArrayList<>();
			lore.add(t(type.category.title + (type.sea ? " · at sea" : "") + " · " + days(type.days), ChatFormatting.DARK_AQUA));
			lore.addAll(wrap(type.text, ChatFormatting.GRAY));
			lore.add(t("Stake: " + Bank.format(Jobs.stakeCents(type, 0)) + " / " + Bank.format(Jobs.stakeCents(type, 1)) + " / "
				+ Bank.format(Jobs.stakeCents(type, 2)), ChatFormatting.GOLD));
			if (type.mult > 0) {
				lore.add(t("Pays back about " + Math.round(type.mult * 100) + "% of the stake on success", ChatFormatting.GOLD));
			}
			if (type.special == JobType.Special.EXPORT) {
				lore.add(t("Cargo: the sealed bottles and cigars you carry. Pays 250% of their value", ChatFormatting.GOLD));
			} else if (type.special == JobType.Special.CHARTER) {
				lore.add(t("Cargo: the packed ship or airship you carry. Pays ₥2,000; it may sink", ChatFormatting.GOLD));
			}
			for (Approach a : Approach.values()) {
				lore.add(type.allows(a) ? t(a.advisor + ": " + pct(type.odds[a.ordinal()]) + " base", ChatFormatting.WHITE)
					: t(a.advisor + ": won't touch it", ChatFormatting.DARK_GRAY));
			}
			long left = offer.expires - today + 1;
			lore.add(t("On the board for " + days(Math.max(1, left)) + " more", ChatFormatting.DARK_GRAY));
			lore.add(t("Click to plan it.", ChatFormatting.YELLOW));
			button(GRID[i++], icon(item(type.icon, Items.PAPER), t(type.title, ChatFormatting.AQUA, ChatFormatting.BOLD), lore), () -> {
				click();
				startPlanning(offer);
			});
		}
		if (i == 0) {
			button(22, icon(item("map", Items.PAPER), t("Nothing on the board", ChatFormatting.GRAY), List.of(t("New offers come in at dawn.", ChatFormatting.DARK_GRAY))), null);
		}
		button(49, icon(item("clock", Items.PAPER), t("New offers at every dawn", ChatFormatting.GRAY), List.of(
			t("A " + rank.title + " gets " + rank.board + " offers on its board.", ChatFormatting.DARK_GRAY),
			t("Bigger companies get bigger jobs.", ChatFormatting.DARK_GRAY))), null);
	}

	private void startPlanning(Job job) {
		plan = job;
		approach = null;
		JobType type = job.type();
		if (type != null) {
			for (Approach a : Approach.values()) {
				if (type.allows(a)) {
					approach = a;
					break;
				}
			}
		}
		stake = 1;
		agentId = null;
		for (Agent a : company.agents) {
			if (a.state() == Agent.State.READY && !a.id.equals(job.target)) {
				agentId = a.id;
				break;
			}
		}
		page = Page.PLAN;
	}

	private void plan() {
		Job job = plan;
		JobType type = job == null ? null : job.type();
		if (type == null) {
			page = Page.BOARD;
			board();
			return;
		}
		List<Component> about = new ArrayList<>(wrap(type.text, ChatFormatting.GRAY));
		if (type == JobType.COLLECT_DEBT) {
			Loan loan = company.loan(job.target);
			if (loan != null) {
				about.add(t("Owed by " + loan.borrower + ": " + loan.owedText(), ChatFormatting.GOLD));
			}
		} else if (type == JobType.RESCUE) {
			Agent prisoner = company.agent(job.target);
			if (prisoner != null) {
				about.add(t("To rescue: " + prisoner.name + " (ransom would be " + Bank.format(Hiring.ransom(prisoner)) + ")", ChatFormatting.GOLD));
			}
		}
		button(4, icon(item(type.icon, Items.PAPER), t(type.title, ChatFormatting.AQUA, ChatFormatting.BOLD), about), null);

		Agent agent = agentId == null ? null : company.agent(agentId);
		// the advisors
		int[] advisorSlots = {11, 13, 15};
		for (Approach a : Approach.values()) {
			int slot = advisorSlots[a.ordinal()];
			if (!type.allows(a)) {
				button(slot, icon(Items.BARRIER, t(a.advisor + " won't touch it", ChatFormatting.DARK_GRAY), List.of()), null);
				continue;
			}
			List<Component> lore = new ArrayList<>(wrap(a.text, ChatFormatting.GRAY));
			lore.add(t("Base odds: " + pct(type.odds[a.ordinal()]) + " · uses the agent's " + a.stat, ChatFormatting.WHITE));
			if (agent != null) {
				lore.add(t("With " + agent.name + ": " + pct(Jobs.odds(company, type, a, agent, stake, today(), null)), ChatFormatting.GOLD));
			}
			ItemStack stack = icon(item(a.icon, Items.PAPER), t(a.advisor + " (" + a.stat + ")", approach == a ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
				ChatFormatting.BOLD), lore);
			button(slot, approach == a ? glow(stack) : stack, () -> {
				click();
				approach = a;
			});
		}
		// the stake
		int[] stakeSlots = {20, 22, 24};
		String[] stakeIcons = {"gold_nugget", "gold_ingot", "gold_block"};
		for (int s = 0; s < 3; s++) {
			int which = s;
			List<Component> lore = new ArrayList<>();
			lore.add(t(Bank.format(Jobs.stakeCents(type, s)), ChatFormatting.GOLD));
			lore.add(t(s == 0 ? "-10% odds" : s == 2 ? "+10% odds" : "No change to the odds", ChatFormatting.GRAY));
			if (type.mult > 0) {
				lore.add(t("The payout scales with the stake.", ChatFormatting.DARK_GRAY));
			}
			ItemStack stack = icon(item(stakeIcons[s], Items.GOLD_INGOT), t(Jobs.STAKES[s] + " stake", stake == s ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
				ChatFormatting.BOLD), lore);
			button(stakeSlots[s], stake == s ? glow(stack) : stack, () -> {
				click();
				stake = which;
			});
		}
		// the agents who are in
		int i = 0;
		for (Agent a : company.agents) {
			if (a.state() != Agent.State.READY || a.id.equals(job.target) || i >= 9) {
				continue;
			}
			List<Component> lore = agentLore(a);
			if (approach != null) {
				lore.add(t("Odds this way: " + pct(Jobs.odds(company, type, approach, a, stake, today(), null)) + " · back in "
					+ days(Jobs.days(type, a)), ChatFormatting.GOLD));
			}
			ItemStack stack = icon(item("player_head", Items.PAPER), t(a.name, a.id.equals(agentId) ? ChatFormatting.GOLD : ChatFormatting.WHITE, ChatFormatting.BOLD), lore);
			button(36 + i++, a.id.equals(agentId) ? glow(stack) : stack, () -> {
				click();
				agentId = a.id;
			});
		}
		if (i == 0) {
			button(40, icon(Items.BARRIER, t("Nobody's available", ChatFormatting.RED), List.of(
				t("Hire someone at the Hiring Board, or wait", ChatFormatting.GRAY), t("for your agents to come home.", ChatFormatting.GRAY))), null);
		}
		button(45, icon(Items.ARROW, t("Back to the board", ChatFormatting.YELLOW), List.of()), () -> go(Page.BOARD));
		// go
		if (approach != null && agent != null) {
			double odds = Jobs.odds(company, type, approach, agent, stake, today(), null);
			List<Component> lore = new ArrayList<>();
			lore.add(t(agent.name + ", " + approach.advisor + "'s way", ChatFormatting.WHITE));
			lore.add(t("Stake: " + Bank.format(Jobs.stakeCents(type, stake)), ChatFormatting.GOLD));
			lore.add(t("Odds: " + pct(odds) + " · back in " + days(Jobs.days(type, agent)), ChatFormatting.GOLD));
			if (type.carries()) {
				lore.add(t(type.special == JobType.Special.EXPORT ? "Takes the bottles and cigars you carry." : "Takes the ship you carry.", ChatFormatting.RED));
			}
			lore.add(t("Click to send them.", ChatFormatting.YELLOW));
			button(49, glow(icon(Items.EMERALD, t("Send " + agent.name, ChatFormatting.GREEN, ChatFormatting.BOLD), lore)), () -> send(job, type, agent));
		} else {
			button(49, icon(item("gray_dye", Items.PAPER), t("Pick an advisor and an agent", ChatFormatting.GRAY), List.of()), null);
		}
	}

	private void send(Job job, JobType type, Agent agent) {
		Approach a = approach;
		if (a == null) {
			return;
		}
		boolean special = !type.onBoard();
		List<ItemStack> taken = List.of();
		if (type.carries()) {
			taken = Cargo.peek(type, viewer);
			String why = Cargo.load(job, type, viewer);
			if (why != null) {
				nope(why);
				return;
			}
		}
		if (type == JobType.COLLECT_DEBT) {
			Loan loan = company.loan(job.target);
			if (loan == null || loan.state() != Loan.State.DEFAULTED) {
				nope("That debt isn't there to collect any more.");
				return;
			}
		}
		String why = Jobs.start(company, type, special ? null : job, a, agent, stake, today(), job.target);
		if (why != null) {
			if (type.carries()) {
				Cargo.unload(job, type, viewer, taken);
			}
			nope(why);
			return;
		}
		if (type == JobType.COLLECT_DEBT) {
			Loan loan = company.loan(job.target);
			if (loan != null) {
				loan.state(Loan.State.COLLECTING);
			}
		}
		kaching();
		say(agent.name + " is on their way: " + type.title + ", " + a.advisor + "'s way. Back in " + days(Jobs.days(type, agent)) + ".", ChatFormatting.GOLD);
		plan = null;
		page = Page.ACTIVE;
	}

	private List<Component> agentLore(Agent a) {
		List<Component> lore = new ArrayList<>();
		lore.add(t("Force " + a.force + " · Coin " + a.coin + " · Guile " + a.guile, ChatFormatting.WHITE));
		for (String name : a.traits) {
			Trait trait = Trait.byName(name);
			if (trait != null) {
				lore.add(t(trait.title + ": " + trait.text, ChatFormatting.AQUA));
			}
		}
		lore.add(t("Level " + a.level + " · " + a.jobs + " jobs · " + Bank.format(a.wage) + " a day", ChatFormatting.GRAY));
		return lore;
	}

	private void active() {
		long today = today();
		int i = 0;
		for (Job job : company.active) {
			JobType type = job.type();
			Agent agent = company.agent(job.agent);
			Approach a = job.approach();
			if (type == null || i >= GRID.length) {
				continue;
			}
			List<Component> lore = new ArrayList<>();
			lore.add(t((agent == null ? "?" : agent.name) + ", " + (a == null ? "?" : a.advisor) + "'s way", ChatFormatting.WHITE));
			lore.add(t("Stake: " + Bank.format(job.paid) + " · back in " + days(Math.max(1, job.end - today)), ChatFormatting.GOLD));
			if (agent != null && a != null) {
				lore.add(t("Odds now: " + pct(Jobs.odds(company, type, a, agent, job.stake, today, job)), ChatFormatting.GOLD));
			}
			if (job.pendingTelegram()) {
				lore.add(t("📨 A telegram is waiting for an answer!", ChatFormatting.RED));
			} else if (!job.telegram.isEmpty()) {
				lore.add(t("Telegram answered.", ChatFormatting.DARK_GRAY));
			}
			ItemStack stack = icon(item(type.icon, Items.PAPER), t(type.title, ChatFormatting.AQUA, ChatFormatting.BOLD), lore);
			button(GRID[i++], job.pendingTelegram() ? glow(stack) : stack, job.pendingTelegram() ? () -> go(Page.TELEGRAMS) : null);
		}
		if (i == 0) {
			button(22, icon(item("clock", Items.PAPER), t("Nothing under way", ChatFormatting.GRAY), List.of(t("Plan a job on the Job Board.", ChatFormatting.DARK_GRAY))), null);
		}
	}

	private void agents() {
		int i = 0;
		for (Agent a : company.agents) {
			if (i >= GRID.length) {
				break;
			}
			List<Component> lore = agentLore(a);
			String icon;
			Runnable action = null;
			switch (a.state()) {
				case AWAY -> {
					icon = "compass";
					lore.add(t(a.heldBy == null || a.heldBy.isEmpty() ? "Away on a job." : "Away on an errand for the Heeren.", ChatFormatting.YELLOW));
				}
				case WOUNDED -> {
					icon = "golden_apple";
					lore.add(t("Wounded: back on day " + a.until + " (in " + days(Math.max(1, a.until - today())) + ").", ChatFormatting.RED));
					lore.add(t("Shift-click to dismiss.", ChatFormatting.DARK_GRAY));
					action = () -> {
						if (shift) {
							nope(Hiring.dismiss(company, a));
						}
					};
				}
				case CAPTURED -> {
					icon = "iron_bars";
					lore.add(t("Captured!", ChatFormatting.RED));
					lore.add(t("Click: pay the " + Bank.format(Hiring.ransom(a)) + " ransom.", ChatFormatting.YELLOW));
					lore.add(t("Shift-click: plan a rescue (cheaper, riskier).", ChatFormatting.YELLOW));
					action = () -> {
						if (shift) {
							Job rescue = new Job();
							rescue.type = JobType.RESCUE.id();
							rescue.target = a.id;
							startPlanning(rescue);
						} else {
							String why = Hiring.payRansom(company, a);
							if (why == null) {
								kaching();
								say(a.name + " is free.", ChatFormatting.GREEN);
							}
							nope(why);
						}
					};
				}
				default -> {
					icon = "player_head";
					lore.add(t("Ready for a job.", ChatFormatting.GREEN));
					lore.add(t("Shift-click to dismiss.", ChatFormatting.DARK_GRAY));
					action = () -> {
						if (shift) {
							nope(Hiring.dismiss(company, a));
						}
					};
				}
			}
			button(GRID[i++], icon(item(icon, Items.PAPER), t(a.name, ChatFormatting.WHITE, ChatFormatting.BOLD), lore), action);
		}
		if (i == 0) {
			button(22, icon(item("player_head", Items.PAPER), t("Nobody on the books", ChatFormatting.GRAY), List.of(t("Hire someone at the Hiring Board.", ChatFormatting.DARK_GRAY))),
				() -> go(Page.HIRING));
		}
		button(49, icon(Items.SUNFLOWER, t("Wages: " + Bank.format(company.dailyWages()) + " a day", ChatFormatting.GOLD), List.of(
			t("Paid at every dawn. Can't pay, and the best-paid", ChatFormatting.GRAY), t("agent who's in walks out.", ChatFormatting.GRAY))), null);
	}

	private void hiring() {
		int i = 0;
		for (Agent a : company.candidates) {
			if (i >= GRID.length) {
				break;
			}
			List<Component> lore = agentLore(a);
			lore.add(a.hireCost == 0 ? t("Pressganged: free to sign.", ChatFormatting.GREEN)
				: t("To sign: " + Bank.format(a.hireCost), ChatFormatting.GOLD));
			lore.add(t("Click to hire.", ChatFormatting.YELLOW));
			button(GRID[i++], icon(item("player_head", Items.PAPER), t(a.name, ChatFormatting.WHITE, ChatFormatting.BOLD), lore), () -> {
				String why = Hiring.hire(company, a);
				if (why == null) {
					kaching();
					say(a.name + " signs the Compagnie's books.", ChatFormatting.GREEN);
				}
				nope(why);
			});
		}
		if (i == 0) {
			button(22, icon(Items.EMERALD, t("Nobody looking for work", ChatFormatting.GRAY), List.of(t("New faces turn up every two days.", ChatFormatting.DARK_GRAY))), null);
		}
		button(49, icon(item("clock", Items.PAPER), t("New faces every two days", ChatFormatting.GRAY), List.of(
			t("Better ones as the company grows.", ChatFormatting.DARK_GRAY))), null);
	}

	private void ledger() {
		long today = today();
		int i = 0;
		for (Loan loan : company.loans) {
			if (i >= GRID.length) {
				break;
			}
			List<Component> lore = new ArrayList<>();
			lore.add(t("Wants " + loan.amountText() + " for " + loan.reason, ChatFormatting.WHITE));
			lore.add(t(Math.round(loan.interest * 100) + "% interest over " + days(loan.days) + ": " + loan.owedText() + " back", ChatFormatting.GOLD));
			lore.add(t("Reputation: " + loan.risk().title, loan.risk() == Loan.Risk.RELIABLE ? ChatFormatting.GREEN
				: loan.risk() == Loan.Risk.SHAKY ? ChatFormatting.YELLOW : ChatFormatting.RED));
			String icon;
			Runnable action = null;
			switch (loan.state()) {
				case OFFER -> {
					icon = loan.gold ? "gold_ingot" : "paper";
					lore.add(t("Asking until day " + loan.day + ".", ChatFormatting.DARK_GRAY));
					lore.add(t(loan.gold ? "Click to lend the ingots (bring them)." : "Click to lend.", ChatFormatting.YELLOW));
					action = () -> {
						String why = Ledger.lend(company, loan, viewer, today);
						if (why == null) {
							kaching();
							say("Lent " + loan.amountText() + " to " + loan.borrower + ".", ChatFormatting.GREEN);
						}
						nope(why);
					};
				}
				case ACTIVE -> {
					icon = "book";
					lore.add(t("Due on day " + loan.day + " (in " + days(Math.max(0, loan.day - today)) + ").", ChatFormatting.AQUA));
				}
				case COLLECTING -> {
					icon = "iron_axe";
					lore.add(t("A collector is on the way.", ChatFormatting.AQUA));
				}
				default -> {
					icon = "redstone";
					lore.add(t("IN DEFAULT", ChatFormatting.RED, ChatFormatting.BOLD));
					lore.add(t("Click: send a collector.", ChatFormatting.YELLOW));
					lore.add(t("Right-click: sell the debt to Leonard the Usurer (30%).", ChatFormatting.YELLOW));
					lore.add(t("Shift-click: write it off.", ChatFormatting.YELLOW));
					action = () -> {
						if (shift) {
							Ledger.writeOff(company, loan);
						} else if (right) {
							String why = Ledger.sell(company, loan);
							if (why == null) {
								kaching();
							}
							nope(why);
						} else {
							Job collect = new Job();
							collect.type = JobType.COLLECT_DEBT.id();
							collect.target = loan.id;
							startPlanning(collect);
						}
					};
				}
			}
			button(GRID[i++], icon(item(icon, Items.PAPER), t(loan.borrower, ChatFormatting.WHITE, ChatFormatting.BOLD), lore), action);
		}
		if (i == 0) {
			button(22, icon(Items.BOOK, t("No borrowers today", ChatFormatting.GRAY), List.of(t("They turn up at dawn.", ChatFormatting.DARK_GRAY))), null);
		}
		button(49, icon(Items.BOOK, t("Moneylending", ChatFormatting.GOLD), List.of(
			t("Borrowers pay back with interest on the due day,", ChatFormatting.GRAY),
			t("unless they don't. The riskier, the richer.", ChatFormatting.GRAY),
			t("Gold loans are paid back in ingots, to the strongbox.", ChatFormatting.DARK_GRAY))), null);
	}

	private void telegrams() {
		int row = 1;
		String[] icons = {"gold_ingot", "book", "tnt"};
		for (Job job : company.active) {
			if (!job.pendingTelegram() || row > 4) {
				continue;
			}
			Telegram tg = Telegram.byName(job.telegram);
			JobType type = job.type();
			if (tg == null || type == null) {
				continue;
			}
			List<Component> lore = new ArrayList<>(wrap(tg.text(job), ChatFormatting.WHITE));
			lore.add(t("If nobody answers: \"" + tg.answer(1, job) + "\".", ChatFormatting.DARK_GRAY));
			button(row * 9 + 1, glow(icon(Items.PAPER, t("📨 " + type.title, ChatFormatting.GOLD, ChatFormatting.BOLD), lore)), null);
			for (int c = 0; c < 3; c++) {
				int choice = c;
				button(row * 9 + 3 + 2 * c, icon(item(icons[c], Items.PAPER), t(tg.answer(c, job), ChatFormatting.YELLOW), List.of(t("Click to answer.", ChatFormatting.DARK_GRAY))),
					() -> {
						String why = Jobs.answer(company, job, choice);
						if (why == null) {
							click();
							say("Telegram sent: \"" + tg.answer(choice, job) + "\"", ChatFormatting.GOLD);
						}
						nope(why);
					});
			}
			row++;
		}
		if (row == 1) {
			button(22, icon(Items.PAPER, t("No telegrams waiting", ChatFormatting.GRAY), List.of(t("They come in from the road, at dawn.", ChatFormatting.DARK_GRAY))), null);
		}
	}

	private void strongbox() {
		List<ItemStack> items = company.strongbox;
		int perPage = 36;
		int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
		boxPage = Math.max(0, Math.min(boxPage, pages - 1));
		for (int i = 0; i < perPage; i++) {
			int index = boxPage * perPage + i;
			if (index >= items.size()) {
				break;
			}
			ItemStack stack = items.get(index);
			button(9 + i, stack.copy(), () -> take(stack));
		}
		if (boxPage > 0) {
			button(45, icon(Items.ARROW, t("Previous page", ChatFormatting.YELLOW), List.of()), () -> boxPage--);
		}
		if (boxPage < pages - 1) {
			button(53, icon(Items.ARROW, t("Next page", ChatFormatting.YELLOW), List.of()), () -> boxPage++);
		}
		button(49, icon(Items.HOPPER, t("Take everything", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
			t("Click a stack to take just that one.", ChatFormatting.GRAY))), () -> {
			for (ItemStack stack : new ArrayList<>(items)) {
				take(stack);
			}
		});
	}

	private void take(ItemStack stack) {
		if (!company.strongbox.remove(stack)) {
			return;
		}
		viewer.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		Companies.changed();
		click();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
