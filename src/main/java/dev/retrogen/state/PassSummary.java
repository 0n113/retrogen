package dev.retrogen.state;

public record PassSummary(int completed, int failed, int inProgress) {
	public static final PassSummary EMPTY = new PassSummary(0, 0, 0);
}
