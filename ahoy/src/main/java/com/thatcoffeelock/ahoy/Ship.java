package com.thatcoffeelock.ahoy;

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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One ship, built the same way as the Mobile Home van: an invisible root entity carrying ~50 block
 * displays as passengers, plus invisible seats and click hitboxes that we move along every tick.
 * No blocks are ever placed, so it's light on the server.
 */
public final class Ship implements AhoyApi.ShipView {
	public record Controls(boolean forward, boolean back, boolean left, boolean right, boolean jump) {
		static final Controls NONE = new Controls(false, false, false, false, false);

		static Controls of(Input input) {
			return new Controls(input.forward(), input.backward(), input.left(), input.right(), input.jump());
		}
	}

	private static final double MAX_SPEED = 0.4;

	final ServerLevel level;
	final Entity root;
	final ShipData data;
	private final Entity[] seats = new Entity[ShipModel.SPOTS.size()];
	private final UUID[] riders = new UUID[ShipModel.SPOTS.size()];
	private final Entity[] hitboxes = new Entity[ShipModel.HITBOX_Z.length];
	/** The cannons, when the Cannon mod is installed. */
	final BunkDeck bunkDeck = new BunkDeck(this);
	final @Nullable GunDeck gunDeck = AhoyMod.hasCannon() ? CannonLink.deck(this) : null;

	double speed;
	private int age;
	private float sentYaw = Float.NaN;
	private boolean lastJump;
	private int bellCooldown;
	private boolean removed;
	@Nullable Controls testControls;

