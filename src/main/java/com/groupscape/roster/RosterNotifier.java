package com.groupscape.roster;

import com.groupscape.GroupScapeTrackerConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects notification-worthy transitions in the roster (a member coming online/offline, a
 * member's HP dropping below the configured threshold) and returns chat message strings for the
 * plugin to send, so all {@code ChatMessageManager} usage stays centralized there. Mirrors the
 * armed/rearm hysteresis idiom {@code GroupScapeTrackerPlugin.checkLowHpAlert} already uses for
 * the local player's own HP.
 */
public class RosterNotifier {
    private static final double LOW_HP_REARM_MULTIPLIER = 2.0;

    private final Map<String, Boolean> onlineByName = new HashMap<>();
    private final Map<String, Boolean> lowHpArmedByName = new HashMap<>();

    public List<String> check(List<RosterMember> members, String localPlayerName, GroupScapeTrackerConfig config) {
        List<String> messages = new ArrayList<>();

        for (RosterMember member : members) {
            if (member.name.equalsIgnoreCase(localPlayerName)) {
                continue;
            }
            checkOnlineOffline(member, config, messages);
            checkLowHp(member, config, messages);
        }

        return messages;
    }

    private void checkOnlineOffline(RosterMember member, GroupScapeTrackerConfig config, List<String> messages) {
        boolean isOnline = !isOffline(member);
        Boolean wasOnline = onlineByName.put(member.name, isOnline);

        // First observation of this member - nothing to compare against, so don't fire a
        // notification burst the moment the roster/plugin connects.
        if (wasOnline == null) {
            return;
        }

        if (wasOnline && !isOnline && config.notifyMemberOffline()) {
            messages.add(member.name + " has gone offline.");
        } else if (!wasOnline && isOnline && config.notifyMemberOnline()) {
            messages.add(member.name + " has come online.");
        }
    }

    private void checkLowHp(RosterMember member, GroupScapeTrackerConfig config, List<String> messages) {
        if (member.hp == null || member.maxHp == null || member.maxHp <= 0) {
            return;
        }

        double ratio = (double) member.hp / member.maxHp;
        double threshold = config.lowHpNotifyThreshold() / 100.0;
        double rearmRatio = Math.min(threshold * LOW_HP_REARM_MULTIPLIER, 1.0);
        boolean armed = lowHpArmedByName.getOrDefault(member.name, true);

        if (armed && ratio < threshold) {
            lowHpArmedByName.put(member.name, false);
            if (config.notifyLowHp()) {
                messages.add(member.name + " is low on HP! (" + member.hp + "/" + member.maxHp + ")");
            }
        } else if (!armed && ratio >= rearmRatio) {
            lowHpArmedByName.put(member.name, true);
        }
    }

    private boolean isOffline(RosterMember member) {
        if (member.lastHeartbeatAt == null) {
            return true;
        }
        return Instant.now().toEpochMilli() - member.lastHeartbeatAt.toEpochMilli() >= RosterMember.OFFLINE_THRESHOLD_MS;
    }
}
