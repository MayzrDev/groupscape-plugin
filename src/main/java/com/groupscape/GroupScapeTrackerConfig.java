package com.groupscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("GroupScapeTracker")
public interface GroupScapeTrackerConfig extends Config {
    @ConfigSection(
            name = "Account Config",
            description = "Enter the API key from your account page on the website here",
            position = 0
    )
    String groupSection = "GroupSection";

    @ConfigSection(
            name = "Self Hosted Config",
            description = "Configure your connection to a self hosted server",
            position = 1,
            closedByDefault = true
    )
    String connectionSection = "ConnectionSection";

    @ConfigItem(
            keyName = "apiKey",
            name = "API Key",
            description = "Your account's secret API key. Get it from your account page on the website; the plugin uses it to authenticate every request and automatically link this character.",
            secret = true,
            section = groupSection
    )
    default String apiKey() {
        return "";
    }

    @ConfigItem(
            keyName = "baseUrlOverride",
            name = "Server base URL override (leave blank to use public server)",
            description = "Overrides the public server URL used to send data. Only change this if you are hosting your own server.",
            section = connectionSection
    )
    default String baseUrlOverride() {
        return "";
    }

    @ConfigSection(
            name = "Party frame overlay",
            description = "Configure the in-game overlay showing your group's vitals",
            position = 2
    )
    String partyOverlaySection = "PartyOverlaySection";

    enum PartyOverlaySortOrder {
        JOIN_ORDER,
        ALPHABETICAL,
        LOWEST_HP_FIRST
    }

    @ConfigItem(
            keyName = "partyOverlayHideOverlay",
            name = "Hide overlay",
            description = "Hide the entire party overlay",
            section = partyOverlaySection,
            position = 0
    )
    default boolean partyOverlayHideOverlay() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlaySortOrder",
            name = "Sort order",
            description = "How to order members in the party overlay",
            section = partyOverlaySection,
            position = 1
    )
    default PartyOverlaySortOrder partyOverlaySortOrder() {
        return PartyOverlaySortOrder.JOIN_ORDER;
    }

    @ConfigItem(
            keyName = "partyOverlayOfflineMembersLast",
            name = "Offline members last",
            description = "Always sort offline members to the bottom, regardless of sort order",
            section = partyOverlaySection,
            position = 2
    )
    default boolean partyOverlayOfflineMembersLast() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideOfflineMembers",
            name = "Hide offline members",
            description = "Hide members instead of showing them dimmed when offline",
            section = partyOverlaySection,
            position = 3
    )
    default boolean partyOverlayHideOfflineMembers() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideSelf",
            name = "Hide own row",
            description = "Don't show your own character in the party overlay",
            section = partyOverlaySection,
            position = 4
    )
    default boolean partyOverlayHideSelf() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideWorld",
            name = "Hide world",
            description = "Hide the world number next to each member's name",
            section = partyOverlaySection,
            position = 5
    )
    default boolean partyOverlayHideWorld() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideHp",
            name = "Hide HP bar",
            description = "Hide the hitpoints bar",
            section = partyOverlaySection,
            position = 6
    )
    default boolean partyOverlayHideHp() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHidePrayer",
            name = "Hide prayer bar",
            description = "Hide the prayer bar and active prayer icons",
            section = partyOverlaySection,
            position = 7
    )
    default boolean partyOverlayHidePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideRun",
            name = "Hide run energy bar",
            description = "Hide the run energy bar",
            section = partyOverlaySection,
            position = 8
    )
    default boolean partyOverlayHideRun() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideSpec",
            name = "Hide special attack bar",
            description = "Hide the special attack bar",
            section = partyOverlaySection,
            position = 9
    )
    default boolean partyOverlayHideSpec() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideTarget",
            name = "Hide target/activity line",
            description = "Hide the \"Fighting X\" / \"Talking to X\" / activity line",
            section = partyOverlaySection,
            position = 10
    )
    default boolean partyOverlayHideTarget() {
        return false;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "partyOverlayOpacity",
            name = "Overlay opacity",
            description = "Background opacity of the party overlay panel, from 0 (transparent) to 100 (solid)",
            section = partyOverlaySection,
            position = 11
    )
    default int partyOverlayOpacity() {
        return 85;
    }

    enum PartyOverlayScale {
        NORMAL,
        COMPACT
    }

    @ConfigItem(
            keyName = "partyOverlayScale",
            name = "Scale",
            description = "Compact tightens row/bar spacing to fit more members in less space",
            section = partyOverlaySection,
            position = 12
    )
    default PartyOverlayScale partyOverlayScale() {
        return PartyOverlayScale.NORMAL;
    }

    @Range(min = 0)
    @ConfigItem(
            keyName = "partyOverlayMaxMembers",
            name = "Max members shown",
            description = "Truncate the overlay to this many members (0 = unlimited)",
            section = partyOverlaySection,
            position = 13
    )
    default int partyOverlayMaxMembers() {
        return 0;
    }

    @ConfigSection(
            name = "Sync",
            description = "Controls for sending your data to the server",
            position = 3
    )
    String syncSection = "SyncSection";

    @ConfigItem(
            keyName = "pauseSync",
            name = "Pause sync",
            description = "Stop sending your data to the server without disabling the plugin",
            section = syncSection
    )
    default boolean pauseSync() {
        return false;
    }

    @ConfigSection(
            name = "Notifications",
            description = "Chat notifications for group events",
            position = 4
    )
    String notificationsSection = "NotificationsSection";

    @ConfigItem(
            keyName = "notifyMemberOnline",
            name = "Member came online",
            description = "Send a chat message when a group member comes online",
            section = notificationsSection
    )
    default boolean notifyMemberOnline() {
        return false;
    }

    @ConfigItem(
            keyName = "notifyMemberOffline",
            name = "Member went offline",
            description = "Send a chat message when a group member goes offline",
            section = notificationsSection
    )
    default boolean notifyMemberOffline() {
        return false;
    }

    @ConfigItem(
            keyName = "notifyLowHp",
            name = "Low HP warning",
            description = "Send a chat message when a group member's HP drops below the threshold below",
            section = notificationsSection
    )
    default boolean notifyLowHp() {
        return false;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "lowHpNotifyThreshold",
            name = "Low HP threshold %",
            description = "HP percentage that triggers the low HP warning",
            section = notificationsSection
    )
    default int lowHpNotifyThreshold() {
        return 25;
    }

    @ConfigItem(
            keyName = "notifyBossKill",
            name = "Boss kill",
            description = "Send a chat message when a group member kills a tracked boss",
            section = notificationsSection
    )
    default boolean notifyBossKill() {
        return false;
    }
}
