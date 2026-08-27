package com.groupscape.roster;

import java.util.List;

/**
 * Mirrors the server's {@code GroupMember} (groupscape-web/server/src/models.rs), as returned by
 * {@code GET /api/characters/{accountHash}/get-group-data}. That handler is the plugin-facing
 * mount (same per-character API-key auth as the roster websocket and the rest of DataManager's
 * uploads) of the exact same {@code get_group_data} the website's group dashboard reads from -
 * see main.rs's {@code character_scope}. Field names are left snake_case to match the server's
 * un-renamed serde output verbatim, so Gson's default reflection can deserialize without extra
 * annotations (same convention as {@link RosterWireTypes}).
 *
 * <p>Only the fields the sidepanel actually renders are declared here; the response carries many
 * more (bank, quests, diary_vars, ...) that Gson silently ignores.
 */
public class GroupSnapshotWireTypes {
    public static class GroupMemberWire {
        public String name;
        public String color;
        /** Flat [id, quantity, id, quantity, ...] pairs, one pair per inventory slot (28), empty slots are id 0. */
        public List<Integer> inventory;
        /** Flat [id, quantity, ...] pairs indexed by {@code EquipmentInventorySlot#getSlotIdx()}. */
        public List<Integer> equipment;
        /** XP per skill, ordered per {@code SkillOrder#SKILLS} (alphabetical, Overall excluded). */
        public List<Integer> skills;
        public String last_updated;
    }
}
