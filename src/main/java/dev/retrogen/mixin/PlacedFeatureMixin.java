package dev.retrogen.mixin;

import dev.retrogen.runtime.RetrogenContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
abstract class PlacedFeatureMixin {
	@Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
	private void retrogen$filterPlacedFeature(
		WorldGenLevel level,
		ChunkGenerator generator,
		RandomSource random,
		net.minecraft.core.BlockPos origin,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!RetrogenContext.isActive()) {
			return;
		}
		PlacedFeature self = (PlacedFeature) (Object) this;
		Identifier id = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getKey(self);
		if (id == null || !RetrogenContext.allowsFeature(id.toString())) {
			cir.setReturnValue(false);
		}
	}
}
