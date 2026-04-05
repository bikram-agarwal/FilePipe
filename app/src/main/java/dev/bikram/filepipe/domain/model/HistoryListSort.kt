package dev.bikram.filepipe.domain.model

enum class HistorySortKey {
    LAST_RAN,
    RULE_NAME,
    /** Rules list only: persisted [Rule.sortOrder]. */
    MY_ORDER,
}

enum class HistorySortDirection {
    ASCENDING,
    DESCENDING,
}
