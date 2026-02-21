package nand.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.Perspective;

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
    }
}
