package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Every loaded ship: routes ticks, clicks, block breaking and damage. */
public final class Ships {
	private static final Map<UUID, Ship> BY_ROOT = new HashMap<>();
	private static final Map<UUID, Ship> BY_MARKER = new HashMap<>();
	private static final Map<UUID, Ship> RIDERS = new HashMap<>();
	/** The tick of each passenger's last right-click on their ship (one click arrives as two packets). */
	private static final Map<UUID, Integer> LAST_CLICK = new HashMap<>();
	private static final List<Entity> PENDING = new ArrayList<>();
	private static final List<Entity> STRAYS = new ArrayList<>();

	private Ships() {
	}

	// ---------------------------------------------------------------- registry

	static Ship register(ServerLevel level, Entity root, ShipData data) {
		Ship existing = BY_ROOT.get(root.getUUID());
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		Ship ship = new Ship(level, root, data);
		BY_ROOT.put(root.getUUID(), ship);
		return ship;
	}

	static void unregister(Ship ship) {
		BY_ROOT.values().removeIf(s -> s == ship);
		BY_MARKER.values().removeIf(s -> s == ship);
		RIDERS.values().removeIf(s -> s == ship);
	}

	static void registerMarker(Entity marker, Ship ship) {
		BY_MARKER.put(marker.getUUID(), ship);
	}

	static void rememberRider(UUID player, Ship ship) {
		RIDERS.put(player, ship);
	}

	static void forgetRider(UUID player, Ship ship) {
		RIDERS.remove(player, ship);
	}

	static @Nullable Ship shipOf(Entity entity) {
		Ship ship = RIDERS.get(entity.getUUID());
		if (ship == null || ship.isRemoved() || entity.getVehicle() == null || BY_MARKER.get(entity.getVehicle().getUUID()) != ship) {
			return null;
		}
		return ship;
	}

	static List<Ship> all() {
		return new ArrayList<>(BY_ROOT.values());
	}

	// ---------------------------------------------------------------- lifecycle

	static void onLoad(Entity entity, ServerLevel level) {
		if (entity.hasAttached(AhoyMod.DATA)) {
			PENDING.add(entity);
		} else if (entity.hasAttached(AhoyMod.MARKER) && !BY_MARKER.containsKey(entity.getUUID())) {
			STRAYS.add(entity);
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		if (entity.hasAttached(AhoyMod.DATA)) {
			Ship ship = BY_ROOT.get(entity.getUUID());
			if (ship != null) {
				AhoyMod.nextTick(ship::unload);
			}
		}
	}

	static void tick() {
		if (!PENDING.isEmpty()) {
			List<Entity> pending = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Entity root : pending) {
				ShipData data = root.getAttached(AhoyMod.DATA);
				if (!root.isRemoved() && data != null && root.level() instanceof ServerLevel level) {
					register(level, root, data);
				}
			}
		}
		if (!STRAYS.isEmpty()) {
			List<Entity> strays = new ArrayList<>(STRAYS);
			STRAYS.clear();
			for (Entity stray : strays) {
				if (!stray.isRemoved() && !BY_MARKER.containsKey(stray.getUUID())) {
					stray.ejectPassengers();
					stray.discard();
				}
			}
		}
		for (Ship ship : all()) {
			if (ship.isRemoved()) {
				continue;
			}
			try {
				ship.tick();
			} catch (RuntimeException e) {
				AhoyMod.LOG.error("Ship {} crashed while ticking; stopping it", ship.data.name, e);
				ship.speed = 0;
			}
		}
	}

	static void shutdown() {
		for (Ship ship : all()) {
			ship.unload();
		}
		BY_ROOT.clear();
		BY_MARKER.clear();
		RIDERS.clear();
		LAST_CLICK.clear();
		PENDING.clear();
		STRAYS.clear();
	}

	static void onDisconnect(ServerPlayer player) {
		LAST_CLICK.remove(player.getUUID());
		if (shipOf(player) != null) {
			player.stopRiding();
		}
	}

	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		return !(entity instanceof ServerPlayer player) || shipOf(player) == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	// ---------------------------------------------------------------- launching

	/** Summons a ship floating at the given surface height and registers it. */
	static @Nullable Ship launch(ServerLevel level, ShipData data, double x, double surface, double z, float yaw) {
		data.surface = surface;
		UUID id = UUID.randomUUID();
		Cmd.run(level, Ship.summonCommand(data, id, x, surface, z, yaw));
		Entity root = level.getEntity(id);
		if (root == null) {
			AhoyMod.LOG.error("Could not summon ship {}", data.name);
			return null;
		}
		root.setAttached(AhoyMod.DATA, data);
		PENDING.remove(root);
		return register(level, root, data);
	}

