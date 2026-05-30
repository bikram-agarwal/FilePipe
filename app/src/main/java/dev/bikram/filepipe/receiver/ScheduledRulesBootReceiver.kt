package dev.bikram.filepipe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduledRulesBootReceiver : BroadcastReceiver() {
    @Inject lateinit var ruleRepository: RuleRepository

    @Inject lateinit var scheduleRulesUseCase: ScheduleRulesUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action !in RESCHEDULE_ACTIONS) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduledRules =
                    ruleRepository
                        .getEnabledRules()
                        .filter { rule -> rule.schedule != null }
                scheduledRules.forEach { rule ->
                    scheduleRulesUseCase.scheduleRule(rule)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val RESCHEDULE_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
