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
 * Fortifications (walls, gatehouses, watchtowers) don't use a building slot, may touch each other
 * and are built in the colony's stone of their tier: cobblestone, then stone bricks, then deepslate.
 */
public enum BuildingType {
	TOWN_HALL("town_hall", "Town Hall", Items.BELL, 2500, 4, 4, 11, new int[] {1, 1, 1}, new int[] {2, 3, 4}, 3, false, BuildingType::townHall),
	RESIDENCE("residence", "Residence", Items.OAK_DOOR, 400, 3, 3, 9, new int[] {0, 0, 0}, new int[] {4, 6, 8}, 3, false, BuildingType::residence),
	FARM("farm", "Farm", Items.WHEAT, 800, 4, 4, 4, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::farm),
	LUMBER_CAMP("lumber_camp", "Lumber Camp", Items.IRON_AXE, 900, 4, 4, 7, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::lumberCamp),
	MINE("mine", "Mine", Items.IRON_PICKAXE, 2500, 3, 3, 6, new int[] {3, 4, 5}, new int[] {0, 0, 0}, 3, false, BuildingType::mine),
	WORKSHOP("workshop", "Workshop", Items.CRAFTING_TABLE, 1500, 3, 3, 8, new int[] {2, 3, 4}, new int[] {0, 0, 0}, 3, false, BuildingType::workshop),
	STOREHOUSE("storehouse", "Storehouse", Items.BARREL, 500, 3, 3, 8, new int[] {1, 1, 2}, new int[] {0, 0, 0}, 3, false, BuildingType::storehouse),
	BARRACKS("barracks", "Barracks", Items.IRON_SWORD, 2000, 4, 4, 9, new int[] {1, 2, 3}, new int[] {0, 0, 0}, 15, false, BuildingType::barracks),
	WATCHTOWER("watchtower", "Watchtower", Items.CROSSBOW, 1800, 3, 3, 16, new int[] {1, 2, 3}, new int[] {1, 2, 3}, 10, true, BuildingType::watchtower),
	WALL("wall", "Wall", Items.STONE_BRICK_WALL, 300, 4, 2, 8, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, true, BuildingType::wall),
	GATEHOUSE("gatehouse", "Gatehouse", Items.SPRUCE_FENCE_GATE, 1200, 3, 3, 9, new int[] {0, 0, 0}, new int[] {0, 0, 0}, 0, true, BuildingType::gatehouse);

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

	/** Iron golems don't sleep. Everyone else needs a bed somewhere in the colony. */
	public boolean needsBeds() {
		return this != BARRACKS;
	}

	/** "worker", "archer", "iron golem"... */
	public String crewNoun(int n) {
		String noun = switch (this) {
			case BARRACKS -> "iron golem";
			case WATCHTOWER -> "archer";
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
			case BARRACKS -> new BlockPos(0, 1, -2);
			case WALL -> new BlockPos(0, 6, 0);
			default -> new BlockPos(0, 1, 0);
		};
	}

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

