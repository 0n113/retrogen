package dev.retrogen.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.retrogen.RetrogenMod;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class RetrogenStateStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final Path path;
	private StateFile state;
	private boolean dirty;

	private RetrogenStateStore(Path path, StateFile state) {
		this.path = path;
		this.state = state;
		index(state);
	}

	public static RetrogenStateStore load(Path worldRoot) {
		Path path = worldRoot.resolve("retrogen").resolve("retrogen-state-v1.json");
		if (Files.notExists(path)) {
			return new RetrogenStateStore(path, new StateFile());
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			StateFile state = GSON.fromJson(reader, StateFile.class);
			if (state == null || state.schemaVersion != 1) {
				throw new IOException("Unsupported or empty state file");
			}
			return new RetrogenStateStore(path, state);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot load Retrogen state " + path + "; refusing duplicate-prone operation", e);
		}
	}

	public synchronized boolean isComplete(String dimension, String pass, long chunk) {
		return pass(dimension, pass).completedIndex.contains(chunk);
	}

	public synchronized boolean hasFailed(String dimension, String pass, long chunk) {
		return pass(dimension, pass).failed.containsKey(ChunkKey.fromLong(chunk).toString());
	}

	public synchronized boolean isInProgress(String dimension, String pass, long chunk) {
		return pass(dimension, pass).inProgress.containsKey(ChunkKey.fromLong(chunk).toString());
	}

	public synchronized PassSummary summary(String dimension, String pass) {
		DimensionState dimensionState = state.dimensions.get(dimension);
		if (dimensionState == null) {
			return PassSummary.EMPTY;
		}
		PassState passState = dimensionState.passes.get(pass);
		if (passState == null) {
			return PassSummary.EMPTY;
		}
		return new PassSummary(passState.completed.size(), passState.failed.size(), passState.inProgress.size());
	}

	public synchronized boolean clearBlocked(String dimension, String pass, long chunk) {
		PassState passState = pass(dimension, pass);
		String key = ChunkKey.fromLong(chunk).toString();
		boolean changed = passState.failed.remove(key) != null;
		changed |= passState.inProgress.remove(key) != null;
		if (changed) {
			passState.lastUpdated = Instant.now().toString();
			dirty = true;
		}
		return changed;
	}

	public synchronized boolean clearAll(String dimension, String pass, long chunk) {
		PassState passState = pass(dimension, pass);
		String key = ChunkKey.fromLong(chunk).toString();
		boolean changed = passState.completed.remove(key);
		changed |= passState.completedIndex.remove(chunk);
		changed |= passState.failed.remove(key) != null;
		changed |= passState.inProgress.remove(key) != null;
		if (changed) {
			passState.lastUpdated = Instant.now().toString();
			dirty = true;
		}
		return changed;
	}

	public synchronized void markInProgress(String dimension, String pass, long chunk) {
		PassState passState = pass(dimension, pass);
		passState.inProgress.put(ChunkKey.fromLong(chunk).toString(), Instant.now().toString());
		passState.lastUpdated = Instant.now().toString();
		dirty = true;
	}

	public synchronized void markComplete(String dimension, String pass, long chunk) {
		PassState passState = pass(dimension, pass);
		String key = ChunkKey.fromLong(chunk).toString();
		boolean changed = passState.completedIndex.add(chunk);
		if (changed) {
			passState.completed.add(key);
		}
		changed |= passState.failed.remove(key) != null;
		changed |= passState.inProgress.remove(key) != null;
		if (changed) {
			passState.lastUpdated = Instant.now().toString();
			dirty = true;
		}
	}

	public synchronized void markFailed(String dimension, String pass, long chunk, Throwable error) {
		PassState passState = pass(dimension, pass);
		String message = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
		passState.failed.put(ChunkKey.fromLong(chunk).toString(), message);
		passState.inProgress.remove(ChunkKey.fromLong(chunk).toString());
		passState.lastUpdated = Instant.now().toString();
		dirty = true;
	}

	public synchronized void saveIfDirty() {
		try {
			saveIfDirtyOrThrow();
		} catch (IllegalStateException e) {
			RetrogenMod.LOGGER.error(e.getMessage(), e);
		}
	}

	public synchronized void saveIfDirtyOrThrow() {
		if (!dirty) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			for (DimensionState dimension : state.dimensions.values()) {
				for (PassState pass : dimension.passes.values()) {
					pass.completed = new TreeSet<>(pass.completed);
				}
			}
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(state, writer);
			}
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException e) {
			throw new IllegalStateException("Could not persist Retrogen state to " + path, e);
		}
	}

	private PassState pass(String dimension, String pass) {
		return state.dimensions
			.computeIfAbsent(dimension, ignored -> new DimensionState())
			.passes.computeIfAbsent(pass, ignored -> new PassState());
	}

	private static void index(StateFile state) {
		if (state.dimensions == null) {
			state.dimensions = new HashMap<>();
		}
		for (DimensionState dimension : state.dimensions.values()) {
			if (dimension.passes == null) {
				dimension.passes = new HashMap<>();
			}
			for (PassState pass : dimension.passes.values()) {
				if (pass.completed == null) {
					pass.completed = new TreeSet<>();
				}
				if (pass.failed == null) {
					pass.failed = new HashMap<>();
				}
				if (pass.inProgress == null) {
					pass.inProgress = new HashMap<>();
				}
					pass.completedIndex = new LongOpenHashSet();
				for (String key : pass.completed) {
					pass.completedIndex.add(ChunkKey.parse(key).asLong());
				}
			}
		}
	}

	private static final class StateFile {
		int schemaVersion = 1;
		Map<String, DimensionState> dimensions = new HashMap<>();
	}

	private static final class DimensionState {
		Map<String, PassState> passes = new HashMap<>();
	}

	private static final class PassState {
		Set<String> completed = new TreeSet<>();
		Map<String, String> failed = new HashMap<>();
		Map<String, String> inProgress = new HashMap<>();
		String lastUpdated;
		transient LongOpenHashSet completedIndex = new LongOpenHashSet();
	}
}
