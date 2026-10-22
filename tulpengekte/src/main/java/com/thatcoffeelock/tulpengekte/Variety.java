package com.thatcoffeelock.tulpengekte;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * The twelve tulips, all real names from the 1630s. Each grows as one of the four vanilla tulips, so vanilla clients
 * see them. The broken ones (streaked by a virus, though nobody knew that then) are rare, weak and worth a fortune.
 */
enum Variety {
	WITTE_CROONEN("Witte Croonen", Tier.COMMON, "white", "Sold by the pound, not the bulb. Humble beginnings."),
	SWITSERS("Switsers", Tier.COMMON, "orange", "Red and yellow, like a Swiss guard's coat."),
	GOUDA("Gouda", Tier.COMMON, "red", "Named after the town, not the cheese. Mostly."),
	ROSEN("Rosen", Tier.COMMON, "pink", "Rose on white. Pretty. Common. Sorry."),

	GENERALISSIMO("Generalissimo", Tier.FINE, "orange", "Stands to attention in the flowerbed."),
	ADMIRAEL_VAN_ENCKHUYSEN("Admirael van Enckhuysen", Tier.FINE, "white", "Named after an admiral. Has never seen the sea."),
	LACK_VAN_RIJN("Lack van Rijn", Tier.FINE, "red", "Red on white, like spilled wine on a tablecloth."),
	PARAGON_LIEFKENS("Paragon Liefkens", Tier.FINE, "pink", "Liefkens means 'darling'. It knows."),

	VICEROY("Viceroy", Tier.RARE, "pink", "Once traded for 4 oxen, 8 pigs, 12 sheep, 1,000 pounds of cheese and a bed."),
	ADMIRAL_VAN_DER_EIJCK("Admiral van der Eijck", Tier.RARE, "orange", "Flamed like a cannon going off. Priced like one too."),
	BRABANSON("Brabanson", Tier.RARE, "white", "Feathered purple on white. The neighbours are jealous."),

	SEMPER_AUGUSTUS("Semper Augustus", Tier.LEGENDARY, "red", "Its owner turned down a canal house for it. Twice.");

	/** How rare a variety is: what it's worth, how fast it multiplies, and how it shows off. */
	enum Tier {
		COMMON("Common", ChatFormatting.WHITE),
		FINE("Fine", ChatFormatting.GREEN),
		RARE("Broken", ChatFormatting.LIGHT_PURPLE),
		LEGENDARY("Legendary", ChatFormatting.GOLD);

		final String title;
		final ChatFormatting format;

		Tier(String title, ChatFormatting format) {
			this.title = title;
			this.format = format;
		}
	}

	final String title;
	final Tier tier;
	/** The vanilla tulip it flowers as: red, orange, white or pink. */
	final String color;
	final String story;

	Variety(String title, Tier tier, String color, String story) {
		this.title = title;
		this.tier = tier;
		this.color = color;
		this.story = story;
	}

	String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The vanilla tulip block (and item model) it flowers as. */
	String block() {
		return "minecraft:" + color + "_tulip";
	}

	static @Nullable Variety byId(String id) {
		for (Variety v : values()) {
			if (v.id().equalsIgnoreCase(id)) {
				return v;
			}
		}
		return null;
	}

	static List<Variety> of(Tier tier) {
		List<Variety> list = new ArrayList<>();
		for (Variety v : values()) {
			if (v.tier == tier) {
				list.add(v);
			}
		}
		return list;
	}

	static Variety random(Tier tier, Random random) {
		List<Variety> list = of(tier);
		return list.get(random.nextInt(list.size()));
	}

	/** The common tulip of this color: what a broken bulb reverts to. Wild tulips dig up as these too. */
	static Variety common(String color) {
		for (Variety v : of(Tier.COMMON)) {
			if (v.color.equals(color)) {
				return v;
			}
		}
		return GOUDA;
	}
}
