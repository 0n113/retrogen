package dev.retrogen.runtime;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class RetrogenHooks {
	private RetrogenHooks() {
	}

	public static void afterPopulation(WorldGenLevel level, ChunkAccess chunk) {
		if (!RetrogenContext.isActive() && level instanceof WorldGenRegion region) {
			RetrogenRuntime.markNaturallyPopulated(region.getLevel(), chunk.getPos().pack());
		}
	}
}