	Ship(ServerLevel level, Entity root, ShipData data) {
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

	boolean mayCommand(Player player) {
		return !data.locked || isOwner(player) || player.isCreative();
	}

	// ---------------------------------------------------------------- what other mods see (AhoyApi)

	@Override
	public String name() {
		return data.name;
	}

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public Vec3 position() {
		return root.position();
	}

	@Override
	public List<Container> holds() {
		return List.of(data.cargoA, data.cargoB);
	}

	@Override
	public boolean mayUse(Player player) {
		return mayCommand(player);
	}

	@Override
	public boolean isGone() {
		return isRemoved();
	}

	// ---------------------------------------------------------------- geometry

	/** Local (x = port, z = bow) to world x/z. Same convention as display entity rotation. */
	static double[] toWorld(double x, double z, float yaw, double lx, double lz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[] {x + lx * c - lz * s, z + lx * s + lz * c};
	}

	static double[] toLocal(double x, double z, float yaw, double wx, double wz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		double dx = wx - x;
		double dz = wz - z;
		return new double[] {dx * c + dz * s, -dx * s + dz * c};
	}

	/** Is there room for the hull here? Water doesn't count as in the way; land, blocks and boats do. */
	static boolean hullFits(ServerLevel level, double surface, double x, double z, float yaw) {
		for (double segment : ShipModel.HULL_Z) {
			double[] c = toWorld(x, z, yaw, 0, segment);
			double h = ShipModel.HULL_HALF;
			if (!level.noCollision(new AABB(c[0] - h, surface - 0.85, c[1] - h, c[0] + h, surface + 2.5, c[1] + h))) {
				return false;
			}
		}
		return true;
	}

	/** Is the ship floating? Checks for water under the middle and both ends. */
	static boolean afloat(ServerLevel level, double surface, double x, double z, float yaw) {
		for (double segment : ShipModel.HULL_Z) {
			double[] c = toWorld(x, z, yaw, 0, segment);
			if (!level.getFluidState(BlockPos.containing(c[0], surface - 0.5, c[1])).is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- summoning

	private static String safeName(String name) {
		String cleaned = name.replaceAll("[^A-Za-z0-9 '!?.,()&-]", "").trim();
		return cleaned.isEmpty() ? "Nameless" : cleaned.substring(0, Math.min(40, cleaned.length()));
	}

	/** The whole ship: an invisible root, the model parts and two back-to-back name plates on the stern. */
	static String summonCommand(ShipData data, UUID id, double x, double y, double z, float yaw) {
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		String text = "text:{text:\"" + safeName(data.name) + "\",color:\"gold\",bold:true},background:0,shadow:1b";
		String plate = "{id:\"minecraft:text_display\",Tags:[\"ahoy_part\"]," + rot + ",teleport_duration:2,billboard:\"fixed\"," + text
			+ ",transformation:{left_rotation:[0f,%s],right_rotation:[0f,0f,0f,1f],translation:[0f,2f,-7.56f],scale:[1.2f,1.2f,1.2f]}}";
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(x, y, z)).append(" {")
			.append(Cmd.uuidNbt(id)).append(",Tags:[\"ahoy_ship\"],").append(rot).append(",teleport_duration:2,Passengers:[")
			.append(String.format(plate, "0f,0f,1f")).append(',').append(String.format(plate, "1f,0f,0f"));
		for (ShipModel.Part part : ShipModel.parts()) {
			cmd.append(",{id:\"minecraft:block_display\",block_state:\"").append(part.block()).append("\",Tags:[\"ahoy_part\"],")
				.append(rot).append(",teleport_duration:2,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[")
				.append(Cmd.f(part.x())).append("f,").append(Cmd.f(part.y())).append("f,").append(Cmd.f(part.z())).append("f],scale:[")
				.append(Cmd.f(part.sx())).append("f,").append(Cmd.f(part.sy())).append("f,").append(Cmd.f(part.sz())).append("f]}");
			if (part.glow()) {
				cmd.append(",brightness:{sky:15,block:15}");
			}
			cmd.append('}');
		}
		return cmd.append("]}").toString();
	}

	// ---------------------------------------------------------------- ticking

	void tick() {
		if (isRemoved()) {
			return;
		}
		age++;
		ensureHitboxes();
		updateRiders();
		ServerPlayer captain = rider(0);
		Controls controls = testControls != null ? testControls : captain != null ? Controls.of(captain.getLastClientInput()) : Controls.NONE;
		boolean idle = captain == null && testControls == null && speed == 0;
		if (!idle || age % 20 == 0) {
			sail(controls);
		}
		placeSeatsAndHitboxes();
		if (gunDeck != null) {
			gunDeck.tick();
		}
		bunkDeck.tick();

		if (controls.jump() && !lastJump && bellCooldown <= 0) {
			ringBell();
		}
		lastJump = controls.jump();
		if (bellCooldown > 0) {
			bellCooldown--;
		}
		if (hasRiders()) {
			protectRiders();
			if (age % 4 == 0) {
				repelMonsters();
			}
		}
		if (captain != null && age % 5 == 0) {
			hud(captain);
		}
	}

	private void sail(Controls c) {
		double x = root.getX();
		double z = root.getZ();
		float yaw = root.getYRot();
		double rigging = Shipwright.factor(data.speedLevel);
		double max = MAX_SPEED * rigging * Wind.factor(level, yaw);

		if (c.forward()) {
			speed = Math.min(max, speed + 0.004 * rigging);
		} else if (c.back()) {
			speed = Math.max(-0.08, speed - 0.006);
		} else {
			speed *= 0.99;
		}
		if (speed > max) {
			speed *= 0.99;
		}
		if (Math.abs(speed) < 0.002) {
			speed = 0;
		}

		int rudder = (c.right() ? 1 : 0) - (c.left() ? 1 : 0);
		if (rudder != 0) {
			double way = Mth.clamp(Math.abs(speed) / 0.12, 0.3, 1.0);
			float delta = (float) (rudder * 1.5 * way * (speed < 0 ? -1 : 1));
			float newYaw = Mth.wrapDegrees(yaw + delta);
			if (hullFits(level, data.surface, x, z, newYaw)) {
				yaw = newYaw;
			}
		}

		if (speed != 0) {
			double rad = Math.toRadians(yaw);
			double nx = x - Math.sin(rad) * speed;
			double nz = z + Math.cos(rad) * speed;
			if (hullFits(level, data.surface, nx, nz, yaw) && afloat(level, data.surface, nx, nz, yaw)) {
				x = nx;
				z = nz;
			} else {
				if (Math.abs(speed) > 0.15) {
					Cmd.sound(level, "minecraft:block.wood.break", x, data.surface + 1, z, 1.5f, 0.5f);
					ServerPlayer captain = rider(0);
					if (captain != null) {
						captain.sendSystemMessage(Component.literal("CRUNCH. Land ho! (That's the ground.)").withStyle(ChatFormatting.GRAY));
					}
				}
				speed = 0;
			}
		}

		root.setPos(x, data.surface + 0.06 * Math.sin(age * 0.08), z);
		root.setYRot(yaw);
		if (yaw != sentYaw) {
			sentYaw = yaw;
			for (Entity part : root.getPassengers()) {
				part.setYRot(yaw);
			}
		}
		if (speed > 0.1 && age % 4 == 0) {
			double[] bow = toWorld(x, z, yaw, 0, 10);
			Cmd.particles(level, "minecraft:splash", bow[0], data.surface + 0.3, bow[1], 0.8, 0.1, 10);
		}
	}

	void ringBell() {
		bellCooldown = 20;
		Cmd.sound(level, "minecraft:block.bell.use", root.getX(), data.surface + 3, root.getZ(), 2.0f, 1.0f);
	}

	private void hud(ServerPlayer captain) {
		int kmh = (int) Math.round(Math.abs(speed) * 20 * 3.6);
		MutableComponent line = Component.literal("⛵ " + kmh + " km/h").withStyle(ChatFormatting.AQUA);
		if (data.speedLevel > 0) {
			line.append(Component.literal("  ⚓" + Shipwright.roman(data.speedLevel)).withStyle(ChatFormatting.GOLD));
		}
		line.append(Component.literal("   Wind " + Wind.arrow(level, root.getYRot()) + " " + Wind.label(level, root.getYRot()))
			.withStyle(ChatFormatting.WHITE));
		line.append(Component.literal("   Space: bell · Shift: go ashore").withStyle(ChatFormatting.GRAY));
		captain.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	// ---------------------------------------------------------------- seats and hitboxes

	/** Summons a seat or hitbox and marks it, so strays can be cleaned up after a crash. */
	@Nullable Entity summonMarker(String type, String nbt, double x, double y, double z) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon " + type + " " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id) + "," + nbt + "}");
		Entity entity = level.getEntity(id);
		if (entity != null) {
			entity.setAttached(AhoyMod.MARKER, true);
			Ships.registerMarker(entity, this);
		}
		return entity;
	}

	private @Nullable Entity seatEntity(int index) {
		if (seats[index] == null || seats[index].isRemoved()) {
			ShipModel.Spot s = ShipModel.SPOTS.get(index);
			double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), s.x(), s.z());
			seats[index] = summonMarker("minecraft:item_display", "teleport_duration:2,Tags:[\"ahoy_seat\"]", w[0], root.getY() + s.y(), w[1]);
		}
		return seats[index];
	}

