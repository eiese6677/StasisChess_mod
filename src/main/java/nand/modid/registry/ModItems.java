package nand.modid.registry;

import nand.modid.StasisChess;
import nand.modid.item.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

public class ModItems {

    // 아이템 인스턴스 생성
    public static final Item DROP_TOOL = new drop_tool(new Item.Settings());
    public static void register() {
        Registry.register(
                Registries.ITEM,
                Identifier.of(StasisChess.MOD_ID, "drop_tool"),
                DROP_TOOL
        );

        // 아이템 그룹에 추가
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(content -> {
            content.add(DROP_TOOL);
        });
    }
}
