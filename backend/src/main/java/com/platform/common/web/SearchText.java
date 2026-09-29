package com.platform.common.web;

/** Free-text search in lists: what a person types, turned into a safe SQL pattern. */
public final class SearchText {

    private SearchText() {
    }

    /**
     * A case-insensitive "contains" pattern for LIKE/ILIKE with the default escape character,
     * so a typed %, _ or \ matches itself. Null when there is nothing to search for.
     */
    public static String containsPattern(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.strip().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
