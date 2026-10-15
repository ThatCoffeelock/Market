package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Pitched battles, where you can tip the odds yourself. A battle is set for noon on its day, at a field between your
 * home and their seat. Be within 64 blocks of it at noon and the regiments form up for real (a soldier for every half
 * regiment, up to twelve a side, ours in orange): fight in it, and what happens on the field decides the day. Stay
 * away and the captains fight it without you (a bigger swing than a skirmish, either way).
 * <p>
 * At war, riding up to a rival's seat brings out its garrison, too: every two of them you cut down is a regiment
 * they lose, and once they're gone, the treasury chest by the high table is yours to loot.
 */
final class Battles {
	static final long DAY = 24000;
	static final long NOON = 6000;
	/** How long after noon the captains wait for you, in ticks. */
	static final long GRACE = 1200;
	/** The longest a battle lasts, in ticks (4 minutes). */
	static final long LONGEST = 4800;

	/** A fight in progress: a field battle or a seat's garrison. */
	static final class Fight {
		final String key;
		final Court court;
		final String rival;
		final boolean field;
		final long started;
		int ourStart;
		int theirStart;
		int ourLost;
		int theirLost;
		int byYou;
		/** Seat garrisons: the last time the owner was close. */
		long lastNear;

		Fight(String key, Court court, String rival, boolean field, long started) {
			this.key = key;
			this.court = court;
			this.rival = rival;
			this.field = field;
			this.started = started;
		}
	}

	static final Map<String, Fight> FIGHTS = new HashMap<>();
	private static final Map<String, Long> WARNED = new HashMap<>();

	private Battles() {
	}

	static String fieldKey(Court c) {
		return "field:" + c.owner;
	}

	static String seatKey(Court c, Rival r) {
		return "seat:" + c.owner + ":" + r.id;
	}

	private static String bar(Fight f) {
		return "hoogheheeren:" + f.key.replace(":", "_").replace("-", "").toLowerCase(java.util.Locale.ROOT);
	}

	// ---------------------------------------------------------------- scheduling

	/** Sets a battle for noon: today if it isn't noon yet, otherwise tomorrow. At a field between your home and their seat. */
	static void schedule(Court c, Rival r, long day, boolean ours) {
		MinecraftServer server = Courts.server();
		long when = day;
		if (server != null && server.overworld().getGameTime() >= day * DAY + NOON - 600) {
			when = day + 1;
		}
		int[] home = CompagnieHooks.home(c.uuid());
		int hx = home == null ? 0 : home[0];
		int hz = home == null ? 0 : home[1];
		double dx = r.seatX - hx;
		double dz = r.seatZ - hz;
		double dist = Math.max(1, Math.sqrt(dx * dx + dz * dz));
		double along = Math.min(160, dist / 2);
		Court.Battle b = new Court.Battle();
		b.rival = r.id;
		b.day = when;
		b.x = hx + (int) Math.round(dx / dist * along);
		b.z = hz + (int) Math.round(dz / dist * along);
		int[] siege = ColonyLink.siegeAt(c, r);
		if (siege != null) {
			// they're camped outside one of your colonies: the battle is at its walls, on their side
			double sx = r.seatX - siege[0];
			double sz = r.seatZ - siege[2];
			double sd = Math.max(1, Math.sqrt(sx * sx + sz * sz));
			b.x = siege[0] + (int) Math.round(sx / sd * 40);
			b.z = siege[2] + (int) Math.round(sz / sd * 40);
		}
		b.ours = ours;
		c.battle = b;
		Courts.tell(c, "⚔ " + (ours ? "You give battle! Your regiments march out against " + r.House() + "." : Ai.capital(r.name()) + "'s army marches on you!")
			+ " The armies meet at noon " + (when == day ? "today" : "tomorrow") + " near " + b.x + ", " + b.z
			+ ". Be there to lead them, or let the captains fight it.", ChatFormatting.RED);
	}

	static void cancel(Court c) {
		c.battle = null;
		Fight f = FIGHTS.remove(fieldKey(c));
		if (f != null) {
			finish(f, false);
		}
		Courts.changed();
	}

