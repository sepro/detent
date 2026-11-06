package app.detent.launcher

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.tanh

/** The button between apps and the corner button: the mic, the equalizer, or no button. */
enum class DockButton {
    Mic, Equalizer, None;

    companion object {
        /**
         * The button saved as [name]. Setups saved before the equalizer had only a mic switch, so without a
         * valid name the [mic] switch decides between the mic and no button.
         */
        fun of(name: String?, mic: Boolean) =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: if (mic) Mic else None
    }
}

/** The upper edge of each equalizer band in Hz. A band's gain covers the range up from the previous edge. */
val EQ_EDGES = floatArrayOf(60f, 150f, 400f, 1_000f, 2_500f, 6_000f, 12_000f, 20_000f)

/** A compressor on the output. Sound above [threshold] dB is pressed down by [ratio], then raised by [postGain] dB. */
class Limit(val threshold: Float, val ratio: Float, val attackMs: Float, val releaseMs: Float, val postGain: Float = 0f)

/** Keeps peaks under full scale, so that a boost never clips. */
val SAFETY_LIMIT = Limit(threshold = -1f, ratio = 10f, attackMs = 1f, releaseMs = 60f)

private fun gains(vararg db: Int) = FloatArray(db.size) { db[it].toFloat() }

/**
 * The presets on the sound dial, in dial order. [gains] holds one gain in dB for each band in [EQ_EDGES].
 * [inputGain] in dB comes before the equalizer, and [limit] after it.
 */
enum class SoundPreset(
    val label: String,
    val about: String,
    val gains: FloatArray,
    val inputGain: Float = 0f,
    val limit: Limit = SAFETY_LIMIT,
) {
    Off("off", "the sound as the app plays it", gains(0, 0, 0, 0, 0, 0, 0, 0)),
    // Speech lives between about 1 and 4 kHz. Less rumble below it leaves more room for the words.
    Voice("voice", "clearer words in podcasts and calls", gains(-8, -5, -1, 1, 4, 4, 1, -2)),
    Clear("clear", "more detail in the highs", gains(0, 0, -1, 0, 2, 3, 4, 3)),
    Music("music", "a little more bass and treble", gains(3, 2, 0, -1, 0, 1, 2, 2)),
    // The input drops a little, so the boost has room before the limit.
    Bass("bass boost", "more low end", gains(7, 6, 2, 0, 0, 0, 0, 0), inputGain = -2f),
    Loud(
        "loud", "louder, with the peaks held in", gains(0, 0, 0, 0, 1, 1, 0, 0),
        inputGain = 8f, limit = Limit(threshold = -2f, ratio = 10f, attackMs = 1f, releaseMs = 80f),
    ),
    // A low threshold with a gentle ratio narrows the gap between quiet and loud parts, and the post gain
    // brings the quiet parts back up. Less bass carries less far through walls.
    Night(
        "night", "softer bass and quieter peaks", gains(-6, -4, 0, 0, 1, 1, -1, -2),
        limit = Limit(threshold = -28f, ratio = 4f, attackMs = 5f, releaseMs = 250f, postGain = 8f),
    );

    companion object {
        /** The preset saved as [name], or [Off] when the name is unknown. */
        fun of(name: String?) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Off
    }
}

// The sound dial turns around the equalizer button. Its rim is much tighter than the app dial.

/** Radius of the sound dial rim in design units. */
const val SOUND_R = 112f

/**
 * Angle between two presets in radians: 34 degrees. The names follow the curve of the rim, so they need more
 * room along it than the names on the app dial, which point toward its centre.
 */
const val SOUND_STEP = (34 * PI / 180).toFloat()

/** The preset in the notch grows to this scale. Less than on the app dial, so it never touches its neighbours. */
const val SOUND_SEL_SCALE = 1.3f

/**
 * Presets fade out this many rows from the notch. The button sits close to the screen edge, so the names fade out
 * at 80 degrees, before they turn sideways at the edge.
 */
const val SOUND_SPAN = 1.4f / SOUND_STEP

/** The last row on the sound dial. A getter, since the presets read this file's limits as they start. */
val SOUND_LAST: Int get() = SoundPreset.entries.size - 1

/**
 * Angle of the point ([x], [y]) around the hub at ([hx], [hy]), clockwise from straight up. The notch sits
 * straight up, at angle 0.
 */
fun soundAngle(x: Float, y: Float, hx: Float, hy: Float) = atan2(x - hx, hy - y)

/** [a] wrapped to the range from -PI to PI, so that a turn across straight down does not jump a full circle. */
fun wrapAngle(a: Float): Float {
    var w = a
    while (w > PI) w -= (2 * PI).toFloat()
    while (w < -PI) w += (2 * PI).toFloat()
    return w
}

/**
 * The dial position for a raw drag position. Past the first and last preset, the dial gives way less and less,
 * and never more than [give] rows.
 */
fun softClamp(raw: Float, last: Int, give: Float = .35f): Float = when {
    raw < 0f -> give * tanh(raw / give)
    raw > last -> last + give * tanh((raw - last) / give)
    else -> raw
}

/**
 * The preset row under a tap at ([x], [y]), or null when the tap misses the ring of names. The dial sits at
 * [pos] around the hub at ([hx], [hy]).
 */
fun soundRowAt(x: Float, y: Float, hx: Float, hy: Float, pos: Float): Int? {
    val dist = hypot(x - hx, y - hy)
    // The names sit outside the rim. A tap near the rim or on a name counts, while the hub itself does not.
    if (dist < SOUND_R - 40f || dist > SOUND_R + 120f) return null
    val row = (pos + soundAngle(x, y, hx, hy) / SOUND_STEP).roundToInt()
    if (row !in 0..SOUND_LAST || abs(row - pos) > SOUND_SPAN) return null
    return row
}
