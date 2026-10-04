package com.thatcoffeelock.fuckillagers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.thatcoffeelock.market.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The Bounty Station: sell trophies on the left, contracts on the right. With no contract running you pick a
 * difficulty; with one running you see where the target is, get a new poster, or abandon it.
 */
final class StationMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int[] TIER_SLOTS = {14, 15, 16};

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final ServerLevel level;
	private final BlockPos station;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();
	private boolean confirmAbandon;

	static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StationMenu(id, player, level, pos, new SimpleContainer(SIZE)),
			Component.literal("Bounty Station").withStyle(ChatFormatting.DARK_RED)));
	}

	private StationMenu(int syncId, ServerPlayer viewer, ServerLevel level, BlockPos station, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.level = level;
		this.station = station;
		this.box = box;
		render();
	}

	private static ItemStack icon(Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static Component t(String text, ChatFormatting... formats) {
		return Trophies.text(text, formats);
	}

	private void button(int slot, ItemStack icon, @Nullable Action action) {
		box.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	private void render() {
		actions.clear();
		ItemStack filler = icon(Items.GLASS_PANE, Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		button(4, icon(Items.FLETCHING_TABLE, t("Bounty Station", ChatFormatting.GOLD, ChatFormatting.BOLD), List.of(
			t("Illager fingers: " + Money.format(Bounties.fingerPrice()) + " each.", ChatFormatting.GRAY),
			t("Contracts: find a named illager 1000-2000 blocks away,", ChatFormatting.GRAY),
			t("kill it, bring back its skull.", ChatFormatting.GRAY))), null);

		// selling
		int fingers = Bounties.count(viewer, false);
		int skulls = Bounties.count(viewer, true);
		long value = fingers * Bounties.fingerPrice() + Bounties.skullValue(viewer);
		List<Component> sell = new ArrayList<>();
		sell.add(t("Fingers: " + fingers + " × " + Money.format(Bounties.fingerPrice()), ChatFormatting.GRAY));
		sell.add(t("Skulls: " + skulls + (skulls > 0 ? " (" + Money.format(Bounties.skullValue(viewer)) + ")" : ""), ChatFormatting.GRAY));
		sell.add(t(value > 0 ? "Click to sell all for " + Money.format(value) : "Nothing to sell. Go remove some fingers.", value > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
		button(11, icon(Items.BONE, t("Sell trophies", ChatFormatting.GOLD, ChatFormatting.BOLD), sell), value > 0 ? () -> {
			Bounties.sellAll(viewer);
			Cmd.sound(level, "minecraft:entity.experience_orb.pickup", viewer.getX(), viewer.getY(), viewer.getZ(), 0.7f, 1.2f);
		} : null);

		// contracts
		Contract open = Bounties.openContract(viewer.getUUID());
		if (open == null) {
			Tier[] tiers = Tier.values();
			for (int i = 0; i < tiers.length; i++) {
				Tier tier = tiers[i];
				List<Component> lore = new ArrayList<>();
				lore.add(t("Reward: " + Money.format(Bounties.reward(tier)), ChatFormatting.GOLD));
				lore.add(t("Target hides in " + tier.sites.get(0).what + " or " + tier.sites.get(1).what + ".", ChatFormatting.GRAY));
				for (Tier.Site site : tier.sites) {
					lore.add(t("· " + site.blurb, ChatFormatting.DARK_GRAY));
				}
				lore.add(t("Click to accept.", ChatFormatting.YELLOW));
				Item paper = i == 0 ? Items.PAPER : i == 1 ? Items.MAP : Items.WRITABLE_BOOK;
				button(TIER_SLOTS[i], icon(paper, t(tier.label + " contract", tier.color, ChatFormatting.BOLD), lore), () -> {
					Contract c = Bounties.accept(viewer, level, station, tier);
					if (c != null) {
						Cmd.sound(level, "minecraft:item.book.page_turn", viewer.getX(), viewer.getY(), viewer.getZ(), 1f, 0.9f);
						FuckIllagersMod.nextTick(viewer::closeContainer);
					}
				});
			}
		} else {
			double dx = open.x - viewer.getX();
			double dz = open.z - viewer.getZ();
			long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
			button(15, icon(Items.FILLED_MAP, t("Wanted: " + open.target, ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t(open.tier().label + " contract · " + Money.format(Bounties.reward(open.tier())), open.tier().color),
				t("Hiding in " + open.site().what + " near X " + open.x + ", Z " + open.z, ChatFormatting.GRAY),
				t(dist + " blocks " + Bounties.direction(dx, dz) + " from here", ChatFormatting.WHITE),
				t(Contract.ACTIVE.equals(open.state) ? "Someone has found the place already." : "Nobody has been there yet.", ChatFormatting.DARK_GRAY),
				t("Click for a new Wanted Poster.", ChatFormatting.YELLOW))), () -> {
				Trophies.give(viewer, Trophies.poster(open));
				viewer.sendSystemMessage(Component.literal("A fresh poster. Try not to lose this one.").withStyle(ChatFormatting.GRAY));
			});
			button(24, icon(Items.BARRIER, t(confirmAbandon ? "Click again to abandon" : "Abandon contract", ChatFormatting.RED, ChatFormatting.BOLD), List.of(
				t("No reward. You can take a new one right away.", ChatFormatting.GRAY))), () -> {
				if (!confirmAbandon) {
					confirmAbandon = true;
					return;
				}
				confirmAbandon = false;
				Bounties.abandon(viewer);
			});
		}
		button(26, icon(Items.ARROW, t("Close", ChatFormatting.GRAY), List.of()), () -> FuckIllagersMod.nextTick(viewer::closeContainer));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run();
			}
			render();
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
		return Bounties.isStation(level, station) && player.distanceToSqr(station.getX() + 0.5, station.getY() + 0.5, station.getZ() + 0.5) < 64;
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
