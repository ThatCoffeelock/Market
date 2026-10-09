package com.thatcoffeelock.mobilehome;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A live vehicle. The "root" is an invisible item display that carries the model parts and the click
 * hitbox as passengers. Seats are separate invisible displays that we move along every tick, because
 * passengers of one entity can't be spread out. All the physics runs here, on the server.
 */
public final class Vehicle {
	/** What the driver is pressing. */
	public record Controls(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sprint) {
		public static final Controls NONE = new Controls(false, false, false, false, false, false);

		static Controls of(Input input) {
			return new Controls(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.sprint());
		}
	}

	final ServerLevel level;
	final Entity root;
	final VehicleType type;
	final VehicleData data;
	final Entity[] seats;
	final UUID[] riders;

	double speed;
	double vy;
	boolean onGround;
	private int age;
	private int honkCooldown;
	private boolean lastJump;
	private boolean warnedLowFuel;
	private boolean removed;
	/** Smoke test hook: pretend a driver is pressing these. */
	@Nullable Controls testControls;

	Vehicle(ServerLevel level, Entity root, VehicleData data) {
		this.level = level;
		this.root = root;
		this.type = data.type;
		this.data = data;
		this.seats = new Entity[type.seats.length];
		this.riders = new UUID[type.seats.length];
	}

	public boolean isRemoved() {
		return removed || root.isRemoved();
	}

	// ---------------------------------------------------------------- geometry

	/** Local (x = left, z = forward) to world, for the current position and heading. */
	private static double[] toWorld(double x, double z, float yaw, double lx, double lz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[] {x + lx * c - lz * s, z + lx * s + lz * c};
	}

	/**
	 * Would the vehicle fit here? The footprint is approximated by three squares along its length,
	 * which is cheap and lets it turn in tight spots without getting stuck on its own corners.
	 */
	static boolean fits(ServerLevel level, VehicleType type, double x, double y, double z, float yaw) {
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		double half = type.halfWidth - 0.05;
		double seg = type.halfLength - type.halfWidth;
		for (int i = -1; i <= 1; i++) {
			double cx = x + fx * seg * i;
			double cz = z + fz * seg * i;
			if (!level.noCollision(new AABB(cx - half, y + 0.01, cz - half, cx + half, y + type.height, cz + half))) {
				return false;
			}
		}
		return true;
	}

	private boolean fits(double x, double y, double z, float yaw) {
		return fits(level, type, x, y, z, yaw);
	}

	private boolean fluidAt(double x, double y, double z) {
		return !level.getFluidState(BlockPos.containing(x, y, z)).isEmpty();
	}

	// ---------------------------------------------------------------- ticking

	void tick() {
		if (isRemoved()) {
			return;
		}
		age++;
		ensureSeats();
		updateRiders();

		ServerPlayer driver = rider(0);
		Controls controls = testControls != null ? testControls
			: driver != null ? Controls.of(driver.getLastClientInput()) : Controls.NONE;
		boolean freeFuel = driver != null && driver.isCreative();

		boolean parked = driver == null && testControls == null && speed == 0 && onGround && vy == 0;
		if (!parked || age % 20 == 0) {
			physics(controls, freeFuel);
		}
		placeSeats();

		if (controls.jump && !lastJump && honkCooldown <= 0) {
			honk();
		}
		lastJump = controls.jump;
		if (honkCooldown > 0) {
			honkCooldown--;
		}

		if (hasRiders()) {
			protectRiders();
			if (age % 4 == 0) {
				repelMonsters();
			}
		}
		if (driver != null && age % 5 == 0) {
			hud(driver, controls, freeFuel);
		}
	}

