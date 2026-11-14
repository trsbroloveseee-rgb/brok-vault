package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "NotificationEngine"
private const val CHANNEL_CHAT_ID = "broker_secure_chat_channel"
private const val CHANNEL_ALERT_ID = "broker_security_alert_channel"

/**
 * 4-Channel Real-Time Notification Engine:
 * 1. Programmatic PCM Audio Synthesizer (Two-tone pleasant chime for chat/OTP; Urgent 3-beep alarm for Admin blocks)
 * 2. Native System Notifications (NotificationCompat high-priority heads-up notification)
 * 3. Mobile Haptic Vibration (navigator.vibrate equivalent: [200, 100, 200])
 * 4. Flashing Header/Tab Title Controller (managed in ViewModel + UI banner)
 */
class NotificationEngine(private val context: Context) {

    private val audioScope = CoroutineScope(Dispatchers.Default)
    @Volatile
    var audioUnlocked: Boolean = false
        private set

    init {
        createNotificationChannels()
    }

    fun unlockAudioSession() {
        audioUnlocked = true
        // Play a subtle confirmation micro-chime when user taps "Join Secure Session"
        playSynthesizedTones(
            tones = listOf(ToneSpec(freqHz = 660.0, durationMs = 70), ToneSpec(freqHz = 880.0, durationMs = 110)),
            volume = 0.25f
        )
    }

    /**
     * Channel 1A: Pleasant Two-Tone Notification Chime (D5 587.33Hz -> A5 880Hz)
     */
    fun playIncomingMessageChime(muted: Boolean) {
        if (muted) return
        playSynthesizedTones(
            tones = listOf(
                ToneSpec(freqHz = 587.33, durationMs = 120),
                ToneSpec(freqHz = 0.0, durationMs = 35),
                ToneSpec(freqHz = 880.0, durationMs = 190)
            ),
            volume = 0.45f
        )
    }

    /**
     * Channel 1B: Urgent 3-Beep Admin Security Alarm (960Hz -> 720Hz -> 960Hz)
     */
    fun playAdminBlockedAlarm(muted: Boolean) {
        if (muted) return
        playSynthesizedTones(
            tones = listOf(
                ToneSpec(freqHz = 960.0, durationMs = 140),
                ToneSpec(freqHz = 0.0, durationMs = 50),
                ToneSpec(freqHz = 720.0, durationMs = 140),
                ToneSpec(freqHz = 0.0, durationMs = 50),
                ToneSpec(freqHz = 960.0, durationMs = 220)
            ),
            volume = 0.65f
        )
    }

    /**
     * Channel 2: Native System Notification
     */
    fun sendNativeNotification(
        title: String,
        body: String,
        isSecurityAlert: Boolean = false
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val channelId = if (isSecurityAlert) CHANNEL_ALERT_ID else CHANNEL_CHAT_ID
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Unable to post native notification", e)
        }
    }

    /**
     * Channel 3: Mobile Haptic Vibration ([200, 100, 200])
     */
    fun triggerHapticVibration(isUrgentAlert: Boolean = false) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) return

            val pattern = if (isUrgentAlert) {
                longArrayOf(0, 250, 100, 250, 100, 300)
            } else {
                longArrayOf(0, 200, 100, 200)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private data class ToneSpec(val freqHz: Double, val durationMs: Int)

    private fun playSynthesizedTones(tones: List<ToneSpec>, volume: Float) {
        audioScope.launch {
            var audioTrack: AudioTrack? = null
            try {
                val sampleRate = 24000
                val totalDurationMs = tones.sumOf { it.durationMs }
                val totalSamples = (sampleRate * totalDurationMs) / 1000
                if (totalSamples <= 0) return@launch

                val pcmBuffer = ShortArray(totalSamples)
                var offset = 0

                for (tone in tones) {
                    val toneSamples = (sampleRate * tone.durationMs) / 1000
                    val fadeSamples = (toneSamples * 0.15).toInt().coerceAtLeast(1)
                    for (i in 0 until toneSamples) {
                        if (offset + i >= totalSamples) break
                        if (tone.freqHz <= 0.0) {
                            pcmBuffer[offset + i] = 0
                        } else {
                            val envelope = when {
                                i < fadeSamples -> i.toFloat() / fadeSamples
                                i > toneSamples - fadeSamples -> (toneSamples - i).toFloat() / fadeSamples
                                else -> 1.0f
                            }
                            val angle = 2.0 * PI * i * tone.freqHz / sampleRate
                            val sample = (sin(angle) * Short.MAX_VALUE * volume * envelope).toInt()
                            pcmBuffer[offset + i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }
                    offset += toneSamples
                }

                val byteSize = pcmBuffer.size * 2
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteSize.coerceAtLeast(AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
                audioTrack.play()
                kotlinx.coroutines.delay(totalDurationMs.toLong() + 80L)
            } catch (e: Exception) {
                Log.w(TAG, "Audio synthesis playback error", e)
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val chatChannel = NotificationChannel(
                CHANNEL_CHAT_ID,
                "Secure Broker Credentials & OTP",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts when a Client or Supplier sends an email, password, or OTP code."
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Broker Admin Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts when the 2-Layer Anti-Stealing Filter blocks a suspicious message."
            }

            manager.createNotificationChannel(chatChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }
}
