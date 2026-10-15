package com.thatcoffeelock.compagnie;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * For mods that build on De Compagnie and need it at compile time (De Hooghe Heeren): a page of their own behind a
 * tab on the Company Desk, a turn at every dawn, a say in the odds of the company's jobs, and the company's agents to
 * send on errands of their own. Mods that only touch the Compagnie in passing use {@link CompagnieApi} instead.
 */
public final class CompagnieHooks {
	/** Another mod's part of the company. */
	public interface Extension {
		/** Who's holding an agent that's away on this mod's business (an agent's {@code heldBy}). */
		String id();

		/** The tab in the desk's top-right corner. */
		ItemStack tab(UUID owner);

		/** Clicking the tab: open the mod's own page. */
		void open(ServerPlayer player);

		/** At every dawn, after the company's own day. News added with {@link #news} is told with the rest. */
		default void dawn(UUID owner, String ownerName, long day, Random random) {
		}

		/** Added to the odds of the company's jobs: category "trade", "raid", "explore", "shady" or "special". */
		default double odds(UUID owner, String category, boolean sea, long day) {
			return 0;
		}
	}

	/** One of the company's agents, as another mod sees them. State: READY, AWAY, WOUNDED or CAPTURED. */
	public record AgentInfo(String id, String name, int force, int coin, int guile, List<String> traits, int level, String state, String heldBy) {
	}

	private static final List<Extension> EXTENSIONS = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private CompagnieHooks() {
	}

	public static void register(Extension extension) {
		EXTENSIONS.removeIf(e -> e.id().equals(extension.id()));
		EXTENSIONS.add(extension);
	}

	static List<Extension> extensions() {
		return EXTENSIONS;
	}

