package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * One player's place among the High Lords: their Honour and Fear, their regiments, the rival houses they deal with,
 * the dispatches waiting for an answer, the errands under way, their hall and its guest, and a battle if one's coming.
 */
final class Court {
	/** A message from a rival that wants an answer. Unanswered by the day it expires: the last answer is the default. */
	static final class Dispatch {
		enum Kind {
			TRADE_OFFER, PEACE_PACT_OFFER, ALLIANCE_OFFER, TRIBUTE_DEMAND, SPY_CAUGHT, CALL_TO_ARMS, PEACE_OFFER, TERMS
		}

		String id = "";
		String kind = Kind.TRADE_OFFER.name();
		String rival = "";
		/** CALL_TO_ARMS: the rival your ally is fighting. */
		String other = "";
		long amount;
		long day;
		long expires;

		Kind kind() {
			try {
				return Kind.valueOf(kind);
			} catch (IllegalArgumentException | NullPointerException e) {
				return Kind.TRADE_OFFER;
			}
		}
	}

	/** A covert errand under way: one of the Compagnie's agents, sent against a rival. */
	static final class Op {
		String id = "";
		String type = "";
		String rival = "";
		/** FORGE: the house that gets the letter. */
		String other = "";
		String agent = "";
		String agentName = "";
		String approach = "";
		long paid;
		long start;
		long end;

		Covert.Type type() {
			return Covert.Type.byName(type);
		}
	}

	/** A pitched battle, at noon on its day, somewhere between your home and their seat. */
	static final class Battle {
		String rival = "";
		long day;
		int x;
		int y = Integer.MIN_VALUE;
		int z;
		/** You called it ("Give battle") rather than they. */
		boolean ours;
		boolean started;

		BlockPos pos() {
			return new BlockPos(x, y == Integer.MIN_VALUE ? 64 : y, z);
		}
	}

	String owner = "";
	String ownerName = "";
	/** 0-100: do you keep your word? Starts at 50. */
	int honour = 50;
	/** 0-100: what happens to people who cross you. Fades back to 10 if you don't remind them. */
	int fear = 10;
	int regiments;
	int nextId = 1;
	/** A rival's saboteurs: your Compagnie's trade jobs are 10% harder until this day. */
	long sabotagedUntil = -1;
	int warsWon;
	int warsLost;
	int housesFallen;

	List<Rival> rivals = new ArrayList<>();
	List<Dispatch> dispatches = new ArrayList<>();
	List<Op> ops = new ArrayList<>();
	/** The last few things that happened at court, newest last. */
	List<String> chronicle = new ArrayList<>();
	@Nullable Battle battle;

	// the hall
	boolean hall;
	String hallDim = "";
	int hallX;
	int hallY;
	int hallZ;
	/** The rival staying in your hall, until which dawn, and whether the doors have been barred. */
	String guest = "";
	long guestUntil;
	boolean guestHeld;
	/** The day you last served a feast for the current guest. */
	long feasted = -10;

	UUID uuid() {
		return UUID.fromString(owner);
	}

	String newId(String prefix) {
		return prefix + (nextId++);
	}

	@Nullable Rival rival(String id) {
		for (Rival r : rivals) {
			if (r.id.equals(id)) {
				return r;
			}
		}
		return null;
	}

	@Nullable Dispatch dispatch(String id) {
		for (Dispatch d : dispatches) {
			if (d.id.equals(id)) {
				return d;
			}
		}
		return null;
	}

	BlockPos hallPos() {
		return new BlockPos(hallX, hallY, hallZ);
	}

	void chronicle(String line) {
		chronicle.add(line);
		while (chronicle.size() > 30) {
			chronicle.remove(0);
		}
	}

	void honour(int delta) {
		honour = Math.max(0, Math.min(100, honour + delta));
	}

	void fear(int delta) {
		fear = Math.max(0, Math.min(100, fear + delta));
	}

	boolean atWar() {
		for (Rival r : rivals) {
			if (r.war) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- opinion

	/** What a rival thinks of you, -100 to 100: their temperament's take on your Honour and Fear, their memories, your pacts. */
	int opinion(Rival r, long today) {
		Temperament t = r.temperament();
		double o = t.base + t.honour * (honour - 50) + t.fear * (fear - 30);
		for (Rival.Memory m : r.memories) {
			o += m.now(today);
		}
		if (r.tradePact) {
			o += 10;
		}
		if (r.peacePact) {
			o += 10;
		}
		if (r.alliance) {
			o += 20;
		}
		if (r.war) {
			o -= 25;
		}
		return (int) Math.round(Math.max(-100, Math.min(100, o)));
	}

	enum Tier {
		FEUD("Blood Feud", ChatFormatting.DARK_RED),
		HOSTILE("Hostile", ChatFormatting.RED),
		COLD("Cold", ChatFormatting.GOLD),
		NEUTRAL("Neutral", ChatFormatting.GRAY),
		CORDIAL("Cordial", ChatFormatting.YELLOW),
		FRIENDLY("Friendly", ChatFormatting.GREEN),
		SWORN("Sworn Ally", ChatFormatting.AQUA);

		final String title;
		final ChatFormatting color;

		Tier(String title, ChatFormatting color) {
			this.title = title;
			this.color = color;
		}

		static Tier of(int opinion) {
			if (opinion <= -60) {
				return FEUD;
			}
			if (opinion <= -30) {
				return HOSTILE;
			}
			if (opinion <= -10) {
				return COLD;
			}
			if (opinion < 10) {
				return NEUTRAL;
			}
			if (opinion < 30) {
				return CORDIAL;
			}
			if (opinion < 60) {
				return FRIENDLY;
			}
			return SWORN;
		}
	}

	/** What the other houses call you behind your back, from your Honour and Fear. */
	String reputation() {
		if (Math.abs(honour - 50) < 10 && fear < 25) {
			return "A Newcomer at Court";
		}
		if (honour >= 50) {
			return fear >= 40 ? "The Iron Ruler" : "The Benevolent Diplomat";
		}
		if (fear >= 40) {
			return "The Cutthroat";
		}
		return honour < 30 ? "The Snake" : "A Merchant of Flexible Principles";
	}

	String reputationText() {
		return switch (reputation()) {
			case "The Iron Ruler" -> "Respected and obeyed. Your word is good, and your sword is long.";
			case "The Benevolent Diplomat" -> "Your word is good: pacts and trade come easily.";
			case "The Cutthroat" -> "Feared. Demands get paid; pacts don't get signed.";
			case "The Snake" -> "Nobody trusts you, and nobody fears you. Fix one of those.";
			case "A Merchant of Flexible Principles" -> "Your word is worth about what it costs you to keep it.";
			default -> "Nobody quite knows what to make of you yet.";
		};
	}
}