	/** Where crew member number {@code index} belongs: archers at their post, everyone else at home. */
	public BlockPos station(int index) {
		return this == WATCHTOWER ? post(index) : home();
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

	/** The stone a fortification of a given tier is built from. */
	record Palette(String body, String[] weathered, String plinth, String quoin, String stairs, String wall, String floor, String wood) {
		static Palette of(int tier) {
			return switch (tier) {
				case 1 -> new Palette("minecraft:cobblestone", new String[] {"minecraft:mossy_cobblestone"}, "minecraft:mossy_cobblestone",
					"minecraft:stripped_spruce_log[axis=y]", "minecraft:cobblestone_stairs", "minecraft:cobblestone_wall",
					"minecraft:spruce_planks", "spruce");
				case 2 -> new Palette("minecraft:stone_bricks", new String[] {"minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"},
					"minecraft:polished_andesite", "minecraft:polished_andesite", "minecraft:stone_brick_stairs", "minecraft:stone_brick_wall",
					"minecraft:smooth_stone", "spruce");
				default -> new Palette("minecraft:deepslate_bricks", new String[] {"minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles"},
					"minecraft:polished_deepslate", "minecraft:polished_deepslate", "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_wall",
					"minecraft:polished_deepslate", "dark_oak");
			};
		}

		String stone(int x, int y, int z) {
			return mix(x, y, z, body, weathered);
		}

		String log() {
			return "minecraft:" + wood + "_log[axis=y]";
		}
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

	/** A brick hall on a stone footing with dark oak posts, a slate roof and a belfry with the colony bell. */
	private static void townHall(Plan p) {
		String post = "minecraft:stripped_dark_oak_log[axis=y]";
		p.fill(-4, 0, -4, 4, 0, 4, "minecraft:stone_bricks");
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:dark_oak_planks");
		p.ring(4, 4, 1, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks");
		for (int y = 2; y <= 4; y++) {
			p.ring(4, 4, y, "minecraft:bricks");
		}
		for (int y = 1; y <= 4; y++) {
			for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {-4, 0}, {4, 0}, {-1, -4}, {1, -4}}) {
				p.set(c[0], y, c[1], post);
			}
		}
		// windows: pairs at the front and sides, a tall amber one behind the counter
		p.fill(-3, 2, -4, -2, 3, -4, "minecraft:glass_pane");
		p.fill(2, 2, -4, 3, 3, -4, "minecraft:glass_pane");
		for (int x : new int[] {-4, 4}) {
			p.fill(x, 2, -2, x, 3, -1, "minecraft:glass_pane");
			p.fill(x, 2, 1, x, 3, 2, "minecraft:glass_pane");
		}
		p.fill(0, 2, 4, 0, 4, 4, "minecraft:orange_stained_glass_pane");
		p.door(-4, "dark_oak");
		p.set(0, 4, -4, "minecraft:chiseled_stone_bricks");
		// slate roof, then a belfry with the bell and a spire
		p.hipRoof(4, 2, 5, "minecraft:deepslate_tile_stairs");
		p.fill(-1, 7, -1, 1, 7, 1, "minecraft:dark_oak_planks");
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			p.fill(c[0], 8, c[1], c[0], 9, c[1], "minecraft:dark_oak_fence");
		}
		p.set(0, 9, 0, "minecraft:bell[attachment=ceiling,facing=north]");
		p.hipRoof(1, 1, 10, "minecraft:deepslate_tile_stairs");
		p.set(0, 10, 0, "minecraft:deepslate_tiles");
		p.set(0, 11, 0, "minecraft:lightning_rod");
		// inside: a beam with lanterns, pews, a red carpet up to the counter, books and banners behind it
		p.fill(-3, 4, 0, 3, 4, 0, "minecraft:dark_oak_log[axis=x]");
		p.set(-2, 3, 0, "minecraft:lantern[hanging=true]");
		p.set(2, 3, 0, "minecraft:lantern[hanging=true]");
		p.fill(-1, 1, -3, 1, 1, 1, "minecraft:red_carpet");
		for (int z = -2; z <= 0; z++) {
			p.stairs(-3, 1, z, "minecraft:dark_oak_stairs", "west", false);
			p.stairs(3, 1, z, "minecraft:dark_oak_stairs", "east", false);
		}
		p.set(-3, 1, -3, "minecraft:potted_fern");
		p.set(3, 1, -3, "minecraft:potted_fern");
		p.set(COUNTER.getX(), COUNTER.getY(), COUNTER.getZ(), "minecraft:lectern[facing=north]");
		p.set(-1, 1, 2, "minecraft:dark_oak_slab[type=top]");
		p.set(1, 1, 2, "minecraft:dark_oak_slab[type=top]");
		p.fill(-3, 1, 3, -2, 3, 3, "minecraft:bookshelf");
		p.fill(2, 1, 3, 3, 3, 3, "minecraft:bookshelf");
		p.set(-1, 3, 3, "minecraft:red_wall_banner[facing=north]");
		p.set(1, 3, 3, "minecraft:red_wall_banner[facing=north]");
	}

