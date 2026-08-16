package com.groupscape;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;

public class SpecialAttackState implements ConsumableState {
    @Getter
    private final int percent;
    private final transient String playerName;

    SpecialAttackState(String playerName, Client client) {
        this.playerName = playerName;
        // SPECIAL_ATTACK_PERCENT is stored in 0.1% increments (0-1000).
        this.percent = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
    }

    @Override
    public Object get() {
        return percent;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SpecialAttackState)) return false;

        SpecialAttackState other = (SpecialAttackState) o;
        return other.percent == percent;
    }
}
