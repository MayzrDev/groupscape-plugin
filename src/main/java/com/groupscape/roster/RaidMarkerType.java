package com.groupscape.roster;

import java.awt.Color;

/**
 * The fixed set of raid-callout marker types (see {@link RaidMarkerManager}). The four "standard"
 * types have a real OSRS wiki icon bundled as a plugin resource and a display color shared with the
 * web app's palette (kept in sync with {@code main.css}'s {@code --clue-hard}/{@code
 * --rarity-uncommon}/{@code --clue-master}/{@code --rarity-very-rare} tokens). The 1-4/A-D callout
 * types have no wiki icon to draw on - {@code iconResource} is {@code null} for those and {@link
 * RaidMarkerIcons} falls back to drawing the {@link #displayName} as a text glyph instead.
 */
public enum RaidMarkerType {
    DANGER("Danger", "danger", "#e0574f", "raid-marker-danger.png"),
    DEFEND("Defend", "defend", "#4caf50", "raid-marker-defend.png"),
    LOOT("Loot", "loot", "#d4af37", "raid-marker-loot.png"),
    FOCUS("Focus / Kill Target", "focus", "#a855f7", "raid-marker-focus.png"),

    A("A", "a", "#f97316", null),
    B("B", "b", "#ec4899", null),
    C("C", "c", "#84cc16", null),
    D("D", "d", "#64748b", null),

    ONE("1", "one", "#3b82f6", null),
    TWO("2", "two", "#0ea5e9", null),
    THREE("3", "three", "#14b8a6", null),
    FOUR("4", "four", "#6366f1", null);

    public final String displayName;
    /** The value sent over the wire - matches the server's {@code websocket::MarkerType} snake_case rename. */
    public final String wireValue;
    public final String hexColor;
    /** A bundled plugin resource path, or {@code null} for a type with no real wiki icon (drawn as a text glyph instead). */
    public final String iconResource;

    RaidMarkerType(String displayName, String wireValue, String hexColor, String iconResource) {
        this.displayName = displayName;
        this.wireValue = wireValue;
        this.hexColor = hexColor;
        this.iconResource = iconResource;
    }

    public Color color() {
        return Color.decode(hexColor);
    }

    public static RaidMarkerType fromWireValue(String wireValue) {
        for (RaidMarkerType type : values()) {
            if (type.wireValue.equals(wireValue)) {
                return type;
            }
        }
        return null;
    }
}
