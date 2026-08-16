package com.groupscape;

/**
 * Group tokens issued by the website are formatted as "{groupName}|{secret}" so the
 * plugin can derive the group name locally without asking the user to enter it separately.
 */
public final class GroupToken {
    private GroupToken() {
    }

    public static String parseGroupName(String token) {
        if (token == null) {
            return null;
        }

        String trimmed = token.trim();
        int separatorIndex = trimmed.indexOf('|');
        if (separatorIndex <= 0) {
            return null;
        }

        return trimmed.substring(0, separatorIndex);
    }
}
