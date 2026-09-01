package dev.retrogen.state;

import net.minecraft.world.level.ChunkPos;

public record ChunkKey(int x, int z) {
	public static ChunkKey fromLong(long packed) {
		return new ChunkKey(ChunkPos.getX(packed), ChunkPos.getZ(packed));
	}

	public static ChunkKey parse(String value) {
		String[] parts = value.split(",", -1);
		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid chunk key: " + value);
		}
		return new ChunkKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
	}

	public long asLong() {
		return ChunkPos.pack(x, z);
	}

	@Override
	public String toString() {
		return x + "," + z;
	}
}
