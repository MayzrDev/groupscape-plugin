package com.groupscape;

import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;

public class InteractingState implements ConsumableState {
    private final transient String playerName;
    @Getter
    private final String name;
    @Getter
    private final int scale;
    @Getter
    private final int ratio;
    @Getter
    private final LocationState location;

    public InteractingState(String playerName, Actor actor, Client client) {
        this.playerName = playerName;

        // Non-combat NPCs (bankers, quest givers, Tool Leprechauns, etc.) can flash a
        // stale/default healthbar ratio from RuneLite for a tick even though they're not
        // fightable - combat level isn't reliable since some non-attackable NPCs still report
        // one. Whether "Attack" is actually a menu option, matching LocalRosterMemberFactory's
        // target-bar logic, can't fluctuate tick to tick.
        boolean combatCapable = !(actor instanceof NPC) || isAttackable((NPC) actor);
        this.scale = combatCapable ? actor.getHealthScale() : 0;
        this.ratio = combatCapable ? actor.getHealthRatio() : -1;
        this.name = actor.getName();

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, actor.getLocalLocation());
        this.location = new LocationState(playerName, worldPoint, false);
    }

    private static boolean isAttackable(NPC npc) {
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) return false;
        for (String action : comp.getActions()) {
            if ("Attack".equalsIgnoreCase(action)) return true;
        }
        return false;
    }

    @Override
    public Object get() {
        return this;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof InteractingState)) return false;

        // NOTE: For interactions, we want to keep sending the data until the player stops interacting
        // even if nothing changed about what is being interacted with. The UI will handle not showing
        // the interaction once it goes stale from the player not interacting with anything.
        return false;
    }
}