	/** A narrow Dutch canal house: brick, white trim and a stepped gable at both ends. */
	private static void residence(Plan p) {
		String slab = "minecraft:stone_brick_slab[type=bottom]";
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:stone_bricks");
		p.fill(-2, 0, -2, 2, 0, 2, "minecraft:spruce_planks");
		p.ring(3, 3, 1, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks");
		for (int y = 2; y <= 4; y++) {
			p.ring(3, 3, y, "minecraft:bricks");
		}
		// the roof runs front to back between two stepped gables
		for (int k = 0; k <= 2; k++) {
			for (int z = -2; z <= 2; z++) {
				p.stairs(-3 + k, 5 + k, z, "minecraft:dark_oak_stairs", "east", false);
				p.stairs(3 - k, 5 + k, z, "minecraft:dark_oak_stairs", "west", false);
			}
		}
		p.fill(0, 8, -2, 0, 8, 2, "minecraft:dark_oak_slab[type=bottom]");
		for (int z : new int[] {-3, 3}) {
			for (int k = 0; k <= 3; k++) {
				p.fill(-3 + k, 5 + k, z, 3 - k, 5 + k, z, "minecraft:bricks");
				p.set(-3 + k, 6 + k, z, slab);
				p.set(3 - k, 6 + k, z, slab);
			}
			p.set(0, 6, z, "minecraft:glass_pane");
		}
		// door, windows and white lintels
		p.door(-3, "dark_oak");
		p.set(0, 3, -3, "minecraft:glass_pane");
		p.fill(-2, 2, -3, -2, 3, -3, "minecraft:glass_pane");
		p.fill(2, 2, -3, 2, 3, -3, "minecraft:glass_pane");
		p.fill(-2, 4, -3, 2, 4, -3, "minecraft:smooth_quartz");
		p.fill(-3, 2, 0, -3, 3, 0, "minecraft:glass_pane");
		p.fill(3, 2, 0, 3, 3, 0, "minecraft:glass_pane");
		p.set(-3, 4, 0, "minecraft:smooth_quartz");
		p.set(3, 4, 0, "minecraft:smooth_quartz");
		p.set(-1, 3, 3, "minecraft:glass_pane");
		p.set(1, 3, 3, "minecraft:glass_pane");
		// inside: beds along the back, a rug, a table with flowers, a wash tub, lanterns from the ridge
		String[] colours = {"red", "blue", "green", "yellow"};
		int[] xs = {-2, -1, 1, 2};
		for (int i = 0; i < 4; i++) {
			p.set(xs[i], 1, 1, "minecraft:" + colours[i] + "_bed[facing=south,part=foot]");
			p.set(xs[i], 1, 2, "minecraft:" + colours[i] + "_bed[facing=south,part=head]");
		}
		p.fill(-1, 1, -2, 1, 1, 0, "minecraft:brown_carpet");
		p.set(-2, 1, -2, "minecraft:crafting_table");
		p.set(-2, 2, -2, "minecraft:potted_red_tulip");
		p.set(2, 1, -2, "minecraft:water_cauldron[level=3]");
		p.set(2, 1, 0, "minecraft:bookshelf");
		p.set(0, 7, -1, "minecraft:lantern[hanging=true]");
		p.set(0, 7, 1, "minecraft:lantern[hanging=true]");
	}

	/** A fenced plot with a pond down the middle, six crops, a scarecrow and lamps on the corner posts. */
	private static void farm(Plan p) {
		p.fillMix(-4, 0, -4, 4, 0, 4, "minecraft:coarse_dirt", "minecraft:rooted_dirt");
		p.fill(-3, 0, -3, 2, 0, 2, "minecraft:farmland[moisture=7]");
		p.fill(0, 0, -3, 0, 0, 2, "minecraft:water");
		p.set(0, 1, -2, "minecraft:lily_pad");
		p.set(0, 1, 1, "minecraft:lily_pad");
		p.ring(4, 4, 1, "minecraft:oak_fence");
		p.set(0, 0, -4, "minecraft:dirt_path");
		p.set(0, 1, -4, "minecraft:oak_fence_gate[facing=south,open=false]");
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			p.set(c[0], 1, c[1], "minecraft:oak_log[axis=y]");
			p.set(c[0], 2, c[1], "minecraft:oak_fence");
			p.set(c[0], 3, c[1], "minecraft:lantern[hanging=false]");
		}
		p.fill(-3, 1, -3, -2, 1, 2, "minecraft:wheat[age=7]");
		p.fill(-1, 1, -3, -1, 1, 2, "minecraft:carrots[age=7]");
		p.fill(1, 1, -3, 1, 1, 2, "minecraft:potatoes[age=7]");
		p.fill(2, 1, -3, 2, 1, 2, "minecraft:beetroots[age=3]");
		p.set(3, 1, -3, "minecraft:pumpkin");
		p.set(3, 1, -1, "minecraft:melon");
		p.set(3, 1, 1, "minecraft:pumpkin");
		// a scarecrow in the wheat
		p.set(-2, 0, 0, "minecraft:coarse_dirt");
		p.set(-2, 1, 0, "minecraft:oak_fence");
		p.set(-2, 2, 0, "minecraft:hay_block[axis=y]");
		p.set(-2, 3, 0, "minecraft:carved_pumpkin[facing=north]");
		p.set(-3, 2, 0, "minecraft:oak_fence");
		p.set(-1, 2, 0, "minecraft:oak_fence");
		// along the back: hay, a rain barrel and the compost
		p.set(-3, 1, 3, "minecraft:water_cauldron[level=3]");
		p.set(-2, 1, 3, "minecraft:carved_pumpkin[facing=north]");
		p.set(1, 1, 3, "minecraft:hay_block[axis=x]");
		p.set(2, 1, 3, "minecraft:hay_block[axis=x]");
		p.set(2, 2, 3, "minecraft:hay_block[axis=x]");
		p.set(3, 1, 3, "minecraft:composter[level=4]");
	}

