package com.thatcoffeelock.blimey;

import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One airship, built like an Ahoy ship: an invisible root entity carrying ~80 block displays as passengers, plus
 * invisible seats and click hitboxes that we move along every tick. No blocks are ever placed.
 *
 * It flies on diesel. With fuel in the engines it climbs, cruises and hovers; every tick in the air costs diesel,
 * more when it's racing or climbing. With the tank dry it sinks gently until it touches down. Parked on the ground
 * with nobody at the controls it burns nothing.
 */
final class Airship {
	public record Controls(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
		static final Controls NONE = new Controls(false, false, false, false, false, false);

		static Controls of(Input input) {
			return new Controls(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.sprint());
		}

		boolean any() {
			return forward || back || left || right || up || down;
		}
	}

	static final double MAX_CLIMB = 0.25;
	static final double MAX_DIVE = 0.35;
	/** How fast it sinks with dry tanks. */
	static final double SINK = 0.12;

	final ServerLevel level;
	final Entity root;
	final AirshipData data;
	private final Entity[] seats = new Entity[AirshipModel.SPOTS.size()];
	private final UUID[] riders = new UUID[AirshipModel.SPOTS.size()];
	private final Entity[] hitboxes = new Entity[AirshipModel.HITBOX_Z.length];

	double speed;
	double climb;
	/** The real height of the keel. The root is drawn bobbing slightly around it. */
	double y;
	/** Were the engines running last tick? */
	boolean burning;
	private boolean grounded;
	private boolean warnedDry;
	private int age;
	private int lastBomb = -100;
	private float sentYaw = Float.NaN;
	private boolean removed;
	@Nullable Controls testControls;

