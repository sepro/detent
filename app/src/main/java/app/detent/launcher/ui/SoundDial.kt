package app.detent.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import app.detent.launcher.Ease
import app.detent.launcher.LauncherState
import app.detent.launcher.Markers
import app.detent.launcher.Palette
import app.detent.launcher.SOUND_LAST
import app.detent.launcher.SOUND_R
import app.detent.launcher.SOUND_NOTCH
import app.detent.launcher.SOUND_SPAN
import app.detent.launcher.SOUND_STEP
import app.detent.launcher.SoundPreset
import app.detent.launcher.SoundReach
import app.detent.launcher.labelScale
import app.detent.launcher.soundLabelInset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Leaves fast, the way that the menus close. */
private val Leave = CubicBezierEasing(.4f, 0f, 1f, 1f)

/** Radius of the equalizer button. The rim grows out from just past its edge. */
private const val HUB_R = 22f

/**
 * The sound dial: the equalizer presets on a small dial around the equalizer button, in the style of the layout.
 * The notch sits at half past one, and the names point outward from the rim.
 */
@Composable
fun SoundDialView(s: LauncherState, pal: Palette) {
    val open = s.soundOpen
    // Opening: the rim grows out of the button, the marks fan out from the notch, and the names fade in last.
    val t by animateFloatAsState(
        if (open) 1f else 0f,
        if (open) tween(320, easing = Ease) else tween(200, easing = Leave),
        label = "soundOpen",
    )
    if (!open && t == 0f) return

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = t }.background(pal.veil))
    if (open) SoundGestures(s)
    SoundRing(s, pal, t)
    SoundHub(s, pal, t)
    SoundCaption(s, pal, t)
}

@Composable
private fun SoundGestures(s: LauncherState) {
    val u = LocalUnits.current
    val k by rememberUpdatedState(u.k)
    Box(
        Modifier.fillMaxSize().pointerInput(s) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                s.soundDown(down.position.x / k, down.position.y / k)
                var ended = false
                try {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.changedToUp() || !ch.pressed) {
                            s.soundUp(ch.position.x / k, ch.position.y / k)
                            ended = true
                            break
                        }
                        if (ch.positionChanged()) {
                            s.soundMove(ch.position.x / k, ch.position.y / k)
                            ch.consume()
                        }
                    }
                } finally {
                    if (!ended) s.soundCancel()
                }
            }
        },
    )
}

@Composable
private fun SoundRing(s: LauncherState, pal: Palette, t: Float) {
    val u = LocalUnits.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val regular = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Normal, spacing = .01f) }
    val medium = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Medium, spacing = .01f) }
    val layout = s.layout
    Canvas(Modifier.fillMaxSize()) {
        val k = u.k
        val m = layout.markers
        val tpa = layout.ticksPerApp
        val hx = s.soundHubX
        val hy = s.soundHubY
        val pos = s.soundPos
        val r = HUB_R + 8f + (SOUND_R - HUB_R - 8f) * t
        val spread = .45f + .55f * t
        val left = s.left
        // The unit vector from the hub outward at angle [ang]. A dial on the left edge mirrors it.
        fun outward(ang: Float) = Offset(if (left) -sin(ang) else sin(ang), -cos(ang))
        fun at(rad: Float, ang: Float) = Offset(hx * k, hy * k) + outward(ang) * (rad * k)

        if (m == Markers.Ticks || m == Markers.Nodes) {
            drawCircle(pal.rim, r * k, Offset(hx * k, hy * k), style = Stroke(k), alpha = t)
        }
        // Marks run from the first preset to the last. The ends of the dial show where it stops. As on the app dial,
        // the ticks and the inner arc point toward the centre.
        for (i in 0..SOUND_LAST * tpa) {
            val d = i.toFloat() / tpa - pos
            val a = abs(d)
            if (a > SOUND_SPAN) continue
            val ang = SOUND_NOTCH + d * SOUND_STEP * spread
            val fade = (1 - a / SOUND_SPAN).pow(.8f)
            drawMark(m, pal, k, at(r, ang), -outward(ang), i % tpa == 0, a, fade, alpha = t)
        }

        SoundPreset.entries.forEachIndexed { i, preset ->
            val d = i - pos
            val a = abs(d)
            if (a >= SOUND_SPAN) return@forEachIndexed
            val sel = a < .5f
            val scale = labelScale(a, preset.label.length)
            // The names keep their final places, so they never crowd on the small rim while it grows. They fade
            // in over the second half and slide out a little, so they arrive just after the marks.
            val arrive = ((t - .4f) / .6f).coerceIn(0f, 1f)
            val op = (1 - a / SOUND_SPAN).pow(1.35f) * arrive * arrive
            if (op < .004f) return@forEachIndexed
            val text = measurer.measure(preset.label, if (sel) medium else regular)
            val ang = SOUND_NOTCH + d * SOUND_STEP
            val start = at(SOUND_R + soundLabelInset(m) - 14f * (1 - arrive), ang)
            // A name stands at right angles to the rim and reads outward. On the left edge it reads toward the rim
            // and ends there, the way the app dial mirrors its names.
            val deg = Math.toDegrees(ang.toDouble()).toFloat()
            withTransform({
                translate(start.x, start.y)
                rotate(if (left) 90f - deg else deg - 90f, Offset.Zero)
                scale(scale, scale, Offset.Zero)
            }) {
                drawText(
                    text,
                    color = if (sel) pal.accent else pal.fg,
                    topLeft = Offset(if (left) -text.size.width.toFloat() else 0f, -text.size.height / 2f),
                    alpha = op,
                )
            }
        }

        drawNotchAt(m, pal, k, at(r, SOUND_NOTCH), -outward(SOUND_NOTCH), s.soundPawl, alpha = t)
    }
}

