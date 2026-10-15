package com.thatcoffeelock.hoogheheeren;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The rivals' seats out in the world. A seat is built the first time a player rides up to it (so nothing is built in
 * chunks nobody visits), and from then on the lord is at home in their hall whenever someone's near: walk in and
 * right-click them to talk in person. At war, the gate guards are replaced by a garrison that fights, and the
 * treasury chest by the high table can be looted.
 */
final class Seats {
	private Seats() {
	}

	static String lordMark(Court c, Rival r) {
		return "lord:" + c.owner + ":" + r.id;
	}

	static String guardMark(Court c, Rival r, int n) {
		return "guard:" + c.owner + ":" + r.id + ":" + n;
	}

	/** Every two seconds: build seats players have reached, put lords and guards where players can see them. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (Court c : Courts.ALL.values()) {
			for (Rival r : c.rivals) {
				if (!r.dim.equals(Cmd.dimId(level))) {
					continue;
				}
				BlockPos seat = new BlockPos(r.seatX, r.built ? r.seatY : 64, r.seatZ);
				if (!r.built) {
					if (nearFlat(level, seat, 80) && Builder.loaded(level, r.seatX, r.seatZ)) {
						Builder.build(level, r);
						ServerPlayer owner = Courts.player(c);
						if (owner != null && nearFlat(owner, seat, 100)) {
							owner.sendSystemMessage(Component.literal("You've reached " + r.seatName + ", the seat of " + r.name() + ".")
								.withStyle(ChatFormatting.LIGHT_PURPLE));
						}
					}
					continue;
				}
				boolean near = nearFlat(level, seat, 64);
				if (near && !r.dressed.equals(r.colour) && Builder.loaded(level, r.seatX, r.seatZ)) {
					Builder.dress(level, r);
				}
				// the lord, at home unless visiting your hall (or held in it)
				boolean home = !r.id.equals(c.guest);
				if (near && home) {
					BlockPos at = Builder.at(r, Builder.LORD);
					Figures.ensure(level, lordMark(c, r), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, lordLook(c, r));
				} else if (!nearFlat(level, seat, 96)) {
					Figures.remove(lordMark(c, r));
				}
				// gate guards at peace; at war, a garrison that fights
				for (int i = 0; i < Builder.GATE_GUARDS.length; i++) {
					String mark = guardMark(c, r, i);
					if (near && !r.war) {
						BlockPos at = Builder.at(r, Builder.GATE_GUARDS[i]);
						Figures.ensure(level, mark, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, guardLook(r, Courts.RANDOM.nextInt(1000)));
					} else {
						Figures.remove(mark);
					}
				}
				if (r.war && r.regiments > 0) {
					ServerPlayer owner = Courts.player(c);
					if (owner != null && owner.level() == level && nearFlat(owner, seat, 48) && !owner.isSpectator()) {
						Battles.garrison(c, r, level, now);
					}
				}
			}
		}
	}

	private static boolean nearFlat(ServerLevel level, BlockPos pos, double range) {
		for (ServerPlayer p : level.players()) {
			if (nearFlat(p, pos, range)) {
				return true;
			}
		}
		return false;
	}

	static boolean nearFlat(ServerPlayer p, BlockPos pos, double range) {
		if (p.isSpectator()) {
			return false;
		}
		double dx = p.getX() - pos.getX() - 0.5;
		double dz = p.getZ() - pos.getZ() - 0.5;
		return dx * dx + dz * dz < range * range;
	}

	static Figures.Look lordLook(Court c, Rival r) {
		long today = Courts.today();
		int op = c.opinion(r, today);
		Court.Tier tier = Court.Tier.of(op);
		String what = (r.met || r.known(today) ? r.temperament().title : "Head of the house") + " · " + tier.title;
		return new Figures.Look(r.name(), Figures.line(what, tier.color.name().toLowerCase(java.util.Locale.ROOT)), r.woman, r.skin, r.colour, "",
			r.is(Temperament.WARLORD) ? "minecraft:iron_sword" : r.is(Temperament.ZEALOT) ? "minecraft:book" : "", "");
	}

	static Figures.Look guardLook(Rival r, int seed) {
		boolean crossbow = seed % 2 == 0;
		return new Figures.Look("Guard of " + r.House(), Figures.line(r.seatName, Names.colour(r.colour).chat().name().toLowerCase(java.util.Locale.ROOT)), false,
			Names.skin(new java.util.Random(seed)), r.colour, "minecraft:iron_helmet", crossbow ? "minecraft:crossbow" : "minecraft:iron_sword", "");
	}

	/** Takes a fallen house's lord and guards away (the new house's are put there next time someone comes). */
	static void forget(Court c, Rival r) {
		Figures.removeAll("lord:" + c.owner + ":" + r.id);
		Figures.removeAll("guard:" + c.owner + ":" + r.id + ":");
		Battles.endGarrison(c, r);
	}

