package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Every loaded airship: routes ticks, clicks and damage. */
public final class Airships {
	private static final Map<UUID, Airship> BY_ROOT = new HashMap<>();
	private static final Map<UUID, Airship> BY_MARKER = new HashMap<>();
	private static final Map<UUID, Airship> RIDERS = new HashMap<>();
	/** The tick of each passenger's last right-click on their airship (one click arrives as two packets). */
	private static final Map<UUID, Integer> LAST_CLICK = new HashMap<>();
	/**
	 * The tick each player last used one of our items on a block. A vanilla client follows a right-click on a block
	 * with a plain "use item" packet, which must not unfold a second airship or nag about the bomb just lit.
	 */
	private static final Map<UUID, Integer> LAST_USE = new HashMap<>();
	private static final List<Entity> PENDING = new ArrayList<>();
	private static final List<Entity> STRAYS = new ArrayList<>();

	private Airships() {
	}

	// ---------------------------------------------------------------- registry

	static Airship register(ServerLevel level, Entity root, AirshipData data) {
		Airship existing = BY_ROOT.get(root.getUUID());
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		Airship ship = new Airship(level, root, data);
		BY_ROOT.put(root.getUUID(), ship);
		return ship;
	}

	static void unregister(Airship ship) {
		BY_ROOT.values().removeIf(s -> s == ship);
		BY_MARKER.values().removeIf(s -> s == ship);
		RIDERS.values().removeIf(s -> s == ship);
	}

	static void registerMarker(Entity marker, Airship ship) {
		BY_MARKER.put(marker.getUUID(), ship);
	}

	static void rememberRider(UUID player, Airship ship) {
		RIDERS.put(player, ship);
	}

	static void forgetRider(UUID player, Airship ship) {
		RIDERS.remove(player, ship);
	}

	/** The airship this entity is sitting in, or null. */
	static @Nullable Airship shipOf(Entity entity) {
		Airship ship = RIDERS.get(entity.getUUID());
		if (ship == null || ship.isRemoved() || entity.getVehicle() == null) {
			return null;
		}
		return BY_MARKER.get(entity.getVehicle().getUUID()) == ship ? ship : null;
	}

	/** Is this entity sitting in any airship? (Its own bombs fly past the crew.) */
	static boolean isAboard(Entity entity) {
		return shipOf(entity) != null;
	}

	static List<Airship> all() {
		return new ArrayList<>(BY_ROOT.values());
	}

	// ---------------------------------------------------------------- lifecycle

	static void onLoad(Entity entity, ServerLevel level) {
		if (entity.hasAttached(BlimeyMod.DATA)) {
			PENDING.add(entity);
		} else if ((entity.hasAttached(BlimeyMod.MARKER) && !BY_MARKER.containsKey(entity.getUUID()))
			|| (entity.hasAttached(BlimeyMod.BOMB) && !Bomb.isLive(entity))) {
			STRAYS.add(entity);
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		if (entity.hasAttached(BlimeyMod.DATA)) {
			Airship ship = BY_ROOT.get(entity.getUUID());
			if (ship != null) {
				BlimeyMod.nextTick(ship::unload);
			}
		}
	}

	static void tick() {
		if (!PENDING.isEmpty()) {
			List<Entity> pending = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Entity root : pending) {
				AirshipData data = root.getAttached(BlimeyMod.DATA);
				if (!root.isRemoved() && data != null && root.level() instanceof ServerLevel level) {
					register(level, root, data);
				}
			}
		}
		if (!STRAYS.isEmpty()) {
			List<Entity> strays = new ArrayList<>(STRAYS);
			STRAYS.clear();
			for (Entity stray : strays) {
				if (!stray.isRemoved() && !BY_MARKER.containsKey(stray.getUUID()) && !Bomb.isLive(stray)) {
					stray.ejectPassengers();
					stray.discard();
				}
			}
		}
		for (Airship ship : all()) {
			if (ship.isRemoved()) {
				continue;
			}
			try {
				ship.tick();
			} catch (RuntimeException e) {
				BlimeyMod.LOG.error("Airship {} crashed while ticking; stopping it", ship.data.name, e);
				ship.speed = 0;
				ship.climb = 0;
			}
		}
	}

