package com.thatcoffeelock.sellswords;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Any goat horn gives orders to your whole squad within earshot (48 blocks). Vanilla still plays the horn.
 * <ul>
 * <li>Blow it with mercenaries following you: they all <b>hold</b> where they stand.</li>
 * <li>Blow it with nobody following: everyone close by (guarding or patrolling) <b>rallies</b> and follows you.</li>
 * <li>Sneak and blow it: <b>Charge!</b> Everyone following you goes for whatever you're looking at.</li>
 * </ul>
 */
final class Horn {
	static final double EARSHOT = 48;
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Horn() {
	}

	/** UseItemCallback. Never consumes the click: the horn sounds either way. */
	static void use(ServerPlayer player, ItemStack stack) {
		if (!stack.is(Items.GOAT_HORN)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < 40) {
			return;
		}
		LAST.put(player.getUUID(), now);
		blow(player, player.isShiftKeyDown());
	}

	/** What the horn does. Returns how many mercenaries answered. Public to the tests. */
	static int blow(ServerPlayer player, boolean charge) {
		List<Merc> mine = Mercs.ownedBy(player.getUUID());
		if (mine.isEmpty()) {
			return 0;
		}
		int n = 0;
		if (charge) {
			LivingEntity target = lookedAt(player);
			if (target == null) {
				bar(player, "Charge at what? Look at something first.", ChatFormatting.GRAY);
				return 0;
			}
			for (Merc m : mine) {
				if (m.orders() == Merc.Orders.FOLLOW && near(player, m)) {
					Duty.charge(m, target);
					n++;
				}
			}
			bar(player, n == 0 ? "Nobody's following you to charge with." : "CHARGE! " + n + (n == 1 ? " mercenary goes" : " mercenaries go") + " for the "
				+ target.getName().getString() + ".", n == 0 ? ChatFormatting.GRAY : ChatFormatting.RED);
			return n;
		}
		boolean anyFollowing = false;
		for (Merc m : mine) {
			anyFollowing |= m.orders() == Merc.Orders.FOLLOW && near(player, m);
		}
		for (Merc m : mine) {
			if (!near(player, m)) {
				continue;
			}
			LivingEntity brain = Mercs.brain(m);
			if (anyFollowing && m.orders() == Merc.Orders.FOLLOW) {
				m.orders(Merc.Orders.GUARD);
				if (brain != null) {
					m.post(Cmd.dimId(brain.level()), brain.blockPosition());
				}
				n++;
			} else if (!anyFollowing && m.orders() != Merc.Orders.FOLLOW) {
				m.orders(Merc.Orders.FOLLOW);
				n++;
			}
		}
		Mercs.changed();
		if (anyFollowing) {
			bar(player, "Hold! " + n + (n == 1 ? " mercenary holds" : " mercenaries hold") + " where they stand.", ChatFormatting.YELLOW);
		} else {
			bar(player, n == 0 ? "Nobody in earshot. (" + (int) EARSHOT + " blocks)" : "To me! " + n + (n == 1 ? " mercenary follows" : " mercenaries follow") + " you.",
				n == 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD);
		}
		return n;
	}

	private static boolean near(ServerPlayer player, Merc m) {
		LivingEntity brain = Mercs.brain(m);
		return brain != null && brain.level() == player.level() && brain.distanceTo(player) <= EARSHOT;
	}

	/** The living thing in the player's crosshair, up to 48 blocks away (that isn't a friend). */
	static @Nullable LivingEntity lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(EARSHOT));
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		ServerLevel level = (ServerLevel) player.level();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(2), e -> e.isAlive() && e != player)) {
			if (Combat.friendly(e)) {
				continue;
			}
			var hit = e.getBoundingBox().inflate(0.5).clip(eye, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceTo(eye);
				if (d < bestDist && player.hasLineOfSight(e)) {
					bestDist = d;
					best = e;
				}
			}
		}
		return best;
	}

	private static void bar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}
}
