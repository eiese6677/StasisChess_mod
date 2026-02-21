package nand.modid;
import nand.modid.registry.ModItems;
import nand.modid.game.MinecraftChessManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class StasisChess implements ModInitializer {
	public static final String MOD_ID = "stasischess";
	
	public record PerspectivePacketPayload(int perspective) implements CustomPayload {
		public static final Id<PerspectivePacketPayload> ID = new Id<>(Identifier.of(MOD_ID, "set_perspective"));
		public static final PacketCodec<RegistryByteBuf, PerspectivePacketPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, PerspectivePacketPayload::perspective,
			PerspectivePacketPayload::new
		);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	@Override
	public void onInitialize() {
		ModItems.register();

		// Register the payload type
		PayloadTypeRegistry.playS2C().register(PerspectivePacketPayload.ID, PerspectivePacketPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PerspectivePacketPayload.ID, PerspectivePacketPayload.CODEC);

			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
				dispatcher.register(CommandManager.literal("chess")
					.then(CommandManager.literal("reset")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayer();
							if (player != null) {
								MinecraftChessManager.getInstance().resetGame(player);
							}
							return 1;
						})
					)
				);
			});

			ServerTickEvents.END_SERVER_TICK.register(server -> {
				MinecraftChessManager.getInstance().tick(server);
			});
		}
	}