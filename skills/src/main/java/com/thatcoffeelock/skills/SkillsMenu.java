package com.thatcoffeelock.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * /skills: a 6-row chest screen drawn on the server, so vanilla clients can use it. The front page shows every
 * skill; clicking one opens its perk page, where each perk has five rank panes to click.
 */
final class SkillsMenu extends ChestMenu {
	private static final int SIZE = 54;
	private static final int[] SKILL_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33, 34};
	private static final int[] PERK_ROWS = {2, 3, 4};

	private final ServerPlayer viewer;
	private final SimpleContainer box;
	private final Map<Integer, Runnable> actions = new HashMap<>();
	private @Nullable Skill page;

	private SkillsMenu(int syncId, ServerPlayer viewer, @Nullable Skill page, SimpleContainer box) {
		super(MenuType.GENERIC_9x6, syncId, viewer.getInventory(), box, 6);
		this.viewer = viewer;
		this.box = box;
		this.page = page;
		render();
	}

	static void open(ServerPlayer player, @Nullable Skill page) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SkillsMenu(id, player, page, new SimpleContainer(SIZE)),
			Component.literal("✦ Skills ✦").withStyle(ChatFormatting.DARK_PURPLE)));
	}

	// ---------------------------------------------------------------- drawing

	private static MutableComponent text(String text, ChatFormatting... formats) {
		return Component.literal(text).withStyle(style -> style.withItalic(false)).withStyle(formats);
	}

	private static ItemStack icon(String item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(Skills.item(item));
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static ItemStack glow(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private void button(int slot, ItemStack icon, @Nullable Runnable action) {
		box.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	private void render() {
		actions.clear();
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, ItemStack.EMPTY);
		}
		if (page == null) {
			renderOverview();
		} else {
			renderSkill(page);
		}
		for (int i = 0; i < SIZE; i++) {
			if (box.getItem(i).isEmpty()) {
				box.setItem(i, icon("black_stained_glass_pane", Component.literal(" "), List.of()));
			}
		}
	}

	private void renderOverview() {
		SkillsStore.Profile profile = SkillsStore.of(viewer);
		button(4, glow(icon("nether_star", text(viewer.getName().getString(), ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			text("Total level: " + profile.totalLevel() + " / " + Skill.values().length * Skill.MAX_LEVEL, ChatFormatting.YELLOW),
			Component.empty(),
			text("Use a skill to level it up.", ChatFormatting.GRAY),
			text("Every " + Skill.LEVELS_PER_POINT + " levels: 1 perk point for that skill.", ChatFormatting.GRAY)))), null);
		Skill[] skills = Skill.values();
		for (int i = 0; i < skills.length; i++) {
			Skill skill = skills[i];
			button(SKILL_SLOTS[i], skillIcon(profile, skill, true), () -> {
				click();
				page = skill;
				render();
			});
		}
		button(40, icon("book", text("How it works", ChatFormatting.YELLOW), List.of(
			text("Skills level from 0 to 100 by using them.", ChatFormatting.GRAY),
			text("Each level gives a small passive bonus.", ChatFormatting.GRAY),
			text("Every 10 levels you earn a perk point", ChatFormatting.GRAY),
			text("for that skill: 10 points at level 100.", ChatFormatting.GRAY),
			text("Each skill has 3 perks of 5 ranks, so you", ChatFormatting.GRAY),
			text("can't have them all. Choose wisely.", ChatFormatting.GRAY),
			Component.empty(),
			text("Player-placed ores, logs and dirt give no XP.", ChatFormatting.DARK_GRAY))), null);
	}

	private ItemStack skillIcon(SkillsStore.Profile profile, Skill skill, boolean clickable) {
		double xp = profile.xp(skill);
		int level = profile.level(skill);
		List<Component> lore = new ArrayList<>();
		lore.add(text("Level " + level + " / " + Skill.MAX_LEVEL, ChatFormatting.WHITE, ChatFormatting.BOLD));
		if (level < Skill.MAX_LEVEL) {
			double into = xp - Skill.totalFor(level);
			double need = Skill.xpForLevel(level + 1);
			lore.add(text(Skills.bar(Skill.progress(xp), 20) + " ", ChatFormatting.GREEN)
				.append(text((int) into + " / " + (int) Math.ceil(need), ChatFormatting.GRAY)));
		} else {
			lore.add(text("MASTERED", ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
		lore.add(Component.empty());
		lore.add(text("Passive: ", ChatFormatting.GRAY).append(text(skill.passiveText(level), ChatFormatting.AQUA)));
		lore.add(text("XP from: ", ChatFormatting.GRAY).append(text(skill.xpFrom, ChatFormatting.DARK_GRAY)));
		int free = profile.pointsFree(skill);
		lore.add(text("Perk points: " + free + " free, " + profile.pointsSpent(skill) + " spent",
			free > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		if (clickable) {
			lore.add(Component.empty());
			lore.add(text("Click to see perks", ChatFormatting.YELLOW));
		}
		ItemStack icon = icon(skill.icon, text(skill.title, skill.color, ChatFormatting.BOLD), lore);
		return free > 0 ? glow(icon) : icon;
	}

	private void renderSkill(Skill skill) {
		SkillsStore.Profile profile = SkillsStore.of(viewer);
		button(4, skillIcon(profile, skill, false), null);
		List<Perk> perks = Perk.of(skill);
		int free = profile.pointsFree(skill);
		for (int p = 0; p < perks.size() && p < PERK_ROWS.length; p++) {
			Perk perk = perks.get(p);
			int row = PERK_ROWS[p] * 9;
			int rank = profile.rank(perk);
			List<Component> lore = new ArrayList<>();
			lore.add(text("Rank " + rank + " / " + Perk.MAX_RANK, ChatFormatting.WHITE));
			lore.add(rank > 0 ? text("Now: " + perk.describe(rank), ChatFormatting.AQUA) : text("Not learned yet.", ChatFormatting.DARK_GRAY));
			if (rank < Perk.MAX_RANK) {
				lore.add(text("Next: " + perk.describe(rank + 1), ChatFormatting.GRAY));
				lore.add(Component.empty());
				lore.add(free > 0 ? text("Click to learn (1 point)", ChatFormatting.YELLOW) : text("Needs a perk point", ChatFormatting.RED));
			}
			Runnable learn = () -> learn(skill, perk);
			ItemStack perkIcon = icon(perk.icon, text(perk.title, skill.color, ChatFormatting.BOLD), lore);
			button(row + 1, rank > 0 ? glow(perkIcon) : perkIcon, rank < Perk.MAX_RANK ? learn : null);
			for (int r = 1; r <= Perk.MAX_RANK; r++) {
				String pane;
				ChatFormatting color;
				String state;
				if (r <= rank) {
					pane = "lime_stained_glass_pane";
					color = ChatFormatting.GREEN;
					state = "Learned";
				} else if (r == rank + 1) {
					pane = free > 0 ? "yellow_stained_glass_pane" : "red_stained_glass_pane";
					color = free > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED;
					state = free > 0 ? "Click to learn" : "Needs a perk point";
				} else {
					pane = "gray_stained_glass_pane";
					color = ChatFormatting.DARK_GRAY;
					state = "Locked";
				}
				ItemStack paneIcon = icon(pane, text(perk.title + " " + roman(r), color), List.of(
					text(perk.describe(r), ChatFormatting.GRAY), text(state, color)));
				button(row + 2 + r, paneIcon, r == rank + 1 ? learn : null);
			}
		}

		button(45, icon("arrow", text("Back", ChatFormatting.YELLOW), List.of(text("All skills", ChatFormatting.GRAY))), () -> {
			click();
			page = null;
			render();
		});
		int cost = SkillsConfig.get().respecCostLevels;
		button(53, icon("grindstone", text("Forget perks", ChatFormatting.RED), List.of(
			text("Refunds every " + skill.title + " perk point.", ChatFormatting.GRAY),
			text(cost > 0 ? "Costs " + cost + " experience levels." : "Free.", ChatFormatting.GRAY),
			Component.empty(),
			text("Shift-click to confirm", ChatFormatting.YELLOW))), null);
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}

	// ---------------------------------------------------------------- actions

	private void learn(Skill skill, Perk perk) {
		SkillsStore.Profile profile = SkillsStore.of(viewer);
		int rank = profile.rank(perk);
		if (rank >= Perk.MAX_RANK || profile.pointsFree(skill) <= 0) {
			nope();
			return;
		}
		profile.perks.put(perk.id(), rank + 1);
		SkillsStore.changed();
		Boosts.refresh(viewer);
		Cmd.sound(viewer, "minecraft:block.enchantment_table.use", 0.6f, 1.3f);
		viewer.sendSystemMessage(text("Learned " + perk.title + " " + roman(rank + 1) + ": ", ChatFormatting.GREEN)
			.append(text(perk.describe(rank + 1), ChatFormatting.AQUA)));
		render();
	}

	private void respec(Skill skill) {
		SkillsStore.Profile profile = SkillsStore.of(viewer);
		if (profile.pointsSpent(skill) == 0) {
			nope();
			return;
		}
		int cost = SkillsConfig.get().respecCostLevels;
		if (!viewer.isCreative() && viewer.experienceLevel < cost) {
			nope();
			viewer.sendSystemMessage(text("You need " + cost + " experience levels to forget your perks.", ChatFormatting.RED));
			return;
		}
		if (!viewer.isCreative() && cost > 0) {
			viewer.giveExperienceLevels(-cost);
		}
		for (Perk perk : Perk.of(skill)) {
			profile.perks.remove(perk.id());
		}
		SkillsStore.changed();
		Boosts.refresh(viewer);
		Cmd.sound(viewer, "minecraft:block.grindstone.use", 0.6f, 1.0f);
		viewer.sendSystemMessage(text("Your " + skill.title + " perk points are free to spend again.", ChatFormatting.YELLOW));
		render();
	}

	private void click() {
		Cmd.sound(viewer, "minecraft:ui.button.click", 0.4f, 1.0f);
	}

	private void nope() {
		Cmd.sound(viewer, "minecraft:entity.villager.no", 0.6f, 1.0f);
	}

	// ---------------------------------------------------------------- chest plumbing

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			if (slotId == 53 && page != null && clickType == ContainerInput.QUICK_MOVE) {
				respec(page);
			} else {
				Runnable action = actions.get(slotId);
				if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
					action.run();
				}
			}
			sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != box && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.container != box && super.canDragTo(slot);
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
