package com.thatcoffeelock.riches;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

/**
 * The 48 relics, six per collection. Each one comes from one place: a mob you kill, a block you mine, or the first
 * look inside a structure's loot chest. With uniqueRelics on (the default), each exists once per server: whoever
 * finds it first has the only one.
 */
enum Relic {
	// The Royal Collection
	ILLAGER_CROWN(Collection.ROYAL, "Crown of the Illager King", "golden_helmet", Source.KILL, "evoker", 1 / 25.0,
		"Worn by the last evoker who called himself king.", "Evokers drop it, rarely."),
	RAVAGER_HORN(Collection.ROYAL, "Horn of the Great Ravager", "goat_horn", Source.KILL, "ravager", 1 / 15.0,
		"Blow it and every pillager for a mile looks up.", "Ravagers drop it, now and then."),
	PIGLIN_SCEPTER(Collection.ROYAL, "Scepter of the Piglin Court", "blaze_rod", Source.KILL, "piglin_brute", 1 / 12.0,
		"Solid gold. Piglins would trade their grandmothers.", "Piglin brutes guard it."),
	BASTION_SIGNET(Collection.ROYAL, "The Bastion Signet", "gold_nugget", Source.CHEST, "bastion_treasure", 1 / 3.0,
		"A ring the size of your fist.", "In a bastion's treasure room."),
	MANSION_SEAL(Collection.ROYAL, "Great Seal of the Mansion", "mojang_banner_pattern", Source.CHEST, "woodland_mansion", 1 / 3.0,
		"Stamped on every illager eviction notice.", "In a woodland mansion's chests."),
	GOLDEN_CHALICE(Collection.ROYAL, "The Golden Chalice", "gold_ingot", Source.CHEST, "desert_pyramid", 1 / 5.0,
		"Nobody knows what was in it. Nobody wants to.", "In a desert pyramid's chests."),

	// Treasures of the Deep
	ELDER_EYE(Collection.DEEP, "Eye of the Elder", "heart_of_the_sea", Source.KILL, "elder_guardian", 1 / 2.0,
		"It still blinks. Don't look at it too long.", "Elder guardians, in ocean monuments."),
	CAPTAINS_SPYGLASS(Collection.DEEP, "Captain's Spyglass", "spyglass", Source.CHEST, "shipwreck", 1 / 8.0,
		"Engraved: \"Property of the Ahoy Line\".", "In a shipwreck's chests."),
	SUNKEN_DOUBLOON(Collection.DEEP, "The Sunken Doubloon", "raw_gold", Source.CHEST, "buried_treasure", 1 / 3.0,
		"One coin. Worth more than the chest it came in.", "In buried treasure."),
	DROWNED_COMPASS(Collection.DEEP, "Drowned Sailor's Compass", "compass", Source.KILL, "drowned", 1 / 150.0,
		"Points home. Home is underwater now.", "Drowned carry it, very rarely."),
	KRAKEN_INK(Collection.DEEP, "Kraken's Ink Pot", "glow_ink_sac", Source.KILL, "glow_squid", 1 / 40.0,
		"Glows faintly. Smells of the deep.", "Glow squid, now and then."),
	MONUMENT_PEARL(Collection.DEEP, "Pearl of the Monument", "prismarine_crystals", Source.MINE, "sea_lantern", 1 / 40.0,
		"Pried out of a guardian's lamp.", "Break sea lanterns."),

	// Relics of the Underworld
	DRAGON_TOOTH(Collection.UNDER, "The Dragon's Tooth", "bone", Source.KILL, "ender_dragon", 1.0,
		"The only part of the dragon that stayed.", "Kill the Ender Dragon."),
	CROWN_OF_BONES(Collection.UNDER, "Crown of Bones", "wither_skeleton_skull", Source.KILL, "wither", 1.0,
		"Three heads, one crown. Awkward.", "Kill the Wither."),
	NETHERITE_IDOL(Collection.UNDER, "The Netherite Idol", "netherite_scrap", Source.MINE, "ancient_debris", 1 / 25.0,
		"Heavier than it looks. Older than the Nether.", "Mine ancient debris."),
	GHAST_TEAR(Collection.UNDER, "The Ghast's Last Tear", "ghast_tear", Source.KILL, "ghast", 1 / 30.0,
		"It's still crying. It's always crying.", "Ghasts drop it, now and then."),
	BLAZE_HEART(Collection.UNDER, "Heart of the Blaze", "fire_charge", Source.KILL, "blaze", 1 / 120.0,
		"Warm to the touch. Very warm. Ow.", "Blazes carry it, rarely."),
	WARDEN_ECHO(Collection.UNDER, "The Warden's Echo", "echo_shard", Source.KILL, "warden", 1.0,
		"Shh.", "Kill a warden. Good luck."),

