package com.thatcoffeelock.ahoy;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A ship's cannons, as the rest of Ahoy sees them. The real thing ({@link CannonDeck}) needs the Cannon mod and is only
 * loaded when it's installed, so nothing outside it may mention a Cannon class.
 */
interface GunDeck {
	/** Is this something that can be slotted into a gun port? */
	boolean accepts(ItemStack stack);

	/** Once a ship tick: builds, moves, ticks and takes down the cannons to match the ship and its gun ports. */
	void tick();

	/** Is there a cannon standing at this port right now? (It's built a tick after it's slotted in.) */
	boolean mounted(int port);

	/** Where that cannon is, or null. */
	@Nullable Vec3 where(int port);

	@Nullable ServerPlayer gunner(int port);

	/** Everyone who is manning a cannon. */
	List<ServerPlayer> gunners();

	boolean isManning(Entity entity);

	/** Ticks until that cannon can fire again. */
	int reload(int port);

	/** Fires that cannon without ammunition (false while it reloads). */
	boolean fire(int port);

	/** Sits the player behind that cannon. {@code fromSeat} is where they were sitting, so they go back there afterwards. */
	boolean man(ServerPlayer player, int port, int fromSeat);

	/** Cannonballs lying in the cargo holds. */
	int ballsInHold();

	/** Takes every cannon down. {@code ashore}: put the gunners on dry land too. */
	void shutdown(boolean ashore);
}
