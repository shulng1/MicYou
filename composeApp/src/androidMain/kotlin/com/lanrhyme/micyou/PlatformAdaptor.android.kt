package com.lanrhyme.micyou

actual object PlatformAdaptor {
    actual fun configureAudioOutput(): Any? = null
    actual fun restoreAudioOutput(token: Any?) {}
    actual suspend fun runAdbReverse(port: Int): Boolean = true // Android doesn't need reverse
    actual fun cleanupTempFiles() {}
    actual val usesSystemAudioSinkForVirtualOutput: Boolean = false
}