	// The Ancient World
	TRILOBITE(Collection.ANCIENT, "Petrified Trilobite", "flint", Source.MINE, "deepslate", 1 / 3000.0,
		"Older than dirt. Literally.", "Hidden in deepslate. Keep mining."),
	DINO_TOOTH(Collection.ANCIENT, "Tyrant Lizard Tooth", "pointed_dripstone", Source.MINE, "dripstone_block", 1 / 250.0,
		"Proof that something big lived here first.", "Hidden in dripstone blocks."),
	AMBER_MOSQUITO(Collection.ANCIENT, "Mosquito in Amber", "honeycomb", Source.MINE, "bee_nest", 1 / 12.0,
		"Don't get any ideas.", "Break bee nests."),
	CITY_LANTERN(Collection.ANCIENT, "Lantern of the Deep Dark", "soul_lantern", Source.CHEST, "ancient_city", 1 / 6.0,
		"It burned before the sculk came.", "In an ancient city's chests."),
	JUNGLE_IDOL(Collection.ANCIENT, "The Emerald Idol", "emerald", Source.CHEST, "jungle_temple", 1 / 3.0,
		"Swap it for a bag of sand at your own risk.", "In a jungle temple's chest."),
	STRONGHOLD_CODEX(Collection.ANCIENT, "The Stronghold Codex", "enchanted_book", Source.CHEST, "stronghold_library", 1 / 4.0,
		"Written in a language nobody speaks. Yet.", "In a stronghold library."),

	// The Golden Age
	COMPAGNIE_CHARTER(Collection.GOLDEN, "Charter of the Compagnie", "filled_map", Source.CHEST, "village_cartographer", 1 / 4.0,
		"Signed in 1602. The fine print goes on for miles.", "In a village cartographer's chest."),
	FIRST_SHARE(Collection.GOLDEN, "The First Share", "paper", Source.CHEST, "pillager_outpost", 1 / 5.0,
		"Share number one of the first company on earth. The dividend is late.", "In a pillager outpost's chest."),
	SILVER_FLEET_BAR(Collection.GOLDEN, "Bar of the Silver Fleet", "iron_ingot", Source.CHEST, "underwater_ruin", 1 / 8.0,
		"Piet Heyn took the whole fleet. This one fell overboard.", "In an underwater ruin's chest."),
	HUYGENS_PENDULUM(Collection.GOLDEN, "Huygens' Pendulum", "clock", Source.CHEST, "shipwreck_treasure", 1 / 8.0,
		"Keeps perfect time. Even at the bottom of the sea.", "In a shipwreck's treasure chest."),
	MASTERS_BRUSH(Collection.GOLDEN, "The Master's Brush", "brush", Source.CHEST, "village_", 1 / 40.0,
		"Still wet with umber. Probably.", "In village chests, very rarely."),
	ORANGE_BANNER(Collection.GOLDEN, "Banner of the Prince", "orange_banner", Source.KILL, "pillager", 1 / 150.0,
		"The Prince's colours. The pillagers have no idea whose.", "Pillagers carry it, very rarely."),

	// The Frozen North
	POLAR_CLAW(Collection.FROZEN, "Claw of the Polar King", "white_dye", Source.KILL, "polar_bear", 1 / 20.0,
		"Bigger than your hand. Sharper than your wit.", "Polar bears drop it, now and then."),
	IGLOO_RUNESTONE(Collection.FROZEN, "The Igloo Runestone", "snowball", Source.CHEST, "igloo_chest", 1 / 2.0,
		"Carved by someone who really didn't want to go outside.", "In an igloo's basement chest."),
	STRAY_ARROW(Collection.FROZEN, "The Stray's Last Arrow", "spectral_arrow", Source.KILL, "stray", 1 / 60.0,
		"Frozen mid-flight. Still pointed at you.", "Strays carry it, rarely."),
	GLACIER_HEART(Collection.FROZEN, "Heart of the Glacier", "blue_ice", Source.MINE, "blue_ice", 1 / 80.0,
		"Never melts. Not even in the Nether. We checked.", "Hidden in blue ice."),
	GOAT_HOOF(Collection.FROZEN, "Hoof of the Mountain Goat", "rabbit_foot", Source.KILL, "goat", 1 / 40.0,
		"Climbs walls on its own. Keep it in a box.", "Goats drop it, now and then."),
	MAMMOTH_HAIR(Collection.FROZEN, "Mammoth Hair in Ice", "string", Source.MINE, "packed_ice", 1 / 200.0,
		"Somewhere, a very cold mammoth is missing a patch.", "Hidden in packed ice."),

