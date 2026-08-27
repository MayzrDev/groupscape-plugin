package com.groupscape.sidepanel;

import net.runelite.api.Skill;

/**
 * The 24-skill order the server's {@code skills} array uses (alphabetical by name, Overall
 * excluded - it's derived client-side as the XP sum). Matches the website's
 * {@code GroupData.transformSkillsFromStorage} (site/src/data/group-data.js), which walks
 * {@code Object.keys(SkillName)} - itself alphabetical because that's declaration order in
 * site/src/data/skill.js.
 */
final class SkillOrder {
    static final Skill[] SKILLS = {
            Skill.AGILITY, Skill.ATTACK, Skill.CONSTRUCTION, Skill.COOKING, Skill.CRAFTING,
            Skill.DEFENCE, Skill.FARMING, Skill.FIREMAKING, Skill.FISHING, Skill.FLETCHING,
            Skill.HERBLORE, Skill.HITPOINTS, Skill.HUNTER, Skill.MAGIC, Skill.MINING,
            Skill.PRAYER, Skill.RANGED, Skill.RUNECRAFT, Skill.SLAYER, Skill.SMITHING,
            Skill.STRENGTH, Skill.THIEVING, Skill.WOODCUTTING, Skill.SAILING,
    };

    /** Layout order for the 3-column skill grid, matching site/src/player-skills/player-skills.js. */
    static final Skill[] GRID_ORDER = {
            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
            Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
            Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
            Skill.RANGED, Skill.THIEVING, Skill.COOKING,
            Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
            Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
            Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
            Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
    };

    private SkillOrder() {
    }
}