	static boolean registered(String id) {
		for (Extension e : EXTENSIONS) {
			if (e.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	static double odds(Company c, JobType type, long day) {
		double p = 0;
		for (Extension e : EXTENSIONS) {
			try {
				p += e.odds(c.uuid(), type.category.name().toLowerCase(java.util.Locale.ROOT), type.sea, day);
			} catch (RuntimeException ex) {
				CompagnieMod.LOG.error("{} failed to weigh in on the odds", e.id(), ex);
			}
		}
		return p;
	}

	static void dawn(Company c, long day, Random random) {
		// an agent held by a mod that's gone (uninstalled) comes home
		for (Agent a : c.agents) {
			if (a.heldBy != null && !a.heldBy.isEmpty() && !registered(a.heldBy)) {
				a.heldBy = "";
				if (a.state() == Agent.State.AWAY) {
					a.state(Agent.State.READY);
				}
			}
		}
		for (Extension e : EXTENSIONS) {
			try {
				e.dawn(c.uuid(), c.ownerName, day, random);
			} catch (RuntimeException ex) {
				CompagnieMod.LOG.error("Dawn failed for {} in {}'s company", e.id(), c.ownerName, ex);
			}
		}
	}

	// ---------------------------------------------------------------- the company

	/** Today, on the companies' clock (a Minecraft day of game time). */
	public static long today() {
		return Companies.today();
	}

	public static boolean exists(UUID owner) {
		return Companies.get(owner) != null;
	}

	/** Founds the owner's company if they don't have one yet. */
	public static void found(UUID owner, String name) {
		Companies.of(owner, name);
	}

	/** 0 (Charter) to 3 (VOC), or -1 without a company. */
	public static int rank(UUID owner) {
		Company c = Companies.get(owner);
		return c == null ? -1 : c.rank().ordinal();
	}

	public static String rankTitle(UUID owner) {
		Company c = Companies.get(owner);
		return c == null ? "" : c.rank().title;
	}

	public static void reputation(UUID owner, long rep) {
		Company c = Companies.get(owner);
		if (c != null && rep != 0) {
			c.rep = Math.max(0, c.rep + rep);
			Companies.changed();
		}
	}

	/** Where the owner last used a Company Desk: {x, z}, or null. */
	public static int[] home(UUID owner) {
		Company c = Companies.get(owner);
		return c == null ? null : new int[] {c.homeX, c.homeZ};
	}

	/** A line in the company's news (shown at the desk, told at dawn). */
	public static void news(UUID owner, String line) {
		Company c = Companies.get(owner);
		if (c != null) {
			c.news(line);
			Companies.changed();
		}
	}

	/** Opens the owner's Company Desk (a "back" button on another mod's page). */
	public static void openDesk(ServerPlayer player) {
		DeskMenu.open(player, Companies.of(player.getUUID(), player.getName().getString()));
	}

	// ---------------------------------------------------------------- agents

	public static List<AgentInfo> agents(UUID owner) {
		Company c = Companies.get(owner);
		List<AgentInfo> out = new ArrayList<>();
		if (c != null) {
			for (Agent a : c.agents) {
				out.add(new AgentInfo(a.id, a.name, a.force, a.coin, a.guile, List.copyOf(a.traits), a.level, a.state().name(),
					a.heldBy == null ? "" : a.heldBy));
			}
		}
		return out;
	}

	/** Trait names as the desk shows them ("Lucky, Shadow"). */
	public static String traitText(List<String> traits) {
		List<String> names = new ArrayList<>();
		for (String t : traits) {
			Trait trait = Trait.byName(t);
			if (trait != null) {
				names.add(trait.title);
			}
		}
		return names.isEmpty() ? "no traits" : String.join(", ", names);
	}

	/**
	 * What this agent adds to an errand's base odds done this way ("FORCE", "COIN" or "GUILE"): their stat (4% a point
	 * above or below 5), their traits, the company's rank and the Enterprise skill. The same as on a job.
	 */
	public static double edge(UUID owner, String agentId, String approach) {
		Company c = Companies.get(owner);
		Agent a = c == null ? null : c.agent(agentId);
		Approach ap = Approach.byName(approach);
		return a == null || ap == null ? 0 : Jobs.edge(c, a, ap);
	}

	/** Sends a ready agent off on another mod's errand. False if they're not available. */
	public static boolean dispatch(UUID owner, String agentId, String heldBy) {
		Company c = Companies.get(owner);
		Agent a = c == null ? null : c.agent(agentId);
		if (a == null || a.state() != Agent.State.READY) {
			return false;
		}
		a.state(Agent.State.AWAY);
		a.heldBy = heldBy;
		Companies.changed();
		return true;
	}

	/**
	 * Brings an agent home from an errand. Tier: TRIUMPH, SUCCESS, PARTIAL, FAILURE or DISASTER. They earn
	 * experience, or come back wounded, captured or not at all, as from a job. Returns what became of them ("" if
	 * nothing worth saying).
	 */
	public static String recall(UUID owner, String agentId, String approach, String tier) {
		Company c = Companies.get(owner);
		Agent a = c == null ? null : c.agent(agentId);
		if (a == null) {
			return "";
		}
		a.heldBy = "";
		Approach ap = Approach.byName(approach);
		Jobs.Tier t;
		try {
			t = Jobs.Tier.valueOf(tier);
		} catch (IllegalArgumentException | NullPointerException e) {
			t = Jobs.Tier.FAILURE;
		}
		String fate = Jobs.fate(c, a, ap == null ? Approach.GUILE : ap, t, Companies.today(), RANDOM);
		SkillsLink.xp(c.uuid(), "enterprise", t.good() ? 8 : 2);
		Companies.changed();
		return fate;
	}

	/** Test hook: puts an agent on the books straight away, no fee. Returns their id. */
	public static String addAgent(UUID owner, String name, int force, int coin, int guile) {
		Company c = Companies.of(owner, "");
		Agent a = new Agent();
		a.id = c.newId("a");
		a.name = name;
		a.force = force;
		a.coin = coin;
		a.guile = guile;
		a.wage = Bank.cents(10);
		c.agents.add(a);
		Companies.changed();
		return a.id;
	}
}
