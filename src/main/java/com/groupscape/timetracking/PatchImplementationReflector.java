package com.groupscape.timetracking;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Decodes a farming patch's raw varbit value into produce/stage/tick-rate data by calling
 * RuneLite's own {@code net.runelite.client.plugins.timetracking.farming.PatchImplementation}
 * enum via reflection.
 *
 * <p>{@code PatchImplementation} itself is a <em>public</em> enum, but its {@code forVarbitValue}
 * method and the {@code PatchState}/{@code Produce}/{@code CropState} types it works with are
 * package-private - GroupScape's plugin can't reference them by name at compile time, so this
 * class goes through reflection instead of re-encoding RuneLite's ~3000-line value-range table by
 * hand. This means GroupScape always matches RuneLite's actual crop-decoding behavior (including
 * across game updates), at the cost of depending on method/field names that aren't a stable public
 * API - if RuneLite renames them, decoding here fails closed (returns null / logs once) rather
 * than silently producing wrong data.
 */
@Slf4j
public final class PatchImplementationReflector {
    private static final String PACKAGE = "net.runelite.client.plugins.timetracking.farming.";
    private static boolean unavailableWarningLogged = false;

    /** Decoded snapshot of one patch at one point in time. Mirrors RuneLite's {@code PatchState}. */
    public static final class DecodedPatch {
        public final String produceName;
        public final int produceItemId;
        public final String cropState;
        public final int stage;
        public final int stages;
        public final int tickRateMinutes;

        DecodedPatch(String produceName, int produceItemId, String cropState, int stage, int stages, int tickRateMinutes) {
            this.produceName = produceName;
            this.produceItemId = produceItemId;
            this.cropState = cropState;
            this.stage = stage;
            this.stages = stages;
            this.tickRateMinutes = tickRateMinutes;
        }
    }

    /**
     * @param kind  which {@code PatchImplementation} constant to decode against ({@code "HERB"} or
     *              {@code "TREE"})
     * @param value the raw varbit value, as stored by Time Tracking
     * @return the decoded patch state, or {@code null} if RuneLite's own logic doesn't recognize
     * {@code value} (matches {@code PatchImplementation.forVarbitValue()} returning null) or if the
     * reflective call itself fails (missing/renamed internals - logged once, not per call)
     */
    public static DecodedPatch decode(String kind, int value) {
        try {
            Class<?> patchImplClass = Class.forName(PACKAGE + "PatchImplementation");
            Object constant = findEnumConstant(patchImplClass, kind);
            if (constant == null) return null;

            Method forVarbitValue = patchImplClass.getDeclaredMethod("forVarbitValue", int.class);
            forVarbitValue.setAccessible(true);
            Object patchState = forVarbitValue.invoke(constant, value);
            if (patchState == null) return null;

            Class<?> patchStateClass = patchState.getClass();
            // getProduce/getCropState/getStage are public (Lombok @Value); getStages/getTickRate
            // are hand-written on PatchState without an access modifier (package-private).
            Object produce = invoke(patchStateClass, patchState, "getProduce");
            Object cropState = invoke(patchStateClass, patchState, "getCropState");
            int stage = (int) invoke(patchStateClass, patchState, "getStage");
            int stages = (int) invoke(patchStateClass, patchState, "getStages");
            int tickRate = (int) invoke(patchStateClass, patchState, "getTickRate");

            Class<?> produceClass = produce.getClass();
            String produceName = (String) invoke(produceClass, produce, "getName");
            int produceItemId = (int) invoke(produceClass, produce, "getItemID");
            String cropStateName = cropState.getClass().getMethod("name").invoke(cropState).toString();

            return new DecodedPatch(produceName, produceItemId, cropStateName, stage, stages, tickRate);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!unavailableWarningLogged) {
                unavailableWarningLogged = true;
                log.warn("Time Tracking reflection unavailable (RuneLite internals may have changed) - farming timers will report unconfirmed", e);
            }
            return null;
        }
    }

    private static Object findEnumConstant(Class<?> enumClass, String name) throws ReflectiveOperationException {
        for (Object constant : enumClass.getEnumConstants()) {
            if (constant.toString().equals(name)) return constant;
        }
        return null;
    }

    // getDeclaredMethod (not getMethod) - getStages()/getTickRate() are package-private on
    // PatchState, which getMethod() (public-only) would silently fail to find.
    private static Object invoke(Class<?> declaringClass, Object target, String methodName) throws ReflectiveOperationException {
        Method m = declaringClass.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private PatchImplementationReflector() {
    }
}
