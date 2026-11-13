package dev.lain.claudejb.controller.session

internal object StaleResume {

    fun startsFresh(exitCode: Int, resumed: Boolean, initialized: Boolean, transcriptExists: () -> Boolean): Boolean =
        exitCode != 0 && resumed && !initialized && !transcriptExists()
}