	// Curiosities of the End
	END_CITY_CROWN(Collection.END, "Crown of the End City", "popped_chorus_fruit", Source.CHEST, "end_city_treasure", 1 / 4.0,
		"Purpur and gold. Somebody up there had taste.", "In an end city's treasure."),
	SHULKER_PEARL(Collection.END, "The Shulker's Pearl", "shulker_shell", Source.KILL, "shulker", 1 / 30.0,
		"It floats. Upward. Forever. Keep a lid on it.", "Shulkers guard it."),
	TALL_ONES_EYE(Collection.END, "Eye of the Tall One", "ender_eye", Source.KILL, "enderman", 1 / 300.0,
		"Don't make eye contact. Especially with this.", "Endermen carry it, very rarely."),
	FIRST_CHORUS_SEED(Collection.END, "The First Chorus Seed", "chorus_fruit", Source.MINE, "chorus_flower", 1 / 30.0,
		"Every chorus plant in the End grew from this one. Allegedly.", "Break chorus flowers."),
	BOTTLED_ENDERMITE(Collection.END, "Endermite in a Bottle", "glass_bottle", Source.KILL, "endermite", 1 / 10.0,
		"It's still in there. It's still angry.", "Endermites, now and then."),
	VOID_SHARD(Collection.END, "The Void Shard", "amethyst_shard", Source.MINE, "end_stone", 1 / 2000.0,
		"A piece of nothing, very carefully kept.", "Hidden in end stone. Keep digging."),

	// Wonders of the Wild
	HEX_BOTTLE(Collection.WILD, "The Witch's Hex Bottle", "dragon_breath", Source.KILL, "witch", 1 / 30.0,
		"Smells like bad decisions.", "Witches drop it, now and then."),
	GOLDEN_BAMBOO(Collection.WILD, "The Golden Bamboo", "bamboo", Source.MINE, "bamboo", 1 / 1500.0,
		"Pandas would riot if they knew.", "Hidden in bamboo. A lot of bamboo."),
	SPIDER_SILK(Collection.WILD, "Silk of the Spider Queen", "cobweb", Source.KILL, "cave_spider", 1 / 80.0,
		"Stronger than steel. Stickier than honey.", "Cave spiders, rarely."),
	TRIAL_MASTER_KEY(Collection.WILD, "Key of the Trial Master", "trial_key", Source.CHEST, "trial_chambers", 1 / 8.0,
		"Opens a door nobody has found yet.", "In a trial chamber's chests."),
	EYE_OF_THE_STORM(Collection.WILD, "Eye of the Storm", "breeze_rod", Source.KILL, "breeze", 1 / 25.0,
		"It hums. It's the wind, trapped and furious.", "Breezes drop it, now and then."),
	SNIFFER_SEED(Collection.WILD, "The Sniffer's Lost Seed", "torchflower_seeds", Source.MINE, "suspicious_sand", 1 / 20.0,
		"Ancient. Fragrant. Possibly delicious.", "Break suspicious sand.");

	enum Collection {
		ROYAL("The Royal Collection", ChatFormatting.GOLD),
		DEEP("Treasures of the Deep", ChatFormatting.AQUA),
		UNDER("Relics of the Underworld", ChatFormatting.RED),
		ANCIENT("The Ancient World", ChatFormatting.GREEN),
		GOLDEN("The Golden Age", ChatFormatting.YELLOW),
		FROZEN("The Frozen North", ChatFormatting.BLUE),
		END("Curiosities of the End", ChatFormatting.DARK_PURPLE),
		WILD("Wonders of the Wild", ChatFormatting.DARK_GREEN);

		final String title;
		final ChatFormatting color;

		Collection(String title, ChatFormatting color) {
			this.title = title;
			this.color = color;
		}

		List<Relic> relics() {
			List<Relic> list = new ArrayList<>();
			for (Relic r : Relic.values()) {
				if (r.collection == this) {
					list.add(r);
				}
			}
			return list;
		}

		static @Nullable Collection byName(String name) {
			for (Collection c : values()) {
				if (c.name().equalsIgnoreCase(name)) {
					return c;
				}
			}
			return null;
		}
	}

	/** Where a relic turns up: killing a mob, mining a block, or opening a structure's loot chest for the first time. */
	enum Source { KILL, MINE, CHEST }

	final Collection collection;
	final String title;
	final String model;
	final Source source;
	/** Mob id, block id (path only), or a piece of the loot table's name. */
	final String what;
	final double chance;
	final String story;
	final String hint;

	Relic(Collection collection, String title, String model, Source source, String what, double chance, String story, String hint) {
		this.collection = collection;
		this.title = title;
		this.model = model;
		this.source = source;
		this.what = what;
		this.chance = chance;
		this.story = story;
		this.hint = hint;
	}

	String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	static @Nullable Relic byId(String id) {
		for (Relic r : values()) {
			if (r.id().equalsIgnoreCase(id)) {
				return r;
			}
		}
		return null;
	}
}