	// ---------------------------------------------------------------- the treasury chest

	private record Found(Court court, Rival rival) {
	}

	private static @Nullable Found chestAt(ServerLevel level, BlockPos pos) {
		for (Court c : Courts.ALL.values()) {
			for (Rival r : c.rivals) {
				if (r.built && r.dim.equals(Cmd.dimId(level)) && Builder.at(r, Builder.CHEST).equals(pos)) {
					return new Found(c, r);
				}
			}
		}
		return null;
	}

	/** Right-click on a seat's treasury chest: locked at peace; at war, the owner of the court loots it. */
	static InteractionResult useChest(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		Found f = chestAt(level, hit.getBlockPos());
		if (f == null) {
			return InteractionResult.PASS;
		}
		Court c = f.court();
		Rival r = f.rival();
		long today = Courts.today();
		if (!c.owner.equals(player.getUUID().toString())) {
			HeerenMod.bar(player, "The treasury of " + r.House() + ". Locked, and none of your business.", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (!r.war) {
			HeerenMod.bar(player, "The treasury of " + r.House() + ". At peace, it stays locked: rob it with an agent, or go to war.",
				ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (Battles.garrisonLeft(c, r) > 0) {
			HeerenMod.bar(player, "The garrison still holds the hall. Deal with them first.", ChatFormatting.RED);
			return InteractionResult.SUCCESS;
		}
		if (today - r.lastPlunder < 3) {
			HeerenMod.bar(player, "You've emptied it already. They'll have refilled it in a few days.", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		long take = Math.min(r.treasury * 35 / 100, Bank.cents(5000 * (1 + Ai.rank(c))));
		r.treasury -= take;
		r.lastPlunder = today;
		r.warScore = Math.min(100, r.warScore + 15);
		r.remember("Looted " + r.their() + " treasury", -15, today, 60);
		Bank.credit(c.uuid(), c.ownerName, take);
		BlockPos p = hit.getBlockPos();
		Cmd.sound(level, "minecraft:block.chest.open", p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1f, 0.8f);
		Cmd.sound(level, "minecraft:entity.experience_orb.pickup", p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1f, 1.2f);
		Cmd.particles(level, "minecraft:happy_villager", p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 0.4, 0, 20);
		Courts.tell(c, "You looted the treasury of " + r.House() + ": " + Bank.format(take) + ". War score " + Wars.signed(r.warScore) + ".",
			ChatFormatting.GOLD);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- directions

	/** A compass that points at the seat: "Letter of Introduction". */
	static ItemStack directions(Court c, Rival r) {
		ItemStack stack = new ItemStack(Items.COMPASS);
		ServerLevel level = Courts.server() == null ? null : Courts.server().overworld();
		if (level != null) {
			stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), r.seat())), false));
		}
		stack.set(DataComponents.ITEM_NAME, Component.literal("Directions to " + r.seatName).withStyle(Names.colour(r.colour).chat()));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Ui.t("The seat of " + r.name() + ",", ChatFormatting.GRAY),
			Ui.t("at " + r.seatX + ", " + r.seatZ + ".", ChatFormatting.GRAY),
			Ui.t(r.war ? "At war: mind the garrison." : "Walk in and talk to " + (r.woman ? "her" : "him") + " in person.", ChatFormatting.DARK_GRAY))));
		return stack;
	}

	static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
