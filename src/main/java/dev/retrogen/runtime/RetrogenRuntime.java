package dev.retrogen.runtime;

import dev.retrogen.RetrogenMod;
import dev.retrogen.config.ConfigManager;
import dev.retrogen.config.RetrogenConfig;
import dev.retrogen.state.RetrogenStateStore;
import dev.retrogen.state.PassSummary;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.Map;

public final class RetrogenRuntime {
	private static final Map<String, WorkQueue> QUEUES = new HashMap<>();
	private static RetrogenConfig config;
	private static RetrogenStateStore state;
	private static long ticks;

	private RetrogenRuntime() {
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTING.register(RetrogenRuntime::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(RetrogenRuntime::onServerStopping);
		ServerChunkEvents.CHUNK_LOAD.register(RetrogenRuntime::onChunkLoad);
		ServerTickEvents.END_LEVEL_TICK.register(RetrogenRuntime::onEndWorldTick);
	}

	private static void onServerStarting(MinecraftServer server) {
		config = ConfigManager.load();
		state = RetrogenStateStore.load(server.getWorldPath(LevelResource.ROOT));
		QUEUES.clear();
		ticks = 0;
		RetrogenMod.LOGGER.info("Retrogen is {} with {} configured pass(es)", config.enabled ? "enabled" : "disabled", config.passes.size());
	}

	private static void onServerStopping(MinecraftServer server) {
		if (state != null) {
			state.saveIfDirty();
		}
		QUEUES.clear();
		state = null;
		config = null;
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
		if (config == null || state == null || !config.enabled) {
			return;
		}
		String dimension = dimensionId(level);
		long key = chunk.getPos().pack();
		if (hasPendingPass(dimension, key)) {
			QUEUES.computeIfAbsent(dimension, ignored -> new WorkQueue()).offer(key);
		}
	}

	private static void onEndWorldTick(ServerLevel level) {
		if (config == null || state == null || !config.enabled) {
			return;
		}
		String dimension = dimensionId(level);
		WorkQueue queue = QUEUES.get(dimension);
		if (queue != null) {
			for (int i = 0; i < config.chunksPerTick && !queue.isEmpty(); i++) {
				long key = queue.poll();
				process(level, dimension, key);
			}
		}
		ticks++;
		if (ticks % config.saveIntervalTicks == 0) {
			state.saveIfDirty();
		}
	}

	private static void process(ServerLevel level, String dimension, long key) {
		ChunkPos pos = ChunkPos.unpack(key);
		if (config.requireLoaded3x3 && !isNeighborhoodLoaded(level, pos)) {
			QUEUES.computeIfAbsent(dimension, ignored -> new WorkQueue()).offer(key);
			return;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
		if (chunk == null) {
			return;
		}

		for (RetrogenConfig.Pass pass : config.passes) {
			if (!isPending(pass, dimension, key)) {
				continue;
			}
			if (config.dryRun) {
				RetrogenMod.LOGGER.info("[dry-run] Would execute pass {} for {} chunk {}", pass.id, dimension, pos);
				continue;
			}
			try {
				state.markInProgress(dimension, pass.id, key);
				// Fail closed before the first world mutation. A crash or write failure
				// must never allow an untracked generation pass.
				state.saveIfDirtyOrThrow();
				try (RetrogenContext ignored = RetrogenContext.open(pass)) {
					level.getChunkSource().getGenerator().applyBiomeDecoration(level, chunk, level.structureManager());
				}
				state.markComplete(dimension, pass.id, key);
				RetrogenMod.LOGGER.debug("Completed pass {} for {} chunk {}", pass.id, dimension, pos);
			} catch (Exception error) {
				state.markFailed(dimension, pass.id, key, error);
				RetrogenMod.LOGGER.error("Retrogen pass {} failed for {} chunk {}", pass.id, dimension, pos, error);
			}
		}
	}

	public static void markNaturallyPopulated(ServerLevel level, long key) {
		if (config == null || state == null) {
			return;
		}
		String dimension = dimensionId(level);
		for (RetrogenConfig.Pass pass : config.passes) {
			if (pass.enabled && pass.markNewChunksComplete && pass.matchesDimension(dimension)) {
				state.markComplete(dimension, pass.id, key);
			}
		}
	}

	public static RuntimeStatus status(ServerLevel level, String passId) {
		if (config == null || state == null) {
			return new RuntimeStatus(false, false, dimensionId(level), passId, 0, PassSummary.EMPTY);
		}
		String dimension = dimensionId(level);
		WorkQueue queue = QUEUES.get(dimension);
		return new RuntimeStatus(
			true,
			config.enabled,
			dimension,
			passId,
			queue == null ? 0 : queue.size(),
			state.summary(dimension, passId)
		);
	}

	public static Iterable<RetrogenConfig.Pass> configuredPasses() {
		return config == null ? java.util.List.of() : java.util.List.copyOf(config.passes);
	}

	public static boolean hasConfiguredPass(String passId) {
		return findPass(passId) != null;
	}

	public static CommandResult retry(ServerLevel level, String passId, long key) {
		RetrogenConfig.Pass pass = findPass(passId);
		if (config == null || state == null) {
			return CommandResult.NOT_READY;
		}
		if (pass == null) {
			return CommandResult.UNKNOWN_PASS;
		}
		String dimension = dimensionId(level);
		if (!pass.enabled || !pass.matchesDimension(dimension)) {
			return CommandResult.PASS_INACTIVE;
		}
		if (state.isComplete(dimension, pass.id, key)) {
			return CommandResult.ALREADY_COMPLETE;
		}
		state.clearBlocked(dimension, pass.id, key);
		try {
			state.saveIfDirtyOrThrow();
		} catch (IllegalStateException error) {
			RetrogenMod.LOGGER.error("Cannot persist retry state for pass {} in {} chunk {}", pass.id, dimension, ChunkPos.unpack(key), error);
			return CommandResult.PERSISTENCE_FAILED;
		}
		if (!config.enabled) {
			return CommandResult.MOD_DISABLED;
		}
		if (level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) == null) {
			return CommandResult.WAITING_FOR_CHUNK_LOAD;
		}
		QUEUES.computeIfAbsent(dimension, ignored -> new WorkQueue()).offer(key);
		return CommandResult.QUEUED;
	}

	public static CommandResult clear(ServerLevel level, String passId, long key) {
		if (config == null || state == null) {
			return CommandResult.NOT_READY;
		}
		RetrogenConfig.Pass pass = findPass(passId);
		if (pass == null) {
			return CommandResult.UNKNOWN_PASS;
		}
		String dimension = dimensionId(level);
		if (!state.clearAll(dimension, pass.id, key)) {
			return CommandResult.NOTHING_TO_CLEAR;
		}
		try {
			state.saveIfDirtyOrThrow();
		} catch (IllegalStateException error) {
			RetrogenMod.LOGGER.error("Cannot persist cleared state for pass {} in {} chunk {}", pass.id, dimension, ChunkPos.unpack(key), error);
			return CommandResult.PERSISTENCE_FAILED;
		}
		if (config.enabled && pass.enabled && pass.matchesDimension(dimension)) {
			if (level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) == null) {
				return CommandResult.CLEARED_WAITING_FOR_CHUNK_LOAD;
			}
			QUEUES.computeIfAbsent(dimension, ignored -> new WorkQueue()).offer(key);
			return CommandResult.CLEARED_AND_QUEUED;
		}
		return CommandResult.CLEARED;
	}