	/** A clearing with a sawing shed, log piles, a campfire, a tree nursery and two trees that never get chopped. */
	private static void lumberCamp(Plan p) {
		p.fillMix(-4, 0, -4, 4, 0, 4, "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:rooted_dirt");
		p.fill(0, 0, -4, 0, 0, 0, "minecraft:dirt_path");
		p.ring(4, 4, 1, "minecraft:spruce_fence");
		p.fill(-1, 1, -4, 1, 1, -4, "minecraft:air");
		// the shed, back left
		for (int[] c : new int[][] {{-4, 1}, {-1, 1}, {-4, 4}, {-1, 4}}) {
			p.fill(c[0], 1, c[1], c[0], 3, c[1], "minecraft:spruce_log[axis=y]");
		}
		p.fill(-3, 1, 4, -2, 3, 4, "minecraft:spruce_planks");
		p.fill(-4, 1, 2, -4, 3, 3, "minecraft:spruce_planks");
		p.fill(-4, 4, 1, -1, 4, 4, "minecraft:spruce_slab[type=bottom]");
		p.set(-3, 1, 3, "minecraft:stonecutter[facing=north]");
		p.set(-2, 1, 3, "minecraft:crafting_table");
		p.set(-3, 1, 2, "minecraft:grindstone[face=floor,facing=east]");
		p.set(-2, 3, 2, "minecraft:lantern[hanging=true]");
		// log piles, back right
		p.fill(1, 1, 4, 3, 2, 4, "minecraft:spruce_log[axis=x]");
		p.set(2, 3, 4, "minecraft:spruce_log[axis=x]");
		p.fill(1, 1, 3, 3, 1, 3, "minecraft:oak_log[axis=x]");
		p.set(2, 2, 3, "minecraft:birch_log[axis=x]");
		p.fill(3, 1, 1, 3, 1, 2, "minecraft:stripped_oak_log[axis=z]");
		// campfire with two log benches, and the chopping stump
		p.set(-2, 1, -1, "minecraft:campfire[lit=true,facing=north]");
		p.set(-2, 1, -2, "minecraft:stripped_spruce_log[axis=x]");
		p.set(-2, 1, 0, "minecraft:stripped_spruce_log[axis=x]");
		p.set(1, 1, 0, "minecraft:oak_log[axis=y]");
		// the nursery (in pots, so they don't grow into trees in the middle of the camp)
		p.fill(2, 0, -1, 3, 0, 0, "minecraft:podzol");
		p.set(2, 1, -1, "minecraft:potted_spruce_sapling");
		p.set(3, 1, -1, "minecraft:potted_fern");
		p.set(2, 1, 0, "minecraft:potted_oak_sapling");
		p.set(3, 1, 0, "minecraft:potted_birch_sapling");
		// two trees that never get chopped (the real ones are somewhere else, trust us)
		tree(p, -3, -3, "oak");
		tree(p, 3, -3, "birch");
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

	/** A grassy hill with a timbered tunnel, ore showing in the rock, rails out to a yard with a blast furnace. */
	private static void mine(Plan p) {
		p.fillMix(-3, 0, -3, 3, 0, 3, "minecraft:cobblestone", "minecraft:gravel", "minecraft:andesite");
		for (int x = -3; x <= 3; x++) {
			for (int z = 0; z <= 3; z++) {
				int ax = Math.abs(x);
				int top = z == 0 ? 3 : z == 1 ? (ax <= 2 ? 4 : 3) : (ax <= 1 ? 5 : ax <= 2 ? 4 : 3);
				for (int y = 1; y < top; y++) {
					p.set(x, y, z, mix(x, y, z, "minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:tuff"));
				}
				p.set(x, top, z, "minecraft:grass_block");
			}
		}
		p.fill(0, 1, 0, 0, 2, 2, "minecraft:air");
		p.fill(0, 1, 3, 0, 2, 3, "minecraft:deepslate");
		for (int z : new int[] {0, 2}) {
			p.fill(-1, 1, z, -1, 2, z, "minecraft:spruce_log[axis=y]");
			p.fill(1, 1, z, 1, 2, z, "minecraft:spruce_log[axis=y]");
			p.fill(-1, 3, z, 1, 3, z, "minecraft:spruce_log[axis=x]");
		}
		p.set(0, 2, 1, "minecraft:wall_torch[facing=east]");
		// ore showing in the rock
		p.set(-2, 2, 0, "minecraft:coal_ore");
		p.set(3, 2, 0, "minecraft:coal_ore");
		p.set(2, 1, 0, "minecraft:iron_ore");
		p.set(-3, 1, 0, "minecraft:copper_ore");
		p.set(-3, 2, 1, "minecraft:lapis_ore");
		p.set(-3, 2, 2, "minecraft:redstone_ore");
		p.set(3, 2, 2, "minecraft:gold_ore");
		p.set(3, 1, 3, "minecraft:diamond_ore");
		// rails out of the tunnel, and the yard
		p.fill(0, 1, -3, 0, 1, 2, "minecraft:rail[shape=north_south]");
		p.set(2, 1, -2, "minecraft:blast_furnace[facing=north]");
		p.set(3, 1, -1, "minecraft:raw_copper_block");
		p.set(-3, 1, -2, "minecraft:raw_iron_block");
		p.set(-3, 1, -1, "minecraft:coal_block");
		p.set(-3, 2, -1, "minecraft:cobblestone_slab[type=bottom]");
		p.set(-2, 1, -1, "minecraft:cobblestone");
		for (int x : new int[] {-3, 3}) {
			p.fill(x, 1, -3, x, 2, -3, "minecraft:spruce_fence");
			p.set(x, 3, -3, "minecraft:lantern[hanging=false]");
		}
	}

	/** An open-fronted smithy: a forge with a lava pool under a brick hood, a smoking chimney, a wall of tools. */
	private static void workshop(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:spruce_planks");
		p.fill(-3, 0, 1, -1, 0, 3, "minecraft:stone_bricks");
		p.fill(-3, 0, -3, 3, 0, -3, "minecraft:stone_bricks");
		p.walls(3, 1, 3, "minecraft:spruce_planks", "minecraft:spruce_log[axis=y]");
		p.fill(-3, 1, 0, -3, 3, 0, "minecraft:spruce_log[axis=y]");
		p.fill(3, 1, 0, 3, 3, 0, "minecraft:spruce_log[axis=y]");
		p.fill(-2, 1, -3, 2, 2, -3, "minecraft:air"); // open front
		p.fill(-2, 3, -3, 2, 3, -3, "minecraft:spruce_log[axis=x]");
		p.set(3, 2, -1, "minecraft:glass_pane");
		p.set(3, 2, 1, "minecraft:glass_pane");
		p.set(-3, 2, -1, "minecraft:glass_pane");
		p.gableAlongX(3, 3, 4, "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", "minecraft:spruce_planks");
		p.fill(-2, 4, 0, 2, 4, 0, "minecraft:spruce_log[axis=x]");
		p.set(0, 3, 0, "minecraft:lantern[hanging=true]");
		// the forge and its chimney
		p.set(-2, 1, 2, "minecraft:blast_furnace[facing=north]");
		p.set(-1, 1, 2, "minecraft:lava_cauldron");
		p.set(-2, 2, 2, "minecraft:bricks");
		p.fill(-2, 3, 2, -1, 3, 2, "minecraft:bricks");
		p.fill(-2, 1, 3, -2, 7, 3, "minecraft:bricks");
		p.set(-2, 8, 3, "minecraft:campfire[lit=true,facing=north]");
		p.set(-2, 1, 0, "minecraft:water_cauldron[level=3]");
		// the anvil and the benches
		p.set(0, 1, 2, "minecraft:anvil[facing=north]");
		p.set(1, 1, 2, "minecraft:furnace[facing=north]");
		p.set(2, 1, 2, "minecraft:crafting_table");
		p.set(2, 1, 1, "minecraft:smithing_table");
		p.set(2, 1, 0, "minecraft:grindstone[face=floor,facing=west]");
		p.set(2, 1, -1, "minecraft:stonecutter[facing=west]");
	}

	/** Walls of barrels (lids out) on a stone footing, a timber frame, a hayloft door and sacks and crates inside. */
	private static void storehouse(Plan p) {
		p.fill(-3, 0, -3, 3, 0, 3, "minecraft:stone_bricks");
		p.fill(-2, 0, -2, 2, 0, 2, "minecraft:spruce_planks");
		p.ring(3, 3, 1, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks");
		for (int y = 2; y <= 3; y++) {
			for (int i = -2; i <= 2; i++) {
				p.set(i, y, -3, "minecraft:barrel[facing=north]");
				p.set(i, y, 3, "minecraft:barrel[facing=south]");
				p.set(-3, y, i, "minecraft:barrel[facing=west]");
				p.set(3, y, i, "minecraft:barrel[facing=east]");
			}
		}
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			p.fill(c[0], 1, c[1], c[0], 4, c[1], "minecraft:spruce_log[axis=y]");
		}
		p.fill(-2, 4, -3, 2, 4, -3, "minecraft:spruce_log[axis=x]");
		p.fill(-2, 4, 3, 2, 4, 3, "minecraft:spruce_log[axis=x]");
		p.fill(-3, 4, -2, -3, 4, 2, "minecraft:spruce_log[axis=z]");
		p.fill(3, 4, -2, 3, 4, 2, "minecraft:spruce_log[axis=z]");
		p.door(-3, "spruce");
		p.gableAlongZ(3, 3, 5, "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", "minecraft:spruce_planks");
		p.set(0, 6, -3, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
		// inside: a tie beam with a lantern, hay, sacks of wool and stacked crates
		p.fill(-2, 4, 0, 2, 4, 0, "minecraft:spruce_log[axis=x]");
		p.set(0, 3, 0, "minecraft:lantern[hanging=true]");
		p.set(-2, 1, 2, "minecraft:hay_block[axis=y]");
		p.set(-2, 2, 2, "minecraft:white_carpet");
		p.set(-2, 1, 1, "minecraft:white_wool");
		p.set(-1, 1, 2, "minecraft:barrel[facing=up]");
		p.set(2, 1, 2, "minecraft:barrel[facing=up]");
		p.set(2, 2, 2, "minecraft:barrel[facing=up]");
		p.set(2, 1, 1, "minecraft:barrel[facing=up]");
		p.set(1, 1, 2, "minecraft:spruce_slab[type=bottom]");
	}

	/** A crenellated stone guardhouse with a flag, behind a walled drill yard with a training dummy and a target. */
	private static void barracks(Plan p) {
		p.fillMix(-4, 0, -4, 4, 0, 0, "minecraft:coarse_dirt", "minecraft:gravel");
		p.fill(-4, 0, 1, 4, 0, 4, "minecraft:stone_bricks");
		// the guardhouse
		for (int y = 1; y <= 4; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z : new int[] {1, 4}) {
					p.set(x, y, z, y == 1 ? mix(x, y, z, "minecraft:cobblestone", "minecraft:mossy_cobblestone")
						: mix(x, y, z, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"));
				}
			}
			for (int x : new int[] {-4, 4}) {
				for (int z = 2; z <= 3; z++) {
					p.set(x, y, z, y == 1 ? "minecraft:cobblestone" : mix(x, y, z, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks"));
				}
			}
		}
		for (int[] c : new int[][] {{-4, 1}, {4, 1}, {-4, 4}, {4, 4}}) {
			p.fill(c[0], 1, c[1], c[0], 4, c[1], "minecraft:polished_andesite");
		}
		p.fill(-1, 1, 1, 1, 3, 1, "minecraft:air"); // big enough for an iron golem
		p.stairs(-1, 3, 1, "minecraft:stone_brick_stairs", "west", true);
		p.stairs(1, 3, 1, "minecraft:stone_brick_stairs", "east", true);
		p.fill(-3, 2, 1, -3, 3, 1, "minecraft:iron_bars");
		p.fill(3, 2, 1, 3, 3, 1, "minecraft:iron_bars");
		p.set(-4, 3, 2, "minecraft:iron_bars");
		p.set(4, 3, 2, "minecraft:iron_bars");
		// flat roof with battlements and a flag
		p.fill(-4, 5, 1, 4, 5, 4, "minecraft:smooth_stone");
		for (int x = -4; x <= 4; x++) {
			for (int z = 1; z <= 4; z++) {
				boolean edge = Math.abs(x) == 4 || z == 1 || z == 4;
				if (edge && Math.floorMod(x + z, 2) == 0) {
					p.set(x, 6, z, mix(x, 6, z, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks"));
				}
			}
		}
		p.fill(3, 6, 3, 3, 9, 3, "minecraft:spruce_fence");
		p.fill(1, 8, 3, 2, 9, 3, "minecraft:red_wool");
		// the armoury inside
		p.set(-3, 1, 3, "minecraft:smithing_table");
		p.set(-2, 1, 3, "minecraft:grindstone[face=floor,facing=north]");
		p.set(-3, 1, 2, "minecraft:anvil[facing=east]");
		p.set(2, 1, 3, "minecraft:fletching_table");
		p.set(3, 1, 3, "minecraft:target");
		p.set(3, 1, 2, "minecraft:hay_block[axis=y]");
		p.fill(-1, 1, 2, 1, 1, 3, "minecraft:red_carpet");
		p.set(-1, 3, 3, "minecraft:red_wall_banner[facing=north]");
		p.set(1, 3, 3, "minecraft:red_wall_banner[facing=north]");
		p.set(-2, 4, 2, "minecraft:lantern[hanging=true]");
		p.set(2, 4, 2, "minecraft:lantern[hanging=true]");
		// the drill yard: a low wall with an opening at the front, lamp pillars, a dummy and a target
		for (int z = -4; z <= 0; z++) {
			p.set(-4, 1, z, "minecraft:cobblestone_wall");
			p.set(4, 1, z, "minecraft:cobblestone_wall");
		}
		for (int x = -4; x <= 4; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, -4, "minecraft:cobblestone_wall");
			}
		}
		for (int x : new int[] {-4, 4}) {
			p.fill(x, 1, -4, x, 2, -4, "minecraft:stone_bricks");
			p.set(x, 3, -4, "minecraft:lantern[hanging=false]");
		}
		p.set(-2, 1, -2, "minecraft:oak_fence");
		p.set(-2, 2, -2, "minecraft:hay_block[axis=y]");
		p.set(-2, 3, -2, "minecraft:carved_pumpkin[facing=north]");
		p.set(-3, 2, -2, "minecraft:oak_fence");
		p.set(-1, 2, -2, "minecraft:oak_fence");
		p.set(2, 1, -2, "minecraft:hay_block[axis=y]");
		p.set(2, 2, -2, "minecraft:target");
		p.set(3, 1, 0, "minecraft:water_cauldron[level=3]");
	}

	/**
	 * A stone tower: a door and a ladder inside, a bunk room at wall-walk height with openings on all four
	 * sides (so walls run straight into it), a battlemented top for the archers and a pointed roof.
	 */
	private static void watchtower(Plan p) {
		Palette s = Palette.of(p.tier);
		p.fill(-3, 0, -3, 3, 0, 3, s.plinth);
		for (int y = 1; y <= 9; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (Math.abs(x) == 3 || Math.abs(z) == 3) {
						p.set(x, y, z, y == 1 ? s.plinth : s.stone(x, y, z));
					}
				}
			}
			for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
				p.set(c[0], y, c[1], s.quoin);
			}
		}
		p.door(-3, s.wood);
		p.set(0, 4, -3, "minecraft:iron_bars");
		p.fill(-3, 3, 0, -3, 4, 0, "minecraft:iron_bars");
		p.fill(3, 3, 0, 3, 4, 0, "minecraft:iron_bars");
		p.fill(0, 3, 3, 0, 4, 3, "minecraft:iron_bars");
		// floors, and the openings at wall-walk height
		p.fill(-2, 5, -2, 2, 5, 2, "minecraft:" + s.wood + "_planks");
		p.fill(-2, 9, -2, 2, 9, 2, s.floor);
		p.fill(-1, 6, -3, 1, 7, -3, "minecraft:air");
		p.fill(-1, 6, 3, 1, 7, 3, "minecraft:air");
		p.fill(-3, 6, -1, -3, 7, 1, "minecraft:air");
		p.fill(3, 6, -1, 3, 7, 1, "minecraft:air");
		p.fill(2, 1, 2, 2, 9, 2, "minecraft:ladder[facing=north]");
		// inside: a fletching table downstairs, a bunk upstairs, lanterns under each floor
		p.set(-2, 1, 2, "minecraft:fletching_table");
		p.set(-2, 1, 1, "minecraft:hay_block[axis=y]");
		p.set(0, 4, 0, "minecraft:lantern[hanging=true]");
		p.set(-2, 6, 1, "minecraft:red_bed[facing=south,part=foot]");
		p.set(-2, 6, 2, "minecraft:red_bed[facing=south,part=head]");
		p.set(0, 8, 0, "minecraft:lantern[hanging=true]");
		// the top: a parapet with a gap at each manned post, battlements, corner posts and a roof
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
	 * Nine blocks of thick wall. The side facing whoever places it is the inside: an arched alcove with a bench
	 * and a lantern, and a ladder up. On top: a three-wide walkway, a railing inside, battlements outside.
	 */
	private static void wall(Plan p) {
		Palette s = Palette.of(p.tier);
		p.fill(-4, 0, -2, 4, 0, 2, s.body);
		for (int x = -4; x <= 4; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 1; y <= 5; y++) {
					p.set(x, y, z, y == 1 ? s.plinth : s.stone(x, y, z));
				}
			}
		}
		p.fill(-4, 5, -1, 4, 5, 1, s.floor);
		// outside: pilasters at the joints, a corbelled parapet with battlements and a pinnacle
		for (int x : new int[] {-4, 4}) {
			p.fill(x, 1, 2, x, 6, 2, s.quoin);
		}
		for (int x = -3; x <= 3; x++) {
			p.stairs(x, 5, 2, s.stairs, "north", true);
			p.set(x, 6, 2, s.stone(x, 6, 2));
		}
		for (int x = -4; x <= 4; x += 2) {
			p.set(x, 7, 2, x == 4 ? s.quoin : s.stone(x, 7, 2));
		}
		p.set(4, 8, 2, s.wall);
		// inside: pillars, an arched alcove with a bench, a ladder bay, a railing along the walkway
		for (int x : new int[] {-4, 0, 4}) {
			p.fill(x, 1, -2, x, 5, -2, s.quoin);
		}
		p.fill(-3, 1, -2, -1, 3, -2, "minecraft:air");
		p.stairs(-3, 3, -2, s.stairs, "west", true);
		p.stairs(-1, 3, -2, s.stairs, "east", true);
		p.set(-2, 3, -2, "minecraft:lantern[hanging=true]");
		p.stairs(-2, 1, -2, "minecraft:" + s.wood + "_stairs", "south", false);
		for (int x : new int[] {-3, -2, -1, 1, 3}) {
			p.stairs(x, 5, -2, s.stairs, "south", true);
		}
		p.fill(2, 1, -2, 2, 5, -2, "minecraft:ladder[facing=north]");
		for (int x = -4; x <= 4; x++) {
			if (x != 2) {
				p.set(x, 6, -2, s.wall);
			}
		}
	}

