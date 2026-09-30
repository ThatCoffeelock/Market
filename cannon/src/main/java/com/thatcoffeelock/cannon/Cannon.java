package com.thatcoffeelock.cannon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A placed cannon. The "root" is an invisible item display that carries the carriage, the barrel and the click
 * hitbox as passengers. The gunner sits on a separate invisible display behind the breech, which we move around
 * as the cannon turns. The cannon turns to face wherever the gunner looks; the barrel tilts with their pitch.
 *
 * Model blocks have no block state properties on purpose: their NBT format keeps changing between versions.
 *
 * Local coordinates: origin is the bottom-centre of the cannon, +Z is where the barrel points, +X is the
 * cannon's left, +Y is up. Model parts are boxes: min corner + size.
 */
public final class Cannon {
	/** One box of the model. Barrel parts are relative to the trunnion pivot instead of the origin. */
	record Part(String block, float x, float y, float z, float sx, float sy, float sz) {
	}

	/** The carriage: turns with the cannon but doesn't tilt. */
	static final List<Part> FRAME = List.of(
		new Part("minecraft:dark_oak_planks", 0.24f, 0.35f, -0.85f, 0.14f, 0.62f, 1.35f),
		new Part("minecraft:dark_oak_planks", -0.38f, 0.35f, -0.85f, 0.14f, 0.62f, 1.35f),
		new Part("minecraft:dark_oak_planks", -0.24f, 0.3f, -0.8f, 0.48f, 0.12f, 1.1f),
		new Part("minecraft:dark_oak_planks", -0.2f, 0.0f, -1.7f, 0.4f, 0.22f, 1.0f),
		new Part("minecraft:iron_block", -0.62f, 0.33f, -0.12f, 1.24f, 0.1f, 0.1f),
		new Part("minecraft:spruce_planks", 0.38f, 0.0f, -0.45f, 0.14f, 0.76f, 0.76f),
		new Part("minecraft:spruce_planks", -0.52f, 0.0f, -0.45f, 0.14f, 0.76f, 0.76f));

	/** The barrel: turns and tilts. Coordinates relative to {@link #PIVOT_Y}. */
	static final List<Part> BARREL = List.of(
		new Part("minecraft:polished_blackstone", -0.2f, -0.2f, -0.65f, 0.4f, 0.4f, 2.1f),
		new Part("minecraft:polished_blackstone", -0.24f, -0.24f, -0.7f, 0.48f, 0.48f, 0.7f),
		new Part("minecraft:iron_block", -0.25f, -0.25f, 1.25f, 0.5f, 0.5f, 0.2f),
		new Part("minecraft:iron_block", -0.09f, -0.09f, -0.9f, 0.18f, 0.18f, 0.2f),
		new Part("minecraft:black_concrete", -0.13f, -0.13f, 1.45f, 0.26f, 0.26f, 0.01f));

	/** Height of the trunnions the barrel pivots on. */
	static final double PIVOT_Y = 0.95;
	/** Distance from the pivot to the mouth of the barrel. */
	static final double MUZZLE = 1.5;
	/** Gunner's seat, behind the breech. A seated player's eyes end up about 1 block above it. */
	static final double SEAT_Y = 0.35;
	static final double SEAT_Z = -1.45;
	static final float MIN_ELEVATION = -10f;
	static final float MAX_ELEVATION = 60f;
	static final float REST_ELEVATION = 10f;
	/** Degrees per tick. It's a heavy lump of iron, it doesn't whip around. */
	static final float TRAVERSE_SPEED = 6f;
	static final float ELEVATE_SPEED = 3f;
	static final int RELOAD_TICKS = 40;
	/** Blocks per tick when the ball leaves the barrel. */
	static final double MUZZLE_SPEED = 2.4;
	static final float HITBOX_WIDTH = 1.6f;
	static final float HITBOX_HEIGHT = 1.4f;

