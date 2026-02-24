package nand.modid.render;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class ExternalTextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("StasisChess-TextureLoader");
    private static final String EXTERNAL_TEXTURES_DIR = "mods/stasischess/textures";

    public static void registerPack(Consumer<ResourcePackProfile> profileAdder) {
        Path texturesPath = FabricLoader.getInstance().getGameDir().resolve(EXTERNAL_TEXTURES_DIR);

        if (!Files.exists(texturesPath)) {
            try {
                Files.createDirectories(texturesPath);
            } catch (IOException e) {
                LOGGER.error("Failed to create external textures directory", e);
                return;
            }
        }

        ResourcePackInfo info = new ResourcePackInfo(
                "stasischess_external",
                Text.literal("StasisChess External Textures"),
                ResourcePackSource.BUILTIN,
                Optional.empty()
        );

        ExternalResourcePack pack = new ExternalResourcePack(info, texturesPath);

        ResourcePackProfile profile = ResourcePackProfile.create(
                info,
                new ResourcePackProfile.PackFactory() {
                    @Override
                    public ResourcePack open(ResourcePackInfo info) {
                        return pack;
                    }

                    @Override
                    public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                        return pack;
                    }
                },
                ResourceType.CLIENT_RESOURCES,
                new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, true)
        );

        if (profile != null) {
            profileAdder.accept(profile);
            LOGGER.info("[StasisChess] External texture pack registered as REQUIRED and TOP.");
        }
    }

    private static class ExternalResourcePack implements ResourcePack {
        private final ResourcePackInfo info;
        private final Path root;

        public ExternalResourcePack(ResourcePackInfo info, Path root) {
            this.info = info;
            this.root = root;
        }

        @Override
        public @Nullable InputSupplier<InputStream> open(ResourceType type, Identifier id) {
            if (type == ResourceType.CLIENT_RESOURCES && 
                id.getNamespace().equals("stasischess") && 
                id.getPath().startsWith("textures/item/")) {
                
                String fileName = id.getPath().substring("textures/item/".length());
                Path filePath = root.resolve(fileName);

                if (Files.exists(filePath)) {
                    LOGGER.info("[StasisChess] Loading external texture: {} for ID: {}", fileName, id);
                    return () -> Files.newInputStream(filePath);
                }
            }
            return null;
        }

        @Override
        public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
            if (type == ResourceType.CLIENT_RESOURCES && namespace.equals("stasischess") && prefix.equals("textures/item")) {
                try (var stream = Files.list(root)) {
                    stream.filter(path -> path.toString().endsWith(".png")).forEach(path -> {
                        String name = path.getFileName().toString();
                        Identifier id = Identifier.of("stasischess", "textures/item/" + name);
                        consumer.accept(id, () -> Files.newInputStream(path));
                    });
                } catch (IOException ignored) {}
            }
        }

        @Override
        public Set<String> getNamespaces(ResourceType type) {
            return type == ResourceType.CLIENT_RESOURCES ? Collections.singleton("stasischess") : Collections.emptySet();
        }

        @Override
        public <T> @Nullable T parseMetadata(ResourceMetadataReader<T> reader) {
            return null;
        }

        @Override
        public @Nullable InputSupplier<InputStream> openRoot(String... segments) {
            return null;
        }

        @Override
        public ResourcePackInfo getInfo() {
            return this.info;
        }

        @Override
        public String getId() {
            return "stasischess_external";
        }

        @Override
        public void close() {}
    }
}