	Airship(ServerLevel level, Entity root, AirshipData data) {
		this.level = level;
		this.root = root;
		this.data = data;
		this.y = root.getY();
		this.grounded = grounded(level, root.getX(), y, root.getZ(), root.getYRot());
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

	boolean isGrounded() {
		return grounded;
	}

	// ---------------------------------------------------------------- geometry

	/** Local (x = port, z = bow) to world x/z. Same convention as display entity rotation. */
	static double[] toWorld(double x, double z, float yaw, double lx, double lz) {
		double rad = Math.toRadians(yaw);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[] {x + lx * c - lz * s, z + lx * s + lz * c};
	}

	/** Is there room for the whole airship here (keel at y)? Blocks are in the way, and so are chunks nobody has loaded. */
	static boolean fits(ServerLevel level, double x, double y, double z, float yaw) {
		if (y < level.getMinY() || y + AirshipModel.TOP > level.getMaxY()) {
			return false;
		}
		for (AirshipModel.Bulk b : AirshipModel.BULK) {
			double[] c = toWorld(x, z, yaw, b.x(), b.z());
			if (!level.isLoaded(BlockPos.containing(c[0], y, c[1]))) {
				return false;
			}
			double h = b.half();
			if (!level.noCollision(new AABB(c[0] - h, y + b.y0(), c[1] - h, c[0] + h, y + b.y1(), c[1] + h))) {
				return false;
			}
		}
		return true;
	}

	/** Is the gondola sitting on something? */
	static boolean grounded(ServerLevel level, double x, double y, double z, float yaw) {
		if (y <= level.getMinY() + 0.1) {
			return true;
		}
		for (int i = 0; i < 4; i++) {
			AirshipModel.Bulk b = AirshipModel.BULK.get(i);
			double[] c = toWorld(x, z, yaw, b.x(), b.z());
			double h = b.half() - 0.05;
			if (!level.noCollision(new AABB(c[0] - h, y - 0.12, c[1] - h, c[0] + h, y + 0.02, c[1] + h))) {
				return true;
			}
		}
		return false;
	}

	/** Highest the keel may go: the envelope stays under the build limit. */
	double ceiling() {
		return level.getMaxY() - AirshipModel.TOP - BlimeyConfig.get().ceilingMargin;
	}

	// ---------------------------------------------------------------- summoning

	private static String safeName(String name) {
		String cleaned = name.replaceAll("[^A-Za-z0-9 '!?.,()&-]", "").trim();
		return cleaned.isEmpty() ? "Nameless" : cleaned.substring(0, Math.min(40, cleaned.length()));
	}

	/** The whole airship: an invisible root, the model, and the name painted on both flanks of the envelope. */
	static String summonCommand(AirshipData data, UUID id, double x, double y, double z, float yaw) {
		String rot = "Rotation:[" + Cmd.f(yaw) + "f,0f]";
		String text = "text:{text:\"" + safeName(data.name) + "\",color:\"dark_red\",bold:true},background:0,shadow:0b";
		// each flank gets two plates back to back, so one of them faces out whichever way round the client draws it
		String plate = "{id:\"minecraft:text_display\",Tags:[\"blimey_part\"]," + rot + ",teleport_duration:2,billboard:\"fixed\"," + text
			+ ",transformation:{left_rotation:[0f,%s,0f,0.7071f],right_rotation:[0f,0f,0f,1f],translation:[%sf,7.1f,0f],scale:[2.4f,2.4f,2.4f]}}";
		StringBuilder cmd = new StringBuilder("summon minecraft:item_display ").append(Cmd.pos(x, y, z)).append(" {")
			.append(Cmd.uuidNbt(id)).append(",Tags:[\"blimey_airship\"],").append(rot).append(",teleport_duration:2,Passengers:[")
			.append(String.format(plate, "0.7071f", "3.58")).append(',').append(String.format(plate, "-0.7071f", "3.58")).append(',')
			.append(String.format(plate, "0.7071f", "-3.58")).append(',').append(String.format(plate, "-0.7071f", "-3.58"));
		for (AirshipModel.Part part : AirshipModel.parts()) {
			cmd.append(",{id:\"minecraft:block_display\",block_state:\"").append(part.block()).append("\",Tags:[\"blimey_part\"],")
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

	/** Name plates on the envelope. */
	static final int PLATES = 4;

	// ---------------------------------------------------------------- ticking

	void tick() {
		if (isRemoved()) {
			return;
		}
		age++;
		ensureHitboxes();
		updateRiders();
		ServerPlayer captain = rider(AirshipModel.CAPTAIN);
		Controls controls = testControls != null ? testControls
			: captain != null ? Controls.of(captain.getLastClientInput()) : Controls.NONE;
		boolean idle = grounded && !controls.any() && speed == 0 && climb == 0;
		if (!idle || age % 20 == 0) {
			fly(controls);
		}
		placeSeatsAndHitboxes();
		if (hasRiders()) {
			for (int i = 0; i < seats.length; i++) {
				ServerPlayer p = rider(i);
				if (p != null) {
					p.clearFire();
				}
			}
		}
		if (captain != null && age % 5 == 0) {
			hud(captain);
		}
	}

	private void fly(Controls c) {
		BlimeyConfig cfg = BlimeyConfig.get();
		double x = root.getX();
		double z = root.getZ();
		float yaw = root.getYRot();
		grounded = grounded(level, x, y, z, yaw);
		boolean wantsPower = c.any() || !grounded || speed != 0;
		boolean powered = wantsPower && hasFuel();
		burning = powered;
		double max = cfg.topSpeed * Engineer.speedFactor(data.speedLevel);
		int rudder = (c.right() ? 1 : 0) - (c.left() ? 1 : 0);

		if (powered) {
			warnedDry = false;
			if (c.forward()) {
				speed = Math.min(max, speed + 0.008 * Engineer.speedFactor(data.speedLevel));
			} else if (c.back()) {
				speed = Math.max(-0.15, speed - 0.012);
			} else {
				speed *= 0.985;
			}
			if (speed > max) {
				speed *= 0.98;
			}
			if (c.up()) {
				climb = Math.min(MAX_CLIMB, climb + 0.02);
			} else if (c.down()) {
				climb = Math.max(-MAX_DIVE, climb - 0.03);
			} else {
				climb *= 0.8; // the engines hold the altitude
			}
		} else {
			speed *= 0.97;
			rudder = 0;
			climb = grounded ? 0 : Math.max(-SINK, climb - 0.01);
			if (wantsPower && !grounded && !warnedDry) {
				warnedDry = true;
				ServerPlayer captain = rider(AirshipModel.CAPTAIN);
				if (captain != null) {
					captain.sendSystemMessage(Component.literal("The engines cough and die. Out of diesel! Gliding down with all the grace of a piano.")
						.withStyle(ChatFormatting.RED));
				}
				Cmd.sound(level, "minecraft:block.fire.extinguish", x, y + 3, z, 1.5f, 0.6f);
			}
		}
		if (Math.abs(speed) < 0.002) {
			speed = 0;
		}
		if (Math.abs(climb) < 0.002) {
			climb = 0;
		}

		if (rudder != 0) {
			// propellers turn it even on the spot, a bit slower when it's racing
			float delta = (float) (rudder * (2.0 - Math.min(1.0, Math.abs(speed) / Math.max(0.01, max)) * 0.8));
			float newYaw = Mth.wrapDegrees(yaw + delta);
			if (fits(level, x, y, z, newYaw)) {
				yaw = newYaw;
			}
		}

		double rad = Math.toRadians(yaw);
		double nx = x - Math.sin(rad) * speed;
		double nz = z + Math.cos(rad) * speed;
		double ny = Math.min(ceiling(), y + climb);
		if (ny >= ceiling() && climb > 0) {
			climb = 0;
		}
		boolean landing = false;
		if (speed != 0 || ny != y) {
			if (fits(level, nx, ny, nz, yaw)) {
				x = nx;
				y = ny;
				z = nz;
			} else {
				// sideways and up/down separately: scrape along the ground, or rise straight up off a wall
				if (speed != 0 && fits(level, nx, y, nz, yaw)) {
					x = nx;
					z = nz;
				} else {
					crash();
				}
				if (ny != y) {
					if (fits(level, x, ny, z, yaw)) {
						y = ny;
					} else {
						landing = ny < y;
						bump();
					}
				}
			}
		}
		// touch down properly instead of hovering a hand's width above the grass
		if (landing) {
			for (int i = 0; i < 8 && fits(level, x, y - 0.05, z, yaw) && !grounded(level, x, y, z, yaw); i++) {
				y -= 0.05;
			}
		}
		grounded = grounded(level, x, y, z, yaw);

		if (powered) {
			double burn = cfg.hoverBurn + (1 - cfg.hoverBurn) * Math.abs(speed) / cfg.topSpeed + (climb > 0.01 ? cfg.climbBurn : 0);
			data.fuel = Math.max(0, data.fuel - burn * Engineer.burnFactor(data.efficiencyLevel));
			if (age % 3 == 0) {
				for (double[] e : AirshipModel.EXHAUSTS) {
					double[] w = toWorld(x, z, yaw, e[0], e[2]);
					Cmd.particles(level, "minecraft:smoke", w[0], y + e[1], w[1], 0.15, 0.02, 3);
				}
			}
			if (age % 16 == 0) {
				Cmd.sound(level, "minecraft:block.piston.extend", x, y + 3, z, 0.35f, 0.5f);
			}
		}

		boolean airborne = !grounded;
		double bob = airborne ? 0.05 * Math.sin(age * 0.06) : 0;
		root.setPos(x, y + bob, z);
		root.setYRot(yaw);
		if (yaw != sentYaw) {
			sentYaw = yaw;
			for (Entity part : root.getPassengers()) {
				part.setYRot(yaw);
			}
		}
	}

	/** Bumped the ground or a ceiling while going up or down. */
	private void bump() {
		if (climb < -0.15) {
			Cmd.sound(level, "minecraft:block.anvil.land", root.getX(), y, root.getZ(), 0.6f, 0.6f);
		}
		climb = 0;
	}

	/** Flew into something sideways. */
	private void crash() {
		if (Math.abs(speed) > 0.2) {
			Cmd.sound(level, "minecraft:block.anvil.land", root.getX(), y + 2, root.getZ(), 1.2f, 0.5f);
			ServerPlayer captain = rider(AirshipModel.CAPTAIN);
			if (captain != null) {
				captain.sendSystemMessage(Component.literal("CLANG. That was a mountain. Or a house. Either way, it's still there.").withStyle(ChatFormatting.GRAY));
			}
		}
		speed = 0;
	}

	// ---------------------------------------------------------------- diesel

	/** Is there fuel in the engines? Tops them up from the tank (one bucket of diesel at a time) when they run dry. */
	boolean hasFuel() {
		if (data.fuel > 0) {
			return true;
		}
		SimpleContainer tank = data.tank;
		for (int i = 0; i < tank.getContainerSize(); i++) {
			ItemStack stack = tank.getItem(i);
			if (!BlimeyItems.isDiesel(stack)) {
				continue;
			}
			if (stack.getCount() == 1) {
				tank.setItem(i, new ItemStack(Items.BUCKET));
			} else {
				stack.shrink(1);
				stashBucket();
			}
			tank.setChanged();
			data.fuel += BlimeyConfig.get().ticksPerBucket;
			return true;
		}
		return false;
	}

	/** An empty bucket goes back in the tank, else into the holds, else over the side. */
	private void stashBucket() {
		ItemStack bucket = new ItemStack(Items.BUCKET);
		if (put(data.tank, bucket)) {
			return;
		}
		for (SimpleContainer hold : data.holds()) {
			if (put(hold, bucket)) {
				return;
			}
		}
		Cmd.run(level, "summon minecraft:item " + Cmd.pos(root.getX(), y - 0.5, root.getZ()) + " {Item:{id:\"minecraft:bucket\",count:1}}");
	}

	private static boolean put(SimpleContainer box, ItemStack one) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack slot = box.getItem(i);
			if (slot.is(Items.BUCKET) && slot.getCount() < slot.getMaxStackSize() && ItemStack.isSameItemSameComponents(slot, one)) {
				slot.grow(1);
				box.setChanged();
				return true;
			}
		}
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, one.copy());
				return true;
			}
		}
		return false;
	}

	/** Diesel aboard, in buckets: what's in the tank plus what's left in the engines. */
	double dieselLeft() {
		return data.dieselAboard() + data.fuel / BlimeyConfig.get().ticksPerBucket;
	}

	// ---------------------------------------------------------------- bombs

	/**
	 * Drops the bomb in this player's hand out of the hatch. It leaves with the airship's own speed, so it lands ahead
	 * of where you let go: lead your target. Returns why not, or null when it's away.
	 */
	@Nullable String dropBomb(ServerPlayer player, ItemStack held) {
		BlimeyItems.BombKind kind = BlimeyItems.bombKind(held);
		if (kind == null) {
			return "That's not a bomb.";
		}
		if (age - lastBomb < 6) {
			return null; // one click arrives as two packets; don't drop two
		}
		if (grounded) {
			return "The bomb doors stay shut on the ground. For everyone's sake.";
		}
		double[] hatch = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, AirshipModel.HATCH_Z);
		double rad = Math.toRadians(root.getYRot());
		Vec3 vel = new Vec3(-Math.sin(rad) * speed, climb, Math.cos(rad) * speed);
		Bomb bomb = Bomb.launch(level, kind, new Vec3(hatch[0], y + AirshipModel.HATCH_Y, hatch[1]), vel, 0);
		if (bomb == null) {
			return "The bomb jammed in the hatch. Check the server log.";
		}
		lastBomb = age;
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.iron_trapdoor.open", hatch[0], y, hatch[1], 1.0f, 0.8f);
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("💣 " + kind.title + " away!").withStyle(kind.color)));
		return null;
	}

	private void hud(ServerPlayer captain) {
		int kmh = (int) Math.round(Math.abs(speed) * 20 * 3.6);
		double left = dieselLeft();
		ChatFormatting fuelColour = left <= 0 ? ChatFormatting.RED : left < 2 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
		MutableComponent line = Component.literal("✈ " + kmh + " km/h").withStyle(ChatFormatting.AQUA)
			.append(Component.literal("  ⬍ " + (int) Math.floor(y)).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("  ⛽ " + String.format(java.util.Locale.ROOT, "%.1f", left) + " buckets").withStyle(fuelColour));
		if (grounded) {
			line.append(Component.literal("  landed").withStyle(ChatFormatting.GRAY));
		}
		line.append(Component.literal("   Space up · Ctrl down · Shift bail out").withStyle(ChatFormatting.GRAY));
		captain.connection.send(new ClientboundSetActionBarTextPacket(line));
	}

	// ---------------------------------------------------------------- seats and hitboxes

	/** Summons a seat or hitbox and marks it, so strays can be cleaned up after a crash. */
	@Nullable Entity summonMarker(String type, String nbt, double x, double y, double z) {
		UUID id = UUID.randomUUID();
		Cmd.run(level, "summon " + type + " " + Cmd.pos(x, y, z) + " {" + Cmd.uuidNbt(id) + "," + nbt + "}");
		Entity entity = level.getEntity(id);
		if (entity != null) {
			entity.setAttached(BlimeyMod.MARKER, true);
			Airships.registerMarker(entity, this);
		}
		return entity;
	}

	private @Nullable Entity seatEntity(int index) {
		if (seats[index] == null || seats[index].isRemoved()) {
			AirshipModel.Spot s = AirshipModel.SPOTS.get(index);
			double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), s.x(), s.z());
			seats[index] = summonMarker("minecraft:item_display", "teleport_duration:2,Tags:[\"blimey_seat\"]", w[0], root.getY() + s.y(), w[1]);
		}
		return seats[index];
	}

	private void ensureHitboxes() {
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] == null || hitboxes[i].isRemoved()) {
				double[] w = toWorld(root.getX(), root.getZ(), root.getYRot(), 0, AirshipModel.HITBOX_Z[i]);
				hitboxes[i] = summonMarker("minecraft:interaction", "width:" + Cmd.f(AirshipModel.HITBOX_WIDTH) + "f,height:"
					+ Cmd.f(AirshipModel.HITBOX_HEIGHT) + "f,response:1b,Tags:[\"blimey_hitbox\"]", w[0], root.getY() - 0.1, w[1]);
			}
		}
	}

	private void placeSeatsAndHitboxes() {
		float yaw = root.getYRot();
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && !seats[i].isRemoved()) {
				AirshipModel.Spot s = AirshipModel.SPOTS.get(i);
				double[] w = toWorld(root.getX(), root.getZ(), yaw, s.x(), s.z());
				seats[i].setPos(w[0], root.getY() + s.y(), w[1]);
				seats[i].setYRot(yaw);
			}
		}
		for (int i = 0; i < hitboxes.length; i++) {
			if (hitboxes[i] != null) {
				double[] w = toWorld(root.getX(), root.getZ(), yaw, 0, AirshipModel.HITBOX_Z[i]);
				hitboxes[i].setPos(w[0], root.getY() - 0.1, w[1]);
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
		return AirshipModel.SPOTS.get(index).name();
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
		Airships.rememberRider(player.getUUID(), this);
		return true;
	}

	/** Owners take the controls if they're free; guests take the first free seat. */
	int pickSeat(Player player) {
		if (isOwner(player) && seatFree(AirshipModel.CAPTAIN)) {
			return AirshipModel.CAPTAIN;
		}
		for (int i = 1; i < seats.length; i++) {
			if (seatFree(i)) {
				return i;
			}
		}
		return mayCommand(player) && seatFree(AirshipModel.CAPTAIN) ? AirshipModel.CAPTAIN : -1;
	}

	private void updateRiders() {
		for (int i = 0; i < seats.length; i++) {
			Entity passenger = seats[i] == null ? null : seats[i].getFirstPassenger();
			UUID now = passenger instanceof ServerPlayer ? passenger.getUUID() : null;
			if (riders[i] != null && !riders[i].equals(now)) {
				UUID gone = riders[i];
				riders[i] = null;
				Airships.forgetRider(gone, this);
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
				if (player != null && !player.isDeadOrDying() && player.getVehicle() == null && !grounded) {
					bailOut(player);
				}
			}
			if (now != null && riders[i] == null) {
				riders[i] = now;
				Airships.rememberRider(now, this);
			}
		}
	}

	/** Someone stepped out in mid-air. They get a parachute, of sorts. */
	private void bailOut(ServerPlayer player) {
		Cmd.run(level, "effect give " + player.getUUID() + " minecraft:slow_falling 30 0 true");
		player.sendSystemMessage(Component.literal("You bailed out! Parachute deployed. (It's a bedsheet. It'll do.)").withStyle(ChatFormatting.YELLOW));
		ServerPlayer captain = rider(AirshipModel.CAPTAIN);
		if (captain != null && captain != player) {
			captain.sendSystemMessage(Component.literal(player.getName().getString() + " jumped ship. Rude.").withStyle(ChatFormatting.YELLOW));
		}
	}

	// ---------------------------------------------------------------- removal

	/** Airship → Flat-Pack Airship item, with everything inside. Only on the ground; everyone gets off first. */
	ItemStack packUp() {
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				riders[i] = null;
				Airships.forgetRider(p.getUUID(), this);
				p.stopRiding();
			}
		}
		ItemStack packed = BlimeyItems.packed(level, data);
		remove();
		data.clearContents();
		return packed;
	}

	/** Gone for good. */
	void remove() {
		if (removed) {
			return;
		}
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
		root.removeAttached(BlimeyMod.DATA);
		root.discard();
		Airships.unregister(this);
	}

	/** Chunk unloaded / server stopping: drop the seats and hitboxes, keep the rest. */
	void unload() {
		if (removed) {
			return;
		}
		removed = true;
		root.setPos(root.getX(), y, root.getZ());
		for (int i = 0; i < seats.length; i++) {
			ServerPlayer p = rider(i);
			if (p != null) {
				p.stopRiding();
			}
		}
		discardSeatsAndHitboxes();
		Airships.unregister(this);
	}
}
