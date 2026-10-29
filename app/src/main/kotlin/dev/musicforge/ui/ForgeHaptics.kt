package dev.musicforge.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Haptics through the Vibrator, not View.performHapticFeedback: MagicOS (the X7b) drops view
 * haptics unless the system "touch vibration" setting is on, so long-press ticks never arrived.
 * Provided as LocalHapticFeedback at the root, so every call site works unchanged, and the
 * Haptics setting is applied here once. Short and polite, like a tap on the shoulder, not a shove.
 */
class ForgeHaptics(context: Context, private val enabled: () -> Boolean) : HapticFeedback {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val v = vibrator?.takeIf { enabled() && it.hasVibrator() } ?: return
        val strong = hapticFeedbackType == HapticFeedbackType.LongPress
        runCatching {
            when {
                Build.VERSION.SDK_INT >= 29 ->
                    v.vibrate(VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK))
                else -> v.vibrate(VibrationEffect.createOneShot(if (strong) 25L else 12L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