	static final String ROOT_TAG = "cannon_root";
	static final String PART_TAG = "cannon_part";
	static final String HITBOX_TAG = "cannon_hitbox";
	static final String BARREL_TAG = "cannon_barrel_";
	static final String SEAT_TAG = "cannon_seat";

	final ServerLevel level;
	final Entity root;
	final CannonData data;
	final Entity[] barrel = new Entity[BARREL.size()];
	@Nullable Entity seat;
	@Nullable UUID gunner;

	int reload;
	private int recoil;
	private int age;
	private boolean lastJump;
	private float sentElevation = Float.NaN;
	private boolean removed;
	/** Smoke test hook: pretend a gunner is looking this way. */
	@Nullable Aim testAim;

	record Aim(float yaw, float elevation) {
	}

	Cannon(ServerLevel level, Entity root, CannonData data) {
		this.level = level;
		this.root = root;
		this.data = data;
		for (Entity part : root.getPassengers()) {
			for (String tag : part.getTags()) {
				if (tag.startsWith(BARREL_TAG)) {
					try {
						int i = Integer.parseInt(tag.substring(BARREL_TAG.length()));
						if (i >= 0 && i < barrel.length) {
							barrel[i] = part;
						}
					} catch (NumberFormatException ignored) {
						// not one of ours
					}
				}
			}
		}
	}

	public boolean isRemoved() {
		return removed || root.isRemoved();
	}

	static float clampElevation(float elevation) {
		return Mth.clamp(elevation, MIN_ELEVATION, MAX_ELEVATION);
	}

	// ---------------------------------------------------------------- geometry

	/** Local (x = left, z = forward) to world, for the given position and heading. */
	static double[] toWorld(double x, double z, float yaw, double lx, double lz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[] {x + lx * c - lz * s, z + lx * s + lz * c};
	}

	/** Unit vector the barrel points along. */
	Vec3 direction() {
		double yaw = Math.toRadians(root.getYRot());
		double pitch = Math.toRadians(data.elevation);
		return new Vec3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
	}

	Vec3 muzzle() {
		return new Vec3(root.getX(), root.getY() + PIVOT_Y, root.getZ()).add(direction().scale(MUZZLE));
	}

	/** Is there room for a cannon here? Just the carriage; the trail and the gunner can hang over a ledge. */
	static boolean fits(ServerLevel level, double x, double y, double z) {
		return level.noCollision(new AABB(x - 0.7, y + 0.01, z - 0.7, x + 0.7, y + 1.3, z + 0.7));
	}

	/** Display transformation NBT: optional rotation about local X (in radians), then a scaled box. */
	static String transformation(double angle, double tx, double ty, double tz, double sx, double sy, double sz) {
		double half = angle / 2;
		return "{left_rotation:[" + Cmd.f(Math.sin(half)) + "f,0f,0f," + Cmd.f(Math.cos(half)) + "f],right_rotation:[0f,0f,0f,1f],translation:["
			+ Cmd.f(tx) + "f," + Cmd.f(ty) + "f," + Cmd.f(tz) + "f],scale:[" + Cmd.f(sx) + "f," + Cmd.f(sy) + "f," + Cmd.f(sz) + "f]}";
	}

	/**
	 * A barrel part tilted up by the elevation around the pivot. A display draws translation + rotation * (scale * box),
	 * so to spin the box around the pivot we rotate its corner offset ourselves and use that as the translation.
	 */
	static String barrelTransformation(Part part, float elevation, double recoil) {
		double angle = Math.toRadians(-elevation);
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		double oy = part.y();
		double oz = part.z() - recoil;
		return transformation(angle, part.x(), PIVOT_Y + oy * c - oz * s, oy * s + oz * c, part.sx(), part.sy(), part.sz());
	}