	private static RetrogenConfig.Pass findPass(String passId) {
		if (config == null) {
			return null;
		}
		for (RetrogenConfig.Pass pass : config.passes) {
			if (pass.id.equals(passId)) {
				return pass;
			}
		}
		return null;
	}

	private static boolean hasPendingPass(String dimension, long key) {
		for (RetrogenConfig.Pass pass : config.passes) {
			if (isPending(pass, dimension, key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPending(RetrogenConfig.Pass pass, String dimension, long key) {
		if (!pass.enabled || !pass.matchesDimension(dimension) || state.isComplete(dimension, pass.id, key)) {
			return false;
		}
		if (state.isInProgress(dimension, pass.id, key)) {
			return false;
		}
		return pass.retryFailed || !state.hasFailed(dimension, pass.id, key);
	}

	private static boolean isNeighborhoodLoaded(ServerLevel level, ChunkPos center) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	private static String dimensionId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static final class WorkQueue {
		private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		private final LongOpenHashSet queued = new LongOpenHashSet();

		void offer(long key) {
			if (queued.add(key)) {
				queue.enqueue(key);
			}
		}

		long poll() {
			long key = queue.dequeueLong();
			queued.remove(key);
			return key;
		}

		boolean isEmpty() {
			return queue.isEmpty();
		}

		int size() {
			return queue.size();
		}
	}

	public record RuntimeStatus(
		boolean ready,
		boolean enabled,
		String dimension,
		String passId,
		int queued,
		PassSummary summary
	) {
	}

	public enum CommandResult {
		QUEUED,
		CLEARED,
		CLEARED_AND_QUEUED,
		NOT_READY,
		MOD_DISABLED,
		UNKNOWN_PASS,
		PASS_INACTIVE,
		ALREADY_COMPLETE,
		NOTHING_TO_CLEAR,
		WAITING_FOR_CHUNK_LOAD,
		CLEARED_WAITING_FOR_CHUNK_LOAD,
		PERSISTENCE_FAILED
	}
}
