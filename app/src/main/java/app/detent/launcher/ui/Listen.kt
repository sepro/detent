package app.detent.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import app.detent.launcher.Hear
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** The voice animation of the dial. The values ease, so the dial fades in and out of it. */
class VoiceWave {
    /** Seconds since Detent started to listen. */
    var t by mutableFloatStateOf(0f)

    /** 1 while Detent listens. */
    var on by mutableFloatStateOf(0f)

    /** The voice level from 0 to 1. */
    var level by mutableFloatStateOf(0f)

    /** 1 before the user speaks and while the user speaks. */
    var listen by mutableFloatStateOf(0f)

    /** 1 while the speech service turns the voice into words. */
    var think by mutableFloatStateOf(0f)

    /** How much the voice swells a mark that sits [d] apps from the notch. */
    fun swell(d: Float): Float {
        if (on < .002f) return 0f
        val a = abs(d)
        // Rings run out from the notch, and the voice level sets their height.
        val ring = .5f + .5f * sin(t * 7f - a * 1.5f)
        val talk = level * exp(-a / 2.6f) * (.35f + .65f * ring)
        val idle = listen * .12f * exp(-a / 1.4f) * (.5f + .5f * sin(t * 3.5f))
        // While the service thinks, one swell runs up and down the dial.
        val q = d - 3.5f * sin(t * 2.4f)
        val scan = think * .8f * exp(-q * q / .9f)
        return on * (talk + idle + scan)
    }
}

@Composable
fun rememberVoiceWave(s: LauncherState): VoiceWave {
    val w = remember { VoiceWave() }
    val hearing = s.hear != null
    LaunchedEffect(hearing) {
        if (hearing) w.t = 0f
        var last = 0L
        while (hearing || w.on > .002f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 16f else (now - last) / 1e6f
                last = now
                val e = 1f - .82f.pow(dt / 16f)
                val h = s.hear
                w.t += dt / 1000f
                w.on += ((if (h != null) 1f else 0f) - w.on) * e
                w.level += ((if (h == Hear.Talk) s.voiceLevel else 0f) - w.level) * (1f - .75f.pow(dt / 16f))
                w.listen += ((if (h == Hear.Wait || h == Hear.Talk) 1f else 0f) - w.listen) * e
                w.think += ((if (h == Hear.Think) 1f else 0f) - w.think) * e
            }
        }
        w.on = 0f
        w.level = 0f
        w.listen = 0f
        w.think = 0f
    }
    return w
}

/** The status line while Detent listens. */
fun hearStatus(s: LauncherState): String = when (s.hear) {
    Hear.Wait -> "starting"
    Hear.Think -> "thinking"
    Hear.Miss -> "no app heard · tap to try again"
    else -> "listening · tap to stop"
}

/**
 * While Detent listens, a tap anywhere on the screen stops the voice, or listens again after a miss.
 * The cancel button sits where search sits.
 */
@Composable
fun HearLayer(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val on = s.hear != null
    if (on) Box(Modifier.fillMaxSize().tap { s.hearTap() })
    val alpha by animateFloatAsState(if (on) 1f else 0f, tween(300), label = "hearAlpha")
    val dy by animateFloatAsState(if (on) 0f else 8f, tween(300), label = "hearY")
    if (alpha < .004f) return
    SideBox(s.left, u.searchY, 24f, Modifier.graphicsLayer { this.alpha = alpha; translationY = dy * u.k }) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.height(u.dp(44f)).clip(shape).background(pal.btnBg).border(u.dp(1f), pal.rim2, shape)
                .tap(enabled = on) { s.closeVoice() }
                .padding(start = u.dp(16f), end = u.dp(18f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            Canvas(Modifier.size(u.dp(10f))) {
                val w = 1.5f * u.k
                drawLine(pal.fg, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(pal.fg, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = w, cap = StrokeCap.Round)
            }
            Txt("cancel", 13f, pal.fg)
        }
    }
}
