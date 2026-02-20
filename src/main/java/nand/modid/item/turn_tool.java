package nand.modid.item;

import nand.modid.game.MinecraftChessManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class turn_tool extends Item {
    public turn_tool(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            if (player != null && player.isSneaking()) {
                MinecraftChessManager.getInstance().showTurnActions(player);
            } else {
                MinecraftChessManager.getInstance().endTurn(player);
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            if (player.isSneaking()) {
                MinecraftChessManager.getInstance().showTurnActions(player);
            } else {
                MinecraftChessManager.getInstance().endTurn(player);
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }
}