	private void physics(Controls c, boolean freeFuel) {
		double x = root.getX();
		double y = root.getY();
		double z = root.getZ();
		float yaw = root.getYRot();
		float startYaw = yaw;
		double sx = x;
		double sy = y;
		double sz = z;

		boolean hasFuel = freeFuel || data.fuel > 0;
		boolean inFluid = fluidAt(x, y + 0.3, z);
		double max = type.maxSpeed * (c.sprint ? 1.5 : 1.0) * (inFluid ? 0.6 : 1.0);

		// throttle and brakes
		boolean throttle = false;
		if (hasFuel && c.forward) {
			speed = speed < 0 ? speed + type.accel * 3 : Math.min(max, speed + type.accel);
			throttle = true;
		} else if (hasFuel && c.back) {
			speed = speed > 0 ? speed - type.accel * 3 : Math.max(-max * 0.5, speed - type.accel);
			throttle = true;
		} else {
			speed *= onGround || inFluid ? 0.9 : 0.98;
		}
		if (Math.abs(speed) > max) {
			speed *= 0.95;
		}
		if (Math.abs(speed) < 0.003) {
			speed = 0;
		}
		if (!hasFuel && (c.forward || c.back) && age % 40 == 0) {
			Cmd.sound(level, "minecraft:block.fire.extinguish", x, y + 1, z, 0.5f, 0.6f);
		}

		// steering: tanks spin on the spot, vans need to be rolling (and steer backwards in reverse)
		int steer = (c.right ? 1 : 0) - (c.left ? 1 : 0);
		if (steer != 0 && hasFuel) {
			float delta;
			if (type.pivotTurn) {
				delta = steer * type.turnRate;
				throttle = true;
			} else {
				double grip = Mth.clamp(Math.abs(speed) / 0.15, 0.0, 1.0);
				delta = (float) (steer * type.turnRate * grip * Math.signum(speed));
			}
			float newYaw = Mth.wrapDegrees(yaw + delta);
			if (delta != 0 && fits(x, y, z, newYaw)) {
				yaw = newYaw;
			}
		}
		if (throttle && !freeFuel) {
			data.fuel = Math.max(0, data.fuel - type.fuelPerTick * (c.sprint ? 2 : 1));
		}

		// drive, climbing steps if something is in the way
		if (speed != 0) {
			double rad = Math.toRadians(yaw);
			double dx = -Math.sin(rad) * speed;
			double dz = Math.cos(rad) * speed;
			if (fits(x + dx, y, z + dz, yaw)) {
				x += dx;
				z += dz;
			} else {
				boolean climbed = false;
				if (onGround || inFluid) {
					for (double step : type.steps) {
						if (fits(x + dx, y + step, z + dz, yaw)) {
							x += dx;
							z += dz;
							y += step;
							climbed = true;
							break;
						}
					}
				}
				if (!climbed) {
					if (Math.abs(speed) > 0.25) {
						crash(x, y, z);
					}
					speed = 0;
				}
			}
		}

		// gravity, or floating when in water/lava (it's a boat now)
		if (fluidAt(x, y + 0.8, z)) {
			vy = Math.min(vy + 0.03, 0.1);
		} else if (fluidAt(x, y + 0.3, z)) {
			vy = vy * 0.6 - 0.01;
		} else {
			vy = Math.max(-3.0, (vy - 0.08) * 0.98);
		}
		if (vy > 0) {
			if (fits(x, y + vy, z, yaw)) {
				y += vy;
			} else {
				vy = 0;
			}
			onGround = false;
		} else if (fits(x, y + vy, z, yaw)) {
			y += vy;
			onGround = false;
		} else {
			double lo = y + vy;
			double hi = y;
			for (int i = 0; i < 8; i++) {
				double mid = (lo + hi) / 2;
				if (fits(x, mid, z, yaw)) {
					hi = mid;
				} else {
					lo = mid;
				}
			}
			if (fits(x, hi, z, yaw)) {
				y = hi;
			}
			vy = 0;
			onGround = true;
		}

		if (x != sx || y != sy || z != sz) {
			root.setPos(x, y, z);
		}
		if (yaw != startYaw) {
			root.setYRot(yaw);
			for (Entity part : root.getPassengers()) {
				part.setYRot(yaw);
			}
		}
		if (throttle && speed != 0 && age % 3 == 0) {
			double[] pipe = toWorld(x, z, yaw, -0.7, -type.halfLength - 0.1);
			Cmd.particles(level, "minecraft:smoke", pipe[0], y + 0.4, pipe[1], 0.05, 0.01, 2);
		}
		if (throttle && speed != 0 && age % 8 == 0) {
			if (type == VehicleType.TANK) {
				Cmd.sound(level, "minecraft:entity.ravager.step", x, y, z, 0.6f, 0.7f);
			} else {
				Cmd.sound(level, "minecraft:entity.minecart.riding", x, y, z, 0.25f, (float) (0.6 + Math.abs(speed)));
			}
		}
	}

