package com.thatcoffeelock.hoogheheeren;

/** One thing about a rival that's worth knowing before you sit down with them. */
enum Quirk {
	DRUNKARD("Drunkard", "Spirits at a feast count double."),
	PIOUS("Pious", "Spirits at a feast count against you."),
	VAIN("Vain", "Gifts given in person count double."),
	GREEDY("Greedy", "Gifts of Marks count half again."),
	PARANOID("Paranoid", "Hard to spy on (-10% on errands against them), slow to accept an invitation."),
	PROUD("Proud", "Never pays tribute, whatever you threaten."),
	COWARDLY("Cowardly", "Sues for peace early, and pays up when threatened."),
	OLD("Old", "Won't see many more winters. An heir is waiting.");

	final String title;
	final String text;

	Quirk(String title, String text) {
		this.title = title;
		this.text = text;
	}

	static Quirk byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return VAIN;
		}
	}
}