	/**
	 * Two squat towers either side of an arched passage, closed by fence gates halfway (you can open them,
	 * monsters can't). The deck on top joins the wall-walk of the walls on either side.
	 */
	private static void gatehouse(Plan p) {
		Palette s = Palette.of(p.tier);
		p.fill(-3, 0, -3, 3, 0, 3, s.body);
		p.fill(-1, 0, -3, 1, 0, 3, s.plinth);
		for (int x : new int[] {-3, -2, 2, 3}) {
			for (int z = -3; z <= 3; z++) {
				for (int y = 1; y <= 5; y++) {
					p.set(x, y, z, y == 1 ? s.plinth : s.stone(x, y, z));
				}
			}
		}
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			p.fill(c[0], 1, c[1], c[0], 6, c[1], s.quoin);
		}
		// the passage: an arch the whole way through, lanterns, and the gates halfway
		p.fill(-1, 4, -3, 1, 5, 3, s.body);
		for (int z = -3; z <= 3; z++) {
			p.stairs(-1, 3, z, s.stairs, "west", true);
			p.stairs(1, 3, z, s.stairs, "east", true);
		}
		p.set(0, 3, -2, "minecraft:lantern[hanging=true]");
		p.set(0, 3, 2, "minecraft:lantern[hanging=true]");
		for (int x = -1; x <= 1; x++) {
			p.set(x, 1, 0, "minecraft:" + s.wood + "_fence_gate[facing=north,open=false]");
		}
		p.set(-2, 3, 3, "minecraft:iron_bars");
		p.set(2, 3, 3, "minecraft:iron_bars");
		// the deck: a walkway between battlements outside and a railing inside, and a ladder up the inside
		p.fill(-3, 5, -1, 3, 5, 1, s.floor);
		for (int x = -3; x <= 3; x++) {
			p.set(x, 6, 3, s.stone(x, 6, 3));
			p.set(x, 6, 2, s.stone(x, 6, 2));
			if (Math.floorMod(x, 2) != 0) {
				p.set(x, 7, 3, s.stone(x, 7, 3));
			}
			if (x != -3) {
				p.set(x, 6, -3, s.wall);
				p.set(x, 6, -2, s.wall);
			}
		}
		p.fill(-3, 7, 3, -3, 8, 3, s.quoin);
		p.fill(3, 7, 3, 3, 8, 3, s.quoin);
		p.set(-3, 9, 3, s.wall);
		p.set(3, 9, 3, s.wall);
		p.set(3, 7, -3, s.wall);
		p.fill(-3, 1, -3, -3, 5, -3, "minecraft:ladder[facing=north]");
		p.set(-3, 6, -3, "minecraft:air");
		p.set(-3, 6, -2, "minecraft:air");
	}
}
