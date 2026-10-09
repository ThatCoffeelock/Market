package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Every building you can buy, with its price, crew, housing and prefab design.
 *
 * Designs use local coordinates: x across, z front-to-back with the door at -z (facing whoever
 * placed it), y up with 0 being the floor, which replaces the ground layer. A building covers
 * x in [-half, half] and z in [-depth, depth]; most are square, walls are long and thin.
 *
 * Fortifications (walls, wall stairs, wall towers, gatehouses, watchtowers) don't use a building slot, may touch
 * each other and are built in the colony's stone of their tier: cobblestone, then stone bricks on a cobbled foot,
 * then dressed stone bricks with chiseled trim.
 */
public enum BuildingType {
	TOWN_HALL("town_hall", "Town Hall", Items.BELL, 2500, 5, 5, 17, new int[] {1, 1, 1}, new int[] {2, 3, 4}, 3, false, BuildingType::townHall),
	RESIDENCE("residence", "Residence", Items.OAK_DOOR, 400, 5, 5, 14, new int[] {0, 0, 0}, new int[] {4, 6, 8}, 3, false, BuildingType::residence),
	FARM("farm", "Farm", Items.WHEAT, 800, 6, 6, 8, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::farm),
	LUMBER_CAMP("lumber_camp", "Lumber Camp", Items.IRON_AXE, 900, 6, 6, 9, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::lumberCamp),
	MINE("mine", "Mine", Items.IRON_PICKAXE, 2500, 5, 5, 9, new int[] {3, 4, 5}, new int[] {0, 0, 0}, 3, false, BuildingType::mine),
	WORKSHOP("workshop", "Workshop", Items.CRAFTING_TABLE, 1500, 5, 5, 12, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::workshop),
	STOREHOUSE("storehouse", "Storehouse", Items.BARREL, 500, 5, 5, 14, new int[] {1, 1, 2}, new int[] {0, 0, 0}, 3, false, BuildingType::storehouse),
	BARRACKS("barracks", "Barracks", Items.IRON_SWORD, 2000, 5, 6, 10, new int[] {1, 2, 3}, new int[] {0, 0, 0}, 15, false, BuildingType::barracks),
	FISHERY("fishery", "Fishery", Items.FISHING_ROD, 1000, 5, 6, 9, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::fishery),
	TOBACCO_FARM("tobacco_farm", "Tobacco Farm", Items.FERN, 1200, 6, 6, 10, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::tobaccoFarm),
	HARBOR_OFFICE("harbor_office", "Harbor Office", Items.OAK_BOAT, 2500, 4, 8, 16, new int[] {1, 2, 3}, new int[] {0, 0, 0}, 4, false, BuildingType::harborOffice),
	TRAIN_STATION("train_station", "Train Station", Items.RAIL, 1500, 6, 5, 6, new int[] {1, 1, 1}, new int[] {0, 0, 0}, 3, false, BuildingType::trainStation),
	WATCHTOWER("watchtower", "Watchtower", Items.CROSSBOW, 1800, 3, 3, 16, new int[] {1, 2, 3}, new int[] {1, 2, 3}, 10, true, BuildingType::watchtower),
	WALL("wall", "Wall", Items.STONE_BRICK_WALL, 300, 4, 2, 8, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, true, BuildingType::wall),
	WALL_STAIRS("wall_stairs", "Wall Stairs", Items.COBBLESTONE_STAIRS, 350, 4, 2, 8, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, true, BuildingType::wallStairs),
	WALL_TOWER("wall_tower", "Wall Tower", Items.STONE_BRICKS, 800, 3, 3, 12, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, true, BuildingType::wallTower),
	GATEHOUSE("gatehouse", "Gatehouse", Items.SPRUCE_FENCE_GATE, 1200, 3, 3, 9, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, true, BuildingType::gatehouse),
	CELLBLOCK("cellblock", "Cellblock", Items.IRON_BARS, 2200, 5, 6, 18, new int[] {1, 1, 1}, new int[] {0, 0, 0}, 5, false, BuildingType::cellblock),
	SCAFFOLD("scaffold", "Scaffold", Items.WITHER_SKELETON_SKULL, 1200, 3, 3, 7, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, false, BuildingType::scaffold),
	TRADING_POST("trading_post", "Trading Post", Items.EMERALD, 2000, 5, 5, 12, new int[] {1, 2, 3}, new int[] {0, 0, 0}, 4, false, BuildingType::tradingPost),
	BANK("bank", "Bank", Items.GOLD_INGOT, 3000, 7, 7, 15, new int[] {1, 1, 2}, new int[] {0, 0, 0}, 4, false, BuildingType::bank),
	MUSEUM("museum", "Museum", Items.PAINTING, 2500, 7, 6, 15, new int[] {1, 1, 1}, new int[] {0, 0, 0}, 3, false, BuildingType::museum),
	CHAPEL("chapel", "Chapel", Items.LANTERN, 1500, 6, 7, 20, new int[] {1, 1, 1}, new int[] {0, 0, 0}, 3, false, BuildingType::chapel),
	LIBRARY("library", "Library", Items.ENCHANTING_TABLE, 1800, 5, 5, 12, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, false, BuildingType::library),
	RANCH("ranch", "Ranch", Items.LEATHER, 1000, 6, 6, 9, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::ranch),
	APIARY("apiary", "Apiary", Items.HONEYCOMB, 900, 5, 5, 8, new int[] {1, 2, 3}, new int[] {0, 0, 0}, 3, false, BuildingType::apiary),
	FUEL_DEPOT("fuel_depot", "Fuel Depot", Items.BLAST_FURNACE, 2000, 6, 5, 10, new int[] {1, 1, 2}, new int[] {0, 0, 0}, 3, false, BuildingType::fuelDepot);

	public static final int MAX_TIER = 3;

	public final String id;
	public final String displayName;
	public final Item icon;
	/** Price in ₥ of the blueprint (tier 1). */
	public final double price;
	/** Footprint is (2 * half + 1) across and (2 * depth + 1) front to back. */
	public final int half;
	public final int depth;
	public final int height;
	private final int[] workers;
	private final int[] housing;
	/** Wage in ₥ per crew member per day. */
	public final double wage;
	/** Walls, gatehouses and watchtowers: no building slot, may touch each other, snap together. */
	public final boolean fortification;
	private final Consumer<Plan> design;

	BuildingType(String id, String displayName, Item icon, double price, int half, int depth, int height, int[] workers, int[] housing,
		double wage, boolean fortification, Consumer<Plan> design) {
		this.id = id;
		this.displayName = displayName;
		this.icon = icon;
		this.price = price;
		this.half = half;
		this.depth = depth;
		this.height = height;
		this.workers = workers;
		this.housing = housing;
		this.wage = wage;
		this.fortification = fortification;
		this.design = design;
	}

	public int workers(int tier) {
		return workers[Math.max(1, Math.min(MAX_TIER, tier)) - 1];
	}

	public int housing(int tier) {
		return housing[Math.max(1, Math.min(MAX_TIER, tier)) - 1];
	}

	/**
	 * Fortifications are rebuilt in better stone when upgraded, the cellblock unbricks two more cells and the
	 * storehouse gets more storage racks.
	 */
	public boolean rebuildsOnUpgrade() {
		return fortification || this == CELLBLOCK || this == STOREHOUSE || this == RESIDENCE || this == TRAIN_STATION
			|| this == MUSEUM || this == FUEL_DEPOT;
	}

	/** The highest tier this building goes to. The library is as good as it gets from the start. */
	public int maxTier() {
		return this == LIBRARY ? 1 : MAX_TIER;
	}

	/**
	 * The footprint this type had before Colonycraft 1.3.0 made it bigger: {half, depth, height}. Buildings saved
	 * without a size of their own were built to it, and keep it until they're renovated.
	 */
	public int[] legacySize() {
		return switch (this) {
			case RESIDENCE -> new int[] {4, 4, 12};
			case FARM -> new int[] {4, 4, 4};
			case LUMBER_CAMP -> new int[] {4, 4, 7};
			case MINE -> new int[] {3, 3, 6};
			case WORKSHOP, STOREHOUSE -> new int[] {3, 3, 10};
			case BARRACKS -> new int[] {4, 4, 9};
			case FISHERY, TOBACCO_FARM -> new int[] {4, 4, 8};
			case HARBOR_OFFICE -> new int[] {4, 6, 15};
			case TRAIN_STATION -> new int[] {5, 3, 6};
			default -> new int[] {half, depth, height};
		};
	}

	/**
	 * Built at the water's edge: no complaint about water in the footprint, and the ground is only shored up under
	 * what the design puts down, so a pier doesn't fill in the harbor.
	 */
	public boolean waterfront() {
		return this == HARBOR_OFFICE || this == FISHERY;
	}

	/**
	 * Can it be bought here? The tobacco farm needs the Havana mod for its tobacco, the museum needs Riches for its
	 * display cases and the fuel depot needs Fossil Fool for its tanks and refinery.
	 */
	public boolean available() {
		return switch (this) {
			case TOBACCO_FARM -> HavanaLink.present();
			case MUSEUM -> RichesLink.present();
			case FUEL_DEPOT -> FossilLink.present();
			default -> true;
		};
	}

	/** Which mod it needs, when it isn't {@link #available()}. */
	public String needs() {
		return switch (this) {
			case MUSEUM -> "Riches";
			case FUEL_DEPOT -> "Fossil Fool";
			default -> "Havana";
		};
	}

	/** Iron golems don't sleep. Everyone else needs a bed somewhere in the colony. */
	public boolean needsBeds() {
		return this != BARRACKS;
	}

	/** "worker", "archer", "iron golem"... */
	public String crewNoun(int n) {
		String noun = switch (this) {
			case BARRACKS -> "iron golem";
			case WATCHTOWER -> "archer";
			case CELLBLOCK -> "jailer";
			case TRADING_POST -> "master trader";
			case BANK -> "clerk";
			case MUSEUM -> "curator";
			case CHAPEL -> "priest";
			case RANCH -> "rancher";
			case APIARY -> "beekeeper";
			default -> "worker";
		};
		return n == 1 ? noun : noun + "s";
	}

	/** Price in ₥ to go from this tier to the next. */
	public double upgradePrice(int currentTier) {
		return currentTier == 1 ? price * 1.5 : price * 3;
	}

	/** Half-size along the world x axis when turned this many quarter turns. */
	public int extentX(int quarter) {
		return Math.floorMod(quarter, 2) == 0 ? half : depth;
	}

	/** Half-size along the world z axis when turned this many quarter turns. */
	public int extentZ(int quarter) {
		return Math.floorMod(quarter, 2) == 0 ? depth : half;
	}

	/** Can another fortification be attached to this one along the world x axis (or else z)? */
	public boolean attachesAlongX(int quarter, boolean alongX) {
		if (half == depth) {
			return true; // towers take walls on every side
		}
		return (Math.floorMod(quarter, 2) == 0) == alongX; // walls and gates only at their ends
	}

