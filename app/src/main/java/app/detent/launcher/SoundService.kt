package app.detent.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** How far the equalizer reaches on this phone. */
enum class SoundReach {
    /** No preset is on. */
    Idle,

    /** The effect sits on the phone's output, so all sound goes through it. */
    AllSound,

    /** The phone refused the output, so the effect only reaches music apps that announce their sound. */
    MusicApps,
}

/** Shared between the service and the home screen, which run in the same process. */
object Sound {
    /** The preset, when something other than the home screen changes it, such as `off` in the notification. */
    val preset = MutableStateFlow<SoundPreset?>(null)
    val reach = MutableStateFlow(SoundReach.Idle)
}

/**
 * Keeps the equalizer preset on while you use other apps. An audio effect lives only as long as the process that
 * made it, and Android may close a home screen in the background. A foreground service keeps the process alive,
 * and the price is a notification while a preset is on.
 */
class SoundService : Service() {
    /** The effects by audio session. Session 0 is the phone's output. */
    private val effects = HashMap<Int, DynamicsProcessing>()
    private var preset = SoundPreset.Off
    private var onOutput = false

    // Music apps announce each audio session they open. The service needs them only when the output is refused.
    private val sessions = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, OUTPUT)
            if (session == OUTPUT || onOutput || preset == SoundPreset.Off) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ->
                    if (session !in effects) attach(session)?.let { effects[session] = it; tune(it, preset) }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> effects.remove(session)?.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        // The announcements come from other apps, so the receiver must be exported.
        ContextCompat.registerReceiver(this, sessions, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        if (intent?.action == ACTION_OFF) {
            prefs.soundPreset = SoundPreset.Off
            Sound.preset.value = SoundPreset.Off
        }
        val next = prefs.soundPreset
        // The service enters the foreground before anything else, while the home screen that started it is still in
        // front, and only then leaves when the preset turned off in the meantime.
        if (!enterForeground(next) || next == SoundPreset.Off) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        apply(next)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(sessions)
        effects.values.forEach { it.release() }
        effects.clear()
        Sound.reach.value = SoundReach.Idle
        super.onDestroy()
    }

    private fun apply(next: SoundPreset) {
        preset = next
        if (effects.isEmpty()) attach(OUTPUT)?.let { effects[OUTPUT] = it }
        onOutput = OUTPUT in effects
        effects.values.forEach { tune(it, next) }
        Sound.reach.value = if (onOutput) SoundReach.AllSound else SoundReach.MusicApps
    }

    /** A new effect on [session], or null when the phone refuses it. */
    private fun attach(session: Int): DynamicsProcessing? {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            true, EQ_EDGES.size, // equalizer before the compressor
            false, 0, // no multiband compressor
            false, 0, // no equalizer after the compressor
            true, // the limiter
        ).build()
        return try {
            val effect = DynamicsProcessing(0, session, config)
            if (effect.setEnabled(true) == AudioEffect.SUCCESS) effect else null.also { effect.release() }
        } catch (e: RuntimeException) {
            // Phones that do not allow an effect on the output, or at all, throw here.
            null
        }
    }

    private fun tune(effect: DynamicsProcessing, p: SoundPreset) {
        try {
            val eq = DynamicsProcessing.Eq(true, true, EQ_EDGES.size)
            EQ_EDGES.forEachIndexed { i, edge -> eq.setBand(i, DynamicsProcessing.EqBand(true, edge, p.gains[i])) }
            val l = p.limit
            val limiter = DynamicsProcessing.Limiter(true, true, 0, l.attackMs, l.releaseMs, l.ratio, l.threshold, l.postGain)
            effect.setInputGainAllChannelsTo(p.inputGain)
            effect.setPreEqAllChannelsTo(eq)
            effect.setLimiterAllChannelsTo(limiter)
        } catch (e: RuntimeException) {
            // Another app with a higher priority can take the effect over. It then ignores new settings.
        }
    }

    /** Shows the notification. Returns false when Android does not allow a foreground service right now. */
    private fun enterForeground(p: SoundPreset): Boolean {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(p), type)
            true
        } catch (e: RuntimeException) {
            // Android refuses a foreground service started from the background, for example on a restart.
            // Detent starts it again the next time the home screen shows.
            false
        }
    }

    private fun notification(p: SoundPreset): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Equalizer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while an equalizer preset is on."
                setShowBadge(false)
            },
        )
        val home = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val off = PendingIntent.getService(
            this, 1, Intent(this, SoundService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_equalizer)
            .setContentTitle("equalizer: ${p.label}")
            .setContentText(p.about)
            .setContentIntent(home)
            .addAction(Notification.Action.Builder(null, "off", off).build())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private companion object {
        const val OUTPUT = 0
        const val CHANNELS = 2
        const val CHANNEL = "equalizer"
        const val NOTIFICATION_ID = 7
        const val ACTION_OFF = "app.detent.launcher.SOUND_OFF"
    }
}