	private void ensureHitboxes() {
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] == null || hitboxes[i].isRemoved()) {
				double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, ShipModel.HITBOX_Z[i]);
				hitboxes[i] = summonMarker("minecraft:interaction", "width:7f,height:4f,response:1b,Tags:[\"ahoy_hitbox\"]", w[0], root.getY() - 1, w[1]);
			}
		}
	}

	private void placeSeatsAndHitboxes() {
		float yaw = root.getYRot();
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && !seats[i].isRemoved()) {
				ShipModel.Spot s = ShipModel.SPOTS.get(i);
				double[] w = toWorld(root.getX(), root.getZ(), yaw, s.x(), s.z());
				seats[i].setPos(w[0], root.getY() + s.y(), w[1]);
				seats[i].setYRot(yaw);
			}
		}
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] != null) {
				double[] w = toWorld(root.getX(), root.getZ(), yaw, 0, ShipModel.HITBOX_Z[i]);
				hitboxes[i].setPos(w[0], root.getY() - 1, w[1]);
			}
		}
	}

	private void discardSeatsAndHitboxes() {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null) {
				seats[i].ejectPassengers();
				seats[i].discard();
				seats[i] = null;
			}
		}
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] != null) {
				hitboxes[i].discard();
				hitboxes[i] = null;
			}
		}
	}

	int seatCount() {
		return seats.length;
	}

	/** How many seat and hitbox entities exist right now (for the smoke test). */
	int markerCount() {
		int n = 0;
		for (Entity e : seats) {
			n += e != null && !e.isRemoved() ? 1 : 0;
		}
		for (Entity e : hitboxes) {
			n += e != null && !e.isRemoved() ? 1 : 0;
		}
		return n;
	}

	String seatName(int index) {
		return ShipModel.SPOTS.get(index).name();
	}

	@Nullable ServerPlayer rider(int index) {
		Entity seat = seats[index];
		if (seat == null) {
			return null;
		}
		return seat.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	boolean seatFree(int index) {
		Entity seat = seats[index];
		return seat == null || seat.isRemoved() || seat.getPassengers().isEmpty();
	}

	int seatOf(Player player) {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && player.getVehicle() == seats[i]) {
				return i;
			}
		}
		return -1;
	}

	boolean hasRiders() {
		if ((gunDeck != null && !gunDeck.gunners().isEmpty()) || !bunkDeck.sleepers().isEmpty()) {
			return true;
		}
		for (UUID rider : riders) {
			if (rider != null) {
				return true;
			}
		}
		return false;
	}

	/** Puts someone in a seat (making the seat entity if needed). */
	boolean seat(ServerPlayer player, int index) {
		if (!seatFree(index)) {
			return false;
		}
		Entity seat = seatEntity(index);
		if (seat == null) {
			return false;
		}
		int current = seatOf(player);
		if (current >= 0) {
			riders[current] = null;
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		Cmd.run(level, "ride " + player.getUUID() + " mount " + seat.getUUID());
		if (player.getVehicle() != seat) {
			return false;
		}
		riders[index] = player.getUUID();
		Ships.rememberRider(player.getUUID(), this);
		return true;
	}

	/** Owners take the wheel if it's free; guests take the first free passenger spot. */
	int pickSeat(Player player) {
		if (isOwner(player) && seatFree(0)) {
			return 0;
		}
		for (int i = 1; i < seats.length; i++) {
			if (seatFree(i)) {
				return i;
			}
		}
		return mayCommand(player) && seatFree(0) ? 0 : -1;
	}

	private void updateRiders() {
		for (int i = 0; i < seats.length; i++) {
			Entity passenger = seats[i] == null ? null : seats[i].getFirstPassenger();
			UUID now = passenger instanceof ServerPlayer ? passenger.getUUID() : null;
			if (riders[i] != null && !riders[i].equals(now)) {
				UUID gone = riders[i];
				riders[i] = null;
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
				if (player != null && ((gunDeck != null && gunDeck.isManning(player)) || bunkDeck.isSleeping(player))) {
					continue; // stepped up to a cannon or into a bunk, still aboard
				}
				Ships.forgetRider(gone, this);
				if (player != null && !player.isDeadOrDying() && player.getVehicle() == null && player.level() == level
					&& seats[i] != null && player.distanceToSqr(seats[i]) < 16) {
					goAshore(player, i);
				}
			}
			if (now != null && riders[i] == null) {
				riders[i] = now;
				Ships.rememberRider(now, this);
			}
		}
	}

	/**
	 * Someone stood up. Put them on dry land next to the ship if there is some close by,
	 * otherwise in the water alongside.
	 */
	void goAshore(ServerPlayer player, int index) {
		ShipModel.Spot s = ShipModel.SPOTS.get(index);
		double side = s.x() >= 0 ? 1 : -1;
		double[][] tries = {{side * 4.5, s.z()}, {-side * 4.5, s.z()}, {0, 11.5}, {0, -9.5}, {side * 5.5, s.z()}, {-side * 5.5, s.z()}};
		for (double[] t : tries) {
			double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), t[0], t[1]);
			for (int dy = 0; dy <= 2; dy++) {
				double y = Math.floor(data.surface) + dy;
				BlockPos feet = BlockPos.containing(w[0], y, w[1]);
				boolean ground = !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
				if (ground && level.noCollision(new AABB(w[0] - 0.3, y, w[1] - 0.3, w[0] + 0.3, y + 1.8, w[1] + 0.3))) {
					Cmd.run(level, "tp " + player.getUUID() + " " + Cmd.pos(w[0], y, w[1]));
					return;
				}
			}
		}
		double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), side * 4.5, s.z());
		Cmd.run(level, "tp " + player.getUUID() + " " + Cmd.pos(w[0], data.surface, w[1]));
		if (index != 0) {
			ServerPlayer captain = rider(0);
			if (captain != null) {
				captain.sendSystemMessage(Component.literal("Man overboard! " + player.getName().getString() + " jumped ship.").withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	// ---------------------------------------------------------------- safety at sea

	private void protectRiders() {
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer player = rider(i);
			if (player != null) {
				player.clearFire();
				player.setAirSupply(player.getMaxAirSupply());
			}
		}
		for (ServerPlayer sleeper : bunkDeck.sleepers()) {
			sleeper.clearFire();
			sleeper.setAirSupply(sleeper.getMaxAirSupply());
		}
		if (gunDeck != null) {
			for (ServerPlayer gunner : gunDeck.gunners()) {
				gunner.clearFire();
				gunner.setAirSupply(gunner.getMaxAirSupply());
			}
		}
	}

	/** Drowned, guardians, phantoms...: shoved away from the ship and they forget who was aboard. */
	private void repelMonsters() {
		double x = root.getX();
		double y = root.getY();
		double z = root.getZ();
		AABB near = new AABB(x - 40, y - 20, z - 40, x + 40, y + 20, z + 40);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, near, m -> m instanceof Enemy && m.isAlive())) {
			LivingEntity target = mob.getTarget();
			if (target != null && Ships.shipOf(target) == this) {
				mob.setTarget(null);
			}
			double[] local = toLocal(x, z, root.getYRot(), mob.getX(), mob.getZ());
			if (Math.abs(local[0]) < 7 && Math.abs(local[1]) < 13 && Math.abs(mob.getY() - y) < 10) {
				double[] out = toWorld(0, 0, root.getYRot(), local[0] >= 0 ? 1 : -1, 0);
				mob.setDeltaMovement(mob.getDeltaMovement().add(out[0] * 0.9, 0.3, out[1] * 0.9));
				Cmd.particles(level, "minecraft:electric_spark", mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 0.3, 0.1, 8);
			}
		}
	}

	// ---------------------------------------------------------------- removal

	/** Ship → bottle item, with name and cargo inside. Everyone aboard goes ashore first. */
	ItemStack bottleUp() {
		if (gunDeck != null) {
			gunDeck.shutdown(true);
		}
		bunkDeck.shutdown(true);
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				riders[i] = null;
				Ships.forgetRider(p.getUUID(), this);
				p.stopRiding();
				goAshore(p, i);
			}
		}
		ItemStack bottle = Bottle.packed(level, data);
		remove();
		data.cargoA.clearContent();
		data.cargoB.clearContent();
		data.guns.clearContent();
		data.bunks.clearContent();
		return bottle;
	}

	/** Gone for good. */
	void remove() {
		if (removed) {
			return;
		}
		if (gunDeck != null) {
			gunDeck.shutdown(true);
		}
		bunkDeck.shutdown(true);
		removed = true;
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				p.stopRiding();
			}
		}
		discardSeatsAndHitboxes();
		for (Entity part : new ArrayList<>(root.getPassengers())) {
			part.discard();
		}
		root.removeAttached(AhoyMod.DATA);
		root.discard();
		Ships.unregister(this);
	}

	/** Chunk unloaded / server stopping: drop the seats and hitboxes, keep the rest. */
	void unload() {
		if (removed) {
			return;
		}
		if (gunDeck != null) {
			gunDeck.shutdown(false);
		}
		bunkDeck.shutdown(false);
		removed = true;
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				p.stopRiding();
			}
		}
		discardSeatsAndHitboxes();
		Ships.unregister(this);
	}
}
