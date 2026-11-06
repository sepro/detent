package app.detent.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import app.detent.launcher.AX
import app.detent.launcher.Geo
import app.detent.launcher.LauncherState
import app.detent.launcher.Layout
import app.detent.launcher.Markers
import app.detent.launcher.Palette
import app.detent.launcher.Step
import app.detent.launcher.THEME_NAMES
import app.detent.launcher.W
import app.detent.launcher.labelScale
import app.detent.launcher.labelPose
import app.detent.launcher.mod
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun DialCanvas(s: LauncherState, pal: Palette, wave: VoiceWave) {
    val u = LocalUnits.current
    val measurer = rememberTextMeasurer(cacheSize = 160)
    val regular = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Normal, spacing = .01f) }
    val medium = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Medium, spacing = .01f) }

    val scrub = s.scrub
    val themeStep = s.ob == Step.THEME
    val apps = s.dialApps
    val names = scrub ?: if (themeStep) THEME_NAMES else remember(apps) { apps.map { it.display } }
    val dots = if (scrub == null && !themeStep) apps.map { s.hasDot(it) } else null
    val o = s.open
    val hide = if (o != null && o.fromDial && dots != null) apps.indexOfFirst { it.key == o.key } else -1
    val layout = s.layout
    val left = s.left
    // The app dial steps back behind the sound dial, so the two never compete.
    val alpha by animateFloatAsState(
        when {
            s.ob == Step.WELCOME || s.ob == Step.APPS -> 0f
            s.soundOpen -> .2f
            else -> 1f
        },
        tween(if (s.soundOpen) 250 else 500), label = "dialAlpha",
    )

    Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        val g = Geo(s.radius, u.cy)
        drawMarks(s.pos, g, u.k, pal, layout, left, wave)
        drawLabels(s.pos, g, u.k, pal, layout, left, names, dots, hide, scrub != null, measurer, regular, medium)
        drawNotch(s.pawl, g, u.k, pal, layout.markers, left)
    }
}

private fun DrawScope.drawMarks(pos: Float, g: Geo, k: Float, pal: Palette, layout: Layout, left: Boolean, wave: VoiceWave) {
    val m = layout.markers
    fun x(v: Float) = (if (left) W - v else v) * k
    if (m == Markers.Ticks || m == Markers.Nodes) {
        drawCircle(pal.rim, g.r * k, Offset(x(g.cx), g.cy * k), style = Stroke(k))
    }
    val tpa = layout.ticksPerApp
    val first = ceil((pos - g.span) * tpa).toInt()
    val last = floor((pos + g.span) * tpa).toInt()
    for (i in first..last) {
        val d = i.toFloat() / tpa - pos
        val a = abs(d)
        if (a > g.span) continue
        val ang = d * g.step
        val px = g.cx - g.r * cos(ang)
        val py = g.cy + g.r * sin(ang)
        val fade = (1 - a / g.span).pow(.8f)
        // The names sit inside the rim, toward the centre of the dial.
        val dir = Offset(if (left) -cos(ang) else cos(ang), -sin(ang))
        // While Detent listens, the voice swells the marks.
        drawMark(m, pal, k, Offset(x(px), py * k), dir, mod(i, tpa) == 0, a, fade, wave.swell(d))
    }
}

/**
 * One mark of a dial in the style of [m]. [p] is the mark on the rim in pixels, and [dir] is the unit vector from
 * the rim toward the names. [a] is the distance from the notch in rows, [fade] the fade toward the end of the dial,
 * and [v] the voice swell from 0 to 1. [alpha] fades the whole mark.
 */