	static void shutdown() {
		for (Airship ship : all()) {
			ship.unload();
		}
		BY_ROOT.clear();
		BY_MARKER.clear();
		RIDERS.clear();
		LAST_CLICK.clear();
		LAST_USE.clear();
		PENDING.clear();
		STRAYS.clear();
	}

	static void onDisconnect(ServerPlayer player) {
		LAST_CLICK.remove(player.getUUID());
		LAST_USE.remove(player.getUUID());
		if (shipOf(player) != null) {
			player.stopRiding();
		}
	}

	/** Crew don't take fall or blast damage aboard: their own bombs go off underneath them, after all. */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || shipOf(player) == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_FIRE);
	}

	// ---------------------------------------------------------------- unfolding

	/** Summons an airship with its keel at y and registers it. */
	static @Nullable Airship launch(ServerLevel level, AirshipData data, double x, double y, double z, float yaw) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, Airship.summonCommand(data, id, x, y, z, yaw));
		Entity root = level.getEntity(id);
		if (root == null) {
			BlimeyMod.LOG.error("Could not summon airship {}", data.name);
			return null;
		}
		root.setAttached(BlimeyMod.DATA, data);
		PENDING.remove(root);
		return register(level, root, data);
	}

	/** Right-clicking the ground with a Flat-Pack Airship. */
	static InteractionResult unfold(ServerPlayer player, ServerLevel level, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		HitResult hit = player.pick(24, 0, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			player.sendSystemMessage(Component.literal("Point it at the ground where you want it.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		BlockPos at = blockHit.getBlockPos();
		double keel = at.getY() + 1.0;
		float yaw = player.getYRot();
		// the stern goes where you clicked, so it points away from you
		double[] centre = Airship.toWorld(at.getX() + 0.5, at.getZ() + 0.5, yaw, 0, 13);
		if (!Airship.fits(level, centre[0], keel, centre[1], yaw)) {
			player.sendSystemMessage(Component.literal("Not enough room. An airship needs about 9 × 26 blocks of clear ground and 14 blocks of sky above it.")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		AirshipData saved = BlimeyItems.savedData(held, level);
		AirshipData data = saved != null ? saved : new AirshipData();
		String name = BlimeyItems.customName(held);
		if (name != null && !name.isBlank()) {
			data.name = name;
		}
		data.owner = player.getUUID().toString();
		data.ownerName = player.getName().getString();
		Airship ship = launch(level, data, centre[0], keel, centre[1], yaw);
		if (ship == null) {
			player.sendSystemMessage(Component.literal("The instructions were in Swedish. It didn't unfold. Check the server log.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.anvil.place", centre[0], keel + 2, centre[1], 1.0f, 0.7f);
		Cmd.sound(level, "minecraft:block.iron_door.open", centre[0], keel + 2, centre[1], 1.0f, 0.6f);
		player.sendSystemMessage(Component.literal("The " + data.name + " unfolds with a lot of clanking. ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Right-click it to board. Sneak + right-click for the menu. Fill the tank with diesel first!")
				.withStyle(ChatFormatting.YELLOW)));
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- items in hand

	/** Right-click in the air. */
	static InteractionResult useItem(ServerPlayer player, ServerLevel level, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		Integer usedOnBlock = LAST_USE.get(player.getUUID());
		if (usedOnBlock != null && BlimeyMod.tickCount() - usedOnBlock <= 1
			&& (BlimeyItems.isAirship(held) || BlimeyItems.bombKind(held) != null || held.isEmpty())) {
			return InteractionResult.FAIL; // the second half of a right-click on a block, already handled
		}
		if (BlimeyItems.isAirship(held)) {
			return unfold(player, level, hand);
		}
		if (BlimeyItems.bombKind(held) == null) {
			return InteractionResult.PASS;
		}
		Airship ship = shipOf(player);
		if (ship != null) {
			return bombsAway(player, ship, held);
		}
		player.sendSystemMessage(Component.literal("Bombs go off when they hit something. Drop them from an airship, or right-click a block to set one with a fuse.")
			.withStyle(ChatFormatting.GRAY));
		return InteractionResult.FAIL;
	}

	/** Right-click on a block. */
	static InteractionResult useBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = useOnBlock(player, level, hand, hit);
		if (result != InteractionResult.PASS) {
			LAST_USE.put(player.getUUID(), BlimeyMod.tickCount());
		}
		return result;
	}

	private static InteractionResult useOnBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (BlimeyItems.isAirship(held)) {
			return unfold(player, level, hand);
		}
		BlimeyItems.BombKind kind = BlimeyItems.bombKind(held);
		if (kind == null) {
			return InteractionResult.PASS;
		}
		Airship ship = shipOf(player);
		if (ship != null) {
			return bombsAway(player, ship, held);
		}
		BlockPos spot = hit.getBlockPos().relative(hit.getDirection());
		if (!level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()) {
			return InteractionResult.FAIL;
		}
		int fuse = (int) Math.round(BlimeyConfig.get().fuseSeconds * 20);
		if (Bomb.launch(level, kind, new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5), Vec3.ZERO, fuse, null) == null) {
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		player.sendSystemMessage(Component.literal("The fuse is lit. RUN.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult bombsAway(ServerPlayer player, Airship ship, ItemStack held) {
		String why = ship.dropBomb(player, held);
		if (why != null) {
			player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- clicking an airship

	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		Airship ship = BY_MARKER.get(entity.getUUID());
		if (ship == null || ship.isRemoved()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (shipOf(player) == ship) {
			// the gondola's hitbox is all around a passenger, so every right-click lands on it
			int now = BlimeyMod.tickCount();
			Integer last = LAST_CLICK.put(player.getUUID(), now);
			if (last == null || now - last > 1) {
				useAboard(player, ship);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			AirshipMenu.open(player, ship);
			return InteractionResult.SUCCESS;
		}
		int seat = ship.pickSeat(player);
		if (seat < 0 || !ship.seat(player, seat)) {
			player.sendSystemMessage(Component.literal("No room aboard. You could cling to the outside, but you can't.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		player.sendSystemMessage(Component.literal("Welcome aboard the " + ship.data.name + " (" + ship.seatName(seat) + ").").withStyle(ChatFormatting.GOLD));
		if (seat == AirshipModel.CAPTAIN) {
			player.sendSystemMessage(Component.literal("W/S throttle · A/D turn · Space climb · Ctrl descend · Shift bail out · Empty-hand right-click or /blimey menu for the menu")
				.withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.literal("Hold a bomb and right-click to drop it · Shift to bail out · Empty-hand right-click for the menu")
				.withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}

	/** A passenger right-clicked: drop the bomb in their hand, use the item in their hand, or open the menu. */
	static void useAboard(ServerPlayer player, Airship ship) {
		ServerLevel level = (ServerLevel) player.level();
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (BlimeyItems.bombKind(held) != null) {
				bombsAway(player, ship, held);
				return;
			}
			if (!held.isEmpty() && player.gameMode.useItem(player, level, held, hand).consumesAction()) {
				return;
			}
		}
		if (player.getMainHandItem().isEmpty()) {
			AirshipMenu.open(player, ship);
		}
	}

	/** Every airship near a point (for the smoke test and /blimey). */
	static List<Airship> near(ServerLevel level, Vec3 pos, double radius) {
		List<Airship> found = new ArrayList<>();
		AABB box = new AABB(pos, pos).inflate(radius);
		for (Airship ship : all()) {
			if (!ship.isRemoved() && ship.level == level && box.contains(ship.root.position())) {
				found.add(ship);
			}
		}
		return found;
	}
}
