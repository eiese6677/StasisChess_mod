package nand.modid.registry;

import nand.modid.StasisChess;
import nand.modid.chess.core.Piece;
import nand.modid.item.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

public class ModItems {

    // 아이템 그룹 키 정의
    public static final RegistryKey<net.minecraft.item.ItemGroup> STASIS_CHESS_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(StasisChess.MOD_ID, "stasischess_group"));

    // 아이템 인스턴스 생성
    public static final Item DROP_TOOL = new drop_tool(new Item.Settings());
    public static final Item START_TOOL = new start_tool(new Item.Settings());
    public static final Item MOVE_TOOL = new move_tool(new Item.Settings());
    public static final Item TURN_TOOL = new turn_tool(new Item.Settings());

    public static void register() {
        // 아이템 그룹 등록
        Registry.register(Registries.ITEM_GROUP, STASIS_CHESS_GROUP_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(START_TOOL))
                .displayName(Text.literal("Stasis Chess"))
                .build());

        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, "drop_tool"),
                DROP_TOOL
        );
        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, "start_tool"),
                START_TOOL
        );
        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, "move_tool"),
                MOVE_TOOL
        );
        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, "turn_tool"),
                TURN_TOOL
        );
        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, "start_exp_tool"),
                new start_exp_tool(new Item.Settings())
        );

        // 기물 아이템 등록
        for (Piece.PieceKind kind : Piece.PieceKind.values()) {
            registerPiece(kind);
        }

        // 전용 아이템 그룹에 추가
        ItemGroupEvents.modifyEntriesEvent(STASIS_CHESS_GROUP_KEY).register(content -> {
            content.add(START_TOOL);
            content.add(getPieceItem(Piece.PieceKind.EXPERIMENT)); // 예시 아이콘용
            content.add(DROP_TOOL);
            content.add(MOVE_TOOL);
            content.add(TURN_TOOL);
            
            // 모든 기물 아이템 추가 (중복 없이)
            for (Piece.PieceKind kind : Piece.PieceKind.values()) {
                content.add(getPieceItem(kind));
            }
        });
    }

    private static void registerPiece(Piece.PieceKind kind) {
        String id = kind.scriptName();
        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, id),
                new PieceItem(kind, new Item.Settings())
        );
    }

    public static Item getPieceItem(Piece.PieceKind kind) {
        String id = kind.scriptName();
        return Registries.ITEM.get(Identifier.of(StasisChess.MOD_ID, id));
    }
}
