package com.thatcoffeelock.ahoy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/** Every loaded ship: routes ticks, clicks, block breaking and damage. */
public final class Ships {
	private static final Map<UUID, Ship> BY_ROOT = new HashMap<>();
	private static final Map<UUID, Ship> BY_MARKER = new HashMap<>();
	private static final Map<UUID, Ship> RIDERS = new HashMap<>();
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
		PENDING.clear();
		STRAYS.clear();
	}

	static void onDisconnect(ServerPlayer player) {
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
		if (shipOf(player) == ship || player.isShiftKeyDown()) {
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
			player.sendSystemMessage(Component.literal("W/S sails up/down · A/D rudder · Space ring the bell · Shift go ashore · Right-click menu")
				.withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.literal("Shift to go ashore (or overboard) · Right-click for the menu").withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}
}
