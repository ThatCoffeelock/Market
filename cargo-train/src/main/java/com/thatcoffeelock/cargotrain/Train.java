package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One train: a locomotive (the saved root entity, carrying its model) and a cargo wagon (rebuilt whenever the
 * train loads). While it's running it shuttles along the track by itself: to the end of the line, turn
 * around, back to the other end, stopping at every station on the way. When it's parked, whoever sits in
 * the cab can drive it with W/S.
 *
 * A running train keeps the chunks it's in loaded (with the same short-lived tickets a flying ender pearl uses), so it
 * keeps hauling when you're far away. A parked one doesn't.
 */
public final class Train {
	/** Rails per tick: 8 blocks a second, as fast as a vanilla minecart. */
	static final double MAX_SPEED = 0.4;
	static final double DRIVE_SPEED = 0.3;
	static final double ACCEL = 0.008;
	static final double BRAKE = 0.012;
	/** How far ahead it looks for stations, so it can brake in time. */
	static final int STATION_LOOKAHEAD = 6;
	static final int STATION_DWELL = 60;
	static final int END_DWELL = 30;
	/** After this many rails a station it just served counts as new again (so loops work). */
	static final double FORGET_AFTER = 8;

	final ServerLevel level;
	final Entity root;
	final TrainData data;
	@Nullable Route route;

	double speed;
	int dwell;
	boolean turnAround;
	/** Stations served at the last stop: not served again until the train has moved on a bit. */
	final List<BlockPos> recent = new ArrayList<>();
	private double sinceStop;
	private boolean crossed;
	private int railsCrossed;
	@Nullable String lastStop;

	@Nullable Entity wagon;
	private final List<Entity> crates = new ArrayList<>();
	private int shownCrates = -1;
	private int wagonRetry;
	@Nullable Entity seat;
	@Nullable UUID driver;

	private int age;
	private boolean lastJump;
	private int hornCooldown;
	private boolean removed;

	Train(ServerLevel level, Entity root, TrainData data) {
		this.level = level;
		this.root = root;
		this.data = data;
	}

	boolean isRemoved() {
		return removed || root.isRemoved();
	}

	boolean isOwner(Player player) {
		return data.owner.isEmpty() || data.owner.equals(player.getUUID().toString());
	}

	/** May this player drive, open the cargo, start and stop it? */
	boolean mayUse(Player player) {
		return !data.locked || isOwner(player) || player.isCreative();
	}

	int dir() {
		return data.dir;
	}

	// ---------------------------------------------------------------- geometry

	/** Local (x = left, z = towards the nose) to world x/z. Same convention as display entity rotation. */
	static double[] toWorld(double x, double z, float yaw, double lx, double lz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[] {x + lx * c - lz * s, z + lx * s + lz * c};
	}

	Vec3 locoPos() {
		return route != null ? route.point(route.s) : root.position();
	}

