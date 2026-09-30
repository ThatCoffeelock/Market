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
	private record Key(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final Map<UUID, Ship> BY_ROOT = new HashMap<>();
	private static final Map<UUID, Ship> BY_MARKER = new HashMap<>();
	private static final Map<UUID, Ship> RIDERS = new HashMap<>();
	private static final Map<Key, Ship> BLOCKS = new HashMap<>();
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
		if (!data.sailing) {
			index(ship);
		}
		return ship;
	}

	static void rekey(UUID oldRoot, Ship ship) {
		BY_ROOT.remove(oldRoot, ship);
		BY_ROOT.put(ship.root.getUUID(), ship);
	}

	static void unregister(Ship ship) {
		BY_ROOT.values().removeIf(s -> s == ship);
		BY_MARKER.values().removeIf(s -> s == ship);
		RIDERS.values().removeIf(s -> s == ship);
	}

	static void index(Ship ship) {
		for (BlockPos pos : ship.anchoredPositions()) {
			BLOCKS.put(new Key(ship.level.dimension(), pos), ship);
		}
		ship.repairHelm();
	}

	static void unindex(Ship ship) {
		BLOCKS.values().removeIf(s -> s == ship);
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

	static @Nullable Ship anchoredAt(Level level, BlockPos pos) {
		Ship ship = BLOCKS.get(new Key(level.dimension(), pos));
		return ship == null || ship.isRemoved() ? null : ship;
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
		BLOCKS.clear();
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

	/** Builds a ship (anchored) and registers it. */
	static @Nullable Ship launch(ServerLevel level, ShipData data, BlockPos origin, int quarter) {
		Ship.place(level, data.blocks, origin, quarter);
		data.sailing = false;
		data.waterline = origin.getY();
		UUID id = UUID.randomUUID();
		Cmd.run(level, Ship.anchorCommand(data, id, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, quarter * 90f));
		Entity root = level.getEntity(id);
		if (root == null) {
			AhoyMod.LOG.error("Could not summon the anchor for ship {}", data.name);
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
		int quarter = Math.floorMod(Math.round(player.getYRot() / 90f), 4);
		// start the stern where you clicked, so the ship sails away from you
		BlockPos at = blockHit.getBlockPos();
		BlockPos origin = Ship.toWorld(at, quarter, 0, 0, 9);
		ShipData saved = Bottle.savedData(held, level);
		ShipData data = saved != null ? saved : new ShipData(ShipTemplate.blocks());
		if (!Ship.canPlace(level, data.blocks, origin, quarter, true)) {
			player.sendSystemMessage(Component.literal("Not enough open water there. A ship needs about 7 × 20 blocks of water, 4 deep, with nothing above it.")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		String name = Bottle.customName(held);
		if (name != null && !name.isBlank()) {
			data.name = name;
		}
		data.owner = player.getUUID().toString();
		data.ownerName = player.getName().getString();
		Ship ship = launch(level, data, origin, quarter);
		if (ship == null) {
			player.sendSystemMessage(Component.literal("The ship refused to come out of the bottle. Check the server log.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.glass.break", player.getX(), player.getY(), player.getZ(), 1.0f, 1.2f);
		Cmd.sound(level, "minecraft:entity.generic.splash", origin.getX(), origin.getY(), origin.getZ(), 2.0f, 0.6f);
		player.sendSystemMessage(Component.literal("Launched the " + data.name + "! ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Walk aboard, right-click the wheel (the grindstone at the back) to set sail. The barrels in the hold are your cargo.")
				.withStyle(ChatFormatting.YELLOW)));
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- clicking blocks of an anchored ship

	static InteractionResult useBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		Ship ship = anchoredAt(level, hit.getBlockPos());
		if (ship == null) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (pos.equals(ship.helmPos())) {
			if (hand == InteractionHand.MAIN_HAND) {
				ShipMenu.open(player, ship);
			}
			return InteractionResult.SUCCESS;
		}
		int bay = ship.cargoBayAt(pos);
		if (bay >= 0) {
			if (hand == InteractionHand.MAIN_HAND) {
				if (ship.mayCommand(player)) {
					ShipMenu.openCargo(player, ship, bay);
				} else {
					player.sendSystemMessage(Component.literal("The cargo is locked by the captain.").withStyle(ChatFormatting.RED));
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Only the owner may take the ship apart. The wheel stays put either way. */
	static boolean allowBreak(Player player, Level level, BlockPos pos) {
		Ship ship = anchoredAt(level, pos);
		if (ship == null) {
			return true;
		}
		if (pos.equals(ship.helmPos())) {
			player.sendSystemMessage(Component.literal("The wheel is bolted down. To take the ship with you, use \"Bottle it up\" at the wheel.")
				.withStyle(ChatFormatting.RED));
			return false;
		}
		if (!ship.isOwner(player) && !player.isCreative()) {
			player.sendSystemMessage(Component.literal("Hands off the " + ship.data.name + ", that's " + ship.data.ownerName + "'s ship.")
				.withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- clicking a sailing ship

	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		Ship ship = BY_MARKER.get(entity.getUUID());
		if (ship == null || ship.isRemoved()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (shipOf(player) == ship) {
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
			player.sendSystemMessage(Component.literal("W/S sails up/down · A/D rudder · Space ring the bell · Shift drop anchor · Right-click menu")
				.withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.literal("Shift jumps overboard · Right-click for the menu").withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}
}
