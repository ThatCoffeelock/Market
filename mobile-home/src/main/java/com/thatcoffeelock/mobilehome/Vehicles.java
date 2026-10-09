package com.thatcoffeelock.mobilehome;

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
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Keeps track of every loaded vehicle and routes clicks, damage and ticks to it. */
public final class Vehicles {
	static final String ROOT_TAG = "mh_vehicle";
	static final String SEAT_TAG = "mh_seat";
	static final String HITBOX_TAG = "mh_hitbox";
	static final String PART_TAG = "mh_part";

	private static final Map<UUID, Vehicle> BY_ROOT = new HashMap<>();
	private static final Map<UUID, Vehicle> BY_SEAT = new HashMap<>();
	private static final Map<UUID, Vehicle> RIDERS = new HashMap<>();
	private static final List<Entity> PENDING = new ArrayList<>();
	private static final List<Entity> ORPHAN_SEATS = new ArrayList<>();

	private Vehicles() {
	}

	// ---------------------------------------------------------------- registry

	static Vehicle register(ServerLevel level, Entity root) {
		Vehicle existing = BY_ROOT.get(root.getUUID());
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		VehicleData data = root.getAttached(MobileHomeMod.DATA);
		if (data == null) {
			data = new VehicleData(VehicleType.VAN);
			root.setAttached(MobileHomeMod.DATA, data);
		}
		Vehicle vehicle = new Vehicle(level, root, data);
		BY_ROOT.put(root.getUUID(), vehicle);
		return vehicle;
	}

	static void registerSeat(Entity seat, Vehicle vehicle) {
		BY_SEAT.put(seat.getUUID(), vehicle);
	}

	static void unregister(Vehicle vehicle) {
		BY_ROOT.remove(vehicle.root.getUUID(), vehicle);
		for (Entity seat : vehicle.seats) {
			if (seat != null) {
				BY_SEAT.remove(seat.getUUID(), vehicle);
			}
		}
		RIDERS.values().removeIf(v -> v == vehicle);
	}

	static void rememberRider(UUID player, Vehicle vehicle) {
		RIDERS.put(player, vehicle);
	}

	static void forgetRider(UUID player, Vehicle vehicle) {
		RIDERS.remove(player, vehicle);
	}

	/** The vehicle this entity is sitting in, if any. */
	static @Nullable Vehicle vehicleOf(Entity entity) {
		Vehicle vehicle = RIDERS.get(entity.getUUID());
		if (vehicle == null || vehicle.isRemoved() || entity.getVehicle() == null || BY_SEAT.get(entity.getVehicle().getUUID()) != vehicle) {
			return null;
		}
		return vehicle;
	}

	/**
	 * Sellswords: mercenaries following a player climb into the vehicle that player is in. Registered in the
	 * ObjectShare list {@code sellswords:board}; neither mod needs the other.
	 */
	@SuppressWarnings("unchecked")
	static void offerSeatsToMercenaries() {
		var share = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare();
		share.putIfAbsent("sellswords:board", new java.util.concurrent.CopyOnWriteArrayList<java.util.function.BiFunction<net.minecraft.server.level.ServerPlayer, Entity, Boolean>>());
		if (share.get("sellswords:board") instanceof List<?> list) {
			((List<java.util.function.BiFunction<net.minecraft.server.level.ServerPlayer, Entity, Boolean>>) list).add((player, guest) -> {
				Vehicle vehicle = vehicleOf(player);
				return vehicle != null && vehicle.seatGuest(guest);
			});
		}
	}

	static List<Vehicle> all() {
		return new ArrayList<>(BY_ROOT.values());
	}

	// ---------------------------------------------------------------- lifecycle

	static void onLoad(Entity entity, ServerLevel level) {
		if (entity.hasAttached(MobileHomeMod.DATA)) {
			PENDING.add(entity);
		} else if (entity.hasAttached(MobileHomeMod.SEAT) && !BY_SEAT.containsKey(entity.getUUID())) {
			ORPHAN_SEATS.add(entity);
		}
	}

	static void onUnload(Entity entity, ServerLevel level) {
		if (entity.hasAttached(MobileHomeMod.DATA)) {
			Vehicle vehicle = BY_ROOT.get(entity.getUUID());
			if (vehicle != null) {
				// not while the chunk is busy unloading: tidy the seats up at the end of the tick
				MobileHomeMod.nextTick(vehicle::unload);
			}
		}
	}

