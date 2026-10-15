package com.thatcoffeelock.hoogheheeren;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Your hall: wherever you hang your Audience Bell. Invite a rival from their dossier and their coach pulls up within
 * the minute: the head of the house and two of their guards, standing by your bell until the second dawn. Talk to
 * them in person (every proposal goes better), lay on a feast from what you're carrying (food, Proost spirits, Havana
 * cigars), show them the door, or bar it.
 */
public final class Hall {
	static final String ITEM_TAG = "hoogheheeren_bell";

	private Hall() {
	}

	static String guestMark(Court c) {
		return "guest:" + c.owner + ":";
	}

	// ---------------------------------------------------------------- the bell

	/** Matches data/hoogheheeren/recipe/audience_bell.json, so crafted and given ones stack. */
	static ItemStack item() {
		ItemStack stack = new ItemStack(Items.BELL);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ITEM_TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal("Audience Bell").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Ui.t("Place it in your hall and right-click it:", ChatFormatting.GRAY),
			Ui.t("your guests from the rival houses arrive here.", ChatFormatting.GRAY),
			Ui.t("Mind the silverware.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	static boolean isItem(ItemStack stack) {
		if (!stack.is(Items.BELL)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ITEM_TAG);
	}

	/** Called by the BlockItem mixin right after a block is placed from this stack. */
	public static void onPlaced(Level world, BlockPos pos, ItemStack stack, @Nullable Player player) {
		if (!(world instanceof ServerLevel level) || !isItem(stack) || !(player instanceof ServerPlayer sp)) {
			return;
		}
		BlockPos at = pos.immutable();
		HeerenMod.nextTick(() -> {
			if (!level.getBlockState(at).is(Blocks.BELL)) {
				return;
			}
			Court c = Courts.of(sp.getUUID(), sp.getName().getString());
			if (c.hall && !(c.hallDim.equals(Cmd.dimId(level)) && c.hallPos().equals(at))) {
				sp.sendSystemMessage(Component.literal("Your hall moves here. (The old bell is just a bell now.)").withStyle(ChatFormatting.GRAY));
			}
			sendGuestHome(c, false);
			c.hall = true;
			c.hallDim = Cmd.dimId(level);
			c.hallX = at.getX();
			c.hallY = at.getY();
			c.hallZ = at.getZ();
			Courts.changed();
			sp.sendSystemMessage(Component.literal("Your Audience Bell is hung. Invite a rival from their dossier at your Company Desk; they'll arrive here.")
				.withStyle(ChatFormatting.GOLD));
		});
	}

	private static @Nullable Court hallAt(ServerLevel level, BlockPos pos) {
		String dim = Cmd.dimId(level);
		for (Court c : Courts.ALL.values()) {
			if (c.hall && c.hallDim.equals(dim) && c.hallPos().equals(pos)) {
				return c;
			}
		}
		return null;
	}

	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		Court c = hallAt(level, hit.getBlockPos());
		if (c == null) {
			return InteractionResult.PASS;
		}
		if (!level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
			c.hall = false;
			Courts.changed();
			return InteractionResult.PASS;
		}
		BlockPos p = hit.getBlockPos();
		Cmd.sound(level, "minecraft:block.bell.use", p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1f, 1f);
		if (!c.owner.equals(player.getUUID().toString())) {
			HeerenMod.bar(player, c.ownerName + "'s Audience Bell. You're not expected.", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		Rival guest = c.rival(c.guest);
		if (guest != null) {
			AudienceMenu.open(player, c, guest, AudienceMenu.Where.HALL);
		} else {
			CourtMenu.open(player, c, CourtMenu.Page.RIVALS);
			HeerenMod.bar(player, "Nobody's visiting. Pick a house and invite them from their dossier.", ChatFormatting.YELLOW);
		}
		return InteractionResult.SUCCESS;
	}

	/** Breaking the bell gives it back, and closes the hall (a guest goes home). False cancels vanilla's break. */
	static boolean allowBreak(Player player, Level world, BlockPos pos) {
		if (!(world instanceof ServerLevel level)) {
			return true;
		}
		Court c = hallAt(level, pos);
		if (c == null) {
			return true;
		}
		sendGuestHome(c, false);
		c.hall = false;
		Courts.changed();
		level.removeBlock(pos, false);
		if (!player.isCreative()) {
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		return false;
	}

	// ---------------------------------------------------------------- guests

	static long inviteCost(Court c) {
		return Bank.cents(100 * (1 + Ai.rank(c)));
	}

	/** Will they come? Not at war, not if they can't stand you, and the Paranoid need to like you first. */
	static @Nullable String invite(Court c, Rival r, long day) {
		if (!c.hall) {
			return "You need a hall first: craft an Audience Bell (a bell, a gold ingot and paper) and hang it.";
		}
		if (!c.guest.isEmpty()) {
			Rival g = c.rival(c.guest);
			return (g == null ? "Someone" : Ai.capital(g.name())) + (c.guestHeld ? " is in your cellar" : " is already your guest") + ".";
		}
		if (r.war) {
			return Ai.capital(r.name()) + " won't sit at the table of someone " + r.they() + "'s at war with. Visit " + (r.woman ? "her" : "him")
				+ " at " + r.seatName + " instead.";
		}
		int op = c.opinion(r, day);
		if (op < (r.is(Quirk.PARANOID) ? 10 : -20)) {
			return Ai.capital(r.name()) + " sends regrets. (" + Court.Tier.of(op).title + ": " + (r.is(Quirk.PARANOID) ? "a Paranoid guest needs to like you"
				: "they need to dislike you less") + ".)";
		}
		long cost = inviteCost(c);
		if (!Bank.charge(c.uuid(), cost)) {
			return "You can't afford the " + Bank.format(cost) + " for the coach and the welcome.";
		}
		c.guest = r.id;
		c.guestUntil = day + 2;
		c.guestHeld = false;
		c.feasted = -10;
		r.met = true;
		Courts.tell(c, Ai.capital(r.name()) + " accepts your invitation. " + Ai.capital(r.their()) + " coach will be at your bell within the minute.",
			ChatFormatting.GREEN);
		return null;
	}

	/** Every two seconds: a guest stands by the bell (with two guards) while someone's near. */
	static void tick(MinecraftServer server) {
		for (Court c : Courts.ALL.values()) {
			Rival r = c.rival(c.guest);
			if (r == null || !c.hall) {
				Figures.removeAll(guestMark(c));
				continue;
			}
			ServerLevel level = level(server, c.hallDim);
			if (level == null || !level.isLoaded(c.hallPos())) {
				continue;
			}
			if (!Figures.playerNear(level, c.hallPos(), 48)) {
				if (!Figures.playerNear(level, c.hallPos(), 80)) {
					Figures.removeAll(guestMark(c));
				}
				continue;
			}
			String mark = guestMark(c) + r.id;
			if (Figures.get(mark) == null) {
				BlockPos spot = Figures.standable(level, c.hallPos(), 3);
				if (spot == null) {
					spot = c.hallPos().above();
				}
				Figures.Look look = Seats.lordLook(c, r);
				if (c.guestHeld) {
					look = new Figures.Look(r.name(), Figures.line("Your prisoner", "dark_red"), r.woman, r.skin, "gray", "", "", "");
				}
				Figures.summon(level, mark, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yawToward(spot, c.hallPos()), look, true);
				if (!c.guestHeld) {
					for (int i = 0; i < 2; i++) {
						BlockPos g = Figures.standable(level, spot, 2 + i);
						if (g != null && !g.equals(spot)) {
							Figures.summon(level, guestMark(c) + r.id + ":guard" + i, g.getX() + 0.5, g.getY(), g.getZ() + 0.5,
								yawToward(g, c.hallPos()), Seats.guardLook(r, i * 7 + 3), true);
						}
					}
					Cmd.sound(level, "minecraft:entity.horse.gallop", spot.getX(), spot.getY(), spot.getZ(), 1f, 0.9f);
				}
			}
		}
	}

	private static float yawToward(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
	}

	static @Nullable ServerLevel level(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) {
			if (Cmd.dimId(l).equals(dim)) {
				return l;
			}
		}
		return null;
	}

	/** At dawn: a guest whose visit is over goes home. */
	static void dawn(Court c, long day) {
		Rival r = c.rival(c.guest);
		if (r == null) {
			c.guest = "";
			c.guestHeld = false;
			return;
		}
		if (!c.guestHeld && day >= c.guestUntil) {
			c.chronicle(Ai.capital(r.name()) + "'s coach has left your hall.");
			sendGuestHome(c, false);
		}
	}

	static void sendGuestHome(Court c, boolean tell) {
		Rival r = c.rival(c.guest);
		Figures.removeAll(guestMark(c));
		if (r != null && tell) {
			Courts.tell(c, Ai.capital(r.name()) + " takes " + r.their() + " leave.", ChatFormatting.GRAY);
		}
		c.guest = "";
		c.guestHeld = false;
		Courts.changed();
	}

	/** A house that's fallen can't be your guest any more. */
	static void forget(Court c, Rival r) {
		if (r.id.equals(c.guest)) {
			sendGuestHome(c, false);
		}
	}

	// ---------------------------------------------------------------- the feast

	/**
	 * Lays the table from what the host carries: up to 16 food, 6 Proost spirits and 4 Havana cigars. Food is nice,
	 * spirits nicer (a Drunkard's favourite, a Pious guest's horror), cigars nicest. Once a day.
	 */
	static @Nullable String feast(ServerPlayer host, Court c, Rival r, long day) {
		if (c.feasted == day) {
			return "You've feasted " + r.name() + " today already. Any more and " + r.they() + "'ll never leave.";
		}
		int food = 0;
		int drink = 0;
		int cigars = 0;
		var inv = host.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String kind = kind(stack);
			int take;
			if (kind.equals("spirit") && drink < 6) {
				take = Math.min(stack.getCount(), 6 - drink);
				drink += take;
			} else if (kind.equals("cigar") && cigars < 4) {
				take = Math.min(stack.getCount(), 4 - cigars);
				cigars += take;
			} else if (kind.isEmpty() && stack.get(DataComponents.FOOD) != null && food < 16) {
				take = Math.min(stack.getCount(), 16 - food);
				food += take;
			} else {
				continue;
			}
			stack.shrink(take);
		}
		if (food + drink + cigars == 0) {
			return "Your table is bare. Bring food, Proost spirits or Havana cigars.";
		}
		double score = food * 0.6 + cigars * 2.5;
		double spirits = drink * 2.0;
		if (r.is(Quirk.DRUNKARD)) {
			spirits *= 2;
		}
		if (r.is(Quirk.PIOUS) || r.is(Temperament.ZEALOT)) {
			spirits = -drink * 2.0;
		}
		if (r.is(Temperament.SPENDTHRIFT)) {
			score *= 1.3;
		}
		int value = (int) Math.round(Math.max(-15, Math.min(25, score + spirits)));
		c.feasted = day;
		r.remember("A feast in your hall", value, day, 30);
		Courts.changed();
		ServerLevel level = (ServerLevel) host.level();
		Cmd.sound(level, "minecraft:entity.player.burp", host.getX(), host.getY() + 1, host.getZ(), 1f, 0.9f);
		Cmd.sound(level, "minecraft:entity.generic.drink", host.getX(), host.getY() + 1, host.getZ(), 0.8f, 1f);
		String verdict;
		if (value >= 18) {
			verdict = Ai.capital(r.they()) + " raises a toast to you, and means it.";
		} else if (value >= 8) {
			verdict = Ai.capital(r.they()) + " eats well and says so.";
		} else if (value >= 0) {
			verdict = Ai.capital(r.they()) + " is polite about it.";
		} else {
			verdict = Ai.capital(r.they()) + " stares at the spirits on the table, and at you, in silence.";
		}
		if (drink > 0 && r.is(Quirk.DRUNKARD)) {
			verdict = Ai.capital(r.they()) + " drinks your cellar dry and calls you brother.";
		}
		Courts.tell(c, "A feast for " + r.name() + " (" + food + " food, " + drink + " spirits, " + cigars + " cigars). " + verdict
			+ " (Opinion " + Wars.signed(value) + ")", value >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
		return null;
	}

	/** "spirit" for a Proost bottle, "cigar" for a Havana cigar, "other" for other mods' items, "" for vanilla. */
	private static String kind(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		CompoundTag tag = data.copyTag();
		if (tag.getStringOr("proost", "").equals("spirit")) {
			return "spirit";
		}
		if (tag.getStringOr("havana", "").equals("cigar")) {
			return "cigar";
		}
		return "other";
	}

	// ---------------------------------------------------------------- treachery

	/**
	 * Bars the doors: the guest is your prisoner. Every house remembers it forever; your Honour is gone and your Fear
	 * is sky high. Their house is at war with you, of course.
	 */
	static void barDoors(Court c, Rival r, long day) {
		c.guestHeld = true;
		c.honour(-40);
		c.fear(30);
		r.remember("Seized under your roof, as your guest", -80, day, 0);
		for (Rival o : c.rivals) {
			if (o != r) {
				o.remember("Seized " + r.House() + " at your own table", -40, day, 0);
			}
		}
		Figures.removeAll(guestMark(c) + r.id + ":guard");
		Figures.remove(guestMark(c) + r.id); // put back as a prisoner next tick
		Courts.tell(c, "The doors are barred. " + Ai.capital(r.name()) + "'s guards are disarmed and " + r.they()
			+ " is your prisoner. Every house at the table will hear of this, and none will forget it.", ChatFormatting.DARK_RED);
		if (!r.war) {
			Wars.declare(c, r, false, day);
		}
	}

	/** Lets the prisoner go home, for 40% of their treasury. */
	static void ransom(Court c, Rival r, long day) {
		long take = r.treasury * 40 / 100;
		r.treasury -= take;
		Bank.credit(c.uuid(), c.ownerName, take);
		Courts.tell(c, r.House() + " paid " + Bank.format(take) + " to get " + r.name() + " back.", ChatFormatting.GOLD);
		sendGuestHome(c, false);
	}

	/** Lets the prisoner go, nothing asked. It won't undo it, but it's something. */
	static void release(Court c, Rival r, long day) {
		c.honour(5);
		r.remember("Let you go unharmed, in the end", 15, day, 60);
		Courts.tell(c, "You let " + r.name() + " go. " + Ai.capital(r.they()) + " rides off without a word.", ChatFormatting.GRAY);
		sendGuestHome(c, false);
	}

	/** The worst thing you can do at court. */
	static void execute(Court c, Rival r, long day) {
		c.fear(15);
		c.honour(-20);
		for (Rival o : c.rivals) {
			if (o != r) {
				o.remember("Murdered " + r.name() + ", a guest", -20, day, 0);
			}
		}
		sendGuestHome(c, false);
		Ai.fall(c, r, day, Courts.RANDOM, true, Ai.capital(r.name()) + " was executed in your cellar.");
		r.remember("Murdered the head of the house", -60, day, 0);
	}
}
