package dev.bikram.filepipe.update

import javax.inject.Inject

/**
 * Compile / Hilt stub for the offline flavor. [BuildConfig.SHOW_UPDATES] is false, so the
 * Updates UI and scheduled checks never call this; the class exists so main's
 * [dev.bikram.filepipe.di.UpdateModule] can bind [UpdateChecker].
 */
class UpdateCheckerImpl
    @Inject
    constructor() : UpdateChecker {
        override suspend fun checkForUpdate(): UpdateInfo? = null
    }
