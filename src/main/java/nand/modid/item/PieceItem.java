package nand.modid.item;

import nand.modid.chess.core.Piece;
import nand.modid.game.MinecraftChessManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class PieceItem extends Item {
    private final Piece.PieceKind kind;

    public PieceItem(Piece.PieceKind kind, Settings settings) {
        super(settings.maxCount(1));
        this.kind = kind;
    }

    public Piece.PieceKind getKind() {
        return kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            if (user.isSneaking()) {
                // Shift+우클릭 시 포켓에서 제거
                MinecraftChessManager.getInstance().removePieceFromPocket(serverPlayer, kind);
                return TypedActionResult.success(itemStack);
            } else {
                // 일반 우클릭 시 포켓에 추가
                MinecraftChessManager.getInstance().addPieceToPocket(serverPlayer, kind);
                if (!user.getAbilities().creativeMode) {
                    itemStack.decrement(1);
                }
                return TypedActionResult.success(itemStack);
            }
        }
        return TypedActionResult.consume(itemStack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
        tooltip.add(Text.literal("Score: " + kind.score()).formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Use: Add to pocket").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Shift + Use: Remove from pocket").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
