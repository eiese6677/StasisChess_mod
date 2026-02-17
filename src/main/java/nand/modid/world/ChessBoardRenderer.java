package nand.modid.world;

import nand.modid.chess.Board;
import nand.modid.chess.Piece;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class ChessBoardRenderer {
    public static void render(Board board, ServerWorld world, BlockPos origin) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {

                Piece piece = board.get(x, z);
                BlockPos pos = origin.add(x, 1, z); // 체스판 위 1칸

                // 기존 엔티티 제거 (리렌더링용)
                world.getEntitiesByClass(
                        ArmorStandEntity.class,
                        new Box(pos),
                        e -> e.hasCustomName()
                ).forEach(Entity::kill);

                if (piece == null) continue;

                spawnPieceEntity(piece, world, pos);
            }
        }
    }

    private static void spawnPieceEntity(Piece piece, ServerWorld world, BlockPos pos) {
        ArmorStandEntity stand = new ArmorStandEntity(world,
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setCustomNameVisible(false);

        // 말 타입에 따라 아이템 다르게
        ItemStack item = getItemForPiece(piece);
        stand.equipStack(EquipmentSlot.HEAD, item);

        world.spawnEntity(stand);
    }

    private static ItemStack getItemForPiece(Piece piece) {
        String id = piece.getId();

        if (id.equals("king")) {
            return piece.white
                    ? new ItemStack(Items.DIAMOND_BLOCK)
                    : new ItemStack(Items.COAL_BLOCK);
        }

        if (id.equals("queen")) {
            return new ItemStack(Items.GOLD_BLOCK);
        }

        return new ItemStack(Items.STONE);
    }
}