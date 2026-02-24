package nand.modid.game;

import nand.modid.chess.movegen.StandardGenerators;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ChessemblyFileLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("StasisChess-Loader");
    private static final String SCRIPTS_DIR = "stasischess/scripts";
    private static final String TEXTURES_DIR = "stasischess/textures";

    /**
     * Loads all .chess files from the designated folder inside 'mods' and registers them to the engine.
     */
    public static void loadAll() {
        Path modsBaseDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path scriptsDir = modsBaseDir.resolve(SCRIPTS_DIR);
        Path texturesDir = modsBaseDir.resolve(TEXTURES_DIR);
        
        try {
            // 스크립트 폴더 생성
            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                LOGGER.info("[StasisChess] Script directory created: {}", scriptsDir);
                createExampleFile(scriptsDir);
            }
            // 텍스처 폴더 생성
            if (!Files.exists(texturesDir)) {
                Files.createDirectories(texturesDir);
                LOGGER.info("[StasisChess] Texture directory created: {}", texturesDir);
            }

            try (Stream<Path> paths = Files.walk(scriptsDir)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(".chess"))
                     .forEach(ChessemblyFileLoader::loadFilePath);
            }
            LOGGER.info("[StasisChess] All external Chessembly scripts loaded.");
        } catch (IOException e) {
            LOGGER.error("[StasisChess] Error loading external assets", e);
        }
    }

    private static void loadFilePath(Path path) {
        try {
            String fileName = path.getFileName().toString();
            String pieceName = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase();
            String script = Files.readString(path);
            
            StandardGenerators.registerScript(pieceName, script);
            LOGGER.info("[StasisChess] External script registered: {}", pieceName);
        } catch (IOException e) {
            LOGGER.error("[StasisChess] Failed to read file: {}", path, e);
        }
    }

    private static void createExampleFile(Path dir) throws IOException {
        Path example = dir.resolve("custom_knight.chess");
        if (!Files.exists(example)) {
            StringBuilder sb = new StringBuilder();
            sb.append("// Example: Custom Knight\n");
            sb.append("take-move(1, 2); take-move(2, 1); take-move(2, -1); take-move(1, -2);\n");
            sb.append("take-move(-1, 2); take-move(-2, 1); take-move(-2, -1); take-move(-1, -2);\n");
            sb.append("// Additional move: 2-step jump\n");
            sb.append("move(0, 2); move(0, -2);");
            Files.writeString(example, sb.toString());
        }
    }
}
