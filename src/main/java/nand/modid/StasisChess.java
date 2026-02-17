package nand.modid;
import nand.modid.registry.ModItems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StasisChess implements ModInitializer {
	public static final String MOD_ID = "stasischess";
		// 공중 대쉬 여부 저장
		private static final Set<UUID> JUMP_DASHED_PLAYERS = new HashSet<>();
		// 쿨타임 저장 (UUID, 마지막 사용 시간-밀리초)
		private static final Map<UUID, Long> COOLDOWN_MAP = new HashMap<>();
		// 쿨타임 설정 (예: 2000 = 2초)
		private static final long DASH_COOLDOWN_MS = 2000;

		@Override
		public void onInitialize() {
			PayloadTypeRegistry.playC2S().register(DashPayload.ID, DashPayload.CODEC);

			ServerPlayNetworking.registerGlobalReceiver(DashPayload.ID, (payload, context) -> {
				context.server().execute(() -> {
					var player = context.player();
					UUID uuid = player.getUuid();
					long currentTime = System.currentTimeMillis();

					// 1. 쿨타임 체크
					if (COOLDOWN_MAP.containsKey(uuid)) {
						long lastUsed = COOLDOWN_MAP.get(uuid);
						if (currentTime - lastUsed < DASH_COOLDOWN_MS) {
							// 아직 쿨타임 중이면 무시
							return;
						}
					}

					// 2. 땅에 있으면 공중 대쉬 기록 초기화
					if (player.isOnGround()) {
						JUMP_DASHED_PLAYERS.remove(uuid);
					}

					// 3. 공중 대쉬 제한 체크
					if (!player.isOnGround() && JUMP_DASHED_PLAYERS.contains(uuid)) {
						return;
					}

					// --- 대쉬 실행 성공 시점 ---

					// 4. 물리 적용
					Vec3d look = player.getRotationVec(1.0F);
					player.addVelocity(look.x * 1.5, 0.3, look.z * 1.5);
					player.velocityModified = true;

					// 5. 상태 업데이트
					COOLDOWN_MAP.put(uuid, currentTime); // 쿨타임 시작
					if (!player.isOnGround()) {
						JUMP_DASHED_PLAYERS.add(uuid); // 공중 사용 기록
					}
				});
			});

			ModItems.register();
		}
	}