package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SoundTest {
    @Test fun everyPresetHasAGainForEachBand() = SoundPreset.entries.forEach {
        assertEquals(it.label, EQ_EDGES.size, it.gains.size)
    }

    @Test fun bandEdgesRiseToTheTopOfHearing() {
        assertTrue(EQ_EDGES.toList().zipWithNext().all { (a, b) -> a < b })
        assertEquals(20_000f, EQ_EDGES.last())
    }

    @Test fun presetNamesAreUnique() =
        assertEquals(SoundPreset.entries.size, SoundPreset.entries.map { it.label }.distinct().size)

    @Test fun theDialStartsWithOff() = assertEquals(SoundPreset.Off, SoundPreset.entries.first())

    @Test fun offChangesNothing() {
        assertTrue(SoundPreset.Off.gains.all { it == 0f })
        assertEquals(0f, SoundPreset.Off.inputGain)
    }

    // Every preset ends in a limiter that holds the peaks under full scale, so no boost can clip.
    @Test fun everyPresetHoldsItsPeaks() = SoundPreset.entries.forEach {
        assertTrue(it.label, it.limit.threshold + it.limit.postGain <= 0f || it.limit.ratio >= 4f)
        assertTrue(it.label, it.limit.ratio >= 1f)
    }

    @Test fun savedPresetsReadBack() = SoundPreset.entries.forEach { assertEquals(it, SoundPreset.of(it.name.lowercase())) }

    @Test fun unknownPresetIsOff() {
        assertEquals(SoundPreset.Off, SoundPreset.of(null))
        assertEquals(SoundPreset.Off, SoundPreset.of("disco"))
    }

    @Test fun savedDockButtonsReadBack() = DockButton.entries.forEach {
        assertEquals(it, DockButton.of(it.name.lowercase(), mic = false))
    }

    // Setups saved before the equalizer kept only the mic switch.
    @Test fun oldSetupsKeepTheirMicSwitch() {
        assertEquals(DockButton.Mic, DockButton.of(null, mic = true))
        assertEquals(DockButton.None, DockButton.of(null, mic = false))
    }

    @Test fun angleRunsClockwiseFromStraightUp() {
        assertEquals(0f, soundAngle(100f, 50f, 100f, 100f), 1e-5f)
        assertEquals((PI / 2).toFloat(), soundAngle(150f, 100f, 100f, 100f), 1e-5f)
        assertEquals((-PI / 2).toFloat(), soundAngle(50f, 100f, 100f, 100f), 1e-5f)
    }

    // A dial on the left edge is the mirror image, so its notch sits at half past ten.
    @Test fun leftDialMirrorsTheAngle() =
        assertEquals((PI / 2).toFloat(), soundAngle(50f, 100f, 100f, 100f, left = true), 1e-5f)

    @Test fun wrapKeepsATurnAcrossTheBottomSmall() {
        assertEquals(-.2f, wrapAngle((2 * PI).toFloat() - .2f), 1e-5f)
        assertEquals(.2f, wrapAngle(.2f - (2 * PI).toFloat()), 1e-5f)
    }

    @Test fun softClampLeavesTheRangeAlone() = assertEquals(2.5f, softClamp(2.5f, 6), 0f)

    @Test fun softClampGivesWayPastTheEnds() {
        assertTrue(softClamp(-3f, 6) in -.35f..0f)
        assertTrue(softClamp(9f, 6) in 6f..6.35f)
        // Further past the end still moves a little further, so the dial never feels stuck.
        assertTrue(softClamp(6.2f, 6) < softClamp(6.4f, 6))
    }

    /** A point [r] units from the hub at (200, 800), [rows] rows clockwise from the notch. */
    private fun pointAt(rows: Float, r: Float = SOUND_R + 60f, left: Boolean = false): Pair<Float, Float> {
        val ang = SOUND_NOTCH + rows * SOUND_STEP
        val dx = r * sin(ang)
        return (if (left) 200f - dx else 200f + dx) to 800f - r * cos(ang)
    }

    @Test fun tapOnANameFindsItsRow() {
        // The dial sits on row 2, so the name one row clockwise from the notch is row 3.
        val (x, y) = pointAt(1f)
        assertEquals(3, soundRowAt(x, y, 200f, 800f, pos = 2f))
    }

    @Test fun tapOnTheNotchFindsTheSelectedRow() {
        val (x, y) = pointAt(0f)
        assertEquals(2, soundRowAt(x, y, 200f, 800f, pos = 2f))
    }

    @Test fun tapOnALeftDialFindsTheMirroredRow() {
        val (x, y) = pointAt(1f, left = true)
        assertEquals(3, soundRowAt(x, y, 200f, 800f, pos = 2f, left = true))
    }

    // The names point outward, so a tap near the outer end of a long name still counts.
    @Test fun tapOnTheEndOfANameFindsItsRow() {
        val (x, y) = pointAt(0f, r = SOUND_R + 180f)
        assertEquals(2, soundRowAt(x, y, 200f, 800f, pos = 2f))
    }

    @Test fun tapOnTheHubIsNoRow() = assertNull(soundRowAt(205f, 795f, 200f, 800f, pos = 2f))

    @Test fun tapPastTheLastPresetIsNoRow() {
        val (x, y) = pointAt(1f)
        assertNull(soundRowAt(x, y, 200f, 800f, pos = SOUND_LAST.toFloat()))
    }

    // The names stand at right angles to the rim, so neighbours need room for their height, not their length.
    @Test fun neighbouringNamesHaveRoom() {
        val height = 15f * 1.2f
        val selected = labelScale(0f, SoundPreset.entries.minOf { it.label.length })
        val room = (SOUND_R + Markers.entries.minOf { soundLabelInset(it) }) * SOUND_STEP
        assertTrue(height * selected / 2 + height / 2 < room)
    }

    // A name leaning past straight up would read upside down, so the names fade out before they get there.
    @Test fun namesFadeOutBeforeTheyTurnOver() = assertTrue(SOUND_NOTCH - SOUND_SPAN * SOUND_STEP > -PI.toFloat() / 6)
}
