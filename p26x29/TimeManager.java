package p26x29;

import java.util.concurrent.TimeUnit;

final class TimeManager {
    private static final long TOTAL_TIME_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final long MIN_MOVE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long MAX_MOVE_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long SAFETY_MARGIN_NANOS = TimeUnit.MILLISECONDS.toNanos(150);
    private static final double TIME_FRACTION = 0.8;

    private final long searchUntilNanos;

    private TimeManager(long deadlineNanos) {
        this.searchUntilNanos = deadlineNanos - SAFETY_MARGIN_NANOS;
    }

    static TimeManager forMove(long accumulatedThinkNanos, int remainingEmptySquares) {
        long now = System.nanoTime();
        long remainingGameNanos = Math.max(0L, TOTAL_TIME_NANOS - accumulatedThinkNanos);
        int divisor = Math.max(1, remainingEmptySquares);
        long plannedNanos = (long) ((remainingGameNanos / (double) divisor) * TIME_FRACTION);
        long moveLimitNanos = clamp(plannedNanos, MIN_MOVE_NANOS, MAX_MOVE_NANOS);
        return new TimeManager(now + moveLimitNanos);
    }

    boolean shouldStop() {
        return System.nanoTime() >= this.searchUntilNanos;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
