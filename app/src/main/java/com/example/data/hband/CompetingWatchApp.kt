package com.example.data.hband

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The official H Band app (com.veepoo.hband) holds the VE30 GATT session and
 * fights this companion for the same watch. Patients see a plain-language
 * warning — never GATT/SDK jargon.
 */
object CompetingWatchApp {
    const val HBAND_PACKAGE = "com.veepoo.hband"

    const val MSG_RUNNING =
        "O aplicativo H Band está aberto e usando a pulseira. Feche o H Band para este app conseguir conectar."
    const val MSG_INSTALLED =
        "O aplicativo H Band também está neste celular e pode impedir a pulseira de conectar. Feche o H Band e volte aqui."

    fun warningText(installed: Boolean, running: Boolean): String? = when {
        running -> MSG_RUNNING
        installed -> MSG_INSTALLED
        else -> null
    }

    fun isInstalled(context: Context, packageName: String = HBAND_PACKAGE): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(context: Context, packageName: String = HBAND_PACKAGE): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return try {
            @Suppress("DEPRECATION")
            manager.runningAppProcesses.orEmpty().any { process ->
                val name = process.processName ?: return@any false
                name == packageName || name.startsWith("$packageName:")
            }
        } catch (_: Exception) {
            false
        }
    }

    fun warningFor(context: Context): String? {
        val installed = isInstalled(context)
        val running = installed && isRunning(context)
        return warningText(installed, running)
    }
}