	static String summonCommand(UUID id, double x, double y, double z, float yaw, float elevation) {
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(x, y, z)).append(" {")
			.append(Cmd.uuidNbt(id)).append(",Tags:[\"").append(ROOT_TAG).append("\"],").append(rot).append(",teleport_duration:2,Passengers:[");
		cmd.append("{id:\"minecraft:interaction\",width:").append(Cmd.f(HITBOX_WIDTH)).append("f,height:").append(Cmd.f(HITBOX_HEIGHT))
			.append("f,response:1b,Tags:[\"").append(PART_TAG).append("\",\"").append(HITBOX_TAG).append("\"]}");
		for (Part part : FRAME) {
			cmd.append(",{id:\"minecraft:block_display\",block_state:\"").append(part.block()).append("\",Tags:[\"").append(PART_TAG)
				.append("\"],").append(rot).append(",teleport_duration:2,transformation:")
				.append(transformation(0, part.x(), part.y(), part.z(), part.sx(), part.sy(), part.sz())).append("}");
		}
		for (int i = 0; i < BARREL.size(); i++) {
			Part part = BARREL.get(i);
			cmd.append(",{id:\"minecraft:block_display\",block_state:\"").append(part.block()).append("\",Tags:[\"").append(PART_TAG)
				.append("\",\"").append(BARREL_TAG).append(i).append("\"],").append(rot).append(",teleport_duration:2,transformation:")
				.append(barrelTransformation(part, elevation, 0)).append("}");
		}
		return cmd.append("]}").toString();
	}

	/** Tilts the barrel model. Interpolated on the client, so it moves smoothly. */
	private void sendBarrel(float elevation, double recoil, int duration) {
		for (int i = 0; i < barrel.length; i++) {
			Entity part = barrel[i];
			if (part != null && !part.isRemoved()) {
				Cmd.run(level, "data merge entity " + part.getUUID() + " {transformation:" + barrelTransformation(BARREL.get(i), elevation, recoil)
					+ ",start_interpolation:0,interpolation_duration:" + duration + "}");
			}
		}
		sentElevation = elevation;
	}

	// ---------------------------------------------------------------- ticking

	void tick() {
		if (isRemoved()) {
			return;
		}
		age++;
		ensureSeat();
		updateGunner();
		if (reload > 0) {
			reload--;
			if (reload == 0 && gunner() != null) {
				Cmd.sound(level, "minecraft:block.iron_trapdoor.close", root.getX(), root.getY() + 1, root.getZ(), 0.8f, 0.7f);
			}
		}

		ServerPlayer player = gunner();
		boolean fire = false;
		if (testAim != null) {
			aim(testAim.yaw(), testAim.elevation());
		} else if (player != null) {
			aim(player.getYRot(), -player.getXRot());
			boolean jump = player.getLastClientInput().jump();
			fire = jump && !lastJump;
			lastJump = jump;
		}

		if (recoil > 0) {
			recoil--;
			if (recoil == 0) {
				sendBarrel(data.elevation, 0, 8); // roll back into battery
			}
		} else if (Float.isNaN(sentElevation) || Math.abs(sentElevation - data.elevation) > 0.2f) {
			sendBarrel(data.elevation, 0, 2);
		}

		if (fire && player != null) {
			tryFire(player);
		}
		if (player != null && age % 5 == 0) {
			hud(player);
		}
	}

	/** Turns and tilts towards where the gunner is looking, at the cannon's own (slow) pace. */
	private void aim(float targetYaw, float targetElevation) {
		float yaw = root.getYRot();
		float delta = Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), -TRAVERSE_SPEED, TRAVERSE_SPEED);
		if (Math.abs(delta) > 0.01f) {
			float newYaw = Mth.wrapDegrees(yaw + delta);
			root.setYRot(newYaw);
			for (Entity part : root.getPassengers()) {
				part.setYRot(newYaw);
			}
			if (age % 6 == 0) {
				Cmd.sound(level, "minecraft:block.grindstone.use", root.getX(), root.getY() + 0.5, root.getZ(), 0.25f, 0.6f);
			}
		}
		float target = clampElevation(targetElevation);
		data.elevation += Mth.clamp(target - data.elevation, -ELEVATE_SPEED, ELEVATE_SPEED);
		placeSeat();
	}

	private void tryFire(ServerPlayer player) {
		if (reload > 0) {
			Cmd.sound(level, "minecraft:block.dispenser.fail", root.getX(), root.getY() + 1, root.getZ(), 0.6f, 1.4f);
			return;
		}
		if (!player.isCreative() && !CannonItems.takeCannonball(player)) {
			Cmd.sound(level, "minecraft:block.dispenser.fail", root.getX(), root.getY() + 1, root.getZ(), 0.8f, 0.8f);
			player.sendSystemMessage(Component.literal("*click* Out of cannonballs. Craft some: 1 iron ingot + 1 gunpowder = 2 balls.")
				.withStyle(ChatFormatting.YELLOW));
			reload = 10; // so holding space doesn't spam the chat
			return;
		}
		fire(player);
	}

	/** Fires a ball (no ammo check). Returns false while reloading. */
	boolean fire(@Nullable Entity shooter) {
		if (reload > 0 || isRemoved()) {
			return false;
		}
		Vec3 dir = direction();
		Vec3 muzzle = muzzle();
		if (Cannonball.launch(level, muzzle, dir.scale(MUZZLE_SPEED), shooter) == null) {
			return false;
		}
		reload = RELOAD_TICKS;
		recoil = 3;
		sendBarrel(data.elevation, 0.35, 1);
		Cmd.sound(level, "minecraft:entity.generic.explode", muzzle.x, muzzle.y, muzzle.z, 4.0f, 0.6f);
		Cmd.sound(level, "minecraft:entity.firework_rocket.blast", muzzle.x, muzzle.y, muzzle.z, 3.0f, 0.5f);
		Vec3 puff = muzzle.add(dir.scale(0.6));
		Cmd.particles(level, "minecraft:explosion", puff.x, puff.y, puff.z, 0.1, 0, 1);
		Cmd.particles(level, "minecraft:large_smoke", puff.x, puff.y, puff.z, 0.4, 0.05, 25);
		Cmd.particles(level, "minecraft:flame", puff.x, puff.y, puff.z, 0.15, 0.08, 10);
		return true;
	}

	private void hud(ServerPlayer player) {
		MutableComponent line = Component.literal("Elevation ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(Math.round(data.elevation) + "°").withStyle(ChatFormatting.WHITE))
			.append(Component.literal("   Range ≈ ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(Math.round(estimatedRange()) + " blocks").withStyle(ChatFormatting.AQUA))
			.append(Component.literal("   Balls: ").withStyle(ChatFormatting.GRAY));
		if (player.isCreative()) {
			line.append(Component.literal("∞").withStyle(ChatFormatting.YELLOW));
		} else {
			int balls = CannonItems.countCannonballs(player);
			line.append(Component.literal(String.valueOf(balls)).withStyle(balls > 0 ? ChatFormatting.WHITE : ChatFormatting.RED));
		}
		if (reload > 0) {
			int done = (RELOAD_TICKS - reload) * 10 / RELOAD_TICKS;
			line.append(Component.literal("   Reloading ").withStyle(ChatFormatting.GOLD))
				.append(Component.literal("█".repeat(done)).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("█".repeat(10 - done)).withStyle(ChatFormatting.DARK_GRAY));
		} else {
			line.append(Component.literal("   READY").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	/** How far a ball flies over flat ground at the cannon's own height. Ignores hills, obviously. */
	double estimatedRange() {
		Vec3 pos = muzzle();
		Vec3 vel = direction().scale(MUZZLE_SPEED);
		double ground = root.getY();
		for (int t = 0; t < Cannonball.MAX_AGE && pos.y > ground; t++) {
			pos = pos.add(vel);
			vel = Cannonball.step(vel);
		}
		double dx = pos.x - root.getX();
		double dz = pos.z - root.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---------------------------------------------------------------- seat

	private void ensureSeat() {
		if (seat != null && !seat.isRemoved()) {
			return;
		}
		UUID id = UUID.randomUUID();
		double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, SEAT_Z);
		Cmd.run(level, "summon minecraft:item_display " + Cmd.pos(w[0], root.getY() + SEAT_Y, w[1])
			+ " {" + Cmd.uuidNbt(id) + ",Tags:[\"" + SEAT_TAG + "\"],teleport_duration:2,Rotation:[" + Cmd.f(root.getYRot()) + "f,0f]}");
		seat = level.getEntity(id);
		if (seat != null) {
			seat.setAttached(CannonMod.SEAT, true);
			Cannons.registerSeat(seat, this);
		}
	}

	private void placeSeat() {
		if (seat == null) {
			return;
		}
		float yaw = root.getYRot();
		double[] w = toWorld(root.getX(), root.getZ(), yaw, 0, SEAT_Z);
		double y = root.getY() + SEAT_Y;
		if (seat.getX() != w[0] || seat.getY() != y || seat.getZ() != w[1]) {
			seat.setPos(w[0], y, w[1]);
		}
		if (seat.getYRot() != yaw) {
			seat.setYRot(yaw);
		}
	}

	private void updateGunner() {
		Entity passenger = seat == null ? null : seat.getFirstPassenger();
		UUID now = passenger instanceof ServerPlayer ? passenger.getUUID() : null;
		if (gunner != null && !gunner.equals(now)) {
			UUID gone = gunner;
			gunner = null;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
			if (player != null) {
				stepOut(player);
			}
		}
		if (now != null && gunner == null) {
			gunner = now;
			lastJump = true; // the jump that got them in doesn't count as a shot
		}
	}

	@Nullable ServerPlayer gunner() {
		return seat != null && seat.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	boolean isOwner(Player player) {
		return data.owner.isEmpty() || data.owner.equals(player.getUUID().toString());
	}

	/** Puts the player behind the cannon. */
	boolean man(ServerPlayer player) {
		if (seat == null || !seat.getPassengers().isEmpty()) {
			return false;
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		Cmd.run(level, "ride " + player.getUUID() + " mount " + seat.getUUID());
		if (player.getVehicle() != seat) {
			return false;
		}
		gunner = player.getUUID();
		lastJump = true;
		return true;
	}

	/** Called when the gunner gets off: put them next to the cannon instead of inside the carriage. */
	private void stepOut(ServerPlayer player) {
		if (seat == null || player.isRemoved() || player.isDeadOrDying() || player.getVehicle() != null || player.level() != level
			|| player.distanceToSqr(seat) > 16) {
			return;
		}
		float yaw = root.getYRot();
		double[][] spots = {{0, SEAT_Z - 0.6}, {1.2, SEAT_Z}, {-1.2, SEAT_Z}, {1.3, 0}, {-1.3, 0}};
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
	}

	private void eject() {
		ServerPlayer player = gunner();
		if (player != null) {
			player.stopRiding();
			gunner = null;
			stepOut(player);
		}
	}

	// ---------------------------------------------------------------- removal

	/** Takes the whole thing out of the world: seat, model, hitbox. */
	void remove() {
		if (removed) {
			return;
		}
		removed = true;
		eject();
		if (seat != null) {
			seat.ejectPassengers();
			seat.discard();
		}
		for (Entity part : new ArrayList<>(root.getPassengers())) {
			part.discard();
		}
		root.discard();
		Cannons.unregister(this);
	}

	/** Chunk unloaded or server stopping: drop the seat (it's rebuilt on load), keep the rest. */
	void unload() {
		if (removed) {
			return;
		}
		removed = true;
		eject();
		if (seat != null) {
			seat.ejectPassengers();
			seat.discard();
		}
		Cannons.unregister(this);
	}
}