	/** Right-clicking water with a Ship in a Bottle. */
	static InteractionResult useBottle(ServerPlayer player, ServerLevel level, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!Bottle.isBottle(held)) {
			return InteractionResult.PASS;
		}
		HitResult hit = player.pick(24, 0, true);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
			|| !level.getFluidState(blockHit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
			player.sendSystemMessage(Component.literal("Point the bottle at open water.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		// the water surface is the top of the highest water block under where you clicked
		BlockPos at = blockHit.getBlockPos();
		while (level.getFluidState(at.above()).is(net.minecraft.tags.FluidTags.WATER)) {
			at = at.above();
		}
		double surface = at.getY() + 0.9;
		float yaw = player.getYRot();
		// put the stern where you clicked, so the ship points away from you
		double[] centre = Ship.toWorld(at.getX() + 0.5, at.getZ() + 0.5, yaw, 0, 8);
		if (!Ship.hullFits(level, surface, centre[0], centre[1], yaw) || !Ship.afloat(level, surface, centre[0], centre[1], yaw)) {
			player.sendSystemMessage(Component.literal("Not enough open water there. A ship needs about 7 × 20 blocks of water with nothing in the way.")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		ShipData saved = Bottle.savedData(held, level);
		ShipData data = saved != null ? saved : new ShipData();
		String name = Bottle.customName(held);
		if (name != null && !name.isBlank()) {
			data.name = name;
		}
		data.owner = player.getUUID().toString();
		data.ownerName = player.getName().getString();
		Ship ship = launch(level, data, centre[0], surface, centre[1], yaw);
		if (ship == null) {
			player.sendSystemMessage(Component.literal("The ship refused to come out of the bottle. Check the server log.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.glass.break", player.getX(), player.getY(), player.getZ(), 1.0f, 1.2f);
		Cmd.sound(level, "minecraft:entity.generic.splash", centre[0], surface, centre[1], 2.0f, 0.6f);
		player.sendSystemMessage(Component.literal("Launched the " + data.name + "! ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Right-click it to climb aboard. Sneak + right-click for cargo and more.")
				.withStyle(ChatFormatting.YELLOW)));
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- clicking a ship

	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		Ship ship = BY_MARKER.get(entity.getUUID());
		if (ship == null || ship.isRemoved()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (shipOf(player) == ship) {
			// the ship's hitbox is all around a passenger, so every right-click lands on it
			int now = AhoyMod.tickCount();
			Integer last = LAST_CLICK.put(player.getUUID(), now);
			if (last == null || now - last > 1) {
				useThrough(player, ship);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			ShipMenu.open(player, ship);
			return InteractionResult.SUCCESS;
		}
		int seat = ship.pickSeat(player);
		if (seat < 0 || !ship.seat(player, seat)) {
			player.sendSystemMessage(Component.literal("No room aboard. Swim alongside and look pitiful.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		player.sendSystemMessage(Component.literal("Welcome aboard the " + ship.data.name + " (" + ship.seatName(seat) + ").").withStyle(ChatFormatting.GOLD));
		if (seat == 0) {
			player.sendSystemMessage(Component.literal("W/S sails up/down · A/D rudder · Space ring the bell · Shift go ashore · Empty-hand right-click or /ahoy menu for the menu")
				.withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.literal("Shift to go ashore (or overboard) · Empty-hand right-click or /ahoy menu for the menu").withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- right-clicking from a seat

	/**
	 * A passenger right-clicked. The ship's click hitbox is a big box around the deck, so the game thinks they're always
	 * pointing at it (and never uses what's in their hand while pointing at one). So we do what it would have done
	 * without the ship in the way: use whatever they're really pointing at (a block, a mob), then the item in their
	 * hand (fish, shoot, throw, eat, drink), main hand first. If none of that does anything and their hand is empty,
	 * it's the ship's menu.
	 */
	static void useThrough(ServerPlayer player, Ship ship) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult block = null;
		HitResult picked = player.pick(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 1.0f, false);
		if (picked instanceof BlockHitResult blockHit && picked.getType() == HitResult.Type.BLOCK) {
			block = blockHit;
		}
		double blockDistance = block == null ? Double.MAX_VALUE : block.getLocation().distanceToSqr(eye);
		EntityHitResult entity = pickEntity(player, ship, eye, look, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), blockDistance);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (entity != null) {
				Entity target = entity.getEntity();
				// 26.3 folded "interact at a point" and "interact" into one call; the point is relative to the mob
				if (player.interactOn(target, hand, entity.getLocation().subtract(target.position())).consumesAction()) {
					return;
				}
			} else if (block != null) {
				InteractionResult result = player.gameMode.useItemOn(player, level, held, hand, block);
				if (result.consumesAction() || result == InteractionResult.FAIL) {
					return;
				}
			}
			if (!held.isEmpty() && player.gameMode.useItem(player, level, held, hand).consumesAction()) {
				return;
			}
		}
		if (player.getMainHandItem().isEmpty()) {
			ShipMenu.open(player, ship);
		}
	}

	/** The nearest thing a player could interact with along their line of sight, not counting the ship itself. */
	private static @Nullable EntityHitResult pickEntity(ServerPlayer player, Ship ship, Vec3 eye, Vec3 look, double reach, double blockDistanceSq) {
		Vec3 end = eye.add(look.scale(reach));
		AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
		double best = Math.min(reach * reach, blockDistanceSq);
		Entity found = null;
		Vec3 at = null;
		for (Entity candidate : player.level().getEntities(player, area, e -> !e.isSpectator() && e.isPickable() && !isPartOf(e, ship))) {
			AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
			Optional<Vec3> clip = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
			if (clip.isPresent() && eye.distanceToSqr(clip.get()) < best) {
				best = eye.distanceToSqr(clip.get());
				found = candidate;
				at = clip.get();
			}
		}
		return found == null ? null : new EntityHitResult(found, at);
	}

	/** The ship itself: its root, its model, its seats and its hitboxes. */
	private static boolean isPartOf(Entity entity, Ship ship) {
		return entity == ship.root || entity.getRootVehicle() == ship.root || BY_MARKER.get(entity.getUUID()) == ship;
	}
}
