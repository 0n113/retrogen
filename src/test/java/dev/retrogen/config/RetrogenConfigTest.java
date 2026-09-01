package dev.retrogen.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrogenConfigTest {
	@Test
	void validateClampsOperationalLimits() {
		RetrogenConfig config = new RetrogenConfig();
		config.chunksPerTick = 0;
		config.saveIntervalTicks = 100_000;

		config.validate();

		assertEquals(1, config.chunksPerTick);
		assertEquals(72_000, config.saveIntervalTicks);
	}

	@Test
	void validateRejectsUnknownSchemaAndInvalidPasses() {
		RetrogenConfig config = new RetrogenConfig();
		config.schemaVersion = 2;
		assertThrows(IllegalArgumentException.class, config::validate);

		config.schemaVersion = 1;
		config.passes.getFirst().id = "INVALID PASS";
		assertThrows(IllegalArgumentException.class, config::validate);

		config.passes.getFirst().id = "valid_pass";
		config.passes.getFirst().dimensions = new ArrayList<>();
		assertThrows(IllegalArgumentException.class, config::validate);
	}

	@Test
	void namespaceWildcardAndExclusionAreApplied() {
		RetrogenConfig.Pass pass = new RetrogenConfig.Pass();
		pass.dimensions = List.of("minecraft:*");
		pass.includePlacedFeatures = List.of("examplemod:*", "minecraft:ore_coal_upper");
		pass.excludePlacedFeatures = List.of("examplemod:unsafe_ore");

		assertTrue(pass.matchesDimension("minecraft:overworld"));
		assertTrue(pass.allowsFeature("examplemod:tin_ore"));
		assertTrue(pass.allowsFeature("minecraft:ore_coal_upper"));
		assertFalse(pass.allowsFeature("examplemod:unsafe_ore"));
		assertFalse(pass.allowsFeature("minecraft:ore_diamond"));
	}

	@Test
	void validateRejectsDuplicateAndNullPassEntries() {
		RetrogenConfig config = new RetrogenConfig();
		RetrogenConfig.Pass duplicate = new RetrogenConfig.Pass();
		config.passes.add(duplicate);
		assertThrows(IllegalArgumentException.class, config::validate);

		config.passes = new ArrayList<>();
		config.passes.add(null);
		assertThrows(IllegalArgumentException.class, config::validate);
	}
}
