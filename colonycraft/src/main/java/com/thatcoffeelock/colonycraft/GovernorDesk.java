package com.thatcoffeelock.colonycraft;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

/**
 * The Governor's Desk: the Governor's Table without the palace. A lectern you buy at any Town Hall and put down
 * anywhere (your house, your ship, your Company House) that opens the same table for its owner. Without a seat of
 * government it still shows every colony, its money and opens their Town Halls by letter; give one desk the charter
 * (or build the palace) and it's your Headquarters: linking, convoys, roles and governors.
 */
final class GovernorDesk {
	static final String TAG = "colonycraft_desk";
	static final long PRICE = Bank.cents(750);

	private GovernorDesk() {
	}

	static ItemStack item() {
		ItemStack stack = new ItemStack(Items.LECTERN);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Blueprints.text("Governor's Desk", ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Blueprints.text("Put it down anywhere and right-click it:", ChatFormatting.GRAY),
			Blueprints.text("the Governor's Table, without the palace.", ChatFormatting.GRAY),
			Blueprints.text("Every colony, its money, its Town Hall by letter.", ChatFormatting.DARK_GRAY))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	static boolean isItem(ItemStack stack) {
		if (!stack.is(Items.LECTERN)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(TAG);
	}

	static String key(Level level, BlockPos pos) {
		return Colonies.dim(level) + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	/** Who owns the desk here, or null. */
	static Network.Net ownerAt(Level level, BlockPos pos) {
		String k = key(level, pos);
		for (Network.Net n : Network.ALL.values()) {
			if (n.desks.contains(k)) {
				return n;
			}
		}
		return null;
	}

	/**
	 * Right-click with the desk in hand: put it down against the face clicked (we place it ourselves, so the tag is
	 * remembered). Right-click on a placed desk: its owner gets the table.
	 */
	static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		BlockPos clicked = hit.getBlockPos();
		Network.Net owner = ownerAt(level, clicked);
		if (owner != null) {
			if (!level.getBlockState(clicked).is(Blocks.LECTERN)) {
				owner.desks.remove(key(level, clicked));
				Network.changed();
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND) {
				if (owner.owner.equals(player.getUUID().toString())) {
					NetworkMenu.open(player, key(level, clicked));
				} else {
					player.sendSystemMessage(Component.literal("Somebody else's Governor's Desk. Not your papers.").withStyle(ChatFormatting.YELLOW));
				}
			}
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!isItem(held)) {
			return InteractionResult.PASS;
		}
		BlockPos at = clicked.relative(hit.getDirection());
		if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).isSolid()) {
			player.sendSystemMessage(Component.literal("The desk needs a free spot on solid ground.").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		String facing = player.getDirection().getOpposite().getSerializedName();
		Cmd.run(level, "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " minecraft:lectern[facing=" + facing + ",has_book=true]");
		if (!level.getBlockState(at).is(Blocks.LECTERN)) {
			return InteractionResult.SUCCESS;
		}
		Network.of(player.getUUID()).desks.add(key(level, at));
		Network.changed();
		if (!player.isCreative()) {
			held.shrink(1);
		}
		Cmd.sound(level, "minecraft:block.wood.place", at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1f, 1f);
		player.sendSystemMessage(Component.literal("Your Governor's Desk is set up. Right-click it for the Governor's Table.").withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS;
	}

	/** A desk is gone. If you governed from it, the charter waits (tier and all) for the next desk you give it to. */
	static void forget(Network.Net owner, String key) {
		owner.desks.remove(key);
		if (key.equals(owner.hqDesk)) {
			owner.hqDesk = "";
			owner.report("Your Governor's Desk is gone: no seat of government until you give another desk the charter.");
		}
		Network.changed();
	}

	/** Breaking a desk gives it back. False cancels vanilla's break (which would drop a plain lectern). */
	static boolean allowBreak(Player player, Level world, BlockPos pos) {
		if (!(world instanceof ServerLevel level)) {
			return true;
		}
		Network.Net owner = ownerAt(level, pos);
		if (owner == null) {
			return true;
		}
		if (!owner.owner.equals(player.getUUID().toString()) && !player.isCreative()) {
			player.sendSystemMessage(Component.literal("That's somebody's Governor's Desk. Hands off.").withStyle(ChatFormatting.RED));
			return false;
		}
		forget(owner, key(level, pos));
		level.removeBlock(pos, false);
		if (!player.isCreative()) {
			ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item());
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}
		return false;
	}
}