	static void tick() {
		if (!PENDING.isEmpty()) {
			List<Entity> pending = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Entity root : pending) {
				if (!root.isRemoved() && root.level() instanceof ServerLevel level) {
					register(level, root);
				}
			}
		}
		if (!ORPHAN_SEATS.isEmpty()) {
			List<Entity> orphans = new ArrayList<>(ORPHAN_SEATS);
			ORPHAN_SEATS.clear();
			for (Entity seat : orphans) {
				if (!seat.isRemoved() && !BY_SEAT.containsKey(seat.getUUID())) {
					seat.ejectPassengers();
					seat.discard();
				}
			}
		}
		for (Vehicle vehicle : all()) {
			if (vehicle.isRemoved()) {
				continue;
			}
			try {
				vehicle.tick();
			} catch (RuntimeException e) {
				MobileHomeMod.LOG.error("Vehicle {} crashed while ticking; parking it", vehicle.root.getUUID(), e);
				vehicle.speed = 0;
			}
		}
	}

	/** Server stopping: get everyone out and remove the seats so nobody logs back in riding a ghost. */
	static void shutdown() {
		for (Vehicle vehicle : all()) {
			vehicle.unload();
		}
		BY_ROOT.clear();
		BY_SEAT.clear();
		RIDERS.clear();
		PENDING.clear();
		ORPHAN_SEATS.clear();
	}

	static void onDisconnect(ServerPlayer player) {
		if (vehicleOf(player) != null) {
			player.stopRiding();
		}
	}

	/** Seated players can't be hurt, except by things that ignore invulnerability (/kill, the void). */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		return !(entity instanceof ServerPlayer player) || vehicleOf(player) == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	// ---------------------------------------------------------------- spawning

	/** Summons the vehicle model. Returns null if the summon failed. */
	static @Nullable Vehicle spawn(ServerLevel level, VehicleData data, double x, double y, double z, float yaw) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, summonCommand(data.type, id, x, y, z, yaw));
		Entity root = level.getEntity(id);
		if (root == null) {
			MobileHomeMod.LOG.error("Could not summon a {} at {} {} {}", data.type.id, x, y, z);
			return null;
		}
		root.setAttached(MobileHomeMod.DATA, data);
		PENDING.remove(root);
		return register(level, root);
	}

	static String summonCommand(VehicleType type, UUID id, double x, double y, double z, float yaw) {
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(x, y, z)).append(" {")
			.append(Cmd.uuidNbt(id))
			.append(",Tags:[\"").append(ROOT_TAG).append("\",\"mh_type_").append(type.id).append("\"],")
			.append(rot).append(",teleport_duration:2,Passengers:[");
		cmd.append("{id:\"minecraft:interaction\",width:").append(Cmd.f(type.hitboxWidth)).append("f,height:").append(Cmd.f(type.hitboxHeight))
			.append("f,response:1b,Tags:[\"").append(PART_TAG).append("\",\"").append(HITBOX_TAG).append("\"]}");
		for (VehicleType.Part part : type.parts()) {
			cmd.append(",{id:\"minecraft:block_display\",block_state:\"").append(part.block()).append("\",Tags:[\"").append(PART_TAG).append("\"],")
				.append(rot).append(",teleport_duration:2,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[")
				.append(Cmd.f(part.x())).append("f,").append(Cmd.f(part.y())).append("f,").append(Cmd.f(part.z())).append("f],scale:[")
				.append(Cmd.f(part.sx())).append("f,").append(Cmd.f(part.sy())).append("f,").append(Cmd.f(part.sz())).append("f]}");
			if (part.glow()) {
				cmd.append(",brightness:{sky:15,block:15}");
			}
			cmd.append("}");
		}
		return cmd.append("]}").toString();
	}

	// ---------------------------------------------------------------- interaction

	/** Right-clicking the ground with a vehicle item parks it there. */
	static InteractionResult useKit(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		VehicleType type = Kits.type(held);
		if (type == null) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos base = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		double x = base.getX() + 0.5;
		double y = base.getY();
		double z = base.getZ() + 0.5;
		float yaw = player.getYRot();
		if (!Vehicle.fits(level, type, x, y, z, yaw)) {
			if (Vehicle.fits(level, type, x, y + 1, z, yaw)) {
				y += 1;
			} else {
				player.sendSystemMessage(Component.literal("Not enough room to park the " + type.displayName
					+ " here. It needs about " + Math.round(type.halfWidth * 2) + " × " + Math.round(type.halfLength * 2) + " blocks of space.")
					.withStyle(ChatFormatting.RED));
				return InteractionResult.SUCCESS;
			}
		}
		VehicleData data = Kits.savedData(held, level);
		if (data == null) {
			data = new VehicleData(type);
		}
		data.owner = player.getUUID().toString();
		data.ownerName = player.getName().getString();
		Vehicle vehicle = spawn(level, data, x, y, z, yaw);
		if (vehicle == null) {
			player.sendSystemMessage(Component.literal("Something went wrong building the vehicle. Check the server log.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.anvil.use", x, y + 1, z, 0.6f, 1.2f);
		player.sendSystemMessage(Component.literal("Your " + type.displayName + " is parked. ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Right-click it to get in, sneak + right-click for storage, fuel and more.").withStyle(ChatFormatting.YELLOW)));
		if (data.fuel <= 0) {
			player.sendSystemMessage(Component.literal("The tank is empty. Feed it anything a furnace would burn: coal, wood, a lava bucket...")
				.withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}

	/** Right-clicking the vehicle's hitbox. */
	static InteractionResult useHitbox(ServerPlayer player, InteractionHand hand, Entity hitbox) {
		if (!(hitbox instanceof Interaction) || hitbox.getVehicle() == null) {
			return InteractionResult.PASS;
		}
		Vehicle vehicle = BY_ROOT.get(hitbox.getVehicle().getUUID());
		if (vehicle == null || vehicle.isRemoved()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		if (vehicleOf(player) == vehicle) {
			VehicleMenu.open(player, vehicle);
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			ItemStack held = player.getMainHandItem();
			if (Fuel.burnTicks(vehicle.level, held) > 0) {
				int before = vehicle.data.fuel;
				ItemStack rest = vehicle.feed(held);
				if (rest != held) {
					player.setItemInHand(InteractionHand.MAIN_HAND, rest);
				}
				if (vehicle.data.fuel > before) {
					player.sendSystemMessage(Component.literal("Refuelled: " + vehicle.data.fuelPercent() + "% (" + vehicle.data.fuelTime() + " of driving)")
						.withStyle(ChatFormatting.GREEN));
				} else {
					player.sendSystemMessage(Component.literal("The tank is full. It can't eat another bite.").withStyle(ChatFormatting.YELLOW));
				}
			} else {
				VehicleMenu.open(player, vehicle);
			}
			return InteractionResult.SUCCESS;
		}
		board(player, vehicle);
		return InteractionResult.SUCCESS;
	}

	static void board(ServerPlayer player, Vehicle vehicle) {
		int seat = vehicle.pickSeat(player);
		if (seat < 0) {
			player.sendSystemMessage(Component.literal(vehicle.data.locked && !vehicle.isOwner(player)
				? "No free passenger seats, and the owner locked the driver's seat."
				: "Every seat is taken. You could try the roof, but no.").withStyle(ChatFormatting.RED));
			return;
		}
		if (!vehicle.seat(player, seat)) {
			player.sendSystemMessage(Component.literal("Couldn't get in. Try again?").withStyle(ChatFormatting.RED));
			return;
		}
		String who = vehicle.data.ownerName.isEmpty() ? "the" : vehicle.data.ownerName + "'s";
		player.sendSystemMessage(Component.literal("You're in " + who + " " + vehicle.type.displayName + " (" + vehicle.type.seats[seat].name() + "). ")
			.withStyle(ChatFormatting.GOLD)
			.append(Component.literal("Monsters can't touch you in here.").withStyle(ChatFormatting.GREEN)));
		if (seat == 0) {
			player.sendSystemMessage(Component.literal("W/S drive · A/D steer · Ctrl turbo · Space honk · Shift get out · Right-click menu")
				.withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.literal("Shift to get out · Right-click for the menu (storage, switch seats)").withStyle(ChatFormatting.GRAY));
		}
	}
}
