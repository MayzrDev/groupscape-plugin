package com.groupscape;

/**
 * Notified when the server rejects a request because this character isn't linked to a group yet
 * (HTTP 403), and again once a request subsequently succeeds. Shared between every client that
 * talks to a `/api/characters/{accountHash}/...` route ({@link com.groupscape.roster.RosterClient}'s
 * websocket, {@link DataManager}'s telemetry POSTs) so a single debounced warning covers all of
 * them instead of each failing silently on its own.
 */
public interface GroupLinkListener {
    void onLinkRequired();
    void onLinked();
}
