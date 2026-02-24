package nand.modid.mixin.client;

import nand.modid.render.ExternalTextureManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(ResourcePackManager.class)
public class ResourcePackManagerMixin {
    @Shadow @Final @Mutable private Set<ResourcePackProvider> providers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addExternalProvider(CallbackInfo ci) {
        // 일부 환경에서 이 Set이 불변일 수 있으므로 가변 Set으로 변환 후 추가합니다.
        Set<ResourcePackProvider> mutableProviders = new HashSet<>(this.providers);
        mutableProviders.add(profileAdder -> ExternalTextureManager.registerPack(profileAdder));
        this.providers = mutableProviders;
    }
}
