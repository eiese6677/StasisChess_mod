package nand.modid;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DashPayload() implements CustomPayload {
    public static final Id<DashPayload> ID = new Id<>(Identifier.of("stasischess", "dash_packet"));
    // 데이터가 비어있어도 코덱은 정의해야 합니다.
    public static final PacketCodec<RegistryByteBuf, DashPayload> CODEC = PacketCodec.unit(new DashPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}