package com.thatcoffeelock.cargotrain;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Every loaded train: routes ticks, clicks and chunk loads to it. */
public final class Trains {
	private static final Map<UUID, Train> BY_ROOT = new HashMap<>();
	/** The wagon and the driver's seat, which are rebuilt on every load. */
	private static final Map<UUID, Train> BY_MARKER = new HashMap<>();
	private static final List<Entity> PENDING = new ArrayList<>();
	private static final List<Entity> STRAYS = new ArrayList<>();

	private Trains() {
	}

	// ---------------------------------------------------------------- registry

	static Train register(ServerLevel level, Entity root, TrainData data) {
		Train existing = BY_ROOT.get(root.getUUID());
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		Train train = new Train(level, root, data);
		BY_ROOT.put(root.getUUID(), train);
		return train;
	}

	static void registerMarker(Entity marker, Train train) {
		BY_MARKER.put(marker.getUUID(), train);
	}

	static void unregister(Train train) {
		BY_ROOT.values().removeIf(t -> t == train);
		BY_MARKER.values().removeIf(t -> t == train);
	}

	static List<Train> all() {
		return new ArrayList<>(BY_ROOT.values());
	}

	/** The train this player is driving (or riding in), if any. */
	static @Nullable Train trainOf(Entity entity) {
		Entity vehicle = entity.getVehicle();
		Train train = vehicle == null ? null : BY_MARKER.get(vehicle.getUUID());
		return train == null || train.isRemoved() || train.seat != vehicle ? null : train;
	}

	// ---------------------------------------------------------------- lifecycle

	static void onLoad(Entity entity, ServerLevel level) {
		if (entity.hasAttached(CargoTrainMod.DATA)) {
			PENDING.add(entity);
		} else if (entity.hasAttached(CargoTrainMod.MARKER) && !BY_MARKER.containsKey(entity.getUUID())) {
			STRAYS.add(entity);
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		if (entity.hasAttached(CargoTrainMod.DATA)) {
			Train train = BY_ROOT.get(entity.getUUID());
			if (train != null) {
				// not while the chunk is busy unloading: tidy up at the end of the tick
				CargoTrainMod.nextTick(train::unload);
			}
		} else if (entity.hasAttached(CargoTrainMod.MARKER)) {
			// a wagon that wandered into a chunk that unloaded: the train builds a new one, and this one is a stray if it comes back
			BY_MARKER.remove(entity.getUUID());
		}
	}

	static void tick() {
		if (!PENDING.isEmpty()) {
			List<Entity> pending = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Entity root : pending) {
				TrainData data = root.getAttached(CargoTrainMod.DATA);
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
		for (Train train : all()) {
			if (train.isRemoved()) {
				continue;
			}
			try {
				train.tick();
			} catch (RuntimeException e) {
				CargoTrainMod.LOG.error("Train {} crashed while ticking; parking it", train.root.getUUID(), e);
				train.speed = 0;
				train.data.running = false;
			}
		}
	}

	/** Server stopping: everyone off, and the wagons and seats go (they're rebuilt when the train loads again). */
	static void shutdown() {
		for (Train train : all()) {
			train.unload();
		}
		BY_ROOT.clear();
		BY_MARKER.clear();
		PENDING.clear();
		STRAYS.clear();
	}

	/** Vanilla saves a player together with whatever they ride. Don't let them take the seat home. */
	static void onDisconnect(ServerPlayer player) {
		if (trainOf(player) != null) {
			player.stopRiding();
		}
	}

	/** Low tunnels are fine: the driver never suffocates in the cab. */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		return !(entity instanceof ServerPlayer player) || !source.is(DamageTypes.IN_WALL) || trainOf(player) == null;
	}

	// ---------------------------------------------------------------- putting a train on the track

	/** Builds a train with its locomotive on the given rail. Null if there isn't room on the track. */
	static @Nullable Train spawn(ServerLevel level, TrainData data, BlockPos at, float yaw) {
		Route route = Route.start(level, at, yaw);
		if (route == null) {
			return null;
		}
		Vec3 p = route.point(route.s);
		float heading = route.heading(route.s, yaw);
		UUID id = UUID.randomUUID();
		Cmd.run(level, TrainModel.locoCommand(id, p.x, p.y, p.z, heading));
		Entity root = level.getEntity(id);
		if (root == null) {
			CargoTrainMod.LOG.error("Could not summon a train at {}", at);
			return null;
		}
		data.at = at.immutable();
		root.setAttached(CargoTrainMod.DATA, data);
		PENDING.remove(root);
		Train train = register(level, root, data);
		train.route = route;
		return train;
	}

	/** Takes the train off the track and returns it as an item. The wagon must be empty. */
	static ItemStack pickUp(Train train) {
		train.remove();
		return TrainItems.train();
	}

	/** Right-clicking a rail with a Cargo Train. */
	static InteractionResult useTrainItem(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!TrainItems.isTrain(held)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!Track.isRail(level.getBlockState(pos))) {
			player.sendSystemMessage(Component.literal("Right-click a rail to put the train on the track.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		TrainData data = new TrainData();
		data.owner = player.getUUID().toString();
		data.ownerName = player.getName().getString();
		Train train = spawn(level, data, pos, player.getYRot());
		if (train == null) {
			player.sendSystemMessage(Component.literal("Not enough track here. The train needs at least 4 rails in a row.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.anvil.place", pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0.5f, 1.2f);
		train.horn();
		player.sendSystemMessage(Component.literal("Your train is on the track and off it goes! ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("It shuttles between the ends of the line and stops at every station. Sneak + right-click the locomotive for the menu.")
				.withStyle(ChatFormatting.YELLOW)));
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- clicking a train

	static InteractionResult useEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (!(entity instanceof Interaction) || entity.getVehicle() == null) {
			return InteractionResult.PASS;
		}
		Entity car = entity.getVehicle();
		Train train = BY_ROOT.get(car.getUUID());
		boolean loco = train != null;
		if (train == null) {
			train = BY_MARKER.get(car.getUUID());
			if (train == null || train.wagon != car) {
				return InteractionResult.PASS;
			}
		}
		if (train.isRemoved()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (!loco) {
			if (!train.mayUse(player)) {
				player.sendSystemMessage(Component.literal("That's " + train.data.ownerName + "'s cargo, and it's locked. Nice try.").withStyle(ChatFormatting.RED));
			} else {
				TrainMenu.openCargo(player, train);
			}
			return InteractionResult.SUCCESS;
		}
		if (trainOf(player) == train || player.isShiftKeyDown()) {
			TrainMenu.open(player, train);
			return InteractionResult.SUCCESS;
		}
		if (!train.mayUse(player)) {
			player.sendSystemMessage(Component.literal("This train is locked by " + train.data.ownerName + ". Sneak + right-click to look at it.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		if (!train.board(player)) {
			player.sendSystemMessage(Component.literal("Someone's already in the cab. It's a one-seater.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		player.sendSystemMessage(Component.literal("You're in the cab. ").withStyle(ChatFormatting.GOLD).append(Component.literal(train.data.running
			? "The train drives itself; enjoy the ride. Space: horn · Shift: get off · Right-click: menu (stop it there to drive yourself)."
			: "W/S: drive · Space: horn · Shift: get off · Right-click: menu").withStyle(ChatFormatting.GRAY)));
		return InteractionResult.SUCCESS;
	}
}
