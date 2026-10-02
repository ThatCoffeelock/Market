package com.thatcoffeelock.cargotrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The stretch of track the train is on: a short list of rails from a few behind the train to a dozen ahead of
 * it, grown as it goes and trimmed behind it. It never needs to know the whole line, so long lines, loops and
 * track you lay while the train is running all just work.
 *
 * Positions along the list are indices (one rail = 1). The locomotive is at {@code s}, wagon k (1, 2, ...) at
 * {@code s - k * GAP} and the last wagon at {@code s - length}, so the locomotive is always on the high side.
 * Going "forward" (dir +1) means the locomotive leads; going back (dir -1) it pushes the wagons ahead of it.
 */
final class Route {
	/** Centre to centre of two coupled cars, in rails. */
	static final double GAP = 2.25;
	/** How many rails we like to know about ahead of the leading car. */
	static final int LOOKAHEAD = 12;
	/** How many we keep behind the trailing car. */
	static final int KEEP_BEHIND = 4;

	final ServerLevel level;
	final List<Track.Node> nodes = new ArrayList<>();
	double s;
	/** Locomotive to last wagon, in rails. */
	double length;
	/** The high / low end of the list is the real end of the track (as opposed to "not looked yet"). */
	boolean headDead;
	boolean tailDead;
	/** A chunk the track runs into that isn't loaded yet. */
	@Nullable BlockPos waitingFor;

	private Route(ServerLevel level, double length) {
		this.level = level;
		this.length = length;
	}

	/**
	 * A route with the locomotive on {@code at}, its nose pointing along {@code yaw} as far as the track allows.
	 * Null if there's no rail there or not enough track behind it for the wagons.
	 */
	static @Nullable Route start(ServerLevel level, BlockPos at, float yaw, int wagons) {
		RailShape shape = Track.shape(level.getBlockState(at));
		if (shape == null) {
			return null;
		}
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		// the wagons go on the side facing away from the nose; if that doesn't work, try the other side
		int[][] exits = Track.exits(shape);
		int[][] sorted = Arrays.copyOf(exits, exits.length);
		Arrays.sort(sorted, Comparator.comparingDouble(e -> e[0] * fx + e[2] * fz));
		for (int[] exit : sorted) {
			Track.Step back = Track.step(level, at, exit);
			if (back.kind() != Track.Kind.RAIL) {
				continue;
			}
			Route route = new Route(level, wagons * GAP);
			route.nodes.add(Track.Node.of(level, back.pos()));
			route.nodes.add(Track.Node.of(level, at));
			route.s = 1;
			int guard = 0;
			while (route.s - route.length < 0 && guard++ < 16 && route.extend(false)) {
				// grow the tail until the wagons fit
			}
			if (route.s - route.length >= 0) {
				return route;
			}
		}
		return null;
	}

	int size() {
		return nodes.size();
	}

	Track.Node node(int i) {
		return nodes.get(Math.max(0, Math.min(nodes.size() - 1, i)));
	}

	/** Where the leading car is. */
	double front(int dir) {
		return dir > 0 ? s : s - length;
	}

	void setFront(int dir, double front) {
		s = dir > 0 ? front : front + length;
	}

	/** The last rail we can go to in this direction. */
	int limit(int dir) {
		return dir > 0 ? nodes.size() - 1 : 0;
	}

	/**
	 * Makes room for a different number of wagons. Grows the track behind the last wagon if it has to; false
	 * (and nothing changes) if the track behind is too short.
	 */
	boolean resize(int wagons) {
		double old = length;
		length = wagons * GAP;
		int guard = 0;
		while (s - length < 0 && !tailDead && guard++ < 16 && extend(false)) {
			// more track behind for the new wagon
		}
		if (s - length < 0) {
			length = old;
			return false;
		}
		return true;
	}

	boolean deadAhead(int dir) {
		return dir > 0 ? headDead : tailDead;
	}

	void forgetEnd(int dir) {
		if (dir > 0) {
			headDead = false;
		} else {
			tailDead = false;
		}
	}

	// ---------------------------------------------------------------- growing and trimming

