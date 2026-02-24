package nand.modid;

import nand.modid.render.ExternalTextureManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.Identifier;

public class StasisChessClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StasisChess.PerspectivePacketPayload.ID, (payload, context) -> {
            int perspectiveIndex = payload.perspective();
            context.client().execute(() -> {
                Perspective p = switch (perspectiveIndex) {
                    case 1 -> Perspective.THIRD_PERSON_BACK;
                    case 2 -> Perspective.THIRD_PERSON_FRONT;
                    default -> Perspective.FIRST_PERSON;
                };
                context.client().options.setPerspective(p);
            });
        });

        // 서버로부터 리로드 신호를 받았을 때
        ClientPlayNetworking.registerGlobalReceiver(StasisChess.ReloadPacketPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // 중요: 리소스를 새로고침하여 모델이 새 텍스처를 참조하도록 함
                context.client().reloadResources();
            });
        });
    }
}
