package nand.modid.item;

import nand.modid.game.MinecraftChessManager;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class start_tool extends Item {
    public start_tool(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user.isSneaking()) {
            MinecraftChessManager.getInstance().resetGame((ServerPlayerEntity) user);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            if (player != null && player.isSneaking()) {
                MinecraftChessManager.getInstance().resetGame(player);
                return ActionResult.SUCCESS;
            }

            BlockPos basePos = context.getBlockPos().up();

            // 1. Reset existing game (and restore blocks) before saving the new area
            MinecraftChessManager.getInstance().resetGame(player);

            // 2. Save the area before modifying it
            MinecraftChessManager.getInstance().saveArea((ServerWorld) world, basePos, -3, 18, -1, 1, -3, 18);

            // 3. Create a 22x22 platform (from -3 to 18 in both x and z)
            // The board will be 16x16 (8*2) from (0,0) to (15,15)
            for (int x = -3; x <= 18; x++) {
                for (int z = -3; z <= 18; z++) {
                    // Base platform (one block below)
                    world.setBlockState(basePos.add(x, -1, z), Blocks.POLISHED_ANDESITE.getDefaultState());

                    // Perimeter fences
                    if (x == -3 || x == 18 || z == -3 || z == 18) {
                        world.setBlockState(basePos.add(x, 0, z), Blocks.DARK_OAK_FENCE.getDefaultState());
                    }
                }
            }

            // 2. Corner Lanterns
            world.setBlockState(basePos.add(-3, 1, -3), Blocks.LANTERN.getDefaultState());
            world.setBlockState(basePos.add(18, 1, -3), Blocks.LANTERN.getDefaultState());
            world.setBlockState(basePos.add(-3, 1, 18), Blocks.LANTERN.getDefaultState());
            world.setBlockState(basePos.add(18, 1, 18), Blocks.LANTERN.getDefaultState());

            // 3. Create the 8x8 chess board (each square is 2x2, 0 to 15)
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    // Standard chess board: a1 (0,0) is dark.
                    boolean isWhite = (x + z) % 2 != 0;
                    var blockState = isWhite ? Blocks.QUARTZ_BLOCK.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState();
                    
                    for (int dx = 0; dx < 2; dx++) {
                        for (int dz = 0; dz < 2; dz++) {
                            world.setBlockState(basePos.add(x * 2 + dx, 0, z * 2 + dz), blockState);
                        }
                    }
                }
            }

            if (context.getPlayer() != null) {
                context.getPlayer().sendMessage(Text.literal("Chess board and platform created with 2x2 squares!"), false);
                nand.modid.game.MinecraftChessManager.getInstance().startNewGame(basePos, (net.minecraft.server.network.ServerPlayerEntity) context.getPlayer());
            }
        }
        return ActionResult.success(world.isClient);
    }
}
