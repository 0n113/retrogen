package dev.retrogen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.retrogen.RetrogenMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "retrogen.json";

	private ConfigManager() {
	}

	public static RetrogenConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		try {
			if (Files.notExists(path)) {
				RetrogenConfig defaults = new RetrogenConfig();
				writeAtomic(path, defaults);
				RetrogenMod.LOGGER.warn("Created disabled Retrogen config at {}", path);
				return defaults;
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				RetrogenConfig config = GSON.fromJson(reader, RetrogenConfig.class);
				if (config == null) {
					throw new IOException("Configuration is empty");
				}
				config.validate();
				return config;
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cannot load " + path + "; refusing to run Retrogen", e);
		}
	}

	private static void writeAtomic(Path path, RetrogenConfig config) throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
