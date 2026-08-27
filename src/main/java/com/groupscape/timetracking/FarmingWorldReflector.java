package com.groupscape.timetracking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.timetracking.farming.FarmingRegion;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reads RuneLite's own farming patch table (region + varbit + patch-type for every tracked patch)
 * via reflection instead of hand-porting it. {@code FarmingWorld}/{@code FarmingPatch} are
 * package-private in RuneLite's own source, so GroupScape's plugin can't reference them at compile
 * time even though they're on the runtime classpath - but their public getters are reachable
 * through reflection with {@code setAccessible}. This keeps the patch list identical to what Time
 * Tracking itself uses, including across game updates that add/move patches, and covers every
 * patch kind (not just the ones anyone thought to hand-type) at no extra cost.
 */
@Slf4j
public final class FarmingWorldReflector {
    public static final class PatchEntry {
        public final String label;
        public final int regionId;
        public final int varbitId;
        public final PatchImplementation kind;

        PatchEntry(String label, int regionId, int varbitId, PatchImplementation kind) {
            this.label = label;
            this.regionId = regionId;
            this.varbitId = varbitId;
            this.kind = kind;
        }
    }

    private static List<PatchEntry> cached;
    private static boolean unavailableWarningLogged = false;

    public static synchronized List<PatchEntry> entries() {
        if (cached != null) return cached;

        try {
            Class<?> worldClass = Class.forName("net.runelite.client.plugins.timetracking.farming.FarmingWorld");
            Constructor<?> ctor = worldClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object world = ctor.newInstance();

            Method getTabs = worldClass.getDeclaredMethod("getTabs");
            getTabs.setAccessible(true);
            Map<?, ?> tabs = (Map<?, ?>) getTabs.invoke(world);

            List<PatchEntry> result = new ArrayList<>();
            for (Object patchesForTab : tabs.values()) {
                for (Object patch : (Collection<?>) patchesForTab) {
                    Class<?> patchClass = patch.getClass();
                    String name = (String) invoke(patchClass, patch, "getName");
                    int varbit = (int) invoke(patchClass, patch, "getVarbit");
                    PatchImplementation kind = (PatchImplementation) invoke(patchClass, patch, "getImplementation");
                    FarmingRegion region = (FarmingRegion) invoke(patchClass, patch, "getRegion");
                    if (region == null) continue;
                    result.add(new PatchEntry(name, region.getRegionID(), varbit, kind));
                }
            }
            cached = Collections.unmodifiableList(result);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!unavailableWarningLogged) {
                unavailableWarningLogged = true;
                log.warn("Time Tracking farming world reflection unavailable (RuneLite internals may have changed) - farming timers will report unconfirmed", e);
            }
            cached = Collections.emptyList();
        }

        return cached;
    }

    private static Object invoke(Class<?> declaringClass, Object target, String methodName) throws ReflectiveOperationException {
        Method m = declaringClass.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private FarmingWorldReflector() {
    }
}
