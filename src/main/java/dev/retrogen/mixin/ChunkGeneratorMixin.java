package dev.retrogen.mixin;

import dev.retrogen.runtime.RetrogenHooks;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
	@Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
	private void retrogen$afterPopulation(
		WorldGenLevel level,
		ChunkAccess chunk,
		StructureManager structureManager,
		CallbackInfo ci
	) {
		RetrogenHooks.afterPopulation(level, chunk);
	}
}
