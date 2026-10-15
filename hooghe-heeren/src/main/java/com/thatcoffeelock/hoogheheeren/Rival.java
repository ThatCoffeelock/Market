package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * A rival house: a head of house with a name and a face, a temperament and a quirk, the same three numbers you have
 * (a treasury, regiments, an income), what they remember of you, what they think of the other houses, and a seat
 * somewhere out in the world.
 */
final class Rival {
	/** Something they did or that was done to them, worth so much opinion, fading over so many days (0: never). */
	static final class Memory {
		String text = "";
		int value;
		long day;
		int fade;

		Memory() {
		}

		Memory(String text, int value, long day, int fade) {
			this.text = text;
			this.value = value;
			this.day = day;
			this.fade = fade;
		}

		/** What it's still worth today. */
		double now(long today) {
			if (fade <= 0) {
				return value;
			}
			double left = 1.0 - (double) (today - day) / fade;
			return left <= 0 ? 0 : value * Math.min(1.0, left);
		}
	}

	String id = "";
	// the head of house
	String house = "";
	String first = "";
	String title = "";
	String nation = "";
	String seatName = "";
	boolean woman;
	String skin = "steve";
	String colour = "red";
	String temperament = Temperament.MERCHANT.name();
	String quirk = Quirk.VAIN.name();
	/** How many times the house has changed hands within the family. */
	int generation = 1;
	long founded;

	// the same numbers you have
	long treasury;
	long income;
	int regiments;

	// you and them
	List<Memory> memories = new ArrayList<>();
	boolean tradePact;
	boolean peacePact;
	boolean alliance;
	boolean war;
	/** +100: you've crushed them; -100: they've crushed you. */
	int warScore;
	long warSince;
	/** The last day a peace offer (theirs or yours) was made. */
	long lastPeaceTalk = -10;
	/** Tribute: so many cents a day until a day, from them to you (or you to them). */
	long tribute;
	long tributeUntil;
	boolean tributeToUs;
	/** You know their real numbers, their temperament and their plans until this day. */
	long intelUntil = -1;
	/** You've met them in person (their temperament and quirk are no secret any more). */
	boolean met;
	/** One proposal a day: the last day you asked them for something. */
	long lastAsked = -10;
	long lastGift = -10;
	/** What they mean to do at the next dawn (an Ai.Intent name), and to whom. */
	String intent = "";
	String intentTarget = "";
	long sabotagedUntil = -1;
	/** Until this day you have a reason for war on them (they raided, sabotaged or spied on you): no Honour lost declaring it. */
	long provoked = -1;
	/** Their spy read your letters: their raids find your convoys more easily until this day. */
	long spied = -1;
	long broke;

	// them and the other houses
	/** Their opinion of the other houses, by rival id. */
	Map<String, Integer> relations = new HashMap<>();
	List<String> wars = new ArrayList<>();
	List<String> partners = new ArrayList<>();

	// their seat
	String dim = "minecraft:overworld";
	int seatX;
	int seatY = Integer.MIN_VALUE;
	int seatZ;
	boolean built;
	/** The colour the seat's banners were last hung in (a new house re-dresses it). */
	String dressed = "";
	long lastPlunder = -10;

	Temperament temperament() {
		return Temperament.byName(temperament);
	}

	Quirk quirk() {
		return Quirk.byName(quirk);
	}

	boolean is(Quirk q) {
		return quirk().equals(q);
	}

	boolean is(Temperament t) {
		return temperament().equals(t);
	}

	Names.Colour colour() {
		return Names.colour(colour);
	}

	/** "Heer Joost van Houten". */
	String name() {
		return title + " " + first + " " + house;
	}

	/** "van Houten" -> "Van Houten", for the start of a sentence. */
	String House() {
		return house.isEmpty() ? house : Character.toUpperCase(house.charAt(0)) + house.substring(1);
	}

	/** "he" or "she". */
	String they() {
		return woman ? "she" : "he";
	}

	String their() {
		return woman ? "her" : "his";
	}

	BlockPos seat() {
		return new BlockPos(seatX, seatY == Integer.MIN_VALUE ? 64 : seatY, seatZ);
	}

	boolean known(long today) {
		return intelUntil >= today;
	}

	int relation(String other) {
		Integer v = relations.get(other);
		return v == null ? 0 : v;
	}

	void relation(String other, int value) {
		relations.put(other, Math.max(-100, Math.min(100, value)));
	}

	void remember(String text, int value, long today, int fade) {
		memories.add(new Memory(text, value, today, fade));
		// keep it to the ones that still matter
		memories.removeIf(m -> m.fade > 0 && m.now(today) == 0);
		while (memories.size() > 12) {
			Memory weakest = null;
			for (Memory m : memories) {
				if (m.fade > 0 && (weakest == null || Math.abs(m.now(today)) < Math.abs(weakest.now(today)))) {
					weakest = m;
				}
			}
			memories.remove(weakest == null ? memories.get(0) : weakest);
		}
	}
}
