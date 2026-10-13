package com.thatcoffeelock.colonycraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
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

/**
 * The tavern's bar: right-click a barkeep and buy a bottle off the shelf. Young spirits on the top row, aged ones on
 * the bottom. Guests pay {@link Street#BAR_MARKUP} times the Market price and the colony's owner gets
 * {@link Street#OWNER_CUT} of it; the owner drinks at cost (a quarter over the Market price, so nobody gets rich
 * buying from their own bar and selling it on).
 */
final class TavernMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final String[] DRINKS = {"genever", "whisky", "rum", "brandy"};
	private static final double OWNER_MARKUP = 1.25;

	@FunctionalInterface
	private interface Action {
		void run();
	}

	private final ServerPlayer viewer;
	private final Colony.Building tavern;
	private final SimpleContainer box;
	private final Map<Integer, Action> actions = new HashMap<>();

	static void open(ServerPlayer player, Colony.Building tavern) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TavernMenu(id, player, tavern, new SimpleContainer(SIZE)),
			Component.literal(tavern.colony.name + " · Tavern").withStyle(ChatFormatting.DARK_GREEN)));
	}

	private TavernMenu(int syncId, ServerPlayer viewer, Colony.Building tavern, SimpleContainer box) {
		super(MenuType.GENERIC_9x3, syncId, viewer.getInventory(), box, 3);
		this.viewer = viewer;
		this.tavern = tavern;
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
		return Blueprints.text(text, formats);
	}

	private boolean isOwner() {
		return tavern.colony.isOwner(viewer.getUUID());
	}

	/** What a bottle of this costs the viewer here, in cents. */
	static long price(ItemStack bottle, boolean owner) {
		return Math.round(Bank.sellValue(bottle.copyWithCount(1)) * (owner ? OWNER_MARKUP : Street.BAR_MARKUP));
	}

	private void render() {
		actions.clear();
		ItemStack filler = icon(TownHallMenu.item("minecraft:brown_stained_glass_pane", Items.GLASS_PANE), Component.literal(" "), List.of());
		for (int i = 0; i < SIZE; i++) {
			box.setItem(i, filler.copy());
		}
		boolean owner = isOwner();
		for (int row = 0; row < 2; row++) {
			for (int i = 0; i < DRINKS.length; i++) {
				String what = DRINKS[i] + (row == 1 ? ":aged" : "");
				ItemStack bottle = ProostLink.item(what, 1);
				if (bottle.isEmpty()) {
					continue;
				}
				long cents = price(bottle, owner);
				ItemStack shown = bottle.copy();
				shown.set(DataComponents.LORE, new ItemLore(List.of(
					Blueprints.text("Price: ", ChatFormatting.GRAY).append(Bank.text(cents).withStyle(style -> style.withItalic(false))),
					t(owner ? "On the house, nearly: you pay cost." : "Click to buy a bottle.", ChatFormatting.YELLOW))));
				int slot = row == 0 ? 1 + i * 2 : 19 + i * 2;
				box.setItem(slot, shown);
				actions.put(slot, () -> buy(bottle, cents, owner));
			}
		}
		box.setItem(13, icon(Items.HONEY_BOTTLE, t(tavern.colony.name + " Tavern", ChatFormatting.GOLD, ChatFormatting.BOLD),
			List.of(t("Young spirits on top, aged ones below.", ChatFormatting.GRAY),
				t("Your balance: " + Bank.format(Bank.balance(viewer)), ChatFormatting.GRAY),
				t("Drink responsibly. Or at least upright.", ChatFormatting.DARK_GRAY))));
	}

	private void buy(ItemStack bottle, long cents, boolean owner) {
		if (!Bank.charge(viewer.getUUID(), cents)) {
			nope("You can't afford that. Have some water.");
			return;
		}
		if (!owner) {
			long cut = Math.round(cents * Street.OWNER_CUT);
			Bank.credit(tavern.colony.owner, tavern.colony.ownerName, cut);
			ServerPlayer boss = Colonies.owner(tavern.colony);
			if (boss != null) {
				boss.sendSystemMessage(Component.literal("[" + tavern.colony.name + " Tavern] " + viewer.getName().getString() + " bought a "
					+ bottle.getHoverName().getString() + ". Your cut: " + Bank.format(cut) + ".").withStyle(ChatFormatting.GRAY));
			}
		}
		viewer.getInventory().placeItemBackInInventory(bottle.copy(), Prediction.SERVER_ONLY);
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:block.glass.place", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.4f);
		viewer.sendSystemMessage(Component.literal("The barkeep slides a " + bottle.getHoverName().getString() + " across the counter. Proost!")
			.withStyle(ChatFormatting.GOLD));
	}

	private void nope(String why) {
		Cmd.sound((ServerLevel) viewer.level(), "minecraft:entity.villager.no", viewer.getX(), viewer.getY(), viewer.getZ(), 0.6f, 1.0f);
		viewer.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			Action action = actions.get(slotId);
			if (action != null && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_MOVE)) {
				action.run();
				render();
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
		net.minecraft.core.BlockPos bar = tavern.world(BuildingType.TAVERN_BAR[0]);
		return tavern.colony.buildings.contains(tavern) && player.distanceToSqr(bar.getX() + 0.5, bar.getY(), bar.getZ() + 0.5) < 100;
	}

	@Override
	public void removed(Player player) {
		box.clearContent();
		super.removed(player);
	}
}