	Vec3 wagonPos() {
		if (route != null) {
			return route.point(route.s - Route.GAP);
		}
		double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, -Route.GAP);
		return new Vec3(w[0], root.getY(), w[1]);
	}

	float wagonYaw() {
		return route != null ? route.heading(route.s - Route.GAP, root.getYRot()) : root.getYRot();
	}

	// ---------------------------------------------------------------- ticking

	void tick() {
		if (isRemoved()) {
			return;
		}
		age++;
		if (route == null && age % 40 == 1) {
			findTrack();
		}
		ensureWagon();
		ensureSeat();
		updateDriver();
		ServerPlayer driver = driver();

		if (route != null) {
			if (dwell > 0) {
				dwell--;
				if (dwell == 0) {
					depart();
				}
			} else if (data.running) {
				advance(MAX_SPEED, true);
			} else {
				drive(driver);
			}
		}
		place();
		keepLoaded(driver);
		if (age % 10 == 0) {
			updateCrates();
		}

		if (driver != null) {
			boolean jump = driver.getLastClientInput().jump();
			if (jump && !lastJump) {
				horn();
			}
			lastJump = jump;
			if (age % 5 == 0) {
				hud(driver);
			}
		}
		if (hornCooldown > 0) {
			hornCooldown--;
		}
		if (speed > 0.05) {
			effects();
		}
	}

	/** After loading, or if the track under it went missing: get back on the rails. */
	private void findTrack() {
		route = Route.start(level, data.at, root.getYRot());
		if (route == null) {
			BlockPos under = BlockPos.containing(root.getX(), root.getY(), root.getZ());
			if (!under.equals(data.at)) {
				route = Route.start(level, under, root.getYRot());
			}
		}
	}

	/** Moves along the track at up to {@code wanted} rails a tick, braking in time for the end of the line and (if asked) stations. */
	private void advance(double wanted, boolean stations) {
		Route r = route;
		int dir = dir();
		if (crossed) {
			crossed = false;
			r.revalidate(dir);
		}
		r.grow(dir);
		r.prune(dir);

		double front = r.front(dir);
		int stop = r.limit(dir);
		boolean station = false;
		if (stations) {
			int i = dir > 0 ? (int) Math.floor(front + 1e-6) + 1 : (int) Math.ceil(front - 1e-6) - 1;
			for (int k = 0; k < STATION_LOOKAHEAD && i >= 0 && i < r.size(); k++, i += dir) {
				if (!Stations.near(level, r.node(i).pos(), recent).isEmpty()) {
					stop = i;
					station = true;
					break;
				}
			}
		}
		double dist = Math.max(0, (stop - front) * dir);
		double cap = Math.max(0.03, Math.sqrt(2 * BRAKE * dist));
		speed = Math.min(Math.min(speed + ACCEL, wanted), cap);
		if (speed <= 0) {
			speed = 0;
			return;
		}
		boolean arrives = dist <= speed;
		double to = arrives ? stop : front + dir * speed;
		if ((int) Math.floor(to) != (int) Math.floor(front)) {
			crossed = true;
			if (++railsCrossed % 2 == 0) {
				Vec3 p = r.point(to);
				Cmd.sound(level, "minecraft:block.metal.step", p.x, p.y, p.z, 0.3f, 0.5f + (float) speed);
			}
		}
		sinceStop += Math.abs(to - front);
		if (sinceStop > FORGET_AFTER) {
			recent.clear();
		}
		r.setFront(dir, to);
		if (arrives) {
			speed = 0;
			if (data.running) {
				arrive(stop, station);
			}
		}
	}

	/** Parked, with someone in the cab: W/S drives it. No station stops when you're driving. */
	private void drive(@Nullable ServerPlayer driver) {
		int want = 0;
		if (driver != null && mayUse(driver)) {
			Input input = driver.getLastClientInput();
			want = input.forward() ? 1 : input.backward() ? -1 : 0;
		}
		if (want == 0) {
			advance(Math.max(0, speed - BRAKE), false);
			return;
		}
		if (want != data.dir) {
			if (speed > 0.02) {
				advance(Math.max(0, speed - 2 * BRAKE), false);
				return;
			}
			speed = 0;
			data.dir = want;
		}
		advance(DRIVE_SPEED, false);
	}

	private void arrive(int index, boolean stationStop) {
		Route r = route;
		BlockPos rail = r.node(index).pos();
		List<BlockPos> chests = Stations.near(level, rail, recent);
		boolean end = index == r.limit(dir()) && r.deadAhead(dir());
		if (!chests.isEmpty()) {
			serve(chests);
			recent.clear();
			recent.addAll(chests);
			sinceStop = 0;
			dwell = STATION_DWELL;
		}
		if (end) {
			if (chests.isEmpty()) {
				recent.clear();
			}
			turnAround = true;
			dwell = Math.max(dwell, END_DWELL);
		}
	}

	private void depart() {
		if (turnAround) {
			turnAround = false;
			// maybe someone laid more track while we were standing here
			route.forgetEnd(dir());
			route.grow(dir());
			boolean more = route.limit(dir()) != (int) Math.round(route.front(dir())) || !route.deadAhead(dir());
			if (!more) {
				data.dir = -data.dir;
			}
		}
		if (data.running) {
			Vec3 p = locoPos();
			Cmd.sound(level, "minecraft:item.goat_horn.sound.1", p.x, p.y + 1, p.z, 1.2f, 1.6f);
		}
	}

	private void serve(List<BlockPos> chests) {
		List<String> parts = new ArrayList<>();
		for (BlockPos pos : chests) {
			Stations.Visit visit = Stations.serve(level, pos, data.cargo);
			if (visit == null) {
				continue;
			}
			data.hauled += visit.unloaded();
			String what = visit.mode().title + ": ";
			List<String> moved = new ArrayList<>();
			if (visit.unloaded() > 0) {
				moved.add("unloaded " + visit.unloaded());
			}
			if (visit.loaded() > 0) {
				moved.add("loaded " + visit.loaded());
			}
			if (!moved.isEmpty()) {
				what += String.join(", ", moved) + " items";
			} else if (visit.mode() == Stations.Mode.DROPOFF) {
				what += data.cargo.isEmpty() ? "nothing to drop off" : "the chest is full!";
			} else if (visit.mode() == Stations.Mode.PICKUP) {
				what += wagonFull() ? "the wagon is full!" : "nothing to pick up";
			} else {
				what += "nothing to swap";
			}
			parts.add(what);
			Cmd.particles(level, "minecraft:happy_villager", pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0, 8);
		}
		lastStop = String.join(" · ", parts);
		Vec3 p = locoPos();
		// the Dutch station chime, more or less
		Cmd.sound(level, "minecraft:block.note_block.bell", p.x, p.y + 1, p.z, 1.0f, 1.19f);
		CargoTrainMod.later(6, () -> Cmd.sound(level, "minecraft:block.note_block.bell", p.x, p.y + 1, p.z, 1.0f, 0.94f));
		ServerPlayer driver = driver();
		if (driver != null && !parts.isEmpty()) {
			driver.sendSystemMessage(Component.literal(lastStop).withStyle(ChatFormatting.YELLOW));
		}
	}

	boolean wagonFull() {
		for (int i = 0; i < data.cargo.getContainerSize(); i++) {
			if (data.cargo.getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	void horn() {
		if (hornCooldown > 0) {
			return;
		}
		hornCooldown = 30;
		Vec3 p = locoPos();
		Cmd.sound(level, "minecraft:item.goat_horn.sound.1", p.x, p.y + 1, p.z, 3.0f, 1.3f);
	}

	private void effects() {
		Vec3 p = locoPos();
		float yaw = root.getYRot();
		if (age % 6 == 0) {
			double[] w = toWorld(p.x, p.z, yaw, 0, 0.62);
			Cmd.particles(level, "minecraft:smoke", w[0], p.y + 1.5, w[1], 0.05, 0.02, 3);
		}
		if (age % 4 == 0 && route != null) {
			shove();
		}
	}

	/** Mobs standing on the track get shoved aside; players get the horn. */
	private void shove() {
		int dir = dir();
		Vec3 front = dir > 0 ? locoPos() : wagonPos();
		Vec3 ahead = route.point(route.front(dir) + dir * 1.2);
		AABB box = new AABB(ahead.x - 0.9, ahead.y, ahead.z - 0.9, ahead.x + 0.9, ahead.y + 1.8, ahead.z + 0.9);
		double fx = ahead.x - front.x;
		double fz = ahead.z - front.z;
		double len = Math.max(1e-3, Math.sqrt(fx * fx + fz * fz));
		fx /= len;
		fz /= len;
		for (Entity entity : level.getEntities(root, box, e -> e.isAlive() && e.getVehicle() == null && !e.isSpectator())) {
			if (entity instanceof Mob mob) {
				double side = (entity.getX() - front.x) * -fz + (entity.getZ() - front.z) * fx >= 0 ? 1 : -1;
				mob.setDeltaMovement(-fz * side * 0.6 + fx * 0.3, 0.35, fx * side * 0.6 + fz * 0.3);
			} else if (entity instanceof Player) {
				horn();
			}
		}
	}

	// ---------------------------------------------------------------- placing the cars

	/** Moves a car (and turns its model parts with it). The seat's passenger is a player, whose head we leave alone. */
	private static void move(Entity car, Vec3 p, float yaw, boolean turnParts) {
		if (car.getX() != p.x || car.getY() != p.y || car.getZ() != p.z) {
			car.setPos(p.x, p.y, p.z);
		}
		if (Math.abs(Mth.wrapDegrees(car.getYRot() - yaw)) > 0.01f) {
			car.setYRot(yaw);
			if (turnParts) {
				for (Entity part : car.getPassengers()) {
					part.setYRot(yaw);
				}
			}
		}
	}

	private void place() {
		if (route != null) {
			move(root, route.point(route.s), route.heading(route.s, root.getYRot()), true);
			data.at = route.node((int) Math.round(route.s)).pos();
		}
		if (wagon != null && !wagon.isRemoved()) {
			move(wagon, wagonPos(), wagonYaw(), true);
		}
		if (seat != null && !seat.isRemoved()) {
			float yaw = root.getYRot();
			double[] w = toWorld(root.getX(), root.getZ(), yaw, 0, TrainModel.SEAT_Z);
			move(seat, new Vec3(w[0], root.getY() + TrainModel.SEAT_Y, w[1]), yaw, false);
		}
	}

	/** Summons something we rebuild on every load, and marks it so a stray copy gets cleaned up after a crash. */
	private @Nullable Entity summonMarked(String command, UUID id) {
		Cmd.run(level, command);
		Entity entity = level.getEntity(id);
		if (entity != null) {
			entity.setAttached(CargoTrainMod.MARKER, true);
			for (Entity part : entity.getPassengers()) {
				part.setAttached(CargoTrainMod.MARKER, true);
			}
			Trains.registerMarker(entity, this);
		}
		return entity;
	}

	private void ensureWagon() {
		if ((wagon != null && !wagon.isRemoved()) || wagonRetry-- > 0) {
			return;
		}
		Vec3 p = wagonPos();
		int show = crateCount();
		UUID id = UUID.randomUUID();
		wagon = summonMarked(TrainModel.wagonCommand(id, p.x, p.y, p.z, wagonYaw(), show), id);
		crates.clear();
		if (wagon == null) {
			wagonRetry = 100; // probably in a chunk that isn't loaded; try again in a bit
			return;
		}
		List<Entity> parts = wagon.getPassengers();
		int first = parts.size() - TrainModel.CRATES.size();
		for (int i = 0; i < TrainModel.CRATES.size() && first >= 0; i++) {
			crates.add(parts.get(first + i));
		}
		shownCrates = show;
	}

	/** How many crates to show: none when empty, all four when (nearly) full. */
	int crateCount() {
		int used = data.usedSlots();
		return used == 0 ? 0 : Math.min(TrainModel.CRATES.size(), 1 + used * TrainModel.CRATES.size() / (TrainData.SLOTS + 1));
	}

	private void updateCrates() {
		int show = crateCount();
		if (show == shownCrates || crates.size() != TrainModel.CRATES.size()) {
			return;
		}
		for (int i = 0; i < crates.size(); i++) {
			boolean was = i < shownCrates;
			boolean now = i < show;
			Entity crate = crates.get(i);
			if (was != now && !crate.isRemoved()) {
				Cmd.run(level, "data merge entity " + crate.getUUID() + " {transformation:" + TrainModel.transformation(TrainModel.CRATES.get(i), now)
					+ ",start_interpolation:0,interpolation_duration:6}");
			}
		}
		shownCrates = show;
	}

	// ---------------------------------------------------------------- keeping chunks loaded

	/** A running (or driven) train keeps the chunks under it and just ahead loaded and ticking. */
	private void keepLoaded(@Nullable ServerPlayer driver) {
		if (route == null || (!data.running && driver == null) || (age % 10 != 0 && age > 2)) {
			return;
		}
		Set<ChunkPos> chunks = new HashSet<>();
		for (Track.Node node : route.nodes) {
			chunks.add(new ChunkPos(node.pos().getX() >> 4, node.pos().getZ() >> 4));
		}
		if (route.waitingFor != null) {
			chunks.add(new ChunkPos(route.waitingFor.getX() >> 4, route.waitingFor.getZ() >> 4));
		}
		for (ChunkPos chunk : chunks) {
			level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunk, 2);
		}
	}

	// ---------------------------------------------------------------- the driver's seat

	private void ensureSeat() {
		if (seat != null && !seat.isRemoved()) {
			return;
		}
		UUID id = UUID.randomUUID();
		double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, TrainModel.SEAT_Z);
		seat = summonMarked("summon minecraft:item_display " + Cmd.pos(w[0], root.getY() + TrainModel.SEAT_Y, w[1]) + " {" + Cmd.uuidNbt(id)
			+ ",Tags:[\"cargotrain_seat\"],teleport_duration:2,Rotation:[" + Cmd.f(root.getYRot()) + "f,0f]}", id);
	}

	@Nullable ServerPlayer driver() {
		return seat != null && seat.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	private void updateDriver() {
		Entity passenger = seat == null ? null : seat.getFirstPassenger();
		UUID now = passenger instanceof ServerPlayer ? passenger.getUUID() : null;
		if (driver != null && !driver.equals(now)) {
			UUID gone = driver;
			driver = null;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
			if (player != null) {
				stepOut(player);
			}
		}
		if (now != null && driver == null) {
			driver = now;
			lastJump = true; // the jump that got them in doesn't honk
		}
	}

	/** Puts the player in the cab. */
	boolean board(ServerPlayer player) {
		if (seat == null || seat.isRemoved() || !seat.getPassengers().isEmpty()) {
			return false;
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		Cmd.run(level, "ride " + player.getUUID() + " mount " + seat.getUUID());
		if (player.getVehicle() != seat) {
			return false;
		}
		driver = player.getUUID();
		lastJump = true;
		return true;
	}

	/** Called when the driver gets off: put them beside the track instead of inside the cab. */
	private void stepOut(ServerPlayer player) {
		if (seat == null || player.isRemoved() || player.isDeadOrDying() || player.getVehicle() != null || player.level() != level
			|| player.distanceToSqr(seat) > 25) {
			return;
		}
		float yaw = root.getYRot();
		double[][] spots = {{1.3, TrainModel.SEAT_Z}, {-1.3, TrainModel.SEAT_Z}, {1.3, 0.6}, {-1.3, 0.6}, {0, 1.8}};
		for (double[] spot : spots) {
			double[] w = toWorld(root.getX(), root.getZ(), yaw, spot[0], spot[1]);
			for (double dy : new double[] {0, 1, -1}) {
				double y = Math.floor(root.getY()) + dy;
				if (level.noCollision(new AABB(w[0] - 0.3, y, w[1] - 0.3, w[0] + 0.3, y + 1.8, w[1] + 0.3))) {
					Cmd.run(level, "tp " + player.getUUID() + " " + Cmd.pos(w[0], y, w[1]));
					return;
				}
			}
		}
	}

	void getOff(ServerPlayer player) {
		if (driver() == player) {
			player.stopRiding();
			driver = null;
			stepOut(player);
		}
	}

	// ---------------------------------------------------------------- status

	String status() {
		if (route == null) {
			return "Off the rails! Pick it up and put it back on a track.";
		}
		if (dwell > 0 && turnAround) {
			return "End of the line. Turning around...";
		}
		if (dwell > 0) {
			return "At a station.";
		}
		if (!data.running) {
			return speed > 0 ? "Driving." : "Parked.";
		}
		if (speed == 0 && route.waitingFor != null) {
			return "Waiting for the track ahead to load...";
		}
		if (speed == 0 && route.headDead && route.tailDead) {
			return "Stuck: there's no track either way.";
		}
		return dir() > 0 ? "Running, locomotive first." : "Running, wagon first.";
	}

	int kmh() {
		return (int) Math.round(speed * 20 * 3.6);
	}

	private void hud(ServerPlayer player) {
		MutableComponent line = Component.literal(kmh() + " km/h").withStyle(ChatFormatting.AQUA)
			.append(Component.literal("   " + status()).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("   Cargo " + data.usedSlots() + "/" + TrainData.SLOTS).withStyle(ChatFormatting.GOLD));
		if (data.running) {
			line.append(Component.literal("   Space: horn · Shift: get off").withStyle(ChatFormatting.GRAY));
		} else {
			line.append(Component.literal("   W/S: drive · Space: horn · Shift: get off").withStyle(ChatFormatting.GRAY));
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	// ---------------------------------------------------------------- removal

	private void discardRebuilt() {
		ServerPlayer player = driver();
		if (player != null) {
			player.stopRiding();
			driver = null;
			stepOut(player);
		}
		if (seat != null) {
			seat.ejectPassengers();
			seat.discard();
			seat = null;
		}
		if (wagon != null) {
			for (Entity part : new ArrayList<>(wagon.getPassengers())) {
				part.discard();
			}
			wagon.discard();
			wagon = null;
		}
		crates.clear();
	}

	/** Gone for good: every entity it's made of. */
	void remove() {
		if (removed) {
			return;
		}
		removed = true;
		discardRebuilt();
		for (Entity part : new ArrayList<>(root.getPassengers())) {
			part.discard();
		}
		root.removeAttached(CargoTrainMod.DATA);
		root.discard();
		Trains.unregister(this);
	}

	/** Chunk unloaded or server stopping: drop the wagon and seat (they're rebuilt on load), keep the locomotive. */
	void unload() {
		if (removed) {
			return;
		}
		removed = true;
		discardRebuilt();
		Trains.unregister(this);
	}
}
