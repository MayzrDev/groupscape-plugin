package com.groupscape.timetracking;

/**
 * Ports the tick-rounding math from RuneLite's own
 * {@code FarmingTracker.getTickTime()}/{@code predictPatch()} - plain arithmetic, no
 * package-private dependency, safe to reimplement directly (unlike the crop-decoding table, see
 * {@link PatchImplementationReflector}).
 *
 * <p>Farming grows in fixed real-world ticks (e.g. every 20 minutes on the hour-ish), but exactly
 * when those ticks land is offset per RuneScape profile and only discoverable by observation -
 * Time Tracking itself does that observation and stores the result under
 * {@code TimeTrackingConfig.FARM_TICK_OFFSET}/{@code FARM_TICK_OFFSET_PRECISION}. GroupScape reads
 * that already-computed calibration rather than re-deriving it.
 */
final class TickOffsetMath {
    /**
     * @param tickRateMinutes growth tick length, in minutes (0 means "no timer applies")
     * @param ticks           how many ticks forward from {@code requestedTime} to project
     * @param requestedTime   epoch-second anchor time
     * @param offsetPrecisionMins Time Tracking's {@code FARM_TICK_OFFSET_PRECISION}, or null if uncalibrated
     * @param offsetTimeMins      Time Tracking's {@code FARM_TICK_OFFSET}, or null if uncalibrated
     */
    static long tickTime(int tickRateMinutes, int ticks, long requestedTime, Integer offsetPrecisionMins, Integer offsetTimeMins) {
        long calculatedOffsetTime = 0L;
        if (offsetPrecisionMins != null && offsetTimeMins != null
                && (offsetPrecisionMins >= tickRateMinutes || offsetPrecisionMins >= 40)) {
            calculatedOffsetTime = (offsetTimeMins % tickRateMinutes) * 60L;
        }

        long unixNow = requestedTime + calculatedOffsetTime;
        long timeOfCurrentTick = unixNow - (unixNow % (tickRateMinutes * 60L));
        long timeOfGoalTick = timeOfCurrentTick + ((long) ticks * tickRateMinutes * 60L);

        return timeOfGoalTick - calculatedOffsetTime;
    }

    /**
     * Ports {@code FarmingTracker.predictPatch()}'s completion-time estimate for a single
     * observation ({@code observedValue} at {@code observedUnixTime}), given the already-decoded
     * patch state. Returns {@code null} when the produce has no forward timer to project (e.g.
     * already fully grown with no regrow tick, or dead) - matches
     * {@code doneEstimate == 0} / {@code tickrate <= 0} in the original.
     */
    static Long estimateReadyAt(PatchImplementationReflector.DecodedPatch decoded, long observedUnixTime,
                                 Integer offsetPrecisionMins, Integer offsetTimeMins) {
        if (decoded.tickRateMinutes <= 0) return null;

        long tickTime = tickTime(decoded.tickRateMinutes, 0, observedUnixTime, offsetPrecisionMins, offsetTimeMins);
        return tickTime(decoded.tickRateMinutes, decoded.stages - 1 - decoded.stage, tickTime, offsetPrecisionMins, offsetTimeMins);
    }

    private TickOffsetMath() {
    }
}
