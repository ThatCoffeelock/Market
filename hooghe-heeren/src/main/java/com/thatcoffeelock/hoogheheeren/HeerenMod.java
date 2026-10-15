package com.thatcoffeelock.hoogheheeren;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.serialization.Codec;
import com.thatcoffeelock.compagnie.CompagnieHooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HeerenMod implements ModInitializer {
	public static final String MOD_ID = "hoogheheeren";
	/** Who's holding a Compagnie agent that's away on the Heeren's business. */
	static final String ID = "hoogheheeren";
	public static final Logger LOG = LoggerFactory.getLogger("Hooghe Heeren");

	/** On everything we summon: lords, guests, guards, soldiers' brains and bodies. Saved with the entity, so leftovers are found. */
	public static final AttachmentType<String> MARK = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(MOD_ID, "mark"), builder -> builder.persistent(Codec.STRING));

	private static final Queue<Runnable> NEXT_TICK = new ConcurrentLinkedQueue<>();
	private static final List<Delayed> LATER = new ArrayList<>();
	private static int ticks;

	@Override
	public void onInitialize() {
		CompagnieHooks.register(new Extension());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Courts.load(server);
			if (Boolean.getBoolean("hoogheheeren.smokeTest")) {
				SmokeTest.run(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Battles.clear();
			Figures.clear();
			Courts.stop();
			NEXT_TICK.clear();
			LATER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Soldiers.tick(server);
			Runnable task;
			while ((task = NEXT_TICK.poll()) != null) {
				task.run();
			}
			ticks++;
			if (!LATER.isEmpty()) {
				List<Delayed> due = new ArrayList<>();
				LATER.removeIf(d -> d.at <= ticks && due.add(d));
				due.forEach(d -> d.task.run());
			}
			if (ticks % 20 == 0) {
				safely("battles", () -> Battles.tick(server));
			}
			if (ticks % 40 == 0) {
				safely("seats", () -> Seats.tick(server));
				safely("the hall", () -> Hall.tick(server));
			}
			if (ticks % 1200 == 0) {
				Courts.saveIfDirty();
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register(Figures::onLoad);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			String mark = Figures.mark(entity);
			if (mark != null && !mark.startsWith("brain:") && !mark.startsWith("body:")) {
				return false; // lords, guests and guards are never hurt
			}
			return Soldiers.allowDamage(entity, source, amount);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
			Soldiers.afterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register(Soldiers::onDeath);

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			InteractionResult r = Seats.useChest(sp, level, hit);
			if (r != InteractionResult.PASS || player.isShiftKeyDown()) {
				return r;
			}
			return Hall.use(sp, level, hit);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> Hall.allowBreak(player, world, pos));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			String mark = Figures.mark(entity);
			if (mark == null) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND) {
				talk(sp, entity, mark);
			}
			return InteractionResult.SUCCESS;
		});
		CommandRegistrationCallback.EVENT.register(HeerenCommands::register);
		LOG.info("De Hooghe Heeren loaded. The gentlemen will see you now.");
	}

	/** Right-click on a lord or a guest: an audience (for the court they belong to). */
	private static void talk(ServerPlayer player, Entity entity, String mark) {
		String[] parts = mark.split(":");
		if (parts.length < 3 || !(parts[0].equals("lord") || parts[0].equals("guest")) || parts.length > 3) {
			if (parts[0].equals("guard") || mark.contains(":guard")) {
				bar(player, "\"Move along.\"", ChatFormatting.GRAY);
			}
			return;
		}
		Court c = Courts.get(UUID.fromString(parts[1]));
		Rival r = c == null ? null : c.rival(parts[2]);
		if (c == null || r == null) {
			return;
		}
		if (!c.owner.equals(player.getUUID().toString())) {
			bar(player, Ai.capital(r.name()) + " receives " + c.ownerName + ", not you.", ChatFormatting.GRAY);
			return;
		}
		AudienceMenu.open(player, c, r, parts[0].equals("lord") ? AudienceMenu.Where.SEAT : AudienceMenu.Where.HALL);
	}

	private static void safely(String what, Runnable task) {
		try {
			task.run();
		} catch (RuntimeException e) {
			LOG.error("Ticking {} failed", what, e);
		}
	}

	/** A rival's saboteurs got to your warehouses: your Compagnie's trade jobs are harder until that day. */
	static void sabotaged(Court c, long until) {
		c.sabotagedUntil = Math.max(c.sabotagedUntil, until);
		Courts.changed();
	}

	static void bar(ServerPlayer player, String text, ChatFormatting color) {
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
	}

	public static void nextTick(Runnable task) {
		NEXT_TICK.add(task);
	}

	public static void later(int delayTicks, Runnable task) {
		LATER.add(new Delayed(ticks + delayTicks, task));
	}

	private record Delayed(int at, Runnable task) {
	}

	/** Our part of De Compagnie: the tab on the desk, the dawn, and what sabotage does to the company's odds. */
	private static final class Extension implements CompagnieHooks.Extension {
		@Override
		public String id() {
			return ID;
		}

		@Override
		public ItemStack tab(UUID owner) {
			Court c = Courts.get(owner);
			List<Component> lore = new ArrayList<>();
			if (c == null) {
				lore.add(Ui.t("Rival houses: trade, pacts, feasts,", ChatFormatting.GRAY));
				lore.add(Ui.t("spies, and war. Take your seat.", ChatFormatting.GRAY));
			} else {
				lore.add(Ui.t(c.reputation(), ChatFormatting.LIGHT_PURPLE));
				lore.add(Ui.t(c.rivals.size() + " houses · " + c.regiments + " regiments" + (c.atWar() ? " · AT WAR" : ""),
					c.atWar() ? ChatFormatting.RED : ChatFormatting.GRAY));
				if (!c.dispatches.isEmpty()) {
					lore.add(Ui.t("📜 " + c.dispatches.size() + " dispatches waiting!", ChatFormatting.RED));
				}
			}
			ItemStack stack = Ui.icon(Ui.item("white_banner", Items.PAPER), Ui.t("De Hooghe Heeren", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), lore);
			return c != null && !c.dispatches.isEmpty() ? Ui.glow(stack) : stack;
		}

		@Override
		public void open(ServerPlayer player) {
			Court c = Courts.of(player.getUUID(), player.getName().getString());
			CourtMenu.open(player, c, c.dispatches.isEmpty() ? CourtMenu.Page.COURT : CourtMenu.Page.DISPATCHES);
		}

		@Override
		public void dawn(UUID owner, String ownerName, long day, Random random) {
			Courts.dawn(owner, day, random);
		}

		@Override
		public double odds(UUID owner, String category, boolean sea, long day) {
			Court c = Courts.get(owner);
			return c != null && category.equals("trade") && c.sabotagedUntil >= day ? -0.10 : 0;
		}
	}
}
