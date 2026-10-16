package com.thatcoffeelock.blimey;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Planted charges: a bomb set down unlit (sneak + right-click on foot). It sits there until something sets it off: a
 * redstone signal (button, lever, plate, tripwire...), flint and steel or a fire charge, fire or lava next to it, or
 * another bomb going off nearby. Then it's an ordinary bomb on a short fuse. Punch it to pick it back up.
 *
 * It's an item display (the look) plus an interaction entity (something to click and punch). Charges are saved, so a
 * booby trap survives a restart; their entities are tagged so strays left behind get cleaned up.
 */
final class Charges {
	static final String TAG = "blimey_charge";
	/** How long the fuse burns once a charge is set off: 4 seconds by hand, a little less when a blast sets it off. */
	static final int CHAIN_FUSE = 10;

	static final class Charge {
		String dimension = "";
		int x;
		int y;
		int z;
		String kind = "";
		String owner = "";
		String display = "";
		String hitbox = "";

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		@Nullable BlimeyItems.BombKind kind() {
			return BlimeyItems.BombKind.byId(kind);
		}

		@Nullable UUID owner() {
			try {
				return owner.isEmpty() ? null : UUID.fromString(owner);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final List<Charge> ALL = new ArrayList<>();
	private static @Nullable MinecraftServer server;
	private static boolean dirty;

	private Charges() {
	}

	static String dim(ServerLevel level) {
		return level.dimension().toString();
	}

	static @Nullable Charge at(ServerLevel level, BlockPos pos) {
		String d = dim(level);
		for (Charge c : ALL) {
			if (c.dimension.equals(d) && c.x == pos.getX() && c.y == pos.getY() && c.z == pos.getZ()) {
				return c;
			}
		}
		return null;
	}

	static @Nullable Charge of(Entity entity) {
		String id = entity.getUUID().toString();
		for (Charge c : ALL) {
			if (c.display.equals(id) || c.hitbox.equals(id)) {
				return c;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- planting

	/** Sets a bomb down unlit on top of {@code spot} (an empty block with solid ground under it). Returns why not, or null. */
	static @Nullable String plant(ServerLevel level, BlockPos spot, BlimeyItems.BombKind kind, @Nullable UUID owner) {
		if (!level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()) {
			return "There's no room there.";
		}
		BlockPos below = spot.below();
		if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
			return "A charge needs solid ground under it.";
		}
		if (at(level, spot) != null) {
			return "There's a charge there already.";
		}
		UUID displayId = UUID.randomUUID();
		UUID hitboxId = UUID.randomUUID();
		float s = kind.scale;
		double x = spot.getX() + 0.5;
		double y = spot.getY();
		double z = spot.getZ() + 0.5;
		Cmd.run(level, "summon minecraft:item_display " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(displayId) + ",Tags:[\"" + TAG
			+ "\"],item:{id:\"" + kind.model + "\",count:1},transformation:{left_rotation:[0f,0f,0f,1f],"
			+ "right_rotation:[0f,0f,0f,1f],translation:[0f," + Cmd.f(s / 2) + "f,0f],scale:[" + Cmd.f(s) + "f," + Cmd.f(s) + "f," + Cmd.f(s) + "f]}}");
		float w = Math.max(0.6f, Math.min(1.0f, s));
		Cmd.run(level, "summon minecraft:interaction " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(hitboxId) + ",Tags:[\"" + TAG
			+ "\"],width:" + Cmd.f(w) + "f,height:" + Cmd.f(Math.max(0.6f, s)) + "f,response:1b}");
		if (level.getEntity(displayId) == null || level.getEntity(hitboxId) == null) {
			discard(level, displayId.toString());
			discard(level, hitboxId.toString());
			return "The charge wouldn't sit there.";
		}
		Charge c = new Charge();
		c.dimension = dim(level);
		c.x = spot.getX();
		c.y = spot.getY();
		c.z = spot.getZ();
		c.kind = kind.id;
		c.owner = owner == null ? "" : owner.toString();
		c.display = displayId.toString();
		c.hitbox = hitboxId.toString();
		ALL.add(c);
		changed();
		Cmd.sound(level, "minecraft:block.stone.place", x, y, z, 1.0f, 0.8f);
		return null;
	}

	/** Lights it: the charge becomes an ordinary bomb on a fuse. */
	static @Nullable Bomb ignite(ServerLevel level, Charge c, int fuse) {
		BlimeyItems.BombKind kind = c.kind();
		forget(level, c);
		if (kind == null) {
			return null;
		}
		Cmd.sound(level, "minecraft:item.flintandsteel.use", c.x + 0.5, c.y + 0.5, c.z + 0.5, 1.0f, 1.0f);
		return Bomb.launch(level, kind, new Vec3(c.x + 0.5, c.y, c.z + 0.5), Vec3.ZERO, Math.max(1, fuse), c.owner());
	}

	/** Takes it apart again: the bomb drops where it stood. */
	static void disarm(ServerLevel level, Charge c) {
		BlimeyItems.BombKind kind = c.kind();
		forget(level, c);
		if (kind != null) {
			ItemEntity drop = new ItemEntity(level, c.x + 0.5, c.y + 0.3, c.z + 0.5, BlimeyItems.bomb(kind, 1));
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}
		Cmd.sound(level, "minecraft:block.stone.break", c.x + 0.5, c.y + 0.5, c.z + 0.5, 1.0f, 1.0f);
	}

	private static void forget(ServerLevel level, Charge c) {
		ALL.remove(c);
		discard(level, c.display);
		discard(level, c.hitbox);
		changed();
	}

	private static void discard(ServerLevel level, String id) {
		try {
			Entity e = level.getEntity(UUID.fromString(id));
			if (e != null) {
				e.discard();
			}
		} catch (IllegalArgumentException ignored) {
			// not a UUID: nothing to remove
		}
	}

	// ---------------------------------------------------------------- what sets them off

	/** Right-click on a charge: flint and steel or a fire charge lights it. */
	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		Charge c = of(entity);
		if (c == null || !(entity.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) {
			if (!player.isCreative()) {
				if (held.is(Items.FIRE_CHARGE)) {
					held.shrink(1);
				} else {
					held.setDamageValue(held.getDamageValue() + 1);
					if (held.getDamageValue() >= held.getMaxDamage()) {
						held.shrink(1);
					}
				}
			}
			ignite(level, c, (int) Math.round(BlimeyConfig.get().fuseSeconds * 20));
			player.sendSystemMessage(Component.literal("The fuse is lit. RUN.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			return InteractionResult.SUCCESS;
		}
		BlimeyItems.BombKind kind = c.kind();
		player.sendSystemMessage(Component.literal("A planted " + (kind == null ? "bomb" : kind.title)
			+ ". Light it with flint and steel or redstone; punch it to take it back.").withStyle(ChatFormatting.GRAY));
		return InteractionResult.SUCCESS;
	}

	/** Punching a charge picks it back up (it's safe until lit). */
	static InteractionResult attackEntity(ServerPlayer player, Entity entity) {
		Charge c = of(entity);
		if (c == null || !(entity.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		disarm(level, c);
		return InteractionResult.SUCCESS;
	}

	/** Every few ticks: redstone, fire and lava next to a charge set it off; ground gone from under it drops it. */
	static void tick(MinecraftServer srv) {
		if (ALL.isEmpty() || BlimeyMod.tickCount() % 4 != 0) {
			return;
		}
		for (Charge c : new ArrayList<>(ALL)) {
			ServerLevel level = level(srv, c.dimension);
			BlockPos pos = c.pos();
			if (level == null || !level.isLoaded(pos)) {
				continue;
			}
			if (triggered(level, pos)) {
				ignite(level, c, CHAIN_FUSE * 2);
				continue;
			}
			BlockPos below = pos.below();
			if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				disarm(level, c); // the floor went: it rolls off as an item
			}
		}
		if (dirty) {
			save();
		}
	}

	/** A redstone signal into the charge's spot or the ground under it, or fire or lava touching it. */
	static boolean triggered(ServerLevel level, BlockPos pos) {
		if (level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.below())) {
			return true;
		}
		if (burning(level.getBlockState(pos))) {
			return true;
		}
		for (Direction d : Direction.values()) {
			if (burning(level.getBlockState(pos.relative(d)))) {
				return true;
			}
		}
		return false;
	}

	private static boolean burning(BlockState state) {
		return state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA);
	}

	/** A bomb went off: charges in its reach go up a moment later. */
	static void chain(ServerLevel level, Vec3 at, float power) {
		double reach = power * 1.5 + 1;
		String d = dim(level);
		for (Charge c : new ArrayList<>(ALL)) {
			if (c.dimension.equals(d) && at.distanceToSqr(c.x + 0.5, c.y + 0.5, c.z + 0.5) <= reach * reach) {
				ignite(level, c, CHAIN_FUSE + (int) (Math.random() * 10));
			}
		}
	}

	private static @Nullable ServerLevel level(MinecraftServer srv, String dimension) {
		for (ServerLevel level : srv.getAllLevels()) {
			if (dim(level).equals(dimension)) {
				return level;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- entities and saving

	/** A tagged entity loaded in: if no charge owns it, it's left over from one that's gone. */
	static boolean isStray(Entity entity) {
		return entity.getTags().contains(TAG) && of(entity) == null;
	}

	static void changed() {
		dirty = true;
	}

	private static @Nullable Path file() {
		return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("blimey_charges.json");
	}

	static void load(MinecraftServer srv) {
		server = srv;
		ALL.clear();
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<Charge> loaded = GSON.fromJson(reader, new TypeToken<List<Charge>>() { }.getType());
			if (loaded != null) {
				for (Charge c : loaded) {
					if (c != null && c.kind() != null) {
						ALL.add(c);
					}
				}
			}
		} catch (Exception e) {
			BlimeyMod.LOG.error("Couldn't read the planted charges", e);
		}
	}

	static void save() {
		dirty = false;
		Path file = file();
		if (file == null) {
			return;
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(ALL, writer);
		} catch (Exception e) {
			BlimeyMod.LOG.error("Couldn't save the planted charges", e);
		}
	}

	static void stop() {
		save();
		ALL.clear();
		server = null;
	}
}
