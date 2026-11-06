package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

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

    @Test fun tapOnANameFindsItsRow() {
        // The dial sits on row 2. One row clockwise is one step to the right of the notch.
        val ang = SOUND_STEP
        val r = SOUND_R + 40f
        val x = 200f + r * kotlin.math.sin(ang)
        val y = 800f - r * kotlin.math.cos(ang)
        assertEquals(3, soundRowAt(x, y, 200f, 800f, pos = 2f))
    }

    @Test fun tapOnTheHubIsNoRow() = assertNull(soundRowAt(205f, 795f, 200f, 800f, pos = 2f))

    @Test fun tapPastTheLastPresetIsNoRow() {
        val ang = 2 * SOUND_STEP
        val r = SOUND_R + 40f
        assertNull(soundRowAt(200f + r * kotlin.math.sin(ang), 800f - r * kotlin.math.cos(ang), 200f, 800f, pos = SOUND_LAST.toFloat()))
    }

    // Two neighbouring names never touch, even with the longest name in the notch at its full scale.
    @Test fun neighbouringNamesHaveRoom() {
        val names = SoundPreset.entries.map { it.label }
        // The nodes and dots layouts put the names closest to the rim, where the room is least.
        val labelRadius = SOUND_R + labelInset(Markers.Nodes) + 9f * SOUND_SEL_SCALE
        val room = labelRadius * SOUND_STEP
        names.zipWithNext().forEach { (a, b) ->
            val widest = maxOf(a.length * CH * SOUND_SEL_SCALE / 2 + b.length * CH / 2, a.length * CH / 2 + b.length * CH * SOUND_SEL_SCALE / 2)
            assertTrue("$a and $b", widest < room)
        }
    }
}