	// ---------------------------------------------------------------- every second

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (Court c : Courts.ALL.values()) {
			Court.Battle b = c.battle;
			if (b == null || b.started) {
				continue;
			}
			Rival r = c.rival(b.rival);
			if (r == null || !r.war) {
				c.battle = null;
				continue;
			}
			long noon = b.day * DAY + NOON;
			ServerPlayer owner = Courts.player(c);
			boolean there = owner != null && owner.level() == level && Seats.nearFlat(owner, b.pos(), 64) && owner.isAlive();
			if (now >= noon - 1200 && now < noon && owner != null && !WARNED.containsKey(c.owner + b.day)) {
				WARNED.put(c.owner + b.day, now);
				HeerenMod.bar(owner, "⚔ The armies form up near " + b.x + ", " + b.z + ". Noon is a minute away.", ChatFormatting.RED);
			}
			if (now >= noon && there && level.isLoaded(b.pos())) {
				start(c, r, b, level, owner, now);
			} else if (now >= noon + GRACE || now >= (b.day + 1) * DAY) {
				c.battle = null;
				Wars.skirmish(c, r, b.day, Courts.RANDOM, 2.0, true);
				Courts.tell(c, c.chronicle.get(c.chronicle.size() - 1), ChatFormatting.YELLOW);
				c.chronicle.remove(c.chronicle.size() - 1);
				Wars.peaceTalks(c, r, b.day);
			}
		}
		for (Fight f : new ArrayList<>(FIGHTS.values())) {
			update(f, server, now);
		}
	}

	private static void start(Court c, Rival r, Court.Battle b, ServerLevel level, ServerPlayer owner, long now) {
		b.started = true;
		int y = Builder.ground(level, b.x, b.z) + 1;
		b.y = y;
		Fight f = new Fight(fieldKey(c), c, r.id, true, now);
		int ours = Math.min(12, c.regiments * 2);
		int theirs = Math.min(12, r.regiments * 2);
		// the line between the armies runs across the road from your home to their seat
		int[] home = CompagnieHooks.home(c.uuid());
		double dx = r.seatX - (home == null ? 0 : home[0]);
		double dz = r.seatZ - (home == null ? 0 : home[1]);
		double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
		dx /= len;
		dz /= len;
		float ourYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float theirYaw = ourYaw + 180;
		for (int i = 0; i < ours; i++) {
			placeSoldier(level, f, Soldiers.OURS, b.x - dx * 10 + -dz * (i - ours / 2.0) * 1.6, b.z - dz * 10 + dx * (i - ours / 2.0) * 1.6, ourYaw,
				"Your " + Names.soldier(Courts.RANDOM), Names.OURS.dye(), c);
		}
		for (int i = 0; i < theirs; i++) {
			placeSoldier(level, f, Soldiers.THEIRS, b.x + dx * 10 + -dz * (i - theirs / 2.0) * 1.6, b.z + dz * 10 + dx * (i - theirs / 2.0) * 1.6,
				theirYaw, r.House() + "'s " + Names.soldier(Courts.RANDOM), r.colour, c);
		}
		f.ourStart = Soldiers.in(f.key, Soldiers.OURS).size();
		f.theirStart = Soldiers.in(f.key, Soldiers.THEIRS).size();
		FIGHTS.put(f.key, f);
		String id = bar(f);
		Cmd.run(level, "bossbar add " + id + " \"Battle\"");
		Cmd.run(level, "bossbar set " + id + " color red");
		Cmd.run(level, "bossbar set " + id + " players " + owner.getUUID());
		Cmd.sound(level, "minecraft:event.raid.horn", b.x, y, b.z, 4f, 1f);
		Courts.tell(c, "⚔ The battle is joined! " + f.ourStart + " of yours against " + f.theirStart + " of " + r.House()
			+ "'s. Every one you cut down yourself counts.", ChatFormatting.RED);
		if (f.ourStart == 0 || f.theirStart == 0) {
			update(f, level.getServer(), now);
		}
	}

	private static void placeSoldier(ServerLevel level, Fight f, String side, double x, double z, float yaw, String name, String colour, Court c) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int y = level.isLoaded(new BlockPos(bx, 64, bz)) ? Builder.ground(level, bx, bz) + 1 : 64;
		Soldiers.spawn(level, f.key, side, c.uuid(), bx + 0.5, y, bz + 0.5, yaw, name, colour, side.equals(Soldiers.OURS) ? 4.5f : 4.0f, 24);
	}

	/** A soldier fell. {@code byOwner}: the court's owner struck the blow. */
	static void fallen(Soldiers.Soldier s, boolean byOwner) {
		Fight f = FIGHTS.get(s.fight);
		if (f == null) {
			return;
		}
		if (s.side.equals(Soldiers.OURS)) {
			f.ourLost++;
		} else {
			f.theirLost++;
			if (byOwner) {
				f.byYou++;
			}
			if (!f.field) {
				Rival r = f.court.rival(f.rival);
				if (r != null) {
					if (f.theirLost % 2 == 0 && r.regiments > 0) {
						r.regiments--;
					}
					r.warScore = Math.min(100, r.warScore + 2);
				}
			}
		}
		Courts.changed();
	}

	private static void update(Fight f, MinecraftServer server, long now) {
		ServerLevel level = server.overworld();
		int ours = Soldiers.in(f.key, Soldiers.OURS).size();
		int theirs = Soldiers.in(f.key, Soldiers.THEIRS).size();
		ServerPlayer owner = Courts.player(f.court);
		Rival r = f.court.rival(f.rival);
		if (r == null) {
			FIGHTS.remove(f.key);
			finish(f, false);
			return;
		}
		if (f.field) {
			String id = bar(f);
			Cmd.run(level, "bossbar set " + id + " name \"⚔ Battle with " + Figures.esc(r.House()) + ": ours " + ours + " · theirs " + theirs + "\"");
			Cmd.run(level, "bossbar set " + id + " max " + Math.max(1, f.ourStart + f.theirStart));
			Cmd.run(level, "bossbar set " + id + " value " + ours);
			Court.Battle b = f.court.battle;
			boolean left = owner == null || owner.level() != level || b == null || !Seats.nearFlat(owner, b.pos(), 150);
			if (ours == 0 || theirs == 0 || now - f.started > LONGEST || left) {
				FIGHTS.remove(f.key);
				resolveField(f, r, ours, theirs, left);
				finish(f, true);
			}
			return;
		}
		// a seat's garrison
		BlockPos seat = r.seat();
		if (owner != null && owner.level() == level && Seats.nearFlat(owner, seat, 96)) {
			f.lastNear = now;
		}
		if (theirs == 0 || !r.war || now - f.lastNear > 1200) {
			FIGHTS.remove(f.key);
			if (theirs == 0 && f.theirLost > 0) {
				Courts.tell(f.court, "The garrison of " + r.seatName + " is broken (" + f.theirLost + " fell, " + f.byYou + " by your hand). The treasury chest by the high table is yours for the taking.",
					ChatFormatting.GOLD);
			}
			finish(f, false);
		}
	}

	/** The field decides the day: the score swings with who lost more, and the winner plunders the loser's baggage. */
	private static void resolveField(Fight f, Rival r, int ours, int theirs, boolean left) {
		Court c = f.court;
		long day = Courts.today();
		c.battle = null;
		double ourFrac = f.ourStart == 0 ? 1 : (double) f.ourLost / f.ourStart;
		double theirFrac = f.theirStart == 0 ? 1 : (double) f.theirLost / f.theirStart;
		boolean won = theirs == 0 && ours > 0 || (ours > 0 && theirs > 0 && theirFrac > ourFrac);
		boolean lost = ours == 0 && theirs > 0 || (ours > 0 && theirs > 0 && ourFrac > theirFrac);
		int delta = (int) Math.round(45 * (theirFrac - ourFrac)) + (won ? 10 : lost ? -10 : 0);
		r.warScore = Math.max(-100, Math.min(100, r.warScore + delta));
		// each soldier stood for a share of the regiments; the fallen take a little more than half of theirs with them
		int ourRegLoss = f.ourStart == 0 ? 0 : (int) Math.min(c.regiments, Math.round(f.ourLost * (c.regiments / (double) f.ourStart) * 0.6));
		int theirRegLoss = f.theirStart == 0 ? 0 : (int) Math.min(r.regiments, Math.round(f.theirLost * (r.regiments / (double) f.theirStart) * 0.6));
		c.regiments -= ourRegLoss;
		r.regiments -= theirRegLoss;
		StringBuilder line = new StringBuilder();
		if (won) {
			long take = Math.min(r.treasury * 15 / 100, Bank.cents(4000 * (1 + Ai.rank(c))));
			r.treasury -= take;
			Bank.credit(c.uuid(), c.ownerName, take);
			c.fear(5);
			line.append("⚔ Victory over ").append(r.House()).append("! ");
			if (ColonyLink.lift(c, r) > 0) {
				line.append("The siege is lifted. ");
			}
			if (take > 0) {
				line.append("Their baggage train held ").append(Bank.format(take)).append(". ");
			}
		} else if (lost) {
			long take = Bank.seize(c.uuid(), Math.min(Math.max(0, Bank.balance(c.uuid())) * 6 / 100, Bank.cents(2000 + 1000 * Ai.rank(c))));
			r.treasury += take;
			line.append("⚔ Defeat. ").append(Ai.capital(r.House())).append(" holds the field");
			line.append(take > 0 ? " and carries off " + Bank.format(take) + ". " : ". ");
		} else {
			line.append("⚔ The battle ends in a bloody draw. ");
		}
		if (left) {
			line.append("(You left the field before it was over.) ");
		}
		line.append("You lost ").append(f.ourLost).append(" men (").append(ourRegLoss).append(ourRegLoss == 1 ? " regiment" : " regiments")
			.append("), they lost ").append(f.theirLost).append(" (").append(theirRegLoss).append(theirRegLoss == 1 ? " regiment" : " regiments").append(")");
		if (f.byYou > 0) {
			line.append(", ").append(f.byYou).append(" of them by your own hand");
		}
		line.append(". War score ").append(Wars.signed(r.warScore)).append(".");
		Courts.tell(c, line.toString(), won ? ChatFormatting.GREEN : lost ? ChatFormatting.RED : ChatFormatting.YELLOW);
		Wars.peaceTalks(c, r, day);
	}

	/** The survivors march home; the boss bar goes. */
	private static void finish(Fight f, boolean bar) {
		Soldiers.removeAll(f.key);
		MinecraftServer server = Courts.server();
		if (bar && server != null) {
			Cmd.run(server.overworld(), "bossbar remove " + bar(f));
		}
	}

	// ---------------------------------------------------------------- seat garrisons

	/** The owner's at the gates of a rival they're at war with: the garrison comes out (once). */
	static void garrison(Court c, Rival r, ServerLevel level, long now) {
		String key = seatKey(c, r);
		Fight existing = FIGHTS.get(key);
		if (existing != null) {
			return;
		}
		Fight f = new Fight(key, c, r.id, false, now);
		f.lastNear = now;
		int n = Math.min(8, r.regiments * 2);
		for (int i = 0; i < n; i++) {
			int dx = -6 + (i % 4) * 4;
			int dz = 2 + (i / 4) * 4;
			BlockPos at = Builder.at(r, new BlockPos(dx, 1, dz));
			Soldiers.spawn(level, key, Soldiers.THEIRS, c.uuid(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, "Garrison of " + r.House(), r.colour, 4.0f, 24);
		}
		f.theirStart = Soldiers.in(key, Soldiers.THEIRS).size();
		FIGHTS.put(key, f);
		if (f.theirStart > 0) {
			Courts.tell(c, "⚔ The garrison of " + r.seatName + " turns out to meet you: " + f.theirStart + " of them.", ChatFormatting.RED);
		}
	}

	static int garrisonLeft(Court c, Rival r) {
		return Soldiers.in(seatKey(c, r), Soldiers.THEIRS).size();
	}

	static void endGarrison(Court c, Rival r) {
		Fight f = FIGHTS.remove(seatKey(c, r));
		if (f != null) {
			finish(f, false);
		}
	}

	static @Nullable Fight field(Court c) {
		return FIGHTS.get(fieldKey(c));
	}

	static void clear() {
		MinecraftServer server = Courts.server();
		for (Fight f : FIGHTS.values()) {
			if (f.field && server != null) {
				Cmd.run(server.overworld(), "bossbar remove " + bar(f));
			}
		}
		FIGHTS.clear();
		WARNED.clear();
		Soldiers.clear();
	}
}
