package dev.retrogen.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkKeyTest {
	@Test
	void roundTripsCoordinatesAndPackedLong() {
		ChunkKey key = new ChunkKey(-123456, 987654);
		assertEquals(key, ChunkKey.parse(key.toString()));
		assertEquals(key, ChunkKey.fromLong(key.asLong()));
	}

	@Test
	void rejectsMalformedKeys() {
		assertThrows(IllegalArgumentException.class, () -> ChunkKey.parse("1"));
		assertThrows(NumberFormatException.class, () -> ChunkKey.parse("x,2"));
	}
}
