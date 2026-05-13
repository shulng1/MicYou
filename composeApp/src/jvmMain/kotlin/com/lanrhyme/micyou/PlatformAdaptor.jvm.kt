package com.lanrhyme.micyou

import com.lanrhyme.micyou.platform.AdbManager
import com.lanrhyme.micyou.platform.PlatformInfo
import java.io.File

actual object PlatformAdaptor {
    actual fun configureAudioOutput(): Any? {
        return null
    }

    actual fun restoreAudioOutput(token: Any?) {
    }
    
    actual suspend fun runAdbReverse(port: Int): Boolean {
        return AdbManager.runAdbReverse(port)
    }
    
    actual fun cleanupTempFiles() {
        val tempDir = System.getProperty("java.io.tmpdir") ?: return
        val tempDirFile = File(tempDir)
        
        tempDirFile.listFiles()?.forEach { file ->
            if (file.name.startsWith("vbcable_") || 
                file.name.startsWith("micyou_") ||
                file.name.startsWith("setdefaultmic")) {
                try {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    actual val usesSystemAudioSinkForVirtualOutput: Boolean
        get() = PlatformInfo.isLinux
}
