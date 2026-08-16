package com.groupscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("GroupScapeTracker")
public interface GroupScapeTrackerConfig extends Config {
    @ConfigSection(
            name = "Group Config",
            description = "Enter the group details you created on the website here",
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
            keyName = "groupName",
            name = "Group Name (on the website)",
            description = "This is the group name you provided on the website when creating your group",
            section = groupSection
    )
    default String groupName() {
        return "";
    }

    @ConfigItem(
            keyName = "groupToken",
            name = "Group Token",
            description = "Secret token for your group provided by the website. Get this from the member which created the group on the site, or create a new one by visiting the site.",
            secret = true,
            section = groupSection
    )
    default String authorizationToken() {
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
            keyName = "partyOverlaySortOrder",
            name = "Sort order",
            description = "How to order members in the party overlay",
            section = partyOverlaySection
    )
    default PartyOverlaySortOrder partyOverlaySortOrder() {
        return PartyOverlaySortOrder.JOIN_ORDER;
    }

    @ConfigItem(
            keyName = "partyOverlayOfflineMembersLast",
            name = "Offline members last",
            description = "Always sort offline members to the bottom, regardless of sort order",
            section = partyOverlaySection
    )
    default boolean partyOverlayOfflineMembersLast() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideOfflineMembers",
            name = "Hide offline members",
            description = "Hide members instead of showing them dimmed when offline",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideOfflineMembers() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideSelf",
            name = "Hide own row",
            description = "Don't show your own character in the party overlay",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideSelf() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideWorld",
            name = "Hide world",
            description = "Hide the world number next to each member's name",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideWorld() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideHp",
            name = "Hide HP bar",
            description = "Hide the hitpoints bar",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideHp() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHidePrayer",
            name = "Hide prayer bar",
            description = "Hide the prayer bar and active prayer icons",
            section = partyOverlaySection
    )
    default boolean partyOverlayHidePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideRun",
            name = "Hide run energy bar",
            description = "Hide the run energy bar",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideRun() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideSpec",
            name = "Hide special attack bar",
            description = "Hide the special attack bar",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideSpec() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideTarget",
            name = "Hide target/activity line",
            description = "Hide the \"Fighting X\" / \"Talking to X\" / activity line",
            section = partyOverlaySection
    )
    default boolean partyOverlayHideTarget() {
        return false;
    }
}
