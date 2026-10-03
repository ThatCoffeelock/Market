package com.thatcoffeelock.cannon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * For mods that carry cannons around (Ahoy's gun deck uses it): build a cannon that is mounted on something instead of
 * standing on the ground. A mounted cannon has no hitbox, never gets picked up or ticked by Cannon itself, takes its
 * ammunition from the {@link Crew} and isn't kept after a restart (whoever mounted it builds it again).
 */
public final class CannonApi {
	/** What a mounted cannon asks of whoever mounted it. */
	public interface Crew {
		/** The gunner got off (or was thrown off): put them somewhere sensible. */
		void dismounted(ServerPlayer gunner);

		/** Uses up one cannonball for this gunner's shot. False if there isn't one. */
		boolean takeBall(ServerPlayer gunner);

		/** How many cannonballs this gunner can fire (for their action bar). */
		int countBalls(ServerPlayer gunner);
	}

	/** A cannon mounted on something that moves. */
	public interface Mount {
		/**
		 * Puts the cannon's base here. When nobody's manning it, it also turns to {@code restHeading} (a yaw in degrees);
		 * a gunner turns it themselves by looking around.
		 */
		void moveTo(double x, double y, double z, float restHeading);

		/** One tick of the cannon: aiming at the gunner, reloading, firing. Call it once a tick, after {@link #moveTo}. */
		void tick();

		/** Sits the player behind the cannon. False if someone else is already there. */
		boolean man(ServerPlayer player);

		@Nullable ServerPlayer gunner();

		/** Fires a ball without checking for ammunition. False while reloading. */
		boolean fire(@Nullable Entity shooter);

		/** Ticks until the cannon can fire again. */
		int reloadTicks();

		/** The cannon's root entity (for checking where it is). */
		Entity entity();

		boolean isRemoved();

		/** Takes the cannon down: the gunner gets off, every part is removed. */
		void remove();
	}

	private CannonApi() {
	}

	/** Builds a mounted cannon, or returns null if it couldn't be summoned. */
	public static @Nullable Mount mount(ServerLevel level, double x, double y, double z, float heading, String ownerId, String ownerName, Crew crew) {
		CannonData data = new CannonData();
		data.owner = ownerId;
		data.ownerName = ownerName;
		Cannon cannon = Cannons.spawn(level, data, x, y, z, heading, crew);
		return cannon == null ? null : new Mounted(cannon);
	}

	private record Mounted(Cannon cannon) implements Mount {
		@Override
		public void moveTo(double x, double y, double z, float restHeading) {
			cannon.root.setPos(x, y, z);
			if (cannon.gunner() == null) {
				cannon.face(restHeading);
			}
			cannon.placeSeat();
		}

		@Override
		public void tick() {
			cannon.tick();
		}

		@Override
		public boolean man(ServerPlayer player) {
			return cannon.man(player);
		}

		@Override
		public @Nullable ServerPlayer gunner() {
			return cannon.gunner();
		}

		@Override
		public boolean fire(@Nullable Entity shooter) {
			return cannon.fire(shooter);
		}

		@Override
		public int reloadTicks() {
			return cannon.reload;
		}

		@Override
		public Entity entity() {
			return cannon.root;
		}

		@Override
		public boolean isRemoved() {
			return cannon.isRemoved();
		}

		@Override
		public void remove() {
			cannon.remove();
		}
	}
}