	private void crash(double x, double y, double z) {
		Cmd.sound(level, "minecraft:block.anvil.land", x, y + 1, z, 0.5f, 0.6f);
		ServerPlayer driver = rider(0);
		if (driver != null) {
			driver.sendSystemMessage(Component.literal("BONK. That wall has not been moved. Walls rarely are.").withStyle(ChatFormatting.GRAY));
		}
	}

	void honk() {
		honkCooldown = 12;
		Cmd.sound(level, type.horn, root.getX(), root.getY() + 1, root.getZ(), 2.0f, type.hornPitch);
		if (type == VehicleType.VAN) {
			MobileHomeMod.later(4, () -> Cmd.sound(level, type.horn, root.getX(), root.getY() + 1, root.getZ(), 2.0f, type.hornPitch));
		}
	}

	// ---------------------------------------------------------------- seats

	private void ensureSeats() {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && !seats[i].isRemoved()) {
				continue;
			}
			UUID id = UUID.randomUUID();
			double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), type.seats[i].x(), type.seats[i].z());
			Cmd.run(level, "summon minecraft:item_display " + Cmd.pos(w[0], root.getY() + type.seats[i].y(), w[1])
				+ " {" + Cmd.uuidNbt(id) + ",Tags:[\"" + Vehicles.SEAT_TAG + "\"],teleport_duration:2,Rotation:[" + Cmd.f(root.getYRot()) + "f,0f]}");
			seats[i] = level.getEntity(id);
			if (seats[i] != null) {
				seats[i].setAttached(MobileHomeMod.SEAT, true);
				Vehicles.registerSeat(seats[i], this);
			}
		}
	}

	private void placeSeats() {
		float yaw = root.getYRot();
		for (int i = 0; i < seats.length; i++) {
			Entity seat = seats[i];
			if (seat == null) {
				continue;
			}
			VehicleType.Seat s = type.seats[i];
			double[] w = toWorld(root.getX(), root.getZ(), yaw, s.x(), s.z());
			double y = root.getY() + s.y();
			if (seat.getX() != w[0] || seat.getY() != y || seat.getZ() != w[1]) {
				seat.setPos(w[0], y, w[1]);
			}
			if (seat.getYRot() != yaw) {
				seat.setYRot(yaw);
			}
		}
	}

	private void updateRiders() {
		for (int i = 0; i < seats.length; i++) {
			Entity seat = seats[i];
			Entity passenger = seat == null ? null : seat.getFirstPassenger();
			UUID now = passenger instanceof ServerPlayer ? passenger.getUUID() : null;
			if (riders[i] != null && !riders[i].equals(now)) {
				UUID gone = riders[i];
				riders[i] = null;
				Vehicles.forgetRider(gone, this);
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
				if (player != null && seat != null) {
					stepOut(player, i, seat);
				}
			}
			if (now != null && riders[i] == null) {
				riders[i] = now;
				Vehicles.rememberRider(now, this);
			}
		}
	}

	@Nullable ServerPlayer rider(int seat) {
		if (seats[seat] == null) {
			return null;
		}
		return seats[seat].getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	boolean hasRiders() {
		for (UUID rider : riders) {
			if (rider != null) {
				return true;
			}
		}
		return false;
	}

	int seatOf(Player player) {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && player.getVehicle() == seats[i]) {
				return i;
			}
		}
		return -1;
	}

	boolean isOwner(Player player) {
		return data.owner.isEmpty() || data.owner.equals(player.getUUID().toString());
	}

	boolean mayDrive(Player player) {
		return !data.locked || isOwner(player);
	}

	/** Seat order for someone getting in: owners go for the wheel, guests go for shotgun. */
	int pickSeat(Player player) {
		boolean canDrive = mayDrive(player);
		List<Integer> order = new ArrayList<>();
		if (canDrive && isOwner(player)) {
			order.add(0);
		}
		for (int i = 1; i < seats.length; i++) {
			order.add(i);
		}
		if (canDrive && !isOwner(player)) {
			order.add(0);
		}
		for (int i : order) {
			if (seats[i] != null && seats[i].getPassengers().isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Seats someone who isn't a player (a Sellswords mercenary following the driver in) in the first free passenger
	 * seat. Never the driver's seat.
	 */
	boolean seatGuest(Entity guest) {
		for (int i = 1; i < seats.length; i++) {
			Entity seat = seats[i];
			if (seat == null || !seat.getPassengers().isEmpty()) {
				continue;
			}
			if (guest.getVehicle() != null) {
				guest.stopRiding();
			}
			Cmd.run(level, "ride " + guest.getUUID() + " mount " + seat.getUUID());
			return guest.getVehicle() == seat;
		}
		return false;
	}

	/** Puts the player in the given seat. */
	boolean seat(ServerPlayer player, int index) {
		Entity seat = seats[index];
		if (seat == null || !seat.getPassengers().isEmpty()) {
			return false;
		}
		int current = seatOf(player);
		if (current >= 0) {
			riders[current] = null; // switching seats is not getting out
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		Cmd.run(level, "ride " + player.getUUID() + " mount " + seat.getUUID());
		if (player.getVehicle() != seat) {
			return false;
		}
		riders[index] = player.getUUID();
		Vehicles.rememberRider(player.getUUID(), this);
		return true;
	}

	/** Called when someone leaves a seat: put them next to their door instead of inside the walls. */
	private void stepOut(ServerPlayer player, int index, Entity seat) {
		if (player.isRemoved() || player.isDeadOrDying() || player.getVehicle() != null || player.level() != level
			|| player.distanceToSqr(seat) > 16) {
			return;
		}
		float yaw = root.getYRot();
		VehicleType.Seat s = type.seats[index];
		double side = s.x() >= 0 ? 1 : -1;
		double out = type.halfWidth + 0.8;
		double[][] spots = {{side * out, s.z()}, {-side * out, s.z()}, {0, -type.halfLength - 0.8}, {0, type.halfLength + 0.8}};
		for (double[] spot : spots) {
			double[] w = toWorld(root.getX(), root.getZ(), yaw, spot[0], spot[1]);
			for (double dy : new double[] {0, 1, -1}) {
				double y = root.getY() + dy;
				if (level.noCollision(new AABB(w[0] - 0.3, y, w[1] - 0.3, w[0] + 0.3, y + 1.8, w[1] + 0.3))) {
					Cmd.run(level, "tp " + player.getUUID() + " " + Cmd.pos(w[0], y, w[1]));
					return;
				}
			}
		}
		Cmd.run(level, "tp " + player.getUUID() + " " + Cmd.pos(root.getX(), root.getY() + type.height + 0.9, root.getZ()));
	}

	void ejectAll() {
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer player = rider(i);
			if (player != null) {
				player.stopRiding();
				riders[i] = null;
				Vehicles.forgetRider(player.getUUID(), this);
				stepOut(player, i, seats[i]);
			}
		}
	}

	// ---------------------------------------------------------------- safety

	private void protectRiders() {
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer player = rider(i);
			if (player != null) {
				player.clearFire();
				player.setAirSupply(player.getMaxAirSupply());
			}
		}
	}

	/** The force field: monsters close by get shoved away and forget about whoever is inside. */
	private void repelMonsters() {
		double x = root.getX();
		double y = root.getY();
		double z = root.getZ();
		double r = type.repelRadius;
		AABB near = new AABB(x - 32, y - 16, z - 32, x + 32, y + 16, z + 32);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, near, m -> m instanceof Enemy && m.isAlive())) {
			LivingEntity target = mob.getTarget();
			if (target != null && Vehicles.vehicleOf(target) == this) {
				mob.setTarget(null);
			}
			double dx = mob.getX() - x;
			double dz = mob.getZ() - z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < r && Math.abs(mob.getY() - y) < 6) {
				if (dist < 0.01) {
					dx = 1;
					dz = 0;
				}
				double push = 0.9 / Math.max(dist, 0.01);
				mob.setDeltaMovement(mob.getDeltaMovement().add(dx * push, 0.25, dz * push));
				Cmd.particles(level, "minecraft:electric_spark", mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 0.3, 0.1, 8);
				if (age % 20 == 0) {
					Cmd.sound(level, "minecraft:block.beacon.deactivate", mob.getX(), mob.getY(), mob.getZ(), 0.4f, 2.0f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- fuel

	/** Burns fuel items into the tank. Returns what's left (an empty bucket for lava). */
	ItemStack feed(ItemStack stack) {
		if (stack.isEmpty() || data.fuel >= VehicleType.FUEL_CAPACITY) {
			return stack;
		}
		int burn = Fuel.burnTicks(level, stack);
		if (burn <= 0) {
			return stack;
		}
		boolean lava = stack.is(Items.LAVA_BUCKET);
		int fed = 0;
		while (!stack.isEmpty() && data.fuel < VehicleType.FUEL_CAPACITY) {
			data.fuel = Math.min(VehicleType.FUEL_CAPACITY, data.fuel + burn * VehicleType.FUEL_EFFICIENCY);
			stack.shrink(1);
			fed++;
		}
		if (fed > 0) {
			Cmd.sound(level, lava ? "minecraft:item.bucket.empty_lava" : "minecraft:block.fire.ambient", root.getX(), root.getY() + 1, root.getZ(), 1.0f, 1.0f);
			warnedLowFuel = false;
		}
		if (lava && fed > 0 && stack.isEmpty()) {
			return new ItemStack(Items.BUCKET);
		}
		return stack;
	}

	private void hud(ServerPlayer driver, Controls c, boolean freeFuel) {
		MutableComponent line = Component.literal("⛽ ").withStyle(ChatFormatting.GOLD);
		if (freeFuel) {
			line.append(Component.literal("∞ (creative)").withStyle(ChatFormatting.YELLOW));
		} else if (data.fuel <= 0) {
			line.append(Component.literal("EMPTY").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
				.append(Component.literal("  Sneak + right-click me with coal, wood, lava...").withStyle(ChatFormatting.GRAY));
		} else {
			int pct = data.fuelPercent();
			int bars = Math.max(1, pct / 10);
			ChatFormatting color = pct > 30 ? ChatFormatting.GREEN : pct > 10 ? ChatFormatting.YELLOW : ChatFormatting.RED;
			line.append(Component.literal("█".repeat(bars)).withStyle(color))
				.append(Component.literal("█".repeat(10 - bars)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(" " + pct + "% (" + data.fuelTime() + ")").withStyle(ChatFormatting.GRAY));
			if (pct <= 10 && !warnedLowFuel) {
				warnedLowFuel = true;
				driver.sendSystemMessage(Component.literal("Fuel is low. The van is getting hangry.").withStyle(ChatFormatting.YELLOW));
			}
		}
		int kmh = (int) Math.round(Math.abs(speed) * 20 * 3.6);
		line.append(Component.literal("   " + kmh + " km/h").withStyle(ChatFormatting.AQUA));
		if (c.sprint && speed != 0) {
			line.append(Component.literal("  TURBO").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		}
		driver.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	// ---------------------------------------------------------------- removal

	/** Takes the whole thing out of the world: seats, model, hitbox. The data stays in {@link #data}. */
	void remove() {
		if (removed) {
			return;
		}
		removed = true;
		ejectAll();
		for (Entity seat : seats) {
			if (seat != null) {
				seat.ejectPassengers();
				seat.discard();
			}
		}
		for (Entity part : new ArrayList<>(root.getPassengers())) {
			part.discard();
		}
		root.discard();
		Vehicles.unregister(this);
	}

	/** Chunk unloaded or server stopping: drop the seats (they're rebuilt on load), keep the rest. */
	void unload() {
		if (removed) {
			return;
		}
		removed = true;
		ejectAll();
		for (Entity seat : seats) {
			if (seat != null) {
				seat.ejectPassengers();
				seat.discard();
			}
		}
		Vehicles.unregister(this);
	}
}
