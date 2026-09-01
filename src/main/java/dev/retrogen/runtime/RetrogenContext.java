package dev.retrogen.runtime;

import dev.retrogen.config.RetrogenConfig;

public final class RetrogenContext implements AutoCloseable {
	private static final ThreadLocal<RetrogenContext> CURRENT = new ThreadLocal<>();
	private final RetrogenConfig.Pass pass;

	private RetrogenContext(RetrogenConfig.Pass pass) {
		this.pass = pass;
	}

	public static RetrogenContext open(RetrogenConfig.Pass pass) {
		if (CURRENT.get() != null) {
			throw new IllegalStateException("Nested Retrogen population is not supported");
		}
		RetrogenContext context = new RetrogenContext(pass);
		CURRENT.set(context);
		return context;
	}

	public static boolean isActive() {
		return CURRENT.get() != null;
	}

	public static boolean allowsFeature(String id) {
		RetrogenContext context = CURRENT.get();
		return context == null || context.pass.allowsFeature(id);
	}

	@Override
	public void close() {
		if (CURRENT.get() != this) {
			throw new IllegalStateException("Retrogen context closed out of order");
		}
		CURRENT.remove();
	}
}
