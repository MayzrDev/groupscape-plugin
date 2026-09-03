package com.groupscape;

import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarPlayerID;

import java.util.HashMap;
import java.util.Map;

/**
 * Tier completion is a per-tier varbit (set once every task in the tier is done and the
 * reward claimed); per-task completion is a packed bitset spread across 21 varps
 * (net.runelite.api.gameval.VarPlayerID#CA_TASK_COMPLETED_0..20, 32 bits each, task id =
 * varp index * 32 + bit index). Both are read passively off client state - no CA log needs
 * to be open.
 */
public class CombatAchievementState implements ConsumableState {
    static final int[] CA_TASK_COMPLETED_VARPS = {
            VarPlayerID.CA_TASK_COMPLETED_0,
            VarPlayerID.CA_TASK_COMPLETED_1,
            VarPlayerID.CA_TASK_COMPLETED_2,
            VarPlayerID.CA_TASK_COMPLETED_3,
            VarPlayerID.CA_TASK_COMPLETED_4,
            VarPlayerID.CA_TASK_COMPLETED_5,
            VarPlayerID.CA_TASK_COMPLETED_6,
            VarPlayerID.CA_TASK_COMPLETED_7,
            VarPlayerID.CA_TASK_COMPLETED_8,
            VarPlayerID.CA_TASK_COMPLETED_9,
            VarPlayerID.CA_TASK_COMPLETED_10,
            VarPlayerID.CA_TASK_COMPLETED_11,
            VarPlayerID.CA_TASK_COMPLETED_12,
            VarPlayerID.CA_TASK_COMPLETED_13,
            VarPlayerID.CA_TASK_COMPLETED_14,
            VarPlayerID.CA_TASK_COMPLETED_15,
            VarPlayerID.CA_TASK_COMPLETED_16,
            VarPlayerID.CA_TASK_COMPLETED_17,
            VarPlayerID.CA_TASK_COMPLETED_18,
            VarPlayerID.CA_TASK_COMPLETED_19,
            VarPlayerID.CA_TASK_COMPLETED_20,
    };

    private final transient String playerName;
    private final Map<String, Boolean> tiers;
    private final int[] taskVarpValues = new int[CA_TASK_COMPLETED_VARPS.length];

    public CombatAchievementState(String playerName, Client client) {
        this.playerName = playerName;

        tiers = new HashMap<>();
        tiers.put("easy", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_EASY) != 0);
        tiers.put("medium", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM) != 0);
        tiers.put("hard", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_HARD) != 0);
        tiers.put("elite", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE) != 0);
        tiers.put("master", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER) != 0);
        tiers.put("grandmaster", client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_GRANDMASTER) != 0);

        for (int i = 0; i < CA_TASK_COMPLETED_VARPS.length; ++i) {
            taskVarpValues[i] = client.getVarpValue(CA_TASK_COMPLETED_VARPS[i]);
        }
    }

    @Override
    public Object get() {
        Map<String, Object> out = new HashMap<>();
        out.put("tiers", tiers);

        Map<String, Boolean> tasks = new HashMap<>();
        for (int varpIndex = 0; varpIndex < taskVarpValues.length; varpIndex++) {
            int bits = taskVarpValues[varpIndex];
            for (int bit = 0; bit < 32; bit++) {
                boolean done = (bits & (1 << bit)) != 0;
                if (done) {
                    int taskId = varpIndex * 32 + bit;
                    tasks.put(String.valueOf(taskId), true);
                }
            }
        }
        out.put("tasks", tasks);

        return out;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof CombatAchievementState)) return false;

        CombatAchievementState other = (CombatAchievementState) o;
        if (!tiers.equals(other.tiers)) return false;

        for (int i = 0; i < taskVarpValues.length; ++i) {
            if (taskVarpValues[i] != other.taskVarpValues[i]) return false;
        }

        return true;
    }
}
