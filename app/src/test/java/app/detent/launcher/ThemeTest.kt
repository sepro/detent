package app.detent.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThemeTest {
    private val koi = THEMES.first { it.name == "koi-watercolor" }

    @Test fun koiIsADarkSeproTheme() {
        assertEquals("sepro", koi.group)
        assertFalse(koi.light)
        assertEquals("walls/koi-watercolor.webp", koi.wall)
    }

    @Test fun koiUsesTheSeproColours() {
        assertEquals(Color(0xFF121212), koi.bg)
        assertEquals(Color(0xFFD8E0DC), koi.fg)
        assertEquals(Color(0xFF5FA3AC), koi.accent)
    }

    @Test fun koiButtonsHaveGrayText() = assertEquals(Color(0xFF999E9B), Palette(koi).btnFg)

    @Test fun otherThemesKeepTheirButtonText() =
        THEMES.filter { it.name != "koi-watercolor" }.forEach { assertEquals(it.name, it.fg, Palette(it).btnFg) }

    // The setup saves the theme by its place in the list, so new themes must not move the old ones.
    @Test fun oldThemesKeepTheirPlace() {
        assertEquals(0, THEME_NAMES.indexOf("dusk"))
        assertEquals(17, THEME_NAMES.indexOf("nord"))
        assertEquals(26, THEME_NAMES.indexOf("white"))
        assertEquals(THEMES.lastIndex, THEME_NAMES.indexOf("koi-watercolor"))
    }

    @Test fun groupsFollowTheDialOrder() = assertEquals(listOf("phone", "omarchy", "sepro"), THEME_GROUPS)

    @Test fun themeNamesAreUnique() = assertEquals(THEME_NAMES.size, THEME_NAMES.toSet().size)
}
