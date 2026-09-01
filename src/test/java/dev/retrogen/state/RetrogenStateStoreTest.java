package dev.retrogen.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrogenStateStoreTest {
	private static final String DIMENSION = "minecraft:overworld";
	private static final String PASS = "ores_v1";
	private static final long CHUNK = new ChunkKey(-4, 12).asLong();

	@Test
	void clearBlockedAllowsRetryWithoutRemovingCompletion(@TempDir Path world) {
		RetrogenStateStore store = RetrogenStateStore.load(world);
		store.markInProgress(DIMENSION, PASS, CHUNK);
		assertTrue(store.isInProgress(DIMENSION, PASS, CHUNK));

		assertTrue(store.clearBlocked(DIMENSION, PASS, CHUNK));
		assertFalse(store.isInProgress(DIMENSION, PASS, CHUNK));
		assertEquals(PassSummary.EMPTY, store.summary(DIMENSION, PASS));
	}

	@Test
	void clearAllRemovesPersistedCompletion(@TempDir Path world) {
		RetrogenStateStore store = RetrogenStateStore.load(world);
		store.markComplete(DIMENSION, PASS, CHUNK);
		store.saveIfDirty();

		RetrogenStateStore reloaded = RetrogenStateStore.load(world);
		assertTrue(reloaded.isComplete(DIMENSION, PASS, CHUNK));
		assertTrue(reloaded.clearAll(DIMENSION, PASS, CHUNK));
		reloaded.saveIfDirty();

		RetrogenStateStore cleared = RetrogenStateStore.load(world);
		assertFalse(cleared.isComplete(DIMENSION, PASS, CHUNK));
		assertEquals(PassSummary.EMPTY, cleared.summary(DIMENSION, PASS));
	}

	@Test
	void failedMarkerPersistsUntilExplicitlyCleared(@TempDir Path world) {
		RetrogenStateStore store = RetrogenStateStore.load(world);
		store.markFailed(DIMENSION, PASS, CHUNK, new IllegalStateException("synthetic failure"));
		store.saveIfDirty();

		RetrogenStateStore reloaded = RetrogenStateStore.load(world);
		assertTrue(reloaded.hasFailed(DIMENSION, PASS, CHUNK));
		assertEquals(1, reloaded.summary(DIMENSION, PASS).failed());
		assertTrue(reloaded.clearBlocked(DIMENSION, PASS, CHUNK));
		assertFalse(reloaded.hasFailed(DIMENSION, PASS, CHUNK));
	}

	@Test
	void inProgressMarkerSurvivesRestartForManualRecovery(@TempDir Path world) {
		RetrogenStateStore store = RetrogenStateStore.load(world);
		store.markInProgress(DIMENSION, PASS, CHUNK);
		store.saveIfDirty();

		RetrogenStateStore reloaded = RetrogenStateStore.load(world);
		assertTrue(reloaded.isInProgress(DIMENSION, PASS, CHUNK));
		assertEquals(1, reloaded.summary(DIMENSION, PASS).inProgress());
		assertTrue(reloaded.clearBlocked(DIMENSION, PASS, CHUNK));
		assertFalse(reloaded.isInProgress(DIMENSION, PASS, CHUNK));
	}
}