/** The equalizer button in the middle of the dial. It sits on top of the veil, right over the real button. */
@Composable
private fun SoundHub(s: LauncherState, pal: Palette, t: Float) {
    val u = LocalUnits.current
    Box(
        Modifier.place(u, s.soundHubX - HUB_R, s.soundHubY - HUB_R).size(u.dp(HUB_R * 2))
            .clip(CircleShape).background(pal.btnBg).border(u.dp(1f), lerp(pal.rim2, pal.accent, t), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        HeadphonesIcon(soundIconColor(s, pal))
    }
}

/** What the preset in the notch does, and how far the equalizer reaches on this phone. */
@Composable
private fun SoundCaption(s: LauncherState, pal: Palette, t: Float) {
    val u = LocalUnits.current
    val reach = when {
        s.soundPreset == SoundPreset.Off -> ""
        s.soundReach == SoundReach.AllSound -> "on for all sound"
        s.soundReach == SoundReach.MusicApps -> "this phone allows it only in music apps that ask for it"
        else -> ""
    }
    SideBox(
        // Above the names. A long name one row before the notch reaches about 245 units above the button, and the
        // caption is up to about 75 units tall.
        s.left, s.soundHubY - SOUND_R - 225f, 28f,
        Modifier.graphicsLayer { alpha = t; translationY = (1 - t) * 6f * u.k },
    ) {
        Column(
            horizontalAlignment = if (s.left) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(u.dp(6f)),
        ) {
            val align = if (s.left) TextAlign.End else TextAlign.Start
            Txt("equalizer", 12f, pal.muted2, spacing = .04f)
            Txt(s.soundSelected.about, 15f, pal.fg, align = align, maxLines = 2)
            Txt(reach, 12f, pal.muted2, align = align, maxLines = 2)
        }
    }
}

/** The headphones turn the accent colour while a preset plays. */
fun soundIconColor(s: LauncherState, pal: Palette): Color = if (s.soundPreset == SoundPreset.Off) pal.btnFg else pal.accent

/** Headphones: a headband over two ear cups. */
@Composable
fun HeadphonesIcon(color: Color) {
    val u = LocalUnits.current
    Canvas(Modifier.size(u.dp(14f))) {
        val k = u.k
        drawArc(
            color, 180f, 180f, false, Offset(1.75f * k, 2f * k), Size(10.5f * k, 10.5f * k),
            style = Stroke(1.5f * k, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(1.75f * k, 7.25f * k), Offset(1.75f * k, 9f * k), strokeWidth = 1.5f * k)
        drawLine(color, Offset(12.25f * k, 7.25f * k), Offset(12.25f * k, 9f * k), strokeWidth = 1.5f * k)
        val cup = Size(3.5f * k, 5f * k)
        val corner = CornerRadius(1.2f * k)
        drawRoundRect(color, Offset(.75f * k, 8f * k), cup, corner)
        drawRoundRect(color, Offset(9.75f * k, 8f * k), cup, corner)
    }
}
