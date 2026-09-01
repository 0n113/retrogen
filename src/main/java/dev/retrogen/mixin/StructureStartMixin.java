package dev.retrogen.mixin;

import dev.retrogen.runtime.RetrogenContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
abstract class StructureStartMixin {
	@Inject(method = "placeInChunk", at = @At("HEAD"), cancellable = true)
	private void retrogen$skipExistingStructures(
		WorldGenLevel level,
		StructureManager structureManager,
		ChunkGenerator generator,
		RandomSource random,
		BoundingBox chunkBox,
		ChunkPos chunkPos,
		CallbackInfo ci
	) {
		if (RetrogenContext.isActive()) {
			ci.cancel();
		}
	}
}