	/** Makes sure we know LOOKAHEAD rails ahead (or where the line ends) and a few behind. */
	void grow(int dir) {
		waitingFor = null;
		int guard = 0;
		while (!headDead && waitingFor == null && nodes.size() - 1 - s < (dir > 0 ? LOOKAHEAD : KEEP_BEHIND) && guard++ < 32) {
			if (!extend(true)) {
				break;
			}
		}
		while (!tailDead && waitingFor == null && s - length < (dir < 0 ? LOOKAHEAD : KEEP_BEHIND) && guard++ < 64) {
			if (!extend(false)) {
				break;
			}
		}
	}

	/** Adds one rail at the high (head) or low (tail) end. False at the end of the line or an unloaded chunk. */
	boolean extend(boolean head) {
		int n = nodes.size();
		Track.Node cur = head ? nodes.get(n - 1) : nodes.get(0);
		BlockPos prev = n > 1 ? (head ? nodes.get(n - 2) : nodes.get(1)).pos() : null;
		Track.Step step = Track.next(level, prev, cur.pos());
		if (step.kind() == Track.Kind.UNLOADED) {
			waitingFor = step.pos();
			return false;
		}
		if (step.kind() == Track.Kind.END) {
			if (head) {
				headDead = true;
			} else {
				tailDead = true;
			}
			return false;
		}
		Track.Node node = Track.Node.of(level, step.pos());
		if (head) {
			nodes.add(node);
		} else {
			nodes.add(0, node);
			s += 1;
		}
		return true;
	}

	/** Forgets rails well behind the trailing car. */
	void prune(int dir) {
		if (dir > 0) {
			int drop = (int) Math.floor(s - length) - KEEP_BEHIND;
			if (drop > 0) {
				nodes.subList(0, drop).clear();
				s -= drop;
				tailDead = false;
			}
		} else {
			int keep = (int) Math.ceil(s) + KEEP_BEHIND + 1;
			if (nodes.size() > keep) {
				nodes.subList(keep, nodes.size()).clear();
				headDead = false;
			}
		}
	}

	/**
	 * Checks the rails ahead still lead where we thought: a rail broken, a switch flipped or new track laid.
	 * Everything past the first difference is forgotten (and found again by {@link #grow}).
	 */
	void revalidate(int dir) {
		int n = nodes.size();
		if (n < 2) {
			return;
		}
		if (dir > 0) {
			for (int k = Math.max(1, (int) Math.floor(s)); k < n; k++) {
				Track.Step next = Track.next(level, nodes.get(k - 1).pos(), nodes.get(k).pos());
				boolean same = k + 1 < n ? next.kind() == Track.Kind.RAIL && next.pos().equals(nodes.get(k + 1).pos()) : !headDead || next.kind() != Track.Kind.RAIL;
				if (!same) {
					nodes.subList(k + 1, n).clear();
					headDead = false;
					s = Math.min(s, nodes.size() - 1);
					return;
				}
			}
		} else {
			for (int k = Math.min(n - 2, (int) Math.ceil(s - length)); k >= 0; k--) {
				Track.Step next = Track.next(level, nodes.get(k + 1).pos(), nodes.get(k).pos());
				boolean same = k > 0 ? next.kind() == Track.Kind.RAIL && next.pos().equals(nodes.get(k - 1).pos()) : !tailDead || next.kind() != Track.Kind.RAIL;
				if (!same) {
					nodes.subList(0, k).clear();
					s -= k;
					tailDead = false;
					s = Math.max(s, length);
					return;
				}
			}
		}
	}

	// ---------------------------------------------------------------- geometry

	/** World position at an index along the route (between rails it's a straight line). */
	Vec3 point(double i) {
		int n = nodes.size();
		double c = Math.max(0, Math.min(n - 1, i));
		int a = (int) Math.floor(c);
		int b = Math.min(a + 1, n - 1);
		Vec3 pa = nodes.get(a).point();
		Vec3 pb = nodes.get(b).point();
		double t = c - a;
		return new Vec3(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t, pa.z + (pb.z - pa.z) * t);
	}

	/**
	 * Which way the track runs at an index, as a yaw pointing towards the high end. Measured over two rails, so
	 * cars glide round the zig-zag of a diagonal instead of flapping between north and east.
	 */
	float heading(double i, float fallback) {
		Vec3 a = point(i - 1);
		Vec3 b = point(i + 1);
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		if (dx * dx + dz * dz < 1e-6) {
			return fallback;
		}
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
}