internal fun DrawScope.drawMark(
    m: Markers,
    pal: Palette,
    k: Float,
    p: Offset,
    dir: Offset,
    major: Boolean,
    a: Float,
    fade: Float,
    v: Float = 0f,
    alpha: Float = 1f,
) {
    val hot = major && a < .5f
    // A strong swell takes the accent colour.
    val hue = (v * 1.4f).coerceIn(0f, 1f)
    when (m) {
        Markers.Ticks -> {
            val w = (if (major) 14f else 6f) + v * 18f
            drawLine(
                lerp(if (major) pal.tickMaj else pal.tickMin, pal.accent, hue),
                p,
                p + dir * (w * k),
                strokeWidth = k * (1 + v),
                alpha = max(fade, hue) * alpha,
            )
        }
        Markers.Double -> {
            // Outer dots swell toward the notch. A fainter inner arc marks each app.
            val f = max(0f, 1 - a / 3.6f)
            val size = (if (hot) 9f else if (major) 3 + 5 * f else 2 + 2.6f * f) + v * (if (major) 8f else 4f)
            drawCircle(
                if (hot) pal.accent else lerp(pal.dotMaj, pal.accent, hue), size / 2 * k, p,
                alpha = (if (hot) 1f else max((.3f + .7f * f) * fade, hue)) * alpha,
            )
            if (major) {
                val s2 = 2.5f + 3 * f + v * 4f
                drawCircle(
                    pal.dotMaj, s2 / 2 * k, p + dir * (16f * k),
                    alpha = max((.25f + .5f * f) * fade, hue * .6f) * alpha,
                )
            }
        }
        else -> {
            val size = (if (m == Markers.Nodes) (if (major) 6f else 2f) else (if (major) 4f else 2f)) + v * 6f
            val c = when {
                hot -> pal.accent
                major -> pal.dotMaj
                m == Markers.Dots -> pal.dotMin
                else -> pal.nodeMin
            }
            drawCircle(if (hot) c else lerp(c, pal.accent, hue), size / 2 * k, p, alpha = max(fade, hue) * alpha)
        }
    }
}

private fun DrawScope.drawLabels(
    pos: Float,
    g: Geo,
    k: Float,
    pal: Palette,
    layout: Layout,
    left: Boolean,
    names: List<String>,
    dots: List<Boolean>?,
    hide: Int,
    scrubbing: Boolean,
    measurer: TextMeasurer,
    regular: TextStyle,
    medium: TextStyle,
) {
    val n = names.size
    if (n == 0) return
    for (i in 0 until n) {
        var d = mod(i - pos, n.toFloat())
        if (d > n / 2f) d -= n
        val a = abs(d)
        if (a >= g.span) continue
        val cl = max(0f, 1 - a)
        val sel = cl > .5f
        val name = names[i]
        val s = if (scrubbing) 1 + .9f * cl * cl else labelScale(a, name.length)
        val p = labelPose(d, name.length, s, g, layout.markers, left)
        // The open app's name is in the title, so its row stays empty while the dial turns.
        val op = if (i == hide) 0f else (1 - a / g.span).pow(1.35f)
        if (op < .004f) continue
        val text = measurer.measure(name, if (sel) medium else regular)
        withTransform({
            translate(p.x * k, p.y * k)
            rotate(Math.toDegrees(p.rot.toDouble()).toFloat(), Offset.Zero)
            scale(s, s, Offset.Zero)
        }) {
            drawText(
                text,
                color = if (sel) pal.accent else pal.fg,
                topLeft = Offset(p.tx * text.size.width, -text.size.height / 2f),
                alpha = op,
            )
        }
        if (dots != null && dots[i]) drawCircle(pal.accent, 2.5f * k, Offset(p.bx * k, p.by * k), alpha = op)
    }
}

/** The fixed notch at the selected row. It kicks 4 units outward on every detent. */
private fun DrawScope.drawNotch(pawl: Float, g: Geo, k: Float, pal: Palette, m: Markers, left: Boolean) {
    val rim = Offset((if (left) W - AX else AX) * k, g.cy * k)
    drawNotchAt(m, pal, k, rim, Offset(if (left) -1f else 1f, 0f), pawl)
}

/**
 * The notch of a dial in the style of [m]. [p] is the rim at the notch in pixels, and [dir] is the unit vector from
 * the rim toward the names. The notch kicks 4 units away from the names on every detent.
 */
internal fun DrawScope.drawNotchAt(m: Markers, pal: Palette, k: Float, p: Offset, dir: Offset, pawl: Float, alpha: Float = 1f) {
    val c = p - dir * (pawl * 4f * k)
    fun along(units: Float) = c + dir * (units * k)
    when (m) {
        Markers.Ticks -> drawLine(pal.accent, along(-22f), along(16f), strokeWidth = 2 * k, alpha = alpha)
        Markers.Nodes -> drawCircle(pal.accent, 9.25f * k, c, style = Stroke(1.5f * k), alpha = alpha)
        Markers.Double -> {
            drawCircle(pal.bg, 8f * k, c, alpha = alpha)
            drawCircle(pal.accent, 5f * k, c, alpha = alpha)
        }
        Markers.Dots -> {
            val offsets = floatArrayOf(-34.5f, -26f, -16.5f)
            val sizes = floatArrayOf(3f, 4f, 5f)
            val alphas = floatArrayOf(.45f, .7f, 1f)
            for (i in 0..2) drawCircle(pal.accent, sizes[i] / 2 * k, along(offsets[i]), alpha = alphas[i] * alpha)
        }
    }
}