	public static @Nullable BuildingType byId(String id) {
		for (BuildingType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}

	/** Where the crew stands when they're hired, and where they get sent back to. */
	public BlockPos home() {
		return switch (this) {
			case FARM -> new BlockPos(1, 1, 0);
			case LUMBER_CAMP -> new BlockPos(0, 1, 1);
			case RESIDENCE, MINE, WORKSHOP -> new BlockPos(0, 1, -1);
			case TRAIN_STATION -> new BlockPos(1, 1, -3);
			case BARRACKS, FISHERY -> new BlockPos(0, 1, -2);
			case HARBOR_OFFICE -> new BlockPos(0, 1, -3);
			case WALL, WALL_STAIRS -> new BlockPos(0, 6, 0);
			case SCAFFOLD -> SCAFFOLD_SPOT;
			case TRADING_POST -> TRADER_SPOTS[0];
			case BANK -> CLERKS[0];
			case MUSEUM -> new BlockPos(0, 1, -3);
			case CHAPEL -> new BlockPos(0, 1, 4);
			case RANCH -> new BlockPos(0, 1, -1);
			case APIARY, FUEL_DEPOT -> new BlockPos(0, 1, -2);
			default -> new BlockPos(0, 1, 0);
		};
	}

	/** Where the condemned stands on the scaffold, facing the crowd. */
	public static final BlockPos SCAFFOLD_SPOT = new BlockPos(0, 3, 0);

	/** The Town Hall's service counter (right-click to open the Town Hall). */
	public static final BlockPos COUNTER = new BlockPos(0, 1, 2);

	/** Watchtower archer posts: gaps in the parapet, front, back, then left. */
	private static final BlockPos[] POSTS = {new BlockPos(0, 10, -3), new BlockPos(0, 10, 3), new BlockPos(-3, 10, 0)};
	/** Which way each post looks out (local x, z). An archer covers the half of the world in front of it. */
	private static final BlockPos[] LOOKOUT = {new BlockPos(0, 0, -1), new BlockPos(0, 0, 1), new BlockPos(-1, 0, 0)};

	public static BlockPos post(int index) {
		return POSTS[Math.max(0, Math.min(POSTS.length - 1, index))];
	}

	public static BlockPos lookout(int index) {
		return LOOKOUT[Math.max(0, Math.min(LOOKOUT.length - 1, index))];
	}

	/**
	 * Cellblock cells, front to back: which side of the corridor they're on (-1 left, 1 right) and the
	 * first of their two rows. Each tier unbricks the next pair.
	 */
	private static final int[][] CELLS = {{-1, -2}, {1, -2}, {-1, 1}, {1, 1}, {-1, 4}, {1, 4}};

	public static final int MAX_CELLS = CELLS.length;

	/** How many cells a cellblock of this tier has open: 2, 4, then 6. */
	public static int cells(int tier) {
		return 2 * Math.max(1, Math.min(MAX_TIER, tier));
	}

	/** The cell's holding block (a vault in the corridor wall), where its prisoner is locked in and executed. */
	public static BlockPos holding(int cell) {
		int[] c = CELLS[cell];
		return new BlockPos(2 * c[0], 1, c[1]);
	}

	/** Where the prisoner stands: at the back of the cell, behind the bars, facing the corridor. */
	public static BlockPos cellSpot(int cell) {
		int[] c = CELLS[cell];
		return new BlockPos(3 * c[0], 1, c[1] + 1);
	}

	/** The storehouse's Warehouse Core (a cartography table against the back wall). */
	public static final BlockPos STORE_CORE = new BlockPos(0, 1, 2);
	/** The storehouse's Storage Racks, in the order the tiers add them: 2, then 6, then 12. Each touches the core or an earlier rack. */
	private static final BlockPos[] STORE_RACKS = {new BlockPos(-1, 1, 2), new BlockPos(1, 1, 2),
		new BlockPos(-2, 1, 2), new BlockPos(2, 1, 2), new BlockPos(-1, 2, 2), new BlockPos(1, 2, 2),
		new BlockPos(0, 2, 2), new BlockPos(-2, 2, 2), new BlockPos(2, 2, 2), new BlockPos(-2, 1, 1), new BlockPos(2, 1, 1), new BlockPos(0, 3, 2)};

	/** How many storage racks a storehouse of this tier has: 2, 6, then 12. */
	public static int racks(int tier) {
		return tier <= 1 ? 2 : tier == 2 ? 6 : 12;
	}

	public static BlockPos rack(int index) {
		return STORE_RACKS[index];
	}

	/** Where crew member number {@code index} belongs: archers at their post, everyone else at home. */
	public BlockPos station(int index) {
		return switch (this) {
			case WATCHTOWER -> post(index);
			case TRADING_POST -> TRADER_SPOTS[Math.max(0, Math.min(TRADER_SPOTS.length - 1, index))];
			case BANK -> CLERKS[Math.max(0, Math.min(CLERKS.length - 1, index))];
			default -> home();
		};
	}

	public Map<BlockPos, BlockState> plan(int tier) {
		Plan plan = new Plan(Math.max(1, Math.min(MAX_TIER, tier)));
		design.accept(plan);
		return plan.blocks;
	}

	// ---------------------------------------------------------------- building helper

	static final class Plan {
		final int tier;
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		Plan(int tier) {
			this.tier = tier;
		}

		void set(int x, int y, int z, String state) {
			blocks.put(new BlockPos(x, y, z), state(state));
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
			fillMix(x1, y1, z1, x2, y2, z2, state);
		}

		/** Like fill, but now and then one of the alternatives: a mossy stone, a cracked brick, a patch of podzol. */
		void fillMix(int x1, int y1, int z1, int x2, int y2, int z2, String base, String... alts) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
						set(x, y, z, mix(x, y, z, base, alts));
					}
				}
			}
		}

		/** The outline of a rectangle at one height. */
		void ring(int hx, int hz, int y, String state, String... alts) {
			for (int x = -hx; x <= hx; x++) {
				set(x, y, -hz, mix(x, y, -hz, state, alts));
				set(x, y, hz, mix(x, y, hz, state, alts));
			}
			for (int z = -hz; z <= hz; z++) {
				set(-hx, y, z, mix(-hx, y, z, state, alts));
				set(hx, y, z, mix(hx, y, z, state, alts));
			}
		}

		/** Four walls around a square of the given half-size, logs in the corners. */
		void walls(int h, int y1, int y2, String wall, String corner) {
			for (int y = y1; y <= y2; y++) {
				ring(h, h, y, wall);
				for (int[] c : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
					set(c[0], y, c[1], corner);
				}
			}
		}

		void door(int z, String wood) {
			set(0, 1, z, "minecraft:" + wood + "_door[facing=south,half=lower,hinge=left,open=false]");
			set(0, 2, z, "minecraft:" + wood + "_door[facing=south,half=upper,hinge=left,open=false]");
		}

		/** The outline of a rectangle from (x1, z1) to (x2, z2) at one height. */
		void rect(int x1, int z1, int x2, int z2, int y, String state) {
			for (int x = x1; x <= x2; x++) {
				set(x, y, z1, state);
				set(x, y, z2, state);
			}
			for (int z = z1; z <= z2; z++) {
				set(x1, y, z, state);
				set(x2, y, z, state);
			}
		}

		/** A ring of stairs around a rectangle, all climbing inwards. */
		void stairRect(int x1, int z1, int x2, int z2, int y, String stairs) {
			for (int x = x1; x <= x2; x++) {
				stairs(x, y, z1, stairs, "south", false);
				stairs(x, y, z2, stairs, "north", false);
			}
			for (int z = z1 + 1; z < z2; z++) {
				stairs(x1, y, z, stairs, "east", false);
				stairs(x2, y, z, stairs, "west", false);
			}
		}

		/**
		 * A mansard roof on walls outlining (x1, z1)-(x2, z2), starting at height y: a steep slate face two
		 * blocks high set one block in, then two shallow steps and a flat top. Returns the height of the top.
		 */
		int mansard(int x1, int z1, int x2, int z2, int y) {
			stairRect(x1, z1, x2, z2, y, SLATE);
			rect(x1 + 1, z1 + 1, x2 - 1, z2 - 1, y + 1, SLATE_BLOCK);
			rect(x1 + 1, z1 + 1, x2 - 1, z2 - 1, y + 2, SLATE_BLOCK);
			stairRect(x1 + 1, z1 + 1, x2 - 1, z2 - 1, y + 3, SLATE);
			stairRect(x1 + 2, z1 + 2, x2 - 2, z2 - 2, y + 4, SLATE);
			fill(x1 + 3, y + 5, z1 + 3, x2 - 3, y + 5, z2 - 3, SLATE_SLAB);
			return y + 5;
		}

		/** A dormer in the steep face of a mansard that runs along x (the front or the back). */
		void dormerX(int x, int z, int y) {
			set(x, y, z, "minecraft:glass_pane");
			set(x - 1, y, z, TRIM);
			set(x + 1, y, z, TRIM);
			set(x, y + 1, z, ACCENT);
		}

		/** A dormer in the steep face of a mansard that runs along z (a side). */
		void dormerZ(int x, int z, int y) {
			set(x, y, z, "minecraft:glass_pane");
			set(x, y, z - 1, TRIM);
			set(x, y, z + 1, TRIM);
			set(x, y + 1, z, ACCENT);
		}

		/**
		 * Sandstone quoins up a corner: every other course the stone also turns both corners
		 * (one block along each face, in the directions dx and dz), the courses between are single.
		 */
		void quoin(int x, int z, int dx, int dz, int y1, int y2) {
			for (int y = y1; y <= y2; y++) {
				set(x, y, z, TRIM);
				if (Math.floorMod(y, 2) == 0) {
					set(x + dx, y, z, TRIM);
					set(x, y, z + dz, TRIM);
				}
			}
		}

		/** A plain sandstone corner column (for narrow fronts, where full quoins would outnumber the bricks). */
		void corner(int x, int z, int y1, int y2) {
			fill(x, y1, z, x, y2, z, TRIM);
		}

		/** A window in a wall that runs along x: glass from x1 to x2, a sandstone lintel above (with a keystone if it's odd-sized). */
		void windowX(int z, int x1, int x2, int y1, int y2) {
			fill(x1, y1, z, x2, y2, z, "minecraft:glass_pane");
			fill(x1, y2 + 1, z, x2, y2 + 1, z, TRIM);
			if ((x2 - x1) % 2 == 0) {
				set((x1 + x2) / 2, y2 + 1, z, ACCENT);
			}
		}

		/** A window in a wall that runs along z. */
		void windowZ(int x, int z1, int z2, int y1, int y2) {
			fill(x, y1, z1, x, y2, z2, "minecraft:glass_pane");
			fill(x, y2 + 1, z1, x, y2 + 1, z2, TRIM);
			if ((z2 - z1) % 2 == 0) {
				set(x, y2 + 1, (z1 + z2) / 2, ACCENT);
			}
		}

		/** Stairs: "facing" is the side the tall back of the step is on; top = upside down. */
		void stairs(int x, int y, int z, String stairs, String facing, boolean top) {
			set(x, y, z, stairs + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]");
		}

		/** A hipped roof of stairs: rings shrinking from half-size {@code from} down to {@code to}, one step up each. */
		void hipRoof(int from, int to, int y, String stairs) {
			for (int r = from; r >= Math.max(1, to); r--) {
				int yy = y + from - r;
				for (int i = -r; i <= r; i++) {
					stairs(i, yy, -r, stairs, "south", false);
					stairs(i, yy, r, stairs, "north", false);
				}
				for (int i = -r; i <= r; i++) {
					stairs(-r, yy, i, stairs, "east", false);
					stairs(r, yy, i, stairs, "west", false);
				}
			}
		}

		/**
		 * A pitched roof over walls standing on x1..x2, z1..z2 (ridge along x), from height y, overhanging the walls by
		 * one block all round. The gable ends between the walls are filled with {@code gable} (if any), and the eaves
		 * get brackets of upside-down stairs at the corners. Returns the height of the ridge.
		 */
		int roofAlongX(int x1, int z1, int x2, int z2, int y, String stairs, String ridge, @Nullable String gable) {
			int a = z1 - 1;
			int b = z2 + 1;
			int k = 0;
			for (; a + k < b - k; k++) {
				for (int x = x1 - 1; x <= x2 + 1; x++) {
					stairs(x, y + k, a + k, stairs, "south", false);
					stairs(x, y + k, b - k, stairs, "north", false);
				}
				if (gable != null && a + k + 1 <= b - k - 1) {
					fill(x1, y + k, a + k + 1, x1, y + k, b - k - 1, gable);
					fill(x2, y + k, a + k + 1, x2, y + k, b - k - 1, gable);
				}
			}
			if (a + k == b - k) {
				fill(x1 - 1, y + k, a + k, x2 + 1, y + k, a + k, ridge);
			}
			for (int x : new int[] {x1, x2}) {
				stairs(x, y - 1, a, stairs, "south", true);
				stairs(x, y - 1, b, stairs, "north", true);
			}
			return y + k;
		}

		/** The same roof with the ridge along z (front to back): the gables face the front and the back. */
		int roofAlongZ(int x1, int z1, int x2, int z2, int y, String stairs, String ridge, @Nullable String gable) {
			int a = x1 - 1;
			int b = x2 + 1;
			int k = 0;
			for (; a + k < b - k; k++) {
				for (int z = z1 - 1; z <= z2 + 1; z++) {
					stairs(a + k, y + k, z, stairs, "east", false);
					stairs(b - k, y + k, z, stairs, "west", false);
				}
				if (gable != null && a + k + 1 <= b - k - 1) {
					fill(a + k + 1, y + k, z1, b - k - 1, y + k, z1, gable);
					fill(a + k + 1, y + k, z2, b - k - 1, y + k, z2, gable);
				}
			}
			if (a + k == b - k) {
				fill(a + k, y + k, z1 - 1, a + k, y + k, z2 + 1, ridge);
			}
			for (int z : new int[] {z1, z2}) {
				stairs(a, y - 1, z, stairs, "east", true);
				stairs(b, y - 1, z, stairs, "west", true);
			}
			return y + k;
		}

		/** Shutters (open trapdoors) either side of a window in a wall that runs along x, on the side facing -z or +z. */
		void shuttersX(int z, int x1, int x2, int y1, int y2, String wood, boolean front) {
			int out = front ? z - 1 : z + 1;
			String facing = front ? "north" : "south";
			for (int y = y1; y <= y2; y++) {
				set(x1 - 1, y, out, "minecraft:" + wood + "_trapdoor[facing=" + facing + ",half=bottom,open=true]");
				set(x2 + 1, y, out, "minecraft:" + wood + "_trapdoor[facing=" + facing + ",half=bottom,open=true]");
			}
		}

		/** A pitched roof whose ridge runs front to back (along z), with filled gable ends. */
		void gableAlongZ(int hx, int hz, int y, String stairs, String ridge, @Nullable String gable) {
			for (int k = 0; ; k++) {
				int r = hx - k;
				int yy = y + k;
				if (r <= 0) {
					fill(0, yy, -hz, 0, yy, hz, ridge);
					return;
				}
				for (int z = -hz; z <= hz; z++) {
					stairs(-r, yy, z, stairs, "east", false);
					stairs(r, yy, z, stairs, "west", false);
				}
				if (gable != null) {
					fill(-r + 1, yy, -hz, r - 1, yy, -hz, gable);
					fill(-r + 1, yy, hz, r - 1, yy, hz, gable);
				}
			}
		}

		/** A pitched roof whose ridge runs side to side (along x), with filled gable ends. */
		void gableAlongX(int hx, int hz, int y, String stairs, String ridge, @Nullable String gable) {
			for (int k = 0; ; k++) {
				int r = hz - k;
				int yy = y + k;
				if (r <= 0) {
					fill(-hx, yy, 0, hx, yy, 0, ridge);
					return;
				}
				for (int x = -hx; x <= hx; x++) {
					stairs(x, yy, -r, stairs, "south", false);
					stairs(x, yy, r, stairs, "north", false);
				}
				if (gable != null) {
					fill(-hx, yy, -r + 1, -hx, yy, r - 1, gable);
					fill(hx, yy, -r + 1, hx, yy, r - 1, gable);
				}
			}
		}
	}

	/** Picks the base block most of the time and one of the alternatives now and then, the same way every time. */
	static String mix(int x, int y, int z, String base, String... alts) {
		if (alts.length == 0) {
			return base;
		}
		int h = (x * 73_856_093) ^ (y * 19_349_663) ^ (z * 83_492_791);
		h = (h ^ (h >>> 13)) * 0x5bd1e995;
		h ^= h >>> 15;
		int roll = Math.floorMod(h, 100);
		return roll < 24 ? alts[roll % alts.length] : base;
	}

	private static final Map<String, BlockState> STATES = new HashMap<>();
	/** How many block states in the designs failed to parse (the smoke test wants zero). */
	static int badStates;

	/** Block state from text like "minecraft:oak_stairs[facing=east]". */
	static BlockState state(String text) {
		return STATES.computeIfAbsent(text, t -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, t, false).blockState();
			} catch (Exception e) {
				badStates++;
				ColonycraftMod.LOG.error("Bad block state in a building design: {}", t, e);
				return Blocks.OAK_PLANKS.defaultBlockState();
			}
		});
	}

	// ---------------------------------------------------------------- the designs

	// ---------------------------------------------------------------- the colony style
	// Dutch neo-renaissance: red brick dressed in cream sandstone (quoins on the corners, string courses
	// between the floors, lintels with keystones over the windows) under dark slate mansard roofs.

	private static final String BRICK = "minecraft:bricks";
	private static final String TRIM = "minecraft:cut_sandstone";
	private static final String BAND = "minecraft:smooth_sandstone";
	private static final String ACCENT = "minecraft:chiseled_sandstone";
	private static final String TRIM_STAIRS = "minecraft:sandstone_stairs";
	private static final String TRIM_CAP = "minecraft:smooth_sandstone_slab[type=bottom]";
	private static final String TRIM_SLAB_TOP = "minecraft:smooth_sandstone_slab[type=top]";
	private static final String RAIL = "minecraft:sandstone_wall";
	private static final String PLINTH = "minecraft:stone_bricks";
	private static final String SLATE = "minecraft:deepslate_tile_stairs";
	private static final String SLATE_BLOCK = "minecraft:deepslate_tiles";
	private static final String SLATE_SLAB = "minecraft:deepslate_tile_slab[type=bottom]";
	private static final String PAVING = "minecraft:polished_granite";

	/** The town hall: a three-storey brick mansion with a gabled centre bay, a portico, a mansard roof and two chimneys. */
	private static void townHall(Plan p) {
		// paving out front, a stone footing, dark oak floors
		p.fill(-5, 0, -5, 5, 0, 5, PAVING);
		p.fill(-5, 0, -3, 5, 0, 5, PLINTH);
		p.fill(-2, 0, -4, 2, 0, -4, PLINTH);
		p.fill(-4, 0, -2, 4, 0, 4, "minecraft:dark_oak_planks");
		p.fill(-1, 0, -3, 1, 0, -3, "minecraft:dark_oak_planks");
		// the main block and the centre bay, one block proud of the front
		for (int y = 1; y <= 10; y++) {
			p.rect(-5, -3, 5, 5, y, BRICK);
			p.fill(-2, y, -4, 2, y, -4, BRICK);
		}
		for (int[] course : new int[][] {{1, 0}, {5, 1}, {10, 2}}) {
			String band = course[1] == 0 ? PLINTH : course[1] == 1 ? BAND : TRIM;
			p.rect(-5, -3, 5, 5, course[0], band);
			p.fill(-2, course[0], -4, 2, course[0], -4, band);
		}
		p.fill(-1, 1, -3, 1, 9, -3, "minecraft:air"); // the bay is part of the hall
		for (int[] range : new int[][] {{2, 4}, {6, 9}}) {
			p.quoin(-5, -3, 1, 1, range[0], range[1]);
			p.quoin(5, -3, -1, 1, range[0], range[1]);
			p.quoin(-5, 5, 1, -1, range[0], range[1]);
			p.quoin(5, 5, -1, -1, range[0], range[1]);
			p.quoin(-2, -4, 1, 1, range[0], range[1]);
			p.quoin(2, -4, -1, 1, range[0], range[1]);
		}
		// windows: ground floor two high, first floor three high, each under a sandstone lintel
		for (int[] floor : new int[][] {{2, 3}, {6, 8}}) {
			int y1 = floor[0];
			int y2 = floor[1];
			p.windowX(-3, -4, -3, y1, y2);
			p.windowX(-3, 3, 4, y1, y2);
			for (int x : new int[] {-5, 5}) {
				p.windowZ(x, -1, 0, y1, y2);
				p.windowZ(x, 2, 3, y1, y2);
			}
		}
		p.windowX(5, 0, 0, 2, 3);
		p.windowX(5, -1, 1, 6, 8);
		// the bay: a door between sandstone pilasters, French doors above onto a little balcony
		p.fill(-1, 1, -4, -1, 4, -4, TRIM);
		p.fill(1, 1, -4, 1, 4, -4, TRIM);
		p.door(-4, "dark_oak");
		p.set(0, 3, -4, "minecraft:glass_pane");
		p.set(0, 4, -4, ACCENT);
		p.fill(-1, 6, -4, -1, 8, -4, "minecraft:glass_pane");
		p.fill(1, 6, -4, 1, 8, -4, "minecraft:glass_pane");
		p.set(0, 6, -4, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=false]");
		p.set(0, 7, -4, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false]");
		p.set(0, 8, -4, "minecraft:glass_pane");
		p.fill(-1, 9, -4, 1, 9, -4, TRIM);
		p.set(0, 9, -4, ACCENT);
		// portico: two white columns carrying the balcony, a lantern in between
		for (int x : new int[] {-1, 1}) {
			p.fill(x, 1, -5, x, 3, -5, "minecraft:quartz_pillar[axis=y]");
			p.set(x, 4, -5, "minecraft:chiseled_quartz_block");
		}
		p.fill(-1, 5, -5, 1, 5, -5, BAND);
		p.fill(-1, 6, -5, 1, 6, -5, RAIL);
		p.set(0, 4, -5, "minecraft:lantern[hanging=true]");
		p.set(0, 0, -5, "minecraft:smooth_quartz");
		// hedges either side of the portico
		for (int x = -5; x <= 5; x++) {
			if (Math.abs(x) >= 2) {
				p.set(x, 1, -5, mix(x, 1, -5, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
			}
		}
		for (int x : new int[] {-5, -4, -3, 3, 4, 5}) {
			p.set(x, 1, -4, mix(x, 1, -4, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		}
		// the mansard roof with dormers on every side
		p.mansard(-5, -3, 5, 5, 11);
		p.dormerX(-3, -2, 12);
		p.dormerX(3, -2, 12);
		p.dormerX(-2, 4, 12);
		p.dormerX(2, 4, 12);
		p.dormerZ(-4, 1, 12);
		p.dormerZ(4, 1, 12);
		// the gable over the bay: two windows, a round one, stepped shoulders, pinnacles and a spire
		p.fill(-2, 11, -3, 2, 13, -3, "minecraft:air");
		p.fill(-2, 11, -3, -2, 13, -3, BRICK);
		p.fill(2, 11, -3, 2, 13, -3, BRICK);
		p.fill(-2, 14, -3, 2, 14, -3, SLATE_SLAB);
		for (int y = 11; y <= 13; y++) {
			p.set(-2, y, -4, TRIM);
			p.set(2, y, -4, TRIM);
			p.set(0, y, -4, BRICK);
		}
		p.fill(-1, 11, -4, -1, 12, -4, "minecraft:glass_pane");
		p.fill(1, 11, -4, 1, 12, -4, "minecraft:glass_pane");
		p.set(-1, 13, -4, BRICK);
		p.set(1, 13, -4, BRICK);
		p.set(0, 13, -4, "minecraft:glass_pane");
		p.set(-2, 14, -4, RAIL);
		p.set(2, 14, -4, RAIL);
		p.stairs(-1, 14, -4, TRIM_STAIRS, "east", false);
		p.stairs(1, 14, -4, TRIM_STAIRS, "west", false);
		p.set(0, 14, -4, BRICK);
		p.set(0, 15, -4, ACCENT);
		p.set(0, 16, -4, RAIL);
		p.set(0, 17, -4, "minecraft:lightning_rod");
		// two chimneys, one of them smoking
		for (int x : new int[] {-4, 4}) {
			p.fill(x, 11, 4, x, 15, 4, BRICK);
			p.set(x, 16, 4, TRIM);
		}
		p.set(4, 17, 4, "minecraft:campfire[lit=true,facing=north]");
		p.set(-4, 17, 4, TRIM_CAP);
		// floors: the hall, the council chamber upstairs, the attic
		p.fill(-4, 5, -2, 4, 5, 4, "minecraft:dark_oak_planks");
		p.fill(-1, 5, -3, 1, 5, -3, "minecraft:dark_oak_planks");
		p.fill(-4, 10, -2, 4, 10, 4, "minecraft:dark_oak_planks");
		p.fill(-1, 10, -3, 1, 10, -3, "minecraft:dark_oak_planks");
		p.fill(4, 1, -2, 4, 10, -2, "minecraft:ladder[facing=west]");
		// the hall: red carpet to the counter, pews, books and banners behind the counter
		p.fill(-1, 1, -3, 1, 1, 1, "minecraft:red_carpet");
		for (int z = -1; z <= 1; z++) {
			p.stairs(-4, 1, z, "minecraft:dark_oak_stairs", "west", false);
			p.stairs(4, 1, z, "minecraft:dark_oak_stairs", "east", false);
		}
		p.set(-4, 1, -2, "minecraft:potted_fern");
		p.set(COUNTER.getX(), COUNTER.getY(), COUNTER.getZ(), "minecraft:lectern[facing=north]");
		p.set(-1, 1, 2, "minecraft:dark_oak_slab[type=top]");
		p.set(1, 1, 2, "minecraft:dark_oak_slab[type=top]");
		p.fill(-4, 1, 4, -2, 3, 4, "minecraft:bookshelf");
		p.fill(2, 1, 4, 4, 3, 4, "minecraft:bookshelf");
		p.set(-1, 3, 4, "minecraft:red_wall_banner[facing=north]");
		p.set(1, 3, 4, "minecraft:red_wall_banner[facing=north]");
		p.set(-2, 4, 0, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 0, "minecraft:lantern[hanging=true]");
		p.set(0, 4, 3, "minecraft:lantern[hanging=true]");
		// the council chamber: a long table with chairs, bookcases, lanterns
		p.fill(-2, 6, 1, 2, 6, 1, "minecraft:dark_oak_slab[type=top]");
		for (int x = -2; x <= 2; x++) {
			p.stairs(x, 6, 0, "minecraft:dark_oak_stairs", "north", false);
			p.stairs(x, 6, 2, "minecraft:dark_oak_stairs", "south", false);
		}
		p.fill(-4, 6, 4, -3, 8, 4, "minecraft:bookshelf");
		p.fill(3, 6, 4, 4, 8, 4, "minecraft:bookshelf");
		p.set(0, 9, 1, "minecraft:lantern[hanging=true]");
		p.set(-3, 9, 1, "minecraft:lantern[hanging=true]");
		p.set(3, 9, 1, "minecraft:lantern[hanging=true]");
	}

	/**
	 * The cellblock, after the Gevangenpoort in The Hague: a two-storey brick gaol between stepped gables.
	 * Downstairs a corridor runs between six cells, two per tier (the others stay bricked up), each closed by
	 * iron bars and its holding block. Upstairs is the jailer's office, with the confiscated banners.
	 */
	private static void cellblock(Plan p) {
		// paving out front, a stone footing, cobbled cells either side of a flagstone corridor
		p.fill(-5, 0, -6, 5, 0, 6, PAVING);
		p.fill(-5, 0, -5, 5, 0, 6, PLINTH);
		p.fillMix(-4, 0, -4, 4, 0, 5, "minecraft:cobblestone", "minecraft:mossy_cobblestone");
		p.fill(-1, 0, -4, 1, 0, 5, "minecraft:polished_andesite");
		p.fill(-4, 0, -4, 4, 0, -4, "minecraft:polished_andesite");
		// two storeys of brick: a stone plinth, a string course between the floors, a cornice on top
		for (int y = 1; y <= 9; y++) {
			p.rect(-5, -5, 5, 6, y, y == 1 ? PLINTH : y == 4 ? BAND : y == 9 ? TRIM : BRICK);
		}
		p.quoin(-5, -5, 1, 1, 2, 8);
		p.quoin(5, -5, -1, 1, 2, 8);
		p.quoin(-5, 6, 1, -1, 2, 8);
		p.quoin(5, 6, -1, -1, 2, 8);
		// the front: a door between sandstone pilasters under a barred fanlight, barred windows either side
		p.fill(-1, 1, -5, -1, 3, -5, TRIM);
		p.fill(1, 1, -5, 1, 3, -5, TRIM);
		p.door(-5, "dark_oak");
		p.set(0, 3, -5, "minecraft:iron_bars");
		p.set(0, 4, -5, ACCENT);
		for (int x : new int[] {-3, 3}) {
			p.fill(x, 2, -5, x, 3, -5, "minecraft:iron_bars");
		}
		// lamps by the door, hedges along the front
		for (int x : new int[] {-2, 2}) {
			p.set(x, 1, -6, RAIL);
			p.set(x, 2, -6, "minecraft:lantern[hanging=false]");
		}
		p.set(0, 0, -6, "minecraft:smooth_quartz");
		for (int x : new int[] {-5, -4, 4, 5}) {
			p.set(x, 1, -6, mix(x, 1, -6, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		}
		// the jailer's windows upstairs, each under a lintel
		p.windowX(-5, -4, -3, 6, 7);
		p.windowX(-5, 0, 0, 6, 7);
		p.windowX(-5, 3, 4, 6, 7);
		p.windowX(6, -1, 1, 6, 7);
		for (int x : new int[] {-5, 5}) {
			p.windowZ(x, -3, -2, 6, 7);
			p.windowZ(x, 2, 3, 6, 7);
		}
		// floors: the gaol, the office, the attic; a ladder up from the hall
		p.fill(-4, 4, -4, 4, 4, 5, "minecraft:dark_oak_planks");
		p.fill(-4, 9, -4, 4, 9, 5, "minecraft:dark_oak_planks");
		p.fill(4, 1, -4, 4, 5, -4, "minecraft:ladder[facing=west]");
		p.set(-4, 1, -4, "minecraft:potted_dead_bush");
		// the cells: brick walls between them, a cell front of bars and a holding block on the corridor side
		for (int side : new int[] {-1, 1}) {
			p.fill(2 * side, 1, -3, 2 * side, 3, 5, BRICK);
			for (int z : new int[] {-3, 0, 3}) {
				p.fill(2 * side, 1, z, 4 * side, 3, z, BRICK);
			}
		}
		for (int cell = 0; cell < MAX_CELLS; cell++) {
			if (cell >= cells(p.tier)) {
				continue; // bricked up until the next upgrade
			}
			int side = CELLS[cell][0];
			int z = CELLS[cell][1];
			BlockPos hold = holding(cell);
			p.set(hold.getX(), 1, z, "minecraft:vault[facing=" + (side < 0 ? "east" : "west") + "]");
			p.fill(hold.getX(), 2, z, hold.getX(), 3, z, "minecraft:iron_bars");
			p.fill(hold.getX(), 1, z + 1, hold.getX(), 3, z + 1, "minecraft:iron_bars");
			// a slit of daylight, a bucket in the corner and the obligatory cobweb
			p.set(5 * side, 3, z, "minecraft:iron_bars");
			p.set(5 * side, 3, z + 1, "minecraft:iron_bars");
			p.set(4 * side, 1, z, "minecraft:cauldron");
			p.set(4 * side, 3, z, "minecraft:cobweb");
		}
		// the corridor: lanterns, and the jailer's corner at the end
		p.set(0, 3, -2, "minecraft:lantern[hanging=true]");
		p.set(0, 3, 1, "minecraft:lantern[hanging=true]");
		p.set(0, 3, 4, "minecraft:lantern[hanging=true]");
		p.set(0, 1, 5, "minecraft:grindstone[face=floor,facing=north]");
		p.stairs(-1, 1, 5, "minecraft:dark_oak_stairs", "south", false);
		p.set(1, 1, 5, "minecraft:barrel[facing=up]");
		// the office: a desk with a chair, bookcases, the keys on a hook, confiscated captain's banners
		p.fill(-3, 5, 3, -1, 5, 3, "minecraft:dark_oak_slab[type=top]");
		p.stairs(-2, 5, 4, "minecraft:dark_oak_stairs", "south", false);
		p.fill(-4, 5, 5, -4, 7, 5, "minecraft:bookshelf");
		p.fill(3, 5, 5, 4, 7, 5, "minecraft:bookshelf");
		p.set(-3, 6, 5, "minecraft:tripwire_hook[facing=north]");
		p.set(-2, 7, 5, "minecraft:white_wall_banner[facing=north]");
		p.set(2, 7, 5, "minecraft:white_wall_banner[facing=north]");
		p.set(3, 5, 1, "minecraft:cartography_table");
		p.set(3, 5, 0, "minecraft:anvil[facing=north]");
		p.fill(-1, 5, -3, 1, 5, -1, "minecraft:gray_carpet");
		p.set(-2, 8, 0, "minecraft:lantern[hanging=true]");
		p.set(2, 8, 0, "minecraft:lantern[hanging=true]");
		// a slate roof between two stepped gables (the Dutch kind), windows and a pinnacle in the front one
		for (int k = 0; k <= 5; k++) {
			int r = 5 - k;
			int y = 10 + k;
			if (r == 0) {
				p.fill(0, y, -5, 0, y, 6, SLATE_SLAB);
				continue;
			}
			for (int z = -5; z <= 6; z++) {
				p.stairs(-r, y, z, SLATE, "east", false);
				p.stairs(r, y, z, SLATE, "west", false);
			}
		}
		for (int z : new int[] {-5, 6}) {
			for (int k = 0; k <= 5; k++) {
				p.fill(-5 + k, 10 + k, z, 5 - k, 10 + k, z, BRICK);
				p.set(-5 + k, 11 + k, z, TRIM_CAP);
				p.set(5 - k, 11 + k, z, TRIM_CAP);
			}
			p.set(0, 16, z, ACCENT);
			p.set(0, 17, z, RAIL);
		}
		p.set(-2, 11, -5, "minecraft:glass_pane");
		p.set(2, 11, -5, "minecraft:glass_pane");
		p.fill(0, 11, -5, 0, 12, -5, "minecraft:glass_pane");
		p.set(0, 13, -5, ACCENT);
		p.set(0, 14, -5, "minecraft:glass_pane");
		p.set(0, 12, 6, "minecraft:glass_pane");
		p.set(0, 18, -5, "minecraft:lightning_rod");
	}

	/**
	 * The scaffold (schavot) that stood on every Dutch market square: a stone-and-brick platform up a flight of
	 * steps, a dark oak railing, a gallows beam with the bell that tolls the countdown, and the headsman's block.
	 */
	private static void scaffold(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, PAVING);
		p.set(0, 0, -3, "minecraft:smooth_quartz");
		// the platform: a stone footing, a sandstone edge, planks on top, steps up the front
		p.fill(-2, 1, -2, 2, 1, 2, PLINTH);
		p.rect(-2, -2, 2, 2, 2, TRIM);
		p.fill(-1, 2, -1, 1, 2, 1, "minecraft:dark_oak_planks");
		for (int x = -1; x <= 1; x++) {
			p.stairs(x, 1, -3, TRIM_STAIRS, "south", false);
		}
		// a railing with a gap for the steps
		for (int x = -2; x <= 2; x++) {
			p.set(x, 3, 2, "minecraft:dark_oak_fence");
			if (x != 0) {
				p.set(x, 3, -2, "minecraft:dark_oak_fence");
			}
		}
		for (int z = -1; z <= 1; z++) {
			p.set(-2, 3, z, "minecraft:dark_oak_fence");
			p.set(2, 3, z, "minecraft:dark_oak_fence");
		}
		// the gallows beam at the back, with the bell, and the headsman's block in front of it
		p.fill(-2, 3, 2, -2, 6, 2, "minecraft:dark_oak_log[axis=y]");
		p.fill(2, 3, 2, 2, 6, 2, "minecraft:dark_oak_log[axis=y]");
		p.fill(-1, 6, 2, 1, 6, 2, "minecraft:dark_oak_log[axis=x]");
		p.set(0, 7, 2, TRIM_CAP);
		p.set(0, 5, 2, "minecraft:bell[attachment=ceiling,facing=north]");
		p.set(0, 3, 1, "minecraft:stripped_dark_oak_log[axis=x]");
		// lamps on the front corners, banner poles at the back
		for (int x : new int[] {-3, 3}) {
			p.set(x, 1, -3, RAIL);
			p.set(x, 2, -3, "minecraft:lantern[hanging=false]");
			p.fill(x, 1, 3, x, 4, 3, "minecraft:dark_oak_fence");
			p.set(x, 5, 3, "minecraft:lantern[hanging=false]");
		}
		for (int x : new int[] {-2, 2}) {
			p.set(x, 5, 1, "minecraft:red_wall_banner[facing=north]"); // on the gallows posts
		}
	}

	/**
	 * A two-storey brick townhouse: a stone plinth, a sandstone string course, quoins, shuttered windows with sills,
	 * a door between pilasters, and a front gable with an attic window under an overhanging slate roof. In front, a
	 * garden with a path, flower beds, a hedge and lamps. Downstairs the parlour and the kitchen by the chimney,
	 * upstairs the beds: 4, 6, then 8.
	 */
	private static void residence(Plan p) {
		// the front garden: a paved path, flower beds behind a hedge, lamps by the gate
		p.fill(-5, 0, -5, 5, 0, -4, "minecraft:grass_block");
		p.fill(-1, 0, -5, 1, 0, -4, PAVING);
		p.set(0, 0, -5, "minecraft:smooth_quartz");
		for (int x : new int[] {-5, -4, -3, 3, 4, 5}) {
			p.set(x, 1, -5, mix(x, 1, -5, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
			p.set(x, 0, -4, "minecraft:coarse_dirt");
			p.set(x, 1, -4, mix(x, 1, -4, "minecraft:red_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:allium"));
		}
		for (int x : new int[] {-2, 2}) {
			p.set(x, 1, -5, RAIL);
			p.set(x, 2, -5, "minecraft:lantern[hanging=false]");
		}
		// the house: a stone plinth, two storeys of brick with a string course between them, quoins on the corners
		p.fill(-4, 0, -3, 4, 0, 4, PLINTH);
		p.fill(-3, 0, -2, 3, 0, 3, "minecraft:spruce_planks");
		for (int y = 1; y <= 8; y++) {
			p.rect(-4, -3, 4, 4, y, y == 1 ? PLINTH : y == 5 ? BAND : BRICK);
		}
		p.corner(-4, -3, 2, 8);
		p.corner(4, -3, 2, 8);
		p.corner(-4, 4, 2, 8);
		p.corner(4, 4, 2, 8);
		// the front: a door between pilasters under a fanlight, shuttered windows with sandstone sills
		p.fill(-1, 1, -3, -1, 4, -3, TRIM);
		p.fill(1, 1, -3, 1, 4, -3, TRIM);
		p.door(-3, "dark_oak");
		p.set(0, 3, -3, "minecraft:glass_pane");
		p.set(0, 4, -3, ACCENT);
		for (int x : new int[] {-3, 3}) {
			p.windowX(-3, x, x, 2, 3);
			p.shuttersX(-3, x, x, 2, 3, "spruce", true);
			p.set(x, 1, -4, TRIM_SLAB_TOP);
		}
		for (int x : new int[] {-3, 0, 3}) {
			p.windowX(-3, x, x, 6, 7);
			p.shuttersX(-3, x, x, 6, 7, "spruce", true);
			p.set(x, 5, -4, TRIM_SLAB_TOP);
		}
		for (int x : new int[] {-4, 4}) {
			p.windowZ(x, 0, 1, 2, 3);
			p.windowZ(x, 0, 1, 6, 7);
		}
		p.windowX(4, -1, 1, 2, 3);
		p.windowX(4, -1, 1, 6, 7);
		// floors, the ladder up, the chimney breast at the back left
		p.fill(-3, 5, -2, 3, 5, 3, "minecraft:spruce_planks");
		p.fill(3, 1, 3, 3, 5, 3, "minecraft:ladder[facing=west]");
		p.fill(-3, 2, 3, -3, 8, 3, BRICK);
		// the slate roof, ridge front to back, overhanging all round; an attic window in the front gable
		p.roofAlongZ(-4, -3, 4, 4, 9, SLATE, SLATE_SLAB, BRICK);
		p.fill(-1, 10, -3, -1, 11, -3, TRIM);
		p.fill(1, 10, -3, 1, 11, -3, TRIM);
		p.fill(0, 10, -3, 0, 11, -3, "minecraft:glass_pane");
		p.set(0, 12, -3, ACCENT);
		p.set(0, 11, 4, "minecraft:glass_pane");
		p.fill(-3, 9, 3, -3, 13, 3, BRICK);
		p.set(-3, 14, 3, "minecraft:campfire[lit=true,facing=north]");
		// downstairs: the kitchen by the chimney, the parlour with a table and bookcases, a rug, lamps
		p.set(-3, 1, 3, "minecraft:smoker[facing=east,lit=false]");
		p.set(-2, 1, 3, "minecraft:barrel[facing=up]");
		p.set(-1, 1, 3, "minecraft:water_cauldron[level=3]");
		p.set(-3, 1, 2, "minecraft:crafting_table");
		p.set(2, 1, 1, "minecraft:spruce_fence");
		p.set(2, 2, 1, "minecraft:white_carpet");
		p.stairs(1, 1, 1, "minecraft:spruce_stairs", "west", false);
		p.stairs(3, 1, 1, "minecraft:spruce_stairs", "east", false);
		p.set(1, 1, 3, "minecraft:bookshelf");
		p.set(2, 1, 3, "minecraft:bookshelf");
		p.set(2, 2, 3, "minecraft:potted_fern");
		p.fill(-1, 1, -2, 1, 1, 0, "minecraft:brown_carpet");
		p.set(-2, 4, -1, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 1, "minecraft:lantern[hanging=true]");
		// upstairs: the beds, a bookcase with a lamp on it
		for (int i = 0; i < 2 + 2 * p.tier; i++) {
			int x = BEDS[i][0];
			int z = BEDS[i][1];
			int head = z + Integer.signum(z);
			String facing = z > 0 ? "south" : "north";
			p.set(x, 6, z, "minecraft:" + BED_COLOURS[i] + "_bed[facing=" + facing + ",part=foot]");
			p.set(x, 6, head, "minecraft:" + BED_COLOURS[i] + "_bed[facing=" + facing + ",part=head]");
		}
		p.set(0, 6, 3, "minecraft:bookshelf");
		p.set(0, 7, 3, "minecraft:lantern[hanging=false]");
	}

	/**
	 * A mixed farm behind a fence with log posts: four fields with water channels and lily pads (wheat, carrots,
	 * potatoes, beetroot), a cart track through the middle, a scarecrow, and a timber barn in the back corner with
	 * hay, the compost and a few pumpkins and melons. Lanterns on the corner posts.
	 */
	private static void farm(Plan p) {
		p.fillMix(-6, 0, -6, 6, 0, 6, "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:moss_block");
		p.fill(0, 0, -6, 0, 0, 6, "minecraft:dirt_path");
		p.fill(-6, 0, 0, 6, 0, 0, "minecraft:dirt_path");
		field(p, -5, -5, "minecraft:wheat[age=7]", "minecraft:carrots[age=7]");
		field(p, 1, -5, "minecraft:potatoes[age=7]", "minecraft:beetroots[age=3]");
		field(p, -5, 1, "minecraft:wheat[age=7]", "minecraft:wheat[age=7]");
		// the fence: log posts every three blocks, gates where the tracks come out, lanterns on the corners
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				if (Math.abs(x) != 6 && Math.abs(z) != 6) {
					continue;
				}
				boolean post = Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0;
				p.set(x, 1, z, post ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:dark_oak_fence");
			}
		}
		p.set(0, 1, -6, "minecraft:dark_oak_fence_gate[facing=south,open=false]");
		p.set(0, 1, 6, "minecraft:dark_oak_fence_gate[facing=south,open=false]");
		p.set(-6, 1, 0, "minecraft:dark_oak_fence_gate[facing=east,open=false]");
		p.set(6, 1, 0, "minecraft:dark_oak_fence_gate[facing=east,open=false]");
		for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			p.set(c[0], 2, c[1], "minecraft:stripped_dark_oak_log[axis=y]");
			p.set(c[0], 3, c[1], "minecraft:lantern[hanging=false]");
		}
		// a scarecrow in the wheat
		p.set(-4, 0, -3, "minecraft:coarse_dirt");
		p.set(-4, 1, -3, "minecraft:dark_oak_fence");
		p.set(-4, 2, -3, "minecraft:hay_block[axis=y]");
		p.set(-4, 3, -3, "minecraft:carved_pumpkin[facing=north]");
		p.set(-5, 2, -3, "minecraft:dark_oak_fence");
		// the barn: a spruce frame on a plank floor, open to the track, under an overhanging roof
		p.fill(1, 0, 1, 5, 0, 5, "minecraft:spruce_planks");
		for (int y = 1; y <= 3; y++) {
			p.fill(2, y, 5, 4, y, 5, "minecraft:spruce_planks");
			p.fill(5, y, 2, 5, y, 4, "minecraft:spruce_planks");
			p.fill(1, y, 4, 1, y, 4, "minecraft:spruce_planks");
			for (int[] c : new int[][] {{1, 1}, {5, 1}, {1, 5}, {5, 5}}) {
				p.set(c[0], y, c[1], "minecraft:stripped_spruce_log[axis=y]");
			}
		}
		p.fill(1, 3, 1, 5, 3, 1, "minecraft:stripped_spruce_log[axis=x]");
		p.fill(1, 3, 2, 1, 3, 3, "minecraft:stripped_spruce_log[axis=z]");
		p.roofAlongX(1, 1, 5, 5, 4, "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", "minecraft:spruce_planks");
		p.set(4, 1, 4, "minecraft:hay_block[axis=y]");
		p.set(4, 2, 4, "minecraft:hay_block[axis=y]");
		p.set(3, 1, 4, "minecraft:hay_block[axis=x]");
		p.set(4, 1, 3, "minecraft:hay_block[axis=z]");
		p.set(2, 1, 4, "minecraft:composter[level=5]");
		p.set(4, 1, 2, "minecraft:water_cauldron[level=3]");
		p.set(2, 1, 2, "minecraft:spruce_fence");
		p.set(2, 2, 2, "minecraft:lantern[hanging=false]");
		p.set(3, 1, 3, "minecraft:grindstone[face=floor,facing=north]");
		// gourds in the barn (not on the fields: farmland under a solid block turns back into dirt)
		p.set(2, 1, 3, "minecraft:pumpkin");
		p.set(3, 1, 2, "minecraft:melon");
	}

	/**
	 * A lumber camp in a clearing: an open timber sawmill with a stonecutter for a saw, stacked log piles, a campfire
	 * with log benches, a chopping stump, a nursery of potted saplings, two trees that never get chopped, and a
	 * spruce fence with log posts and lanterns round it all.
	 */
	private static void lumberCamp(Plan p) {
		p.fillMix(-6, 0, -6, 6, 0, 6, "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:moss_block");
		p.fill(0, 0, -6, 0, 0, 1, "minecraft:dirt_path");
		p.fill(-1, 0, 1, 1, 0, 1, "minecraft:dirt_path");
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				if (Math.abs(x) == 6 || Math.abs(z) == 6) {
					boolean post = Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0;
					p.set(x, 1, z, post ? "minecraft:spruce_log[axis=y]" : "minecraft:spruce_fence");
				}
			}
		}
		p.set(0, 1, -6, "minecraft:spruce_fence_gate[facing=south,open=false]");
		for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			p.set(c[0], 2, c[1], "minecraft:spruce_log[axis=y]");
			p.set(c[0], 3, c[1], "minecraft:lantern[hanging=false]");
		}
		// the sawmill, back left: an open spruce frame on a plank floor, walls at the back and the side
		p.fill(-5, 0, 1, -1, 0, 5, "minecraft:spruce_planks");
		for (int y = 1; y <= 4; y++) {
			p.fill(-4, y, 5, -2, y, 5, "minecraft:spruce_planks");
			p.fill(-5, y, 2, -5, y, 4, "minecraft:spruce_planks");
			for (int[] c : new int[][] {{-5, 1}, {-1, 1}, {-5, 5}, {-1, 5}}) {
				p.set(c[0], y, c[1], "minecraft:stripped_spruce_log[axis=y]");
			}
		}
		p.fill(-5, 4, 1, -1, 4, 1, "minecraft:stripped_spruce_log[axis=x]");
		p.fill(-1, 4, 2, -1, 4, 4, "minecraft:stripped_spruce_log[axis=z]");
		p.roofAlongX(-5, 1, -1, 5, 5, "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", "minecraft:spruce_planks");
		p.set(-3, 1, 3, "minecraft:stonecutter[facing=east]");
		p.fill(-4, 1, 3, -4, 1, 3, "minecraft:stripped_oak_log[axis=x]");
		p.set(-2, 1, 3, "minecraft:stripped_oak_log[axis=x]");
		p.set(-4, 1, 4, "minecraft:crafting_table");
		p.set(-2, 1, 4, "minecraft:grindstone[face=floor,facing=east]");
		p.set(-3, 2, 4, "minecraft:lantern[hanging=false]");
		p.set(-3, 1, 4, "minecraft:spruce_planks");
		// log piles, front right: stacked like a woodpile, logs of three kinds
		String[] logs = {"minecraft:spruce_log[axis=x]", "minecraft:oak_log[axis=x]", "minecraft:birch_log[axis=x]"};
		for (int z = -5; z <= -2; z++) {
			for (int x = 2; x <= 5; x++) {
				p.set(x, 1, z, logs[Math.floorMod(x + z, 3)]);
				if (z >= -4 && z <= -3) {
					p.set(x, 2, z, logs[Math.floorMod(x - z, 3)]);
				}
			}
		}
		p.fill(3, 3, -4, 4, 3, -4, "minecraft:spruce_log[axis=x]");
		p.fill(2, 1, -1, 2, 1, -1, "minecraft:stripped_spruce_log[axis=z]");
		// the campfire, front left, with log benches round it
		p.set(-3, 1, -3, "minecraft:campfire[lit=true,facing=north]");
		p.set(-3, 1, -5, "minecraft:stripped_spruce_log[axis=x]");
		p.set(-4, 1, -5, "minecraft:stripped_spruce_log[axis=x]");
		p.set(-5, 1, -3, "minecraft:stripped_spruce_log[axis=z]");
		p.set(-5, 1, -2, "minecraft:stripped_spruce_log[axis=z]");
		p.set(-1, 1, -3, "minecraft:stripped_spruce_log[axis=z]");
		// the chopping stump and the nursery
		p.set(2, 1, 1, "minecraft:oak_log[axis=y]");
		p.set(2, 2, 1, "minecraft:oak_pressure_plate");
		p.set(-5, 1, -1, "minecraft:potted_spruce_sapling");
		p.set(-4, 1, -1, "minecraft:potted_oak_sapling");
		p.set(-3, 1, -1, "minecraft:potted_birch_sapling");
		// two trees that never get chopped (the real ones are somewhere else, trust us)
		tree(p, 4, 4, "oak");
		p.set(1, 1, 4, "minecraft:birch_log[axis=y]");
		p.set(1, 2, 4, "minecraft:birch_pressure_plate");
	}

	private static void tree(Plan p, int x, int z, String wood) {
		String leaves = "minecraft:" + wood + "_leaves[persistent=true]";
		p.fill(x - 1, 3, z - 1, x + 1, 4, z + 1, leaves);
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			p.set(x + c[0], 4, z + c[1], "minecraft:air");
		}
		p.set(x - 1, 5, z, leaves);
		p.set(x + 1, 5, z, leaves);
		p.set(x, 5, z - 1, leaves);
		p.set(x, 5, z + 1, leaves);
		p.set(x, 6, z, leaves);
		p.fill(x, 1, z, x, 5, z, "minecraft:" + wood + "_log[axis=y]");
	}

	/**
	 * A mine: a grassy hill with rock showing through, a timbered portal three wide with frames down the tunnel and
	 * lanterns on the beams, ore in the rock, rails out to a yard with ore piles, a little stone smelting house with
	 * a blast furnace and a chimney, and lamp posts.
	 */
	private static void mine(Plan p) {
		p.fillMix(-5, 0, -5, 5, 0, 5, "minecraft:gravel", "minecraft:cobblestone", "minecraft:andesite", "minecraft:coarse_dirt");
		// the hill
		for (int x = -5; x <= 5; x++) {
			for (int z = 0; z <= 5; z++) {
				int top = Math.max(2, Math.min(3 + z, 7 - Math.max(0, Math.abs(x) - 1)));
				for (int y = 1; y < top; y++) {
					p.set(x, y, z, mix(x, y, z, "minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:tuff"));
				}
				p.set(x, top, z, mix(x, top, z, "minecraft:grass_block", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:stone"));
			}
		}
		// the tunnel: three wide, three high, timbered at the mouth and twice inside, dark at the back
		p.fill(-1, 1, 0, 1, 3, 4, "minecraft:air");
		p.fill(-1, 1, 5, 1, 3, 5, "minecraft:deepslate");
		for (int z : new int[] {0, 2, 4}) {
			p.fill(-2, 1, z, -2, 3, z, "minecraft:spruce_log[axis=y]");
			p.fill(2, 1, z, 2, 3, z, "minecraft:spruce_log[axis=y]");
			p.fill(-2, 4, z, 2, 4, z, "minecraft:spruce_log[axis=x]");
		}
		p.set(0, 3, 2, "minecraft:lantern[hanging=true]");
		p.set(-2, 3, -1, "minecraft:lantern[hanging=false]");
		p.set(2, 3, -1, "minecraft:lantern[hanging=false]");
		p.fill(-2, 1, -1, -2, 2, -1, "minecraft:spruce_fence");
		p.fill(2, 1, -1, 2, 2, -1, "minecraft:spruce_fence");
		p.fill(0, 1, -5, 0, 1, 4, "minecraft:rail[shape=north_south]");
		// ore showing in the rock
		p.set(-3, 2, 0, "minecraft:coal_ore");
		p.set(4, 2, 1, "minecraft:coal_ore");
		p.set(3, 1, 0, "minecraft:iron_ore");
		p.set(-4, 1, 1, "minecraft:copper_ore");
		p.set(-3, 3, 1, "minecraft:lapis_ore");
		p.set(3, 3, 2, "minecraft:redstone_ore");
		p.set(-3, 4, 3, "minecraft:gold_ore");
		p.set(3, 1, 0, "minecraft:iron_ore");
		// the yard: ore piles and a heap of spoil on the left
		p.set(-4, 1, -4, "minecraft:raw_iron_block");
		p.set(-3, 1, -4, "minecraft:coal_block");
		p.set(-4, 1, -3, "minecraft:raw_copper_block");
		p.set(-4, 2, -4, "minecraft:cobblestone_slab[type=bottom]");
		p.stairs(-3, 1, -3, "minecraft:cobblestone_stairs", "west", false);
		p.set(-2, 1, -4, "minecraft:gravel");
		// the smelting house on the right: stone bricks, a blast furnace, a chimney, a slab roof
		p.fill(2, 0, -5, 5, 0, -2, "minecraft:stone_bricks");
		for (int y = 1; y <= 3; y++) {
			p.rect(2, -5, 5, -2, y, y == 1 ? "minecraft:cobblestone" : "minecraft:stone_bricks");
		}
		p.fill(3, 1, -2, 4, 2, -2, "minecraft:air");
		p.set(3, 2, -5, "minecraft:iron_bars");
		p.set(4, 2, -5, "minecraft:iron_bars");
		p.fill(2, 4, -5, 5, 4, -2, "minecraft:stone_brick_slab[type=bottom]");
		p.set(4, 1, -4, "minecraft:blast_furnace[facing=north,lit=false]");
		p.set(3, 1, -4, "minecraft:anvil[facing=east]");
		p.fill(5, 4, -5, 5, 7, -5, "minecraft:bricks");
		p.set(5, 8, -5, "minecraft:campfire[lit=true,facing=north]");
		p.set(3, 3, -3, "minecraft:lantern[hanging=true]");
		for (int x : new int[] {-5, 5}) {
			p.fill(x, 1, -5, x, 2, -5, x < 0 ? "minecraft:cobblestone_wall" : "minecraft:bricks");
		}
		p.set(-5, 3, -5, "minecraft:lantern[hanging=false]");
	}

	/**
	 * A smithy: a brick forge hall with a sandstone arcade along the front under an overhanging slate roof, a big
	 * chimney over the forge, a tie beam with lanterns, the anvil and the benches inside; in the yard out front a
	 * second anvil, a quench barrel and stacks of coal and iron.
	 */
	private static void workshop(Plan p) {
		p.fillMix(-5, 0, -5, 5, 0, -1, "minecraft:cobblestone", "minecraft:gravel", "minecraft:andesite");
		p.fill(-5, 0, 0, 5, 0, 5, PLINTH);
		p.fill(-3, 0, 1, 3, 0, 3, "minecraft:dark_oak_planks");
		for (int y = 1; y <= 5; y++) {
			p.rect(-4, 0, 4, 4, y, y == 1 ? PLINTH : y == 5 ? TRIM : BRICK);
		}
		p.corner(-4, 0, 2, 4);
		p.corner(4, 0, 2, 4);
		p.corner(-4, 4, 2, 4);
		p.corner(4, 4, 2, 4);
		// the arcade: two wide arches either side of a sandstone pier
		for (int[] a : new int[][] {{-3, -1}, {1, 3}}) {
			p.fill(a[0], 1, 0, a[1], 3, 0, "minecraft:air");
			p.stairs(a[0], 3, 0, TRIM_STAIRS, "west", true);
			p.stairs(a[1], 3, 0, TRIM_STAIRS, "east", true);
			p.set((a[0] + a[1]) / 2, 4, 0, ACCENT);
		}
		p.fill(0, 1, 0, 0, 4, 0, TRIM);
		p.windowZ(-4, 2, 2, 2, 3);
		p.windowZ(4, 2, 2, 2, 3);
		// the roof, the tie beam with its lanterns, the chimney over the forge
		p.roofAlongX(-4, 0, 4, 4, 6, SLATE, SLATE_SLAB, BRICK);
		p.fill(-3, 5, 2, 3, 5, 2, "minecraft:dark_oak_log[axis=x]");
		p.set(-1, 4, 2, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 2, "minecraft:lantern[hanging=true]");
		p.fill(-3, 2, 3, -2, 11, 3, BRICK);
		p.set(-3, 12, 3, "minecraft:campfire[lit=true,facing=north]");
		p.set(-2, 12, 3, TRIM_CAP);
		// the forge under the chimney, the anvil and the benches
		p.set(-3, 1, 3, "minecraft:lava_cauldron");
		p.set(-2, 1, 3, "minecraft:blast_furnace[facing=north,lit=false]");
		p.set(-3, 1, 1, "minecraft:water_cauldron[level=3]");
		p.set(0, 1, 2, "minecraft:anvil[facing=north]");
		p.set(1, 1, 3, "minecraft:furnace[facing=north,lit=false]");
		p.set(2, 1, 3, "minecraft:smithing_table");
		p.set(3, 1, 3, "minecraft:crafting_table");
		p.set(3, 1, 2, "minecraft:grindstone[face=floor,facing=west]");
		p.set(3, 1, 1, "minecraft:stonecutter[facing=west]");
		// the yard
		p.set(3, 1, -3, "minecraft:chipped_anvil[facing=east]");
		p.set(4, 1, -4, "minecraft:water_cauldron[level=3]");
		p.set(-4, 1, -4, "minecraft:coal_block");
		p.set(-3, 1, -4, "minecraft:raw_iron_block");
		p.set(-4, 2, -4, "minecraft:coal_block");
		for (int x : new int[] {-5, 5}) {
			p.fill(x, 1, -5, x, 2, -5, "minecraft:brick_wall");
			p.set(x, 3, -5, "minecraft:lantern[hanging=false]");
		}
	}

	/**
	 * A Dutch merchant's warehouse: a tall brick front under a slate gable with a hayloft door and a hoist beam with
	 * a chain, double-height storage inside with the Warehouse Core and its Storage Racks as free-standing shelving,
	 * a loft with sacks and hay up a ladder, barrel portholes in the sides, and a paved yard with crates.
	 */
	private static void storehouse(Plan p) {
		p.fill(-5, 0, -5, 5, 0, -4, PAVING);
		p.set(0, 0, -5, "minecraft:smooth_quartz");
		p.fill(-4, 0, -3, 4, 0, 4, PLINTH);
		p.fill(-3, 0, -2, 3, 0, 3, "minecraft:spruce_planks");
		for (int y = 1; y <= 8; y++) {
			p.rect(-4, -3, 4, 4, y, y == 1 ? PLINTH : y == 5 ? BAND : BRICK);
		}
		p.corner(-4, -3, 2, 8);
		p.corner(4, -3, 2, 8);
		p.corner(-4, 4, 2, 8);
		p.corner(4, 4, 2, 8);
		// the front: the loading door under a fanlight, shuttered windows, the hayloft door and the hoist
		p.fill(-1, 1, -3, -1, 4, -3, TRIM);
		p.fill(1, 1, -3, 1, 4, -3, TRIM);
		p.door(-3, "spruce");
		p.set(0, 3, -3, "minecraft:glass_pane");
		p.set(0, 4, -3, ACCENT);
		for (int x : new int[] {-3, 3}) {
			p.windowX(-3, x, x, 2, 3);
			p.shuttersX(-3, x, x, 2, 3, "spruce", true);
		}
		p.set(0, 6, -3, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
		p.set(0, 7, -3, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
		p.set(0, 8, -3, TRIM);
		p.windowX(-3, -3, -3, 6, 7);
		p.windowX(-3, 3, 3, 6, 7);
		// barrel portholes: right-click any barrel in the storehouse to open it
		p.set(-4, 2, 0, "minecraft:barrel[facing=west]");
		p.set(4, 2, 0, "minecraft:barrel[facing=east]");
		p.set(-4, 3, 0, TRIM);
		p.set(4, 3, 0, TRIM);
		p.windowZ(-4, 2, 2, 6, 7);
		p.windowZ(4, 2, 2, 6, 7);
		// the roof, the gable with the hoist beam, chain and pulley lamp
		p.roofAlongZ(-4, -3, 4, 4, 9, SLATE, SLATE_SLAB, BRICK);
		p.set(0, 11, -4, "minecraft:stripped_dark_oak_log[axis=z]");
		p.set(0, 10, -4, "minecraft:iron_chain[axis=y]");
		p.set(0, 9, -4, "minecraft:lantern[hanging=true]");
		p.set(0, 12, -3, ACCENT);
		p.set(0, 11, 4, "minecraft:glass_pane");
		// inside: the warehouse core and its racks, a loft up a ladder, lamps under the loft
		p.set(STORE_CORE.getX(), STORE_CORE.getY(), STORE_CORE.getZ(), "minecraft:cartography_table");
		for (int i = 0; i < racks(p.tier); i++) {
			BlockPos r = STORE_RACKS[i];
			p.set(r.getX(), r.getY(), r.getZ(), "minecraft:barrel[facing=up]");
		}
		p.fill(-3, 5, -2, 3, 5, -1, "minecraft:spruce_planks");
		p.fill(-3, 5, 3, 3, 5, 3, "minecraft:spruce_planks");
		p.fill(3, 1, -2, 3, 5, -2, "minecraft:ladder[facing=west]");
		p.set(-3, 6, -2, "minecraft:white_wool");
		p.set(-2, 6, -2, "minecraft:white_wool");
		p.set(-3, 7, -2, "minecraft:white_carpet");
		p.set(-1, 6, -2, "minecraft:hay_block[axis=x]");
		p.set(-2, 4, -1, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 3, "minecraft:lantern[hanging=true]");
		p.set(-3, 1, -2, "minecraft:white_wool");
		// the yard: crates by the door, lamps
		p.set(3, 1, -4, "minecraft:spruce_planks");
		p.set(4, 1, -4, "minecraft:spruce_planks");
		p.set(3, 2, -4, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
		p.set(-3, 1, -4, "minecraft:hay_block[axis=x]");
		for (int x : new int[] {-2, 2}) {
			p.set(x, 1, -5, RAIL);
			p.set(x, 2, -5, "minecraft:lantern[hanging=false]");
		}
	}

	/**
	 * A barracks: a brick guardhouse with a golem-sized arch, a flat roof behind sandstone battlements and a corner
	 * turret with a flag; out front a drill yard behind a low wall, with training dummies, targets and a weapon rack.
	 */
	private static void barracks(Plan p) {
		p.fillMix(-5, 0, -6, 5, 0, -1, "minecraft:coarse_dirt", "minecraft:gravel");
		p.fill(-5, 0, 0, 5, 0, 6, PLINTH);
		// the guardhouse
		for (int y = 1; y <= 5; y++) {
			p.rect(-5, 0, 5, 6, y, y == 1 ? PLINTH : y == 5 ? TRIM : BRICK);
		}
		p.corner(-5, 0, 2, 4);
		p.corner(5, 0, 2, 4);
		p.corner(-5, 6, 2, 4);
		p.corner(5, 6, 2, 4);
		p.fill(-1, 1, 0, 1, 3, 0, "minecraft:air");
		p.stairs(-1, 3, 0, TRIM_STAIRS, "west", true);
		p.stairs(1, 3, 0, TRIM_STAIRS, "east", true);
		p.set(0, 4, 0, ACCENT);
		for (int x : new int[] {-3, 3}) {
			p.fill(x, 2, 0, x, 3, 0, "minecraft:iron_bars");
			p.set(x, 4, 0, TRIM);
		}
		p.set(-5, 3, 3, "minecraft:iron_bars");
		p.set(5, 3, 3, "minecraft:iron_bars");
		// the flat roof behind battlements, the corner turret and its flag
		p.fill(-4, 5, 1, 4, 5, 5, "minecraft:smooth_stone");
		for (int x = -5; x <= 5; x++) {
			for (int z = 0; z <= 6; z++) {
				boolean edge = Math.abs(x) == 5 || z == 0 || z == 6;
				if (edge && Math.floorMod(x + z, 2) == 0) {
					p.set(x, 6, z, TRIM);
				}
			}
		}
		p.fill(3, 6, 4, 5, 7, 6, BRICK);
		p.rect(3, 4, 5, 6, 8, TRIM);
		p.fill(4, 8, 5, 4, 10, 5, "minecraft:dark_oak_fence");
		p.fill(2, 9, 5, 3, 10, 5, "minecraft:red_wool");
		// the armoury inside
		p.set(-4, 1, 5, "minecraft:smithing_table");
		p.set(-3, 1, 5, "minecraft:grindstone[face=floor,facing=north]");
		p.set(-4, 1, 4, "minecraft:anvil[facing=east]");
		p.set(3, 1, 5, "minecraft:fletching_table");
		p.set(4, 1, 5, "minecraft:target");
		p.set(4, 1, 4, "minecraft:hay_block[axis=y]");
		p.set(4, 1, 1, "minecraft:barrel[facing=up]");
		p.fill(-1, 1, 1, 1, 1, 5, "minecraft:red_carpet");
		p.set(-1, 3, 5, "minecraft:red_wall_banner[facing=north]");
		p.set(1, 3, 5, "minecraft:red_wall_banner[facing=north]");
		p.set(-2, 4, 3, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 3, "minecraft:lantern[hanging=true]");
		// the drill yard: a low wall with gate piers, dummies, targets, a weapon rack
		for (int z = -6; z <= -1; z++) {
			p.set(-5, 1, z, "minecraft:stone_brick_wall");
			p.set(5, 1, z, "minecraft:stone_brick_wall");
		}
		for (int x = -5; x <= 5; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, -6, "minecraft:stone_brick_wall");
			}
		}
		for (int x : new int[] {-2, 2}) {
			p.fill(x, 1, -6, x, 2, -6, BRICK);
			p.set(x, 3, -6, TRIM);
			p.set(x, 4, -6, "minecraft:lantern[hanging=false]");
		}
		for (int x : new int[] {-3, 3}) {
			p.set(x, 1, -3, "minecraft:dark_oak_fence");
			p.set(x, 2, -3, "minecraft:hay_block[axis=y]");
			p.set(x, 3, -3, "minecraft:carved_pumpkin[facing=north]");
			p.set(x - 1, 2, -3, "minecraft:dark_oak_fence");
			p.set(x + 1, 2, -3, "minecraft:dark_oak_fence");
		}
		p.set(-4, 1, -5, "minecraft:hay_block[axis=y]");
		p.set(-4, 2, -5, "minecraft:target");
		p.set(4, 1, -5, "minecraft:hay_block[axis=y]");
		p.set(4, 2, -5, "minecraft:target");
		p.fill(-4, 1, -1, -3, 1, -1, "minecraft:dark_oak_fence");
		p.set(4, 1, -1, "minecraft:water_cauldron[level=3]");
	}


/** The residence's beds upstairs, two more each tier: (x, z) of the foot; the head is one further from the middle. */
	private static final int[][] BEDS = {{-3, 1}, {-1, 1}, {1, 1}, {3, 1}, {-3, -1}, {3, -1}, {-1, -1}, {1, -1}};
	private static final String[] BED_COLOURS = {"red", "blue", "green", "yellow", "white", "orange", "cyan", "brown"};





	/** One field from (x0, z0), five by five: two rows of each crop either side of a water channel with lily pads. */
	private static void field(Plan p, int x0, int z0, String left, String right) {
		p.fill(x0, 0, z0, x0 + 4, 0, z0 + 4, "minecraft:farmland[moisture=7]");
		p.fill(x0 + 2, 0, z0, x0 + 2, 0, z0 + 4, "minecraft:water");
		p.set(x0 + 2, 1, z0 + 1, "minecraft:lily_pad");
		p.set(x0 + 2, 1, z0 + 3, "minecraft:lily_pad");
		p.fill(x0, 1, z0, x0 + 1, 1, z0 + 4, left);
		p.fill(x0 + 3, 1, z0, x0 + 4, 1, z0 + 4, right);
	}

/** Where the harbor's Loading Dock stands: a lantern at the end of the pier. */
	public static final BlockPos HARBOR_DOCK = new BlockPos(0, 1, 8);



	/** The station's three tracks (local z), one per tier, and each track's Pickup and Drop-off Station barrels. */
	public static final int[] TRACKS = {-1, 1, 4};
	public static final BlockPos[] PICKUPS = {new BlockPos(-2, 1, -2), new BlockPos(-2, 1, 2), new BlockPos(-3, 1, 3)};
	public static final BlockPos[] DROP_OFFS = {new BlockPos(3, 1, -2), new BlockPos(3, 1, 2), new BlockPos(4, 1, 3)};

	/** How many tracks a train station of this tier has: 1, 2, then 3. */
	public static int tracks(int tier) {
		return Math.max(1, Math.min(MAX_TIER, tier));
	}

	// ---------------------------------------------------------------- new colony buildings

	/**
	 * A fishery: a timber fish shack on a stone footing under an overhanging spruce roof, with a cobblestone
	 * smokehouse chimney; out back a stone-edged basin split by a boardwalk to a lantern post, nets drying between
	 * posts, barrels of fish and lily pads. Build it by the water if you like the look; the fish don't mind.
	 */
	private static void fishery(Plan p) {
		p.fillMix(-5, 0, -6, 5, 0, 6, "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:gravel");
		// the basin, the boardwalk out to the lantern post
		p.fill(-5, 0, 0, 5, 0, 6, "minecraft:mud_bricks");
		p.fill(-4, 0, 1, 4, 0, 5, "minecraft:water");
		p.fill(0, 0, 0, 0, 0, 6, "minecraft:spruce_planks");
		p.set(-2, 1, 2, "minecraft:lily_pad");
		p.set(3, 1, 4, "minecraft:lily_pad");
		p.set(-3, 1, 5, "minecraft:lily_pad");
		p.set(0, 1, 6, "minecraft:spruce_fence");
		p.set(0, 2, 6, "minecraft:lantern[hanging=false]");
		for (int x : new int[] {-5, 5}) {
			for (int z : new int[] {1, 3, 5}) {
				p.fill(x, 1, z, x, 2, z, "minecraft:spruce_fence");
			}
			p.set(x, 2, 2, "minecraft:cobweb");
			p.set(x, 2, 4, "minecraft:cobweb");
		}
		// the shack: a stone footing, a stripped spruce frame with plank walls between
		p.fill(-4, 0, -5, 4, 0, -1, "minecraft:cobblestone");
		p.fill(-3, 0, -4, 3, 0, -2, "minecraft:spruce_planks");
		for (int y = 1; y <= 3; y++) {
			p.rect(-4, -5, 4, -1, y, "minecraft:spruce_planks");
			for (int x : new int[] {-4, 0, 4}) {
				p.set(x, y, -5, "minecraft:stripped_spruce_log[axis=y]");
				p.set(x, y, -1, "minecraft:stripped_spruce_log[axis=y]");
			}
		}
		p.fill(-4, 1, -5, 4, 1, -1, "minecraft:cobblestone");
		p.fill(-3, 1, -4, 3, 1, -2, "minecraft:air");
		p.fill(-4, 3, -5, 4, 3, -5, "minecraft:stripped_spruce_log[axis=x]");
		p.fill(-4, 3, -1, 4, 3, -1, "minecraft:stripped_spruce_log[axis=x]");
		p.door(-5, "spruce");
		p.set(0, 3, -5, "minecraft:spruce_planks");
		for (int x : new int[] {-2, 2}) {
			p.set(x, 2, -5, "minecraft:glass_pane");
			p.shuttersX(-5, x, x, 2, 2, "spruce", true);
		}
		p.set(-4, 2, -3, "minecraft:glass_pane");
		p.door(-1, "spruce");
		p.roofAlongX(-4, -5, 4, -1, 4, "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", "minecraft:spruce_planks");
		// the smokehouse chimney on the side
		p.fill(5, 1, -3, 5, 8, -3, "minecraft:cobblestone");
		p.set(5, 9, -3, "minecraft:campfire[lit=true,facing=north]");
		// inside: a fish barrel with a lamp, the smoker by the chimney, a wash tub, a net
		p.set(-3, 1, -2, "minecraft:barrel[facing=up]");
		p.set(-3, 2, -2, "minecraft:lantern[hanging=false]");
		p.set(3, 1, -3, "minecraft:smoker[facing=west,lit=false]");
		p.set(3, 1, -4, "minecraft:water_cauldron[level=3]");
		p.set(-3, 1, -4, "minecraft:crafting_table");
		// barrels of fish and kelp outside
		p.set(-4, 1, -6, "minecraft:barrel[facing=up]");
		p.set(-3, 1, -6, "minecraft:barrel[facing=up]");
		p.set(4, 1, -6, "minecraft:dried_kelp_block");
		p.set(-4, 2, -6, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
	}

	/**
	 * A tobacco farm: six rows of tall leafy plants on podzol behind a fence, a track to a tall curing barn of dark
	 * oak with louvred vents, wide doors, bales of leaves on the rafters and curing barrels inside.
	 */
	private static void tobaccoFarm(Plan p) {
		p.fillMix(-6, 0, -6, 6, 0, 6, "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:moss_block");
		for (int x = -5; x <= 5; x += 2) {
			for (int z = -5; z <= -2; z++) {
				p.set(x, 0, z, "minecraft:podzol");
				p.set(x, 1, z, "minecraft:large_fern[half=lower]");
				p.set(x, 2, z, "minecraft:large_fern[half=upper]");
			}
		}
		for (int x = -4; x <= 4; x += 2) {
			p.fill(x, 0, -5, x, 0, -2, "minecraft:coarse_dirt");
		}
		p.fill(-6, 0, -1, 6, 0, -1, "minecraft:dirt_path");
		p.fill(0, 0, -6, 0, 0, -1, "minecraft:dirt_path");
		for (int x = -6; x <= 6; x++) {
			p.set(x, 1, -6, x == 0 ? "minecraft:dark_oak_fence_gate[facing=south,open=false]"
				: Math.floorMod(x, 3) == 0 ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:dark_oak_fence");
		}
		for (int z = -5; z <= -2; z++) {
			p.set(-6, 1, z, z == -3 ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:dark_oak_fence");
			p.set(6, 1, z, z == -3 ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:dark_oak_fence");
		}
		p.set(-6, 2, -6, "minecraft:lantern[hanging=false]");
		p.set(6, 2, -6, "minecraft:lantern[hanging=false]");
		// the curing barn
		p.fill(-5, 0, 1, 5, 0, 5, "minecraft:dark_oak_planks");
		for (int y = 1; y <= 5; y++) {
			p.rect(-5, 1, 5, 5, y, y == 1 ? "minecraft:cobblestone" : "minecraft:dark_oak_planks");
			for (int[] c : new int[][] {{-5, 1}, {5, 1}, {-5, 5}, {5, 5}, {-2, 1}, {2, 1}}) {
				p.set(c[0], y, c[1], "minecraft:dark_oak_log[axis=y]");
			}
		}
		for (int x : new int[] {-4, -3, 3, 4}) {
			p.fill(x, 3, 1, x, 4, 1, "minecraft:dark_oak_fence");
			p.fill(x, 3, 5, x, 4, 5, "minecraft:dark_oak_fence");
		}
		for (int z : new int[] {2, 4}) {
			p.fill(-5, 3, z, -5, 4, z, "minecraft:dark_oak_fence");
			p.fill(5, 3, z, 5, 4, z, "minecraft:dark_oak_fence");
		}
		p.fill(-1, 1, 1, 1, 3, 1, "minecraft:air");
		p.set(-1, 1, 1, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=true]");
		p.set(-1, 2, 1, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=true]");
		p.set(1, 1, 1, "minecraft:dark_oak_door[facing=south,half=lower,hinge=right,open=true]");
		p.set(1, 2, 1, "minecraft:dark_oak_door[facing=south,half=upper,hinge=right,open=true]");
		p.fill(-1, 4, 1, 1, 4, 1, "minecraft:dark_oak_log[axis=x]");
		p.roofAlongX(-5, 1, 5, 5, 6, "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab[type=bottom]", "minecraft:dark_oak_planks");
		// inside: leaves drying on the rafters, curing barrels, the rolling bench, a lamp
		for (int x = -4; x <= 4; x += 2) {
			p.set(x, 5, 3, "minecraft:dark_oak_log[axis=z]");
			p.set(x, 4, 2, "minecraft:hay_block[axis=z]");
			p.set(x, 4, 4, "minecraft:hay_block[axis=z]");
		}
		p.set(-4, 1, 4, "minecraft:barrel[facing=up]");
		p.set(-4, 1, 3, "minecraft:barrel[facing=up]");
		p.set(4, 1, 4, "minecraft:barrel[facing=up]");
		p.set(4, 2, 4, "minecraft:lantern[hanging=false]");
		p.set(4, 1, 2, "minecraft:crafting_table");
	}


	/**
	 * The harbor office: a two-storey brick customs house with a projecting centre bay (the door, a balcony window
	 * and a clock gable), a mansard roof with dormers and a weathervane. Out of its back door runs a timber pier with
	 * log curbs, rails, bollards, a cargo crane with a chain, crates, a ship's bell and the Loading Dock lantern at
	 * the end. The pier is built over whatever is there, water included; the water either side of it stays.
	 */
	private static void harborOffice(Plan p) {
		p.fill(-4, 0, -7, 4, 0, -1, PLINTH);
		p.fill(-3, 0, -6, 3, 0, -2, "minecraft:dark_oak_planks");
		p.fill(-1, 0, -8, 1, 0, -8, PLINTH);
		for (int y = 1; y <= 9; y++) {
			String course = y == 1 ? PLINTH : y == 5 ? BAND : y == 9 ? TRIM : BRICK;
			p.rect(-4, -7, 4, -1, y, course);
			p.fill(-1, y, -8, 1, y, -8, course);
		}
		p.fill(0, 1, -7, 0, 8, -7, "minecraft:air"); // the bay is part of the hall
		for (int[] range : new int[][] {{2, 4}, {6, 8}}) {
			p.corner(-4, -7, range[0], range[1]);
			p.corner(4, -7, range[0], range[1]);
			p.corner(-4, -1, range[0], range[1]);
			p.corner(4, -1, range[0], range[1]);
		}
		// the bay: the door, a window above with a balcony, the clock gable
		p.door(-8, "dark_oak");
		p.set(0, 3, -8, "minecraft:glass_pane");
		p.set(0, 4, -8, ACCENT);
		p.fill(0, 6, -8, 0, 7, -8, "minecraft:glass_pane");
		p.set(0, 8, -8, ACCENT);
		p.set(0, 5, -7, "minecraft:dark_oak_planks");
		for (int y = 10; y <= 12; y++) {
			p.fill(-1 + (y - 10) / 2, y, -8, 1 - (y - 10) / 2, y, -8, BRICK);
		}
		p.set(0, 11, -8, "minecraft:chiseled_quartz_block");
		p.set(0, 13, -8, RAIL);
		// windows, two storeys, with shutters and sills
		for (int x : new int[] {-3, 3}) {
			p.windowX(-7, x, x, 2, 3);
			p.shuttersX(-7, x, x, 2, 3, "dark_oak", true);
			p.windowX(-7, x, x, 6, 7);
			p.shuttersX(-7, x, x, 6, 7, "dark_oak", true);
			p.set(x, 5, -8, TRIM_SLAB_TOP);
		}
		for (int x : new int[] {-4, 4}) {
			p.windowZ(x, -5, -4, 2, 3);
			p.windowZ(x, -5, -4, 6, 7);
		}
		p.windowX(-1, -3, -2, 6, 7);
		p.windowX(-1, 2, 3, 6, 7);
		p.door(-1, "dark_oak");
		p.set(0, 3, -1, "minecraft:glass_pane");
		for (int x : new int[] {-3, 3}) {
			p.set(x, 1, -8, RAIL);
			p.set(x, 2, -8, "minecraft:lantern[hanging=false]");
		}
		p.set(-4, 1, -8, mix(-4, 1, -8, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		p.set(4, 1, -8, mix(4, 1, -8, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		// floors, the ladder, the mansard with dormers and a weathervane
		p.fill(-3, 5, -6, 3, 5, -2, "minecraft:dark_oak_planks");
		p.fill(-3, 9, -6, 3, 9, -2, "minecraft:dark_oak_planks");
		p.fill(3, 1, -2, 3, 5, -2, "minecraft:ladder[facing=west]");
		int top = p.mansard(-4, -7, 4, -1, 10);
		p.dormerX(-2, -6, 11);
		p.dormerX(2, -6, 11);
		p.dormerX(0, -2, 11);
		p.dormerZ(-3, -4, 11);
		p.dormerZ(3, -4, 11);
		p.set(0, top + 1, -4, "minecraft:lightning_rod");
		// the customs hall: a long counter, the harbor master's desk upstairs with charts and a lamp
		p.fill(-3, 1, -6, -3, 1, -3, "minecraft:dark_oak_slab[type=top]");
		p.set(2, 1, -5, "minecraft:lectern[facing=west]");
		p.fill(-1, 1, -6, 1, 1, -3, "minecraft:blue_carpet");
		p.set(3, 1, -6, "minecraft:barrel[facing=up]");
		p.set(-2, 4, -4, "minecraft:lantern[hanging=true]");
		p.set(2, 4, -4, "minecraft:lantern[hanging=true]");
		p.fill(-3, 6, -6, -3, 8, -6, "minecraft:bookshelf");
		p.fill(-3, 6, -2, -3, 7, -2, "minecraft:bookshelf");
		p.fill(-1, 6, -6, 1, 6, -6, "minecraft:dark_oak_slab[type=top]");
		p.stairs(0, 6, -5, "minecraft:dark_oak_stairs", "south", false);
		p.set(1, 7, -6, "minecraft:lantern[hanging=false]");
		p.set(-2, 8, -4, "minecraft:lantern[hanging=true]");
		// the pier
		p.fill(-2, 0, 0, 2, 0, 8, "minecraft:spruce_planks");
		p.fill(-3, 0, 0, -3, 0, 8, "minecraft:stripped_spruce_log[axis=z]");
		p.fill(3, 0, 0, 3, 0, 8, "minecraft:stripped_spruce_log[axis=z]");
		for (int z = 1; z <= 7; z += 2) {
			p.set(-3, 1, z, "minecraft:spruce_fence");
			p.set(3, 1, z, "minecraft:spruce_fence");
		}
		p.set(-3, 1, 8, "minecraft:spruce_log[axis=y]");
		p.set(3, 1, 8, "minecraft:spruce_log[axis=y]");
		// the crane: a post, a jib, a chain with a hook lamp over the water
		p.fill(3, 1, 4, 3, 5, 4, "minecraft:spruce_log[axis=y]");
		p.fill(1, 6, 4, 3, 6, 4, "minecraft:stripped_spruce_log[axis=x]");
		p.stairs(2, 5, 4, "minecraft:spruce_stairs", "east", true);
		p.fill(1, 4, 4, 1, 5, 4, "minecraft:iron_chain[axis=y]");
		p.set(1, 3, 4, "minecraft:lantern[hanging=true]");
		// crates, the bell, the Loading Dock
		p.set(-2, 1, 1, "minecraft:barrel[facing=up]");
		p.set(-2, 2, 1, "minecraft:barrel[facing=up]");
		p.set(-1, 1, 1, "minecraft:barrel[facing=up]");
		p.set(2, 1, 1, "minecraft:spruce_planks");
		p.set(2, 2, 1, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
		p.set(-2, 1, 7, "minecraft:bell[attachment=floor,facing=east]");
		p.set(HARBOR_DOCK.getX(), HARBOR_DOCK.getY(), HARBOR_DOCK.getZ(), "minecraft:lantern[hanging=false]");
	}


	/**
	 * A train station: a brick ticket office with a waiting room, a platform with benches and lamps under a slate
	 * canopy on timber posts, and up to three straight tracks through the building from end to end (lay your lines on
	 * from both ends): one at tier 1, a second along an island platform at tier 2, a third behind it at tier 3. Every
	 * track has its own Pickup Station and Drop-off Station barrel next to it.
	 */
	private static void trainStation(Plan p) {
		int tracks = tracks(p.tier);
		p.fill(-6, 0, -5, 6, 0, 5, PAVING);
		p.fill(-6, 0, -2, 6, 0, -2, "minecraft:smooth_stone");
		p.fill(-6, 0, 2, 6, 0, 3, "minecraft:smooth_stone");
		for (int t = 0; t < 3; t++) {
			int z = TRACKS[t];
			p.fill(-6, 0, z, 6, 0, z, "minecraft:gravel");
			if (t < tracks) {
				p.fill(-6, 1, z, 6, 1, z, "minecraft:rail[shape=east_west]");
			} else {
				p.fill(-6, 0, z, 6, 0, z, "minecraft:grass_block");
				for (int x = -6; x <= 6; x += 2) {
					p.set(x, 1, z, mix(x, 1, z, "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion"));
				}
			}
		}
		p.fill(-6, 0, 0, 6, 0, 0, "minecraft:gravel");
		p.fill(-6, 0, 5, 6, 0, 5, PLINTH);
		// the ticket office, front left: brick under a hipped slate roof
		for (int y = 1; y <= 4; y++) {
			p.rect(-6, -4, -3, -2, y, y == 1 ? PLINTH : y == 4 ? TRIM : BRICK);
		}
		p.set(-3, 1, -3, "minecraft:dark_oak_door[facing=west,half=lower,hinge=left,open=false]");
		p.set(-3, 2, -3, "minecraft:dark_oak_door[facing=west,half=upper,hinge=left,open=false]");
		p.windowX(-4, -5, -4, 2, 3);
		p.windowZ(-6, -3, -3, 2, 3);
		p.set(-5, 1, -3, "minecraft:lectern[facing=east]");
		p.stairRect(-6, -4, -3, -2, 5, SLATE);
		p.fill(-5, 5, -3, -4, 5, -3, SLATE_BLOCK);
		p.fill(-5, 6, -3, -4, 6, -3, SLATE_SLAB);
		p.set(-4, 4, -3, "minecraft:lantern[hanging=true]");
		// the canopy over the platform and the tracks: timber posts, a slate roof, lamps
		int[] posts = {-1, 2, 5};
		int[] postRows = {-3, 0, 3};
		for (int x : posts) {
			for (int z : postRows) {
				p.fill(x, 1, z, x, 4, z, "minecraft:dark_oak_fence");
			}
		}
		p.fill(-2, 5, -4, 6, 5, 4, SLATE_SLAB);
		p.fill(-2, 5, -4, 6, 5, -4, "minecraft:deepslate_tile_stairs[facing=south,half=bottom]");
		p.fill(-2, 4, -3, 6, 4, -3, "minecraft:dark_oak_log[axis=x]");
		p.fill(-2, 4, 0, 6, 4, 0, "minecraft:dark_oak_log[axis=x]");
		p.fill(-2, 4, 3, 6, 4, 3, "minecraft:dark_oak_log[axis=x]");
		for (int x : new int[] {0, 4}) {
			p.set(x, 3, -3, "minecraft:lantern[hanging=true]");
			p.set(x, 3, 0, "minecraft:lantern[hanging=true]");
		}
		// benches on the platform, a clock on the office
		p.stairs(0, 1, -4, "minecraft:dark_oak_stairs", "north", false);
		p.stairs(1, 1, -4, "minecraft:dark_oak_stairs", "north", false);
		p.stairs(3, 1, -4, "minecraft:dark_oak_stairs", "north", false);
		p.stairs(4, 1, -4, "minecraft:dark_oak_stairs", "north", false);
		p.set(6, 1, -5, mix(6, 1, -5, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		p.set(-2, 1, -5, mix(-2, 1, -5, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		// a hedge along the back
		for (int x = -6; x <= 6; x++) {
			p.set(x, 1, 5, mix(x, 1, 5, "minecraft:azalea_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
		}
		// the station barrels, one pair per track (they get their names when the station opens)
		for (int t = 0; t < tracks; t++) {
			p.set(PICKUPS[t].getX(), PICKUPS[t].getY(), PICKUPS[t].getZ(), "minecraft:barrel[facing=up]");
			p.set(DROP_OFFS[t].getX(), DROP_OFFS[t].getY(), DROP_OFFS[t].getZ(), "minecraft:barrel[facing=up]");
		}
	}

	// ---------------------------------------------------------------- fortifications
	// Generic medieval curtain walls: a rubble footing, coursed stone above, dressed quoins at the joints and a
	// walkway behind a crenellated parapet. Tier 1 is cobblestone, tier 2 stone bricks on a cobbled foot, tier 3
	// dressed stone bricks with chiseled trim. Every walkway floor is at y = WALK, so walls, gates and towers join up.

	static final int WALK = 5;

	/** The stone a fortification of a given tier is built from. */
	record Palette(String body, String[] weathered, String foot, String[] footWeathered, String trim, String trimStairs, String trimSlab,
		String stairs, String wall, String floor, String wood) {
		static Palette of(int tier) {
			String[] rubble = {"minecraft:mossy_cobblestone"};
			return switch (tier) {
				case 1 -> new Palette("minecraft:cobblestone", new String[] {"minecraft:mossy_cobblestone", "minecraft:andesite"},
					"minecraft:cobblestone", rubble, "minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
					"minecraft:cobblestone_stairs", "minecraft:cobblestone_wall", "minecraft:cobblestone", "spruce");
				case 2 -> new Palette("minecraft:stone_bricks", new String[] {"minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"},
					"minecraft:cobblestone", rubble, "minecraft:polished_andesite", "minecraft:polished_andesite_stairs",
					"minecraft:polished_andesite_slab", "minecraft:stone_brick_stairs", "minecraft:stone_brick_wall", "minecraft:stone_bricks", "spruce");
				default -> new Palette("minecraft:stone_bricks", new String[] {"minecraft:cracked_stone_bricks"},
					"minecraft:cobblestone", rubble, "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
					"minecraft:stone_brick_stairs", "minecraft:stone_brick_wall", "minecraft:polished_andesite", "dark_oak");
			};
		}

		String stone(int x, int y, int z) {
			return mix(x, y, z, body, weathered);
		}

		/** The bottom two courses: rubble. */
		String footing(int x, int y, int z) {
			return mix(x, y, z, foot, footWeathered);
		}

		String log() {
			return "minecraft:" + wood + "_log[axis=y]";
		}
	}

	/**
	 * The solid body every wall segment shares: nine blocks long and five thick. Outside, rubble footings, quoins
	 * up both joints, a corbel table under the parapet and merlons (the joint ones in dressed stone, so long runs
	 * read as bays). On top, a three-wide walkway with a low railing along the inside.
	 */
	private static void curtain(Plan p, Palette s) {
		p.fillMix(-4, 0, -2, 4, 0, 2, s.foot, s.footWeathered);
		for (int x = -4; x <= 4; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 1; y <= WALK; y++) {
					p.set(x, y, z, y <= 2 ? s.footing(x, y, z) : s.stone(x, y, z));
				}
			}
		}
		p.fill(-4, WALK, -1, 4, WALK, 1, s.floor);
		for (int x : new int[] {-4, 4}) {
			p.fill(x, 1, 2, x, 7, 2, s.trim);
		}
		for (int x = -3; x <= 3; x++) {
			p.stairs(x, WALK, 2, s.trimStairs, "north", true);
			p.set(x, 6, 2, s.stone(x, 6, 2));
			if (x % 2 == 0) {
				p.set(x, 7, 2, s.stone(x, 7, 2));
			}
		}
		if (p.tier >= 3) {
			for (int x = -4; x <= 4; x += 2) {
				p.set(x, 8, 2, s.trimSlab + "[type=bottom]");
			}
		}
		p.fill(-4, 6, -2, 4, 6, -2, s.wall);
		if (p.tier >= 2) {
			p.set(0, 7, -2, "minecraft:lantern[hanging=false]");
		}
	}

	/** A buttress up the inside face. */
	private static void innerPier(Plan p, Palette s, int x) {
		p.fill(x, 1, -2, x, WALK, -2, s.trim);
	}

	/** A blind arch in the inside face, from x1 to x2 (two or three wide), with a torch in the three-wide ones. */
	private static void arch(Plan p, Palette s, int x1, int x2) {
		p.fill(x1, 1, -2, x2, 2, -2, "minecraft:air");
		p.stairs(x1, 3, -2, s.stairs, "west", true);
		p.stairs(x2, 3, -2, s.stairs, "east", true);
		if (x2 - x1 == 2) {
			int mid = (x1 + x2) / 2;
			p.set(mid, 3, -2, "minecraft:air");
			p.set(mid, 2, -2, "minecraft:wall_torch[facing=north]");
		}
	}

	/**
	 * Nine blocks of curtain wall. The side facing whoever places it is the inside: two blind arches between
	 * buttresses. Segments tile end to end, the buttresses at the joints pairing up with the next segment's.
	 */
	private static void wall(Plan p) {
		Palette s = Palette.of(p.tier);
		curtain(p, s);
		innerPier(p, s, -4);
		innerPier(p, s, 0);
		innerPier(p, s, 4);
		arch(p, s, -3, -1);
		arch(p, s, 1, 3);
	}

	/** A wall segment with a flight of steps up the inside face to the walkway. */
	private static void wallStairs(Plan p) {
		Palette s = Palette.of(p.tier);
		curtain(p, s);
		innerPier(p, s, -4);
		innerPier(p, s, 4);
		for (int i = 0; i < 4; i++) {
			int x = -3 + i;
			int y = 1 + i;
			p.stairs(x, y, -2, s.stairs, "east", false);
			p.fill(x, y + 1, -2, x, y + 2, -2, "minecraft:air");
		}
		p.set(1, 6, -2, "minecraft:air");
		p.set(0, 7, -2, "minecraft:air");
		arch(p, s, 2, 3);
	}

	/**
	 * The body of a tower: one block thick, quoins on the corners, a string course at walkway height, a door at the
	 * front, arrow slits, floors at walkway height and at the top, openings on all four sides where walls join, a
	 * ladder all the way up.
	 */
	private static void tower(Plan p, Palette s) {
		p.fillMix(-3, 0, -3, 3, 0, 3, s.foot, s.footWeathered);
		for (int y = 1; y <= 9; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (Math.abs(x) == 3 || Math.abs(z) == 3) {
						p.set(x, y, z, y <= 2 ? s.footing(x, y, z) : s.stone(x, y, z));
					}
				}
			}
			for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
				p.set(c[0], y, c[1], s.trim);
			}
		}
		p.ring(3, 3, WALK, s.trim);
		p.door(-3, s.wood);
		p.set(0, 3, -3, s.trim);
		p.set(0, 3, 3, "minecraft:air");
		p.set(-3, 3, 0, "minecraft:air");
		p.set(3, 3, 0, "minecraft:air");
		p.fill(-2, WALK, -2, 2, WALK, 2, "minecraft:" + s.wood + "_planks");
		p.fill(-2, 9, -2, 2, 9, 2, s.floor);
		p.fill(-1, 6, -3, 1, 7, -3, "minecraft:air");
		p.fill(-1, 6, 3, 1, 7, 3, "minecraft:air");
		p.fill(-3, 6, -1, -3, 7, 1, "minecraft:air");
		p.fill(3, 6, -1, 3, 7, 1, "minecraft:air");
		p.fill(2, 1, 2, 2, 9, 2, "minecraft:ladder[facing=north]");
		p.set(0, 4, 0, "minecraft:lantern[hanging=true]");
		p.set(0, 8, 0, "minecraft:lantern[hanging=true]");
	}

	/** A tower where walls meet or turn a corner: crenellated top, dressed corners standing proud. No crew. */
	private static void wallTower(Plan p) {
		Palette s = Palette.of(p.tier);
		tower(p, s);
		p.set(-2, 1, 2, "minecraft:barrel[facing=up]");
		p.ring(3, 3, 10, s.body, s.weathered);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if ((Math.abs(x) == 3 || Math.abs(z) == 3) && Math.floorMod(x + z, 2) == 0) {
					p.set(x, 11, z, s.stone(x, 11, z));
				}
			}
		}
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			p.fill(c[0], 10, c[1], c[0], 11, c[1], s.trim);
			if (p.tier >= 3) {
				p.set(c[0], 12, c[1], s.wall);
			}
		}
	}

	/**
	 * A watchtower: the tower body with a fletching table downstairs and a bunk at walkway height, a parapet with a
	 * gap at each manned post (front, back, then left), timber corner posts and a pointed roof.
	 */
	private static void watchtower(Plan p) {
		Palette s = Palette.of(p.tier);
		tower(p, s);
		p.set(-2, 1, 2, "minecraft:fletching_table");
		p.set(-2, 1, 1, "minecraft:hay_block[axis=y]");
		p.set(-2, 6, 1, "minecraft:red_bed[facing=south,part=foot]");
		p.set(-2, 6, 2, "minecraft:red_bed[facing=south,part=head]");
		p.ring(3, 3, 10, s.body, s.weathered);
		for (int i = 0; i < WATCHTOWER.workers(p.tier); i++) {
			BlockPos post = POSTS[i];
			p.set(post.getX(), post.getY(), post.getZ(), "minecraft:air");
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if ((Math.abs(x) == 3 || Math.abs(z) == 3) && Math.floorMod(x + z, 2) == 0) {
					p.set(x, 11, z, s.stone(x, 11, z));
				}
			}
		}
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			p.fill(c[0], 10, c[1], c[0], 12, c[1], s.log());
		}
		p.hipRoof(3, 1, 13, "minecraft:" + s.wood + "_stairs");
		p.set(0, 16, 0, "minecraft:" + s.wood + "_slab[type=bottom]");
	}

	/**
	 * A gatehouse: two solid towers either side of an arched passage, with a portcullis hanging in the outer arch
	 * and fence gates halfway (you can open them, monsters can't). On top, a deck behind a thick crenellated
	 * parapet with corner turrets; it joins the walkway of the walls either side.
	 */
	private static void gatehouse(Plan p) {
		Palette s = Palette.of(p.tier);
		p.fillMix(-3, 0, -3, 3, 0, 3, s.foot, s.footWeathered);
		p.fill(-1, 0, -3, 1, 0, 3, s.floor);
		for (int x : new int[] {-3, -2, 2, 3}) {
			for (int z = -3; z <= 3; z++) {
				for (int y = 1; y <= WALK; y++) {
					p.set(x, y, z, y <= 2 ? s.footing(x, y, z) : s.stone(x, y, z));
				}
			}
		}
		for (int x : new int[] {-3, 3}) {
			p.fill(x, 1, 3, x, 8, 3, s.trim);
			p.fill(x, 1, -3, x, WALK, -3, s.trim);
		}
		for (int x : new int[] {-2, 2}) {
			p.fill(x, 1, 3, x, 3, 3, s.trim);
			p.fill(x, 1, -3, x, 3, -3, s.trim);
		}
		// the passage: an arch the whole way through, the portcullis, lanterns, and the gates halfway
		p.fillMix(-1, 4, -3, 1, WALK, 3, s.body, s.weathered);
		for (int z = -3; z <= 3; z++) {
			p.stairs(-1, 3, z, s.stairs, "west", true);
			p.stairs(1, 3, z, s.stairs, "east", true);
		}
		p.set(0, 3, 3, "minecraft:iron_bars");
		p.set(0, 3, -2, "minecraft:lantern[hanging=true]");
		p.set(0, 3, 2, "minecraft:lantern[hanging=true]");
		for (int x = -1; x <= 1; x++) {
			p.set(x, 1, 0, "minecraft:" + s.wood + "_fence_gate[facing=north,open=false]");
		}
		// the deck: a walkway between a thick parapet outside and a railing inside, and a ladder up the inside
		p.fill(-3, WALK, -1, 3, WALK, 1, s.floor);
		for (int x = -3; x <= 3; x++) {
			if (Math.abs(x) <= 2) {
				p.stairs(x, WALK, 3, s.trimStairs, "north", true);
			}
			p.set(x, 6, 3, s.stone(x, 6, 3));
			p.set(x, 6, 2, s.stone(x, 6, 2));
			if (Math.floorMod(x, 2) != 0) {
				p.set(x, 7, 3, s.stone(x, 7, 3));
			}
			if (x != -3) {
				p.set(x, 6, -2, s.wall);
			}
		}
		for (int x : new int[] {-3, 3}) {
			p.fill(x, 6, 3, x, 8, 3, s.trim);
			p.set(x, 9, 3, s.wall);
		}
		if (p.tier >= 2) {
			p.set(0, 7, -2, "minecraft:lantern[hanging=false]");
		}
		p.fill(-3, 1, -3, -3, WALK, -3, "minecraft:ladder[facing=north]");
		p.set(-3, 6, -3, "minecraft:air");
	}

	// ---------------------------------------------------------------- the town street (Colonycraft 1.4.0)

	/**
	 * A trading post: a brick market hall open to the street through three sandstone arches, with a counter and a
	 * master trader behind it in every bay (one per tier: toolsmith, armorer, librarian), their workstations at their
	 * backs, striped awnings over the arches, crates and lamps out front.
	 */
	private static void tradingPost(Plan p) {
		p.fill(-5, 0, -5, 5, 0, -3, PAVING);
		p.set(0, 0, -5, "minecraft:smooth_quartz");
		p.fill(-4, 0, -2, 4, 0, 4, PLINTH);
		p.fill(-3, 0, -1, 3, 0, 3, "minecraft:spruce_planks");
		for (int y = 1; y <= 5; y++) {
			p.rect(-4, -2, 4, 4, y, y == 1 ? PLINTH : y == 5 ? TRIM : BRICK);
		}
		p.corner(-4, -2, 2, 4);
		p.corner(4, -2, 2, 4);
		p.corner(-4, 4, 2, 4);
		p.corner(4, 4, 2, 4);
		// three arches onto the street, one per stall, with sandstone piers between them and striped awnings
		for (int a : new int[] {-3, 0, 3}) {
			p.set(a, 1, -2, "minecraft:air");
			p.set(a, 2, -2, "minecraft:air");
			p.set(a, 3, -2, "minecraft:air");
			p.set(a, 4, -2, ACCENT);
			p.set(a, 4, -3, mix(a, 4, -3, "minecraft:red_wool", "minecraft:white_wool"));
		}
		for (int x : new int[] {-2, -1, 1, 2}) {
			p.fill(x, 1, -2, x, 3, -2, TRIM);
			p.set(x, 4, -3, Math.floorMod(x, 2) == 0 ? "minecraft:white_wool" : "minecraft:red_wool");
		}
		p.windowZ(-4, 1, 2, 2, 3);
		p.windowZ(4, 1, 2, 2, 3);
		// the counters, and behind each a stall: the trader stands at TRADER_SPOTS, the workstation at his back
		p.fill(-3, 1, 0, 3, 1, 0, "minecraft:spruce_slab[type=top]");
		p.set(-2, 1, 0, BRICK);
		p.set(2, 1, 0, BRICK);
		p.set(0, 1, 3, "minecraft:smithing_table");
		p.set(-3, 1, 3, "minecraft:blast_furnace[facing=south,lit=false]");
		p.set(3, 1, 3, "minecraft:lectern[facing=south,has_book=false]");
		p.fill(-2, 1, 1, -2, 2, 3, "minecraft:spruce_planks");
		p.fill(2, 1, 1, 2, 2, 3, "minecraft:spruce_planks");
		p.set(-1, 1, 3, "minecraft:barrel[facing=up]");
		p.set(1, 1, 3, "minecraft:barrel[facing=up]");
		p.set(-2, 3, 1, "minecraft:lantern[hanging=false]");
		p.set(2, 3, 1, "minecraft:lantern[hanging=false]");
		p.set(0, 4, 2, "minecraft:lantern[hanging=true]");
		p.set(-3, 4, 2, "minecraft:lantern[hanging=true]");
		p.set(3, 4, 2, "minecraft:lantern[hanging=true]");
		// the roof, an emerald sign in the gable
		p.roofAlongX(-4, -2, 4, 4, 6, SLATE, SLATE_SLAB, BRICK);
		p.set(-4, 7, 1, "minecraft:emerald_block");
		p.set(4, 7, 1, "minecraft:emerald_block");
		// out front: crates, a hay bale, lamps
		p.set(-5, 1, -5, "minecraft:barrel[facing=up]");
		p.set(-4, 1, -5, "minecraft:barrel[facing=north]");
		p.set(-5, 2, -5, "minecraft:barrel[facing=up]");
		p.set(5, 1, -5, "minecraft:hay_block[axis=x]");
		for (int x : new int[] {-2, 2}) {
			p.set(x, 1, -5, RAIL);
			p.set(x, 2, -5, "minecraft:lantern[hanging=false]");
		}
	}

	/** Trader spots, in the order the tiers add them: the middle bay, the left, the right. */
	private static final BlockPos[] TRADER_SPOTS = {new BlockPos(0, 1, 2), new BlockPos(-3, 1, 2), new BlockPos(3, 1, 2)};

	/** The bank's teller counter (a lectern): right-click it to pay in or take out. Anyone can. */
	public static final BlockPos BANK_TELLER = new BlockPos(-4, 1, -2);
	/** The Vault Ledger (a lodestone) in the middle of the vault: with the Riches mod the vault's money piles up around it. */
	public static final BlockPos BANK_LEDGER = new BlockPos(0, 1, 4);
	/** The lower half of the vault door (a Riches Vault Door that opens for anyone and swings shut). */
	public static final BlockPos BANK_DOOR = new BlockPos(0, 1, 0);
	private static final BlockPos[] CLERKS = {new BlockPos(-4, 1, -1), new BlockPos(4, 1, -1)};

	/**
	 * A bank: a wide brick front with a sandstone portico of four columns up a flight of steps, a banking hall with
	 * teller counters either side, and at the back the vault behind a stone wall and a vault door, the Vault Ledger in
	 * the middle of its floor. A mansard roof with dormers and a clock-face oculus over the door.
	 */
	private static void bank(Plan p) {
		p.fill(-7, 0, -7, 7, 0, -6, PAVING);
		p.set(0, 0, -7, "minecraft:smooth_quartz");
		p.fill(-7, 0, -5, 7, 0, 7, PLINTH);
		p.fill(-6, 0, -4, 6, 0, -1, "minecraft:polished_diorite");
		p.fill(-1, 0, -4, 1, 0, -1, "minecraft:polished_granite");
		for (int y = 1; y <= 8; y++) {
			p.rect(-7, -5, 7, 7, y, y == 1 ? PLINTH : y == 4 ? BAND : y == 8 ? TRIM : BRICK);
		}
		p.quoin(-7, -5, 1, 1, 2, 7);
		p.quoin(7, -5, -1, 1, 2, 7);
		p.quoin(-7, 7, 1, -1, 2, 7);
		p.quoin(7, 7, -1, -1, 2, 7);
		// the portico: steps up to four sandstone columns under an entablature and a pediment
		for (int x = -4; x <= 4; x++) {
			p.stairs(x, 1, -7, TRIM_STAIRS, "south", false);
			p.set(x, 1, -6, TRIM);
		}
		for (int x : new int[] {-4, -2, 2, 4}) {
			p.fill(x, 2, -6, x, 5, -6, "minecraft:sandstone_wall");
			p.set(x, 6, -6, ACCENT);
		}
		p.fill(-5, 7, -6, 5, 7, -6, TRIM);
		for (int k = 0; k <= 3; k++) {
			p.fill(-4 + k, 8 + k, -6, 4 - k, 8 + k, -6, k == 3 ? ACCENT : BAND);
		}
		// the doors, between pilasters; tall windows either side; an oculus over the door
		p.fill(-1, 1, -5, -1, 4, -5, TRIM);
		p.fill(1, 1, -5, 1, 4, -5, TRIM);
		p.door(-5, "dark_oak");
		p.set(0, 3, -5, "minecraft:glass_pane");
		p.set(0, 4, -5, ACCENT);
		p.set(0, 6, -5, "minecraft:glass_pane");
		p.windowX(-5, -5, -4, 2, 3);
		p.windowX(-5, 4, 5, 2, 3);
		p.windowX(-5, -5, -4, 5, 6);
		p.windowX(-5, 4, 5, 5, 6);
		for (int x : new int[] {-7, 7}) {
			p.windowZ(x, -4, -2, 2, 3);
			p.windowZ(x, -4, -2, 5, 6);
		}
		// the banking hall: teller counters left and right (the left one's lectern is where you bank), clerks behind
		p.fill(-6, 1, -2, -2, 1, -2, "minecraft:dark_oak_slab[type=top]");
		p.fill(2, 1, -2, 6, 1, -2, "minecraft:dark_oak_slab[type=top]");
		p.set(BANK_TELLER.getX(), BANK_TELLER.getY(), BANK_TELLER.getZ(), "minecraft:lectern[facing=north,has_book=false]");
		p.set(4, 1, -2, "minecraft:lectern[facing=north,has_book=false]");
		p.fill(-6, 2, -2, -6, 3, -2, "minecraft:iron_bars");
		p.fill(6, 2, -2, 6, 3, -2, "minecraft:iron_bars");
		p.set(-6, 1, -1, "minecraft:barrel[facing=up]");
		p.set(6, 1, -1, "minecraft:barrel[facing=up]");
		p.set(-3, 4, -3, "minecraft:lantern[hanging=true]");
		p.set(3, 4, -3, "minecraft:lantern[hanging=true]");
		p.fill(-6, 4, -4, 6, 4, -1, "minecraft:dark_oak_planks");
		p.fill(-1, 4, -4, 1, 4, -1, "minecraft:air");
		p.set(0, 7, -2, "minecraft:lantern[hanging=true]");
		// the vault: a stone wall with the vault door in it, a deepslate room, the ledger in the middle of the floor
		p.fill(-7, 1, 0, 7, 7, 0, PLINTH);
		p.fill(-4, 1, 0, -4, 7, 7, PLINTH);
		p.fill(4, 1, 0, 4, 7, 7, PLINTH);
		p.fill(-3, 0, 1, 3, 0, 6, "minecraft:polished_deepslate");
		p.set(BANK_DOOR.getX(), 1, BANK_DOOR.getZ(), "minecraft:iron_door[facing=south,half=lower,hinge=left,open=false]");
		p.set(BANK_DOOR.getX(), 2, BANK_DOOR.getZ(), "minecraft:iron_door[facing=south,half=upper,hinge=left,open=false]");
		p.set(-1, 3, 0, "minecraft:iron_block");
		p.set(1, 3, 0, "minecraft:iron_block");
		p.set(0, 3, 0, "minecraft:iron_block");
		p.set(BANK_LEDGER.getX(), BANK_LEDGER.getY(), BANK_LEDGER.getZ(), "minecraft:lodestone");
		p.fill(-3, 5, 1, 3, 5, 6, "minecraft:polished_deepslate");
		p.set(-2, 4, 2, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 6, "minecraft:lantern[hanging=true]");
		// offices either side of the vault
		p.set(-6, 1, 6, "minecraft:bookshelf");
		p.set(-5, 1, 6, "minecraft:bookshelf");
		p.set(6, 1, 6, "minecraft:cartography_table");
		p.set(5, 1, 6, "minecraft:bookshelf");
		p.set(-6, 3, 3, "minecraft:lantern[hanging=true]");
		p.set(6, 3, 3, "minecraft:lantern[hanging=true]");
		p.windowZ(-7, 3, 4, 2, 3);
		p.windowZ(7, 3, 4, 2, 3);
		// the roof
		int top = p.mansard(-7, -5, 7, 7, 9);
		p.dormerX(-4, -4, 10);
		p.dormerX(4, -4, 10);
		p.dormerX(-4, 6, 10);
		p.dormerX(4, 6, 10);
		p.set(0, top, 1, ACCENT);
		p.set(0, top + 1, 1, RAIL);
		// lamps by the steps
		for (int x : new int[] {-6, 6}) {
			p.set(x, 1, -7, RAIL);
			p.set(x, 2, -7, "minecraft:lantern[hanging=false]");
		}
	}

	/** Museum display cases (Riches Display Cases on stone plinths), then the pedestals, in the order the tiers add them: 4, 8, 12. */
	private static final BlockPos[] MUSEUM_SHOWS = {
		new BlockPos(-4, 2, -2), new BlockPos(4, 2, -2), new BlockPos(-4, 2, 0), new BlockPos(4, 2, 0),
		new BlockPos(-4, 2, 2), new BlockPos(4, 2, 2), new BlockPos(-4, 2, 4), new BlockPos(4, 2, 4),
		new BlockPos(-1, 1, 1), new BlockPos(1, 1, 1), new BlockPos(-1, 1, 3), new BlockPos(1, 1, 3)};

	/** How many display cases and pedestals a museum of this tier has: 4, 8, then 12. */
	public static int shows(int tier) {
		return 4 * Math.max(1, Math.min(MAX_TIER, tier));
	}

	public static BlockPos show(int index) {
		return MUSEUM_SHOWS[index];
	}

	/** The first eight are glass cases; the last four are pedestals. */
	public static boolean isPedestal(int index) {
		return index >= 8;
	}

	/**
	 * A museum: a broad brick front with a sandstone entrance arch up three steps and tall arched windows, a single
	 * long gallery inside with glass display cases on stone plinths along both walls and pedestals down the middle
	 * (Riches cases: put your relics on show), banners between the windows, a skylight in the mansard roof.
	 */
	private static void museum(Plan p) {
		p.fill(-7, 0, -6, 7, 0, -6, PAVING);
		p.set(0, 0, -6, "minecraft:smooth_quartz");
		p.fill(-7, 0, -5, 7, 0, 6, PLINTH);
		p.fillMix(-6, 0, -4, 6, 0, 5, "minecraft:smooth_quartz", "minecraft:polished_diorite");
		p.fill(-1, 0, -4, 1, 0, 5, "minecraft:polished_granite");
		for (int y = 1; y <= 8; y++) {
			p.rect(-7, -5, 7, 6, y, y == 1 ? PLINTH : y == 8 ? TRIM : BRICK);
		}
		p.quoin(-7, -5, 1, 1, 2, 7);
		p.quoin(7, -5, -1, 1, 2, 7);
		p.quoin(-7, 6, 1, -1, 2, 7);
		p.quoin(7, 6, -1, -1, 2, 7);
		// the entrance: three steps, an arch of sandstone, double doors, a carved plaque above
		for (int x = -2; x <= 2; x++) {
			p.stairs(x, 1, -6, TRIM_STAIRS, "south", false);
		}
		p.fill(-2, 1, -5, -2, 5, -5, TRIM);
		p.fill(2, 1, -5, 2, 5, -5, TRIM);
		p.fill(-1, 5, -5, 1, 5, -5, TRIM);
		p.set(0, 6, -5, ACCENT);
		p.set(-1, 6, -5, ACCENT);
		p.set(1, 6, -5, ACCENT);
		p.set(-1, 1, -5, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=false]");
		p.set(-1, 2, -5, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false]");
		p.set(0, 1, -5, "minecraft:air");
		p.set(0, 2, -5, "minecraft:air");
		p.set(1, 1, -5, "minecraft:dark_oak_door[facing=south,half=lower,hinge=right,open=false]");
		p.set(1, 2, -5, "minecraft:dark_oak_door[facing=south,half=upper,hinge=right,open=false]");
		p.fill(-1, 3, -5, 1, 4, -5, "minecraft:glass_pane");
		// tall windows along the front and the sides, banners between them
		p.windowX(-5, -5, -4, 2, 6);
		p.windowX(-5, 4, 5, 2, 6);
		for (int x : new int[] {-7, 7}) {
			p.windowZ(x, -3, -3, 3, 6);
			p.windowZ(x, 1, 1, 3, 6);
			p.windowZ(x, 5, 5, 3, 6);
		}
		for (int z : new int[] {-1, 3}) {
			p.set(-6, 5, z, "minecraft:red_wall_banner[facing=east]");
			p.set(6, 5, z, "minecraft:red_wall_banner[facing=west]");
		}
		// the exhibits: glass cases on plinths along the walls, pedestals down the middle; the rest wait for upgrades
		for (int i = 0; i < shows(p.tier); i++) {
			BlockPos s = MUSEUM_SHOWS[i];
			if (isPedestal(i)) {
				p.set(s.getX(), s.getY(), s.getZ(), "minecraft:quartz_pillar[axis=y]");
			} else {
				p.set(s.getX(), 1, s.getZ(), "minecraft:chiseled_stone_bricks");
				p.set(s.getX(), s.getY(), s.getZ(), "minecraft:glass");
			}
		}
		// the curator's desk by the door, a guest book, a rope barrier at the back with the star exhibit's alcove
		p.set(-5, 1, -4, "minecraft:dark_oak_slab[type=top]");
		p.set(-6, 1, -4, "minecraft:lectern[facing=east,has_book=false]");
		p.set(5, 1, -4, "minecraft:potted_fern");
		p.fill(-6, 1, 5, 6, 1, 5, "minecraft:polished_andesite");
		p.set(0, 2, 5, "minecraft:gold_block");
		p.set(-3, 2, 5, "minecraft:bookshelf");
		p.set(3, 2, 5, "minecraft:bookshelf");
		for (int x : new int[] {-3, 0, 3}) {
			p.set(x, 7, -2, "minecraft:lantern[hanging=true]");
			p.set(x, 7, 2, "minecraft:lantern[hanging=true]");
		}
		// the roof, with a skylight over the gallery
		int top = p.mansard(-7, -5, 7, 6, 9);
		p.fill(-2, top, -1, 2, top, 2, "minecraft:glass");
		p.dormerX(-4, -4, 10);
		p.dormerX(4, -4, 10);
		p.set(0, 11, -4, ACCENT);
		for (int x : new int[] {-6, 6}) {
			p.set(x, 1, -6, RAIL);
			p.set(x, 2, -6, "minecraft:lantern[hanging=false]");
		}
	}

	/**
	 * A chapel: a brick nave under a steep slate roof, buttresses down the sides, tall stained-glass windows, and a
	 * tower over the porch with a bell in its belfry and a slate spire. Inside, pews either side of an aisle up to an
	 * altar with candles under a rose window.
	 */
	private static void chapel(Plan p) {
		p.fillMix(-6, 0, -7, 6, 0, 7, "minecraft:grass_block", "minecraft:moss_block");
		p.fill(-1, 0, -7, 1, 0, -4, PAVING);
		p.set(0, 0, -7, "minecraft:smooth_quartz");
		p.fill(-4, 0, -3, 4, 0, 6, PLINTH);
		p.fill(-3, 0, -2, 3, 0, 5, "minecraft:spruce_planks");
		p.fill(0, 0, -2, 0, 0, 5, "minecraft:polished_granite");
		// the nave
		for (int y = 1; y <= 7; y++) {
			p.rect(-4, -3, 4, 6, y, y == 1 ? PLINTH : y == 7 ? TRIM : BRICK);
		}
		for (int z : new int[] {-1, 2, 5}) {
			for (int x : new int[] {-5, 5}) {
				p.fill(x, 1, z, x, 4, z, BRICK);
				p.set(x, 5, z, TRIM_CAP);
			}
		}
		String[] glass = {"minecraft:light_blue_stained_glass_pane", "minecraft:yellow_stained_glass_pane", "minecraft:red_stained_glass_pane"};
		for (int x : new int[] {-4, 4}) {
			for (int z : new int[] {0, 3}) {
				p.fill(x, 2, z, x, 5, z, glass[Math.floorMod(z, 3)]);
				p.set(x, 6, z, ACCENT);
			}
		}
		p.roofAlongZ(-4, -3, 4, 6, 8, SLATE, SLATE_SLAB, BRICK);
		// the rose window over the altar
		p.set(0, 9, 6, "minecraft:purple_stained_glass_pane");
		p.set(-1, 9, 6, "minecraft:red_stained_glass_pane");
		p.set(1, 9, 6, "minecraft:red_stained_glass_pane");
		p.set(0, 10, 6, "minecraft:yellow_stained_glass_pane");
		p.set(0, 8, 6, "minecraft:yellow_stained_glass_pane");
		// pews, the altar, candles
		for (int z = -1; z <= 3; z += 2) {
			for (int x : new int[] {-3, -2, 2, 3}) {
				p.stairs(x, 1, z, "minecraft:spruce_stairs", "south", false);
			}
		}
		p.fill(-1, 1, 5, 1, 1, 5, "minecraft:quartz_block");
		p.set(-1, 2, 5, "minecraft:candle[candles=3,lit=true]");
		p.set(1, 2, 5, "minecraft:candle[candles=3,lit=true]");
		p.set(0, 2, 5, "minecraft:gold_block");
		p.set(-3, 1, 5, "minecraft:potted_white_tulip");
		p.set(3, 1, 5, "minecraft:potted_white_tulip");
		p.set(0, 6, 1, "minecraft:lantern[hanging=true]");
		p.set(0, 6, 4, "minecraft:lantern[hanging=true]");
		// the tower over the porch: brick up to the belfry, a bell, a spire
		for (int y = 1; y <= 14; y++) {
			p.rect(-2, -7, 2, -3, y, y == 1 ? PLINTH : y == 7 || y == 14 ? TRIM : BRICK);
		}
		p.corner(-2, -7, 2, 13);
		p.corner(2, -7, 2, 13);
		p.fill(-1, 1, -6, 1, 13, -4, "minecraft:air");
		p.door(-7, "dark_oak");
		p.set(0, 3, -7, "minecraft:glass_pane");
		p.set(0, 4, -7, ACCENT);
		p.set(0, 1, -3, "minecraft:air");
		p.set(0, 2, -3, "minecraft:air");
		p.set(0, 8, -7, "minecraft:glass_pane");
		p.set(0, 9, -7, "minecraft:glass_pane");
		for (int[] side : new int[][] {{0, -7}, {0, -3}, {-2, -5}, {2, -5}}) {
			p.set(side[0], 11, side[1], "minecraft:air");
			p.set(side[0], 12, side[1], "minecraft:air");
		}
		p.fill(-1, 14, -6, 1, 14, -4, "minecraft:spruce_planks");
		p.set(0, 13, -5, "minecraft:bell[attachment=ceiling,facing=north]");
		p.set(0, 4, -5, "minecraft:lantern[hanging=true]");
		for (int x = -2; x <= 2; x++) {
			p.stairs(x, 15, -7, SLATE, "south", false);
			p.stairs(x, 15, -3, SLATE, "north", false);
		}
		for (int z = -6; z <= -4; z++) {
			p.stairs(-2, 15, z, SLATE, "east", false);
			p.stairs(2, 15, z, SLATE, "west", false);
		}
		for (int x = -1; x <= 1; x++) {
			p.stairs(x, 16, -6, SLATE, "south", false);
			p.stairs(x, 16, -4, SLATE, "north", false);
		}
		p.stairs(-1, 16, -5, SLATE, "east", false);
		p.stairs(1, 16, -5, SLATE, "west", false);
		p.set(0, 16, -5, SLATE_BLOCK);
		p.set(0, 17, -5, ACCENT);
		p.set(0, 18, -5, RAIL);
		p.set(0, 19, -5, "minecraft:lightning_rod");
		// lamps along the path
		for (int x : new int[] {-2, 2}) {
			p.set(x, 1, -7, RAIL);
			p.set(x, 2, -7, "minecraft:lantern[hanging=false]");
		}
	}

	/** The library's enchanting table: fifteen bookshelves around it make it a level-30 table. */
	public static final BlockPos ENCHANTING = new BlockPos(0, 1, 1);

	/**
	 * A library: a brick reading room under a slate roof with a tall window in the gable, the enchanting table in
	 * the middle ringed by two-high bookcases (thirty shelves: a maxed table), a reading desk, an anvil and a
	 * grindstone in the corners for after the enchanting, and lamps.
	 */
	private static void library(Plan p) {
		p.fill(-5, 0, -5, 5, 0, -4, PAVING);
		p.set(0, 0, -5, "minecraft:smooth_quartz");
		p.fill(-4, 0, -3, 4, 0, 4, PLINTH);
		p.fill(-3, 0, -2, 3, 0, 3, "minecraft:dark_oak_planks");
		for (int y = 1; y <= 6; y++) {
			p.rect(-4, -3, 4, 4, y, y == 1 ? PLINTH : y == 6 ? TRIM : BRICK);
		}
		p.corner(-4, -3, 2, 5);
		p.corner(4, -3, 2, 5);
		p.corner(-4, 4, 2, 5);
		p.corner(4, 4, 2, 5);
		p.fill(-1, 1, -3, -1, 4, -3, TRIM);
		p.fill(1, 1, -3, 1, 4, -3, TRIM);
		p.door(-3, "dark_oak");
		p.set(0, 3, -3, "minecraft:glass_pane");
		p.set(0, 4, -3, ACCENT);
		p.windowX(-3, -3, -2, 2, 4);
		p.windowX(-3, 2, 3, 2, 4);
		p.windowZ(-4, 0, 2, 3, 4);
		p.windowZ(4, 0, 2, 3, 4);
		// the enchanting table, ringed by bookcases two high with a gap of one, open at the front
		BlockPos t = ENCHANTING;
		p.set(t.getX(), t.getY(), t.getZ(), "minecraft:enchanting_table");
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) != 2 && Math.abs(dz) != 2 || (dx == 0 && dz == -2)) {
					continue;
				}
				p.fill(t.getX() + dx, 1, t.getZ() + dz, t.getX() + dx, 2, t.getZ() + dz, "minecraft:bookshelf");
			}
		}
		p.set(t.getX(), 3, t.getZ() + 2, "minecraft:lantern[hanging=false]");
		// the corners: an anvil, a grindstone, a lectern, a desk
		p.set(-3, 1, -2, "minecraft:anvil[facing=east]");
		p.set(3, 1, -2, "minecraft:grindstone[face=floor,facing=west]");
		p.set(-3, 1, 4, "minecraft:bookshelf");
		p.set(3, 1, 4, "minecraft:bookshelf");
		p.set(-3, 1, 3, "minecraft:lectern[facing=east,has_book=false]");
		p.set(3, 1, 3, "minecraft:chiseled_bookshelf[facing=west]");
		p.set(-2, 5, -1, "minecraft:lantern[hanging=true]");
		p.set(2, 5, -1, "minecraft:lantern[hanging=true]");
		p.set(0, 5, 3, "minecraft:lantern[hanging=true]");
		p.roofAlongZ(-4, -3, 4, 4, 7, SLATE, SLATE_SLAB, BRICK);
		p.fill(0, 8, -3, 0, 9, -3, "minecraft:glass_pane");
		p.set(0, 10, -3, ACCENT);
		for (int x : new int[] {-2, 2}) {
			p.set(x, 1, -5, RAIL);
			p.set(x, 2, -5, "minecraft:lantern[hanging=false]");
		}
	}

	/** Where the ranch's cows, sheep, pigs and chickens are let out (in the paddock). */
	public static final BlockPos PADDOCK = new BlockPos(0, 1, -3);

	/**
	 * A ranch: a paddock behind a fence with log posts and a gate onto the street, a water trough and a hay feeder,
	 * and at the back a timber barn with stalls, hay and a milking corner. Cows, sheep, pigs and chickens in the paddock.
	 */
	private static void ranch(Plan p) {
		p.fillMix(-6, 0, -6, 6, 0, 6, "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:grass_block");
		p.fill(0, 0, -6, 0, 0, 1, "minecraft:dirt_path");
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 1; z++) {
				if (Math.abs(x) != 6 && z != -6) {
					continue;
				}
				boolean post = Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0;
				p.set(x, 1, z, post ? "minecraft:stripped_spruce_log[axis=y]" : "minecraft:spruce_fence");
			}
		}
		p.set(0, 1, -6, "minecraft:spruce_fence_gate[facing=south,open=false]");
		for (int[] c : new int[][] {{-6, -6}, {6, -6}}) {
			p.set(c[0], 2, c[1], "minecraft:stripped_spruce_log[axis=y]");
			p.set(c[0], 3, c[1], "minecraft:lantern[hanging=false]");
		}
		// the trough and the feeder
		p.fill(-5, 0, -3, -3, 0, -3, "minecraft:water");
		p.fill(-5, 1, -4, -3, 1, -4, "minecraft:spruce_slab[type=bottom]");
		p.set(4, 1, -4, "minecraft:hay_block[axis=y]");
		p.set(4, 1, -3, "minecraft:hay_block[axis=x]");
		p.set(5, 1, -4, "minecraft:composter[level=3]");
		// the barn: a spruce frame on a plank floor, open to the paddock, under a slate roof
		p.fill(-5, 0, 2, 5, 0, 5, "minecraft:spruce_planks");
		for (int y = 1; y <= 4; y++) {
			p.fill(-5, y, 5, 5, y, 5, "minecraft:spruce_planks");
			p.fill(-5, y, 2, -5, y, 5, "minecraft:spruce_planks");
			p.fill(5, y, 2, 5, y, 5, "minecraft:spruce_planks");
			for (int x : new int[] {-5, -2, 2, 5}) {
				p.set(x, y, 2, "minecraft:stripped_spruce_log[axis=y]");
				p.set(x, y, 5, "minecraft:stripped_spruce_log[axis=y]");
			}
		}
		p.fill(-5, 4, 2, 5, 4, 2, "minecraft:stripped_spruce_log[axis=x]");
		p.fill(-1, 1, 2, 1, 3, 2, "minecraft:air");
		p.set(-4, 2, 5, "minecraft:glass_pane");
		p.set(4, 2, 5, "minecraft:glass_pane");
		p.roofAlongX(-5, 2, 5, 5, 5, SLATE, SLATE_SLAB, "minecraft:spruce_planks");
		// stalls, hay and a milking stool inside
		p.fill(-4, 1, 3, -4, 1, 4, "minecraft:spruce_fence");
		p.fill(4, 1, 3, 4, 1, 4, "minecraft:spruce_fence");
		p.set(-3, 1, 4, "minecraft:hay_block[axis=y]");
		p.set(-3, 2, 4, "minecraft:hay_block[axis=y]");
		p.set(3, 1, 4, "minecraft:hay_block[axis=x]");
		p.set(2, 1, 4, "minecraft:water_cauldron[level=3]");
		p.set(-1, 1, 4, "minecraft:barrel[facing=up]");
		p.stairs(1, 1, 3, "minecraft:spruce_stairs", "west", false);
		p.set(0, 3, 3, "minecraft:lantern[hanging=true]");
	}

	/**
	 * An apiary: a flower garden with beehives on posts, a gravel path, a beekeeper's shed with a honey press
	 * (a cauldron), jars on the shelves and a smoking campfire, under a slate roof, and a hedge round it all.
	 */
	private static void apiary(Plan p) {
		p.fillMix(-5, 0, -5, 5, 0, 5, "minecraft:grass_block", "minecraft:moss_block", "minecraft:grass_block");
		p.fill(0, 0, -5, 0, 0, 1, "minecraft:gravel");
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				if ((Math.abs(x) == 5 || Math.abs(z) == 5) && !(x == 0 && z == -5)) {
					p.set(x, 1, z, mix(x, 1, z, "minecraft:oak_leaves[persistent=true]", "minecraft:flowering_azalea_leaves[persistent=true]"));
				}
			}
		}
		String[] flowers = {"minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy",
			"minecraft:azure_bluet", "minecraft:lily_of_the_valley"};
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 0; z++) {
				if (x != 0 && Math.floorMod(x + z, 2) == 0) {
					p.set(x, 1, z, flowers[Math.floorMod(x * 3 + z * 5, flowers.length)]);
				}
			}
		}
		// beehives on fence posts in the garden
		for (int[] h : new int[][] {{-3, -3}, {3, -3}, {-3, -1}, {3, -1}}) {
			p.set(h[0], 1, h[1], "minecraft:oak_fence");
			p.set(h[0], 2, h[1], "minecraft:beehive[facing=" + (h[0] < 0 ? "east" : "west") + ",honey_level=3]");
		}
		// the shed
		p.fill(-3, 0, 2, 3, 0, 4, "minecraft:oak_planks");
		for (int y = 1; y <= 3; y++) {
			p.fill(-3, y, 4, 3, y, 4, "minecraft:oak_planks");
			p.fill(-3, y, 2, -3, y, 4, "minecraft:oak_planks");
			p.fill(3, y, 2, 3, y, 4, "minecraft:oak_planks");
			for (int[] c : new int[][] {{-3, 2}, {3, 2}, {-3, 4}, {3, 4}}) {
				p.set(c[0], y, c[1], "minecraft:stripped_oak_log[axis=y]");
			}
		}
		p.fill(-3, 3, 2, 3, 3, 2, "minecraft:stripped_oak_log[axis=x]");
		p.roofAlongX(-3, 2, 3, 4, 4, SLATE, SLATE_SLAB, "minecraft:oak_planks");
		p.set(-2, 1, 3, "minecraft:cauldron");
		p.set(2, 1, 3, "minecraft:beehive[facing=north,honey_level=5]");
		p.set(-1, 1, 3, "minecraft:barrel[facing=up]");
		p.set(1, 1, 3, "minecraft:honey_block");
		p.set(0, 2, 3, "minecraft:lantern[hanging=false]");
		p.set(0, 1, 3, "minecraft:spruce_slab[type=top]");
		p.set(4, 0, 3, "minecraft:campfire[lit=true,facing=north]");
		p.set(4, 1, 3, "minecraft:air");
	}

	/** The depot's tanks (Fossil Fool Tanks) in the order the tiers add them, and the fluid each is set to: crude left, diesel right. */
	private static final BlockPos[] DEPOT_TANKS = {new BlockPos(-4, 1, 3), new BlockPos(4, 1, 3), new BlockPos(-3, 1, 3), new BlockPos(3, 1, 3),
		new BlockPos(-2, 1, 3), new BlockPos(2, 1, 3)};
	/** The Fossil Fool Refinery (a blast furnace) in the middle of the depot. */
	public static final BlockPos DEPOT_REFINERY = new BlockPos(0, 1, 0);
	/** The pipe manifold behind the tanks, out through both side walls: lay your pipeline onto either end. */
	public static final int DEPOT_PIPE_Z = 4;

	/** How many tanks a fuel depot of this tier has: 2, 4, then 6. */
	public static int depotTanks(int tier) {
		return 2 * Math.max(1, Math.min(MAX_TIER, tier));
	}

	public static BlockPos depotTank(int index) {
		return DEPOT_TANKS[index];
	}

	/** Even tanks hold crude, odd ones diesel. */
	public static String depotFluid(int index) {
		return index % 2 == 0 ? "CRUDE" : "DIESEL";
	}

	/**
	 * A fuel depot: a brick shed with a tall chimney, iron-barred windows and a wide door, the refinery in the
	 * middle, the tanks along the back wall (crude on the left, diesel on the right) and a copper pipe manifold
	 * behind them that comes out through both side walls. A forecourt with a pump, barrels and a warning lamp.
	 */
	private static void fuelDepot(Plan p) {
		p.fillMix(-6, 0, -5, 6, 0, -4, "minecraft:gravel", "minecraft:andesite");
		p.fill(-5, 0, -3, 5, 0, 4, PLINTH);
		p.fill(-4, 0, -2, 4, 0, 3, "minecraft:polished_andesite");
		for (int y = 1; y <= 5; y++) {
			p.rect(-5, -3, 5, 4, y, y == 1 ? PLINTH : y == 5 ? TRIM : BRICK);
		}
		p.corner(-5, -3, 2, 4);
		p.corner(5, -3, 2, 4);
		p.corner(-5, 4, 2, 4);
		p.corner(5, 4, 2, 4);
		// a wide door, barred windows
		p.fill(-1, 1, -3, 1, 3, -3, "minecraft:air");
		p.set(-1, 3, -3, TRIM);
		p.set(1, 3, -3, TRIM);
		p.set(0, 4, -3, ACCENT);
		for (int x : new int[] {-3, 3}) {
			p.fill(x, 2, -3, x, 3, -3, "minecraft:iron_bars");
		}
		p.fill(-5, 2, -1, -5, 3, 0, "minecraft:iron_bars");
		p.fill(5, 2, -1, 5, 3, 0, "minecraft:iron_bars");
		// the refinery, the tanks and the manifold
		p.set(DEPOT_REFINERY.getX(), DEPOT_REFINERY.getY(), DEPOT_REFINERY.getZ(), "minecraft:blast_furnace[facing=north,lit=false]");
		p.set(0, 2, 0, "minecraft:copper_block");
		for (int i = 0; i < depotTanks(p.tier); i++) {
			BlockPos t = DEPOT_TANKS[i];
			p.set(t.getX(), t.getY(), t.getZ(), "minecraft:cauldron");
		}
		for (int x = -6; x <= 6; x++) {
			p.set(x, 1, DEPOT_PIPE_Z, "minecraft:lightning_rod[facing=east]");
		}
		p.set(0, 3, 2, "minecraft:lantern[hanging=true]");
		p.set(-3, 3, 0, "minecraft:lantern[hanging=true]");
		p.set(3, 3, 0, "minecraft:lantern[hanging=true]");
		// the chimney over the refinery's flue, the roof
		p.roofAlongX(-5, -3, 5, 4, 6, SLATE, SLATE_SLAB, BRICK);
		p.fill(2, 6, 1, 3, 9, 2, BRICK);
		p.set(2, 10, 1, "minecraft:campfire[lit=true,facing=north]");
		p.set(3, 10, 1, TRIM_CAP);
		p.set(2, 10, 2, TRIM_CAP);
		p.set(3, 10, 2, TRIM_CAP);
		// the forecourt: a pump, barrels, a warning lamp
		p.set(-4, 1, -5, "minecraft:cauldron");
		p.set(-4, 2, -5, "minecraft:lightning_rod[facing=up]");
		p.set(-3, 1, -5, "minecraft:barrel[facing=up]");
		p.set(-3, 2, -5, "minecraft:barrel[facing=up]");
		p.set(4, 1, -5, RAIL);
		p.set(4, 2, -5, "minecraft:lantern[hanging=false]");
		p.set(4, 3, -5, TRIM_CAP);
	}
}
