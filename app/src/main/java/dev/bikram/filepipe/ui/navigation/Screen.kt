package dev.bikram.filepipe.ui.navigation

sealed class Screen(val route: String) {
    data object Rules : Screen("rules")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Faq : Screen("faq?focusSection={focusSection}") {
        const val ARG_FOCUS_SECTION = "focusSection"
        const val FOCUS_STORAGE_ACCESS = "storage"

        /** Use for [composable] route registration and [NavController.navigate]. */
        fun createRoute(focusSection: String = ""): String = "faq?focusSection=$focusSection"
    }

    data object RuleDetail : Screen(
        "rule_detail/{ruleId}?templateIndex={templateIndex}&skipTemplatePicker={skipTemplatePicker}"
    ) {
        fun createRoute(
            ruleId: Long = NEW_RULE_ID,
            templateIndex: Int = -1,
            skipTemplatePicker: Boolean = false
        ): String {
            val queryParts = mutableListOf<String>()
            if (templateIndex >= 0) queryParts += "templateIndex=$templateIndex"
            if (skipTemplatePicker) queryParts += "skipTemplatePicker=true"
            return if (queryParts.isEmpty()) {
                "rule_detail/$ruleId"
            } else {
                "rule_detail/$ruleId?${queryParts.joinToString("&")}"
            }
        }
        const val ARG_RULE_ID = "ruleId"
        const val ARG_TEMPLATE_INDEX = "templateIndex"
        const val ARG_SKIP_TEMPLATE_PICKER = "skipTemplatePicker"
        const val NEW_RULE_ID = -1L
    }

    data object HistoryDetail : Screen("history_detail/{historyId}") {
        fun createRoute(historyId: Long) = "history_detail/$historyId"
        const val ARG_HISTORY_ID = "historyId"
    }

    data object HistoryForRule : Screen("history_for_rule/{ruleId}") {
        fun createRoute(ruleId: Long) = "history_for_rule/$ruleId"
        const val ARG_RULE_ID = "ruleId"
    }

    data object OnboardingTitle : Screen("onboarding_title")

    data object OnboardingPermissions : Screen("onboarding_permissions")

    data object OnboardingRuleWizard : Screen("onboarding_rule_wizard")
}
