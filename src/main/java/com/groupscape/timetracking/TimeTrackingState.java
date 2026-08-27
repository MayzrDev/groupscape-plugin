package com.groupscape.timetracking;

import com.groupscape.ConsumableState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges RuneLite's own Time Tracking plugin (herb/tree farming patches, bird houses) into
 * GroupScape's telemetry, per the plan at {@code docs} (see conversation) - reads Time Tracking's
 * stored config rather than detecting patch state independently. Herb/tree crop decoding goes
 * through {@link PatchImplementationReflector}; birdhouses need only {@link BirdHouseSpaceData}.
 *
 * <p>If Time Tracking isn't enabled/hasn't observed a given patch yet, that patch is reported
 * {@code unconfirmed} rather than omitted, so the group can tell "no data" apart from "empty
 * patch" - the same distinction RuneLite's own UI makes.
 */
public class TimeTrackingState implements ConsumableState {
    private final String playerName;
    private final List<PatchTimerEntry> entries;

    public TimeTrackingState(String playerName, ConfigManager configManager) {
        this.playerName = playerName;
        this.entries = build(configManager);
    }

    private static List<PatchTimerEntry> build(ConfigManager configManager) {
        List<PatchTimerEntry> result = new ArrayList<>();

        Integer offsetPrecision = getIntConfig(configManager, TimeTrackingConfig.FARM_TICK_OFFSET_PRECISION);
        Integer offsetMinutes = getIntConfig(configManager, TimeTrackingConfig.FARM_TICK_OFFSET);

        for (FarmingPatchData.Entry patch : FarmingPatchData.entries()) {
            result.add(decodeFarmingPatch(configManager, patch, offsetPrecision, offsetMinutes));
        }
        for (BirdHouseSpaceData.Entry space : BirdHouseSpaceData.entries()) {
            result.add(decodeBirdHouse(configManager, space));
        }

        // Every patch/space is unconfirmed exactly when Time Tracking has never stored anything
        // for this profile (disabled, or never visited any tracked location) - report that as "no
        // data at all" rather than a wall of unconfirmed rows, so the server/frontend can tell
        // "Time Tracking not enabled" apart from "enabled but this one patch is unchecked".
        if (result.stream().allMatch(entry -> entry.unconfirmed)) {
            return new ArrayList<>();
        }

        return result;
    }

    private static final Map<FarmingPatchData.Kind, String> CATEGORY_NAMES = new EnumMap<>(FarmingPatchData.Kind.class);

    static {
        CATEGORY_NAMES.put(FarmingPatchData.Kind.HERB, "herb");
        CATEGORY_NAMES.put(FarmingPatchData.Kind.TREE, "tree");
        CATEGORY_NAMES.put(FarmingPatchData.Kind.FRUIT_TREE, "fruit_tree");
        CATEGORY_NAMES.put(FarmingPatchData.Kind.HARDWOOD_TREE, "hardwood_tree");
    }

    private static PatchTimerEntry decodeFarmingPatch(ConfigManager configManager, FarmingPatchData.Entry patch,
                                                        Integer offsetPrecision, Integer offsetMinutes) {
        String category = CATEGORY_NAMES.get(patch.kind);
        String stored = configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, patch.configKey());
        if (stored == null) {
            return new PatchTimerEntry(category, patch.label, "unknown", null, true, null);
        }

        String[] parts = stored.split(":");
        if (parts.length != 2) {
            return new PatchTimerEntry(category, patch.label, "unknown", null, true, null);
        }

        int value;
        long observedAt;
        try {
            value = Integer.parseInt(parts[0]);
            observedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return new PatchTimerEntry(category, patch.label, "unknown", null, true, null);
        }

        PatchImplementationReflector.DecodedPatch decoded = PatchImplementationReflector.decode(patch.kind.name(), value);
        if (decoded == null) {
            return new PatchTimerEntry(category, patch.label, "unknown", null, true, null);
        }

        Long readyAt = TickOffsetMath.estimateReadyAt(decoded, observedAt, offsetPrecision, offsetMinutes);
        String status = decoded.cropState.toLowerCase();
        Integer produceItemId = decoded.produceItemId >= 0 ? decoded.produceItemId : null;
        String label = produceItemId != null ? decoded.produceName + " - " + patch.label : patch.label;
        return new PatchTimerEntry(category, label, status, readyAt, false, produceItemId);
    }

    private static PatchTimerEntry decodeBirdHouse(ConfigManager configManager, BirdHouseSpaceData.Entry space) {
        String stored = configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, space.configKey());
        if (stored == null) {
            return new PatchTimerEntry("birdhouse", space.label, "unknown", null, true, null);
        }

        String[] parts = stored.split(":");
        if (parts.length != 2) {
            return new PatchTimerEntry("birdhouse", space.label, "unknown", null, true, null);
        }

        int varp;
        long observedAt;
        try {
            varp = Integer.parseInt(parts[0]);
            observedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return new PatchTimerEntry("birdhouse", space.label, "unknown", null, true, null);
        }

        if (varp <= 0) {
            return new PatchTimerEntry("birdhouse", space.label, "empty", null, false, null);
        }
        if (!BirdHouseSpaceData.isSeeded(varp)) {
            return new PatchTimerEntry("birdhouse", space.label, "built", null, false, null);
        }

        long readyAt = observedAt + BirdHouseSpaceData.DURATION_SECONDS;
        return new PatchTimerEntry("birdhouse", space.label, "seeded", readyAt, false, null);
    }

    private static Integer getIntConfig(ConfigManager configManager, String key) {
        try {
            return configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, key, int.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Object get() {
        return entries;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof TimeTrackingState)) return false;
        TimeTrackingState other = (TimeTrackingState) o;
        return Objects.equals(playerName, other.playerName) && Objects.equals(entries, other.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, entries);
    }
}
