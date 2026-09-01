package dev.retrogen.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RetrogenConfig {
	public int schemaVersion = 1;
	public boolean enabled = false;
	public boolean dryRun = false;
	public int chunksPerTick = 1;
	public int saveIntervalTicks = 100;
	public boolean requireLoaded3x3 = true;
	public List<Pass> passes = new ArrayList<>(List.of(Pass.defaultPass()));

	public void validate() {
		if (schemaVersion != 1) {
			throw new IllegalArgumentException("Unsupported config schemaVersion: " + schemaVersion);
		}
		chunksPerTick = Math.clamp(chunksPerTick, 1, 64);
		saveIntervalTicks = Math.clamp(saveIntervalTicks, 20, 72000);
		if (passes == null) {
			passes = new ArrayList<>();
		}
		Set<String> passIds = new HashSet<>();
		for (Pass pass : passes) {
			if (pass == null) {
				throw new IllegalArgumentException("Pass entries must not be null");
			}
			pass.validate();
			if (!passIds.add(pass.id)) {
				throw new IllegalArgumentException("Duplicate pass id: " + pass.id);
			}
		}
	}

	public static final class Pass {
		public String id = "example_ores_v1";
		public boolean enabled = true;
		public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
		public List<String> includePlacedFeatures = new ArrayList<>(List.of("examplemod:*"));
		public List<String> excludePlacedFeatures = new ArrayList<>();
		public boolean markNewChunksComplete = true;
		public boolean retryFailed = false;

		private static Pass defaultPass() {
			return new Pass();
		}

		private void validate() {
			if (id == null || !id.matches("[a-z0-9_.-]{1,64}")) {
				throw new IllegalArgumentException("Invalid pass id: " + id);
			}
			dimensions = nonNull(dimensions);
			includePlacedFeatures = nonNull(includePlacedFeatures);
			excludePlacedFeatures = nonNull(excludePlacedFeatures);
			if (dimensions.isEmpty()) {
				throw new IllegalArgumentException("Pass " + id + " must select at least one dimension");
			}
			if (includePlacedFeatures.isEmpty()) {
				throw new IllegalArgumentException("Pass " + id + " must select at least one placed feature");
			}
		}

		private static List<String> nonNull(List<String> list) {
			return list == null ? new ArrayList<>() : new ArrayList<>(list);
		}

		public boolean matchesDimension(String dimension) {
			return matchesAny(dimensions, dimension);
		}

		public boolean allowsFeature(String feature) {
			return matchesAny(includePlacedFeatures, feature) && !matchesAny(excludePlacedFeatures, feature);
		}

		private static boolean matchesAny(List<String> patterns, String value) {
			for (String pattern : patterns) {
				if ("*".equals(pattern)) {
					return true;
				}
				if (pattern.endsWith(":*") && value.startsWith(pattern.substring(0, pattern.length() - 1))) {
					return true;
				}
				if (pattern.equals(value)) {
					return true;
				}
			}
			return false;
		}
	}
}
