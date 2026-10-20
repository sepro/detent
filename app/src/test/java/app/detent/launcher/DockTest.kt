package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockTest {
    @Test fun readsSavedCorners() {
        assertEquals(Corner.Quick, Corner.of("quick"))
        assertEquals(Corner.Web, Corner.of("web"))
        assertEquals(Corner.None, Corner.of("none"))
    }

    // Setups saved before the dock step have no corner. The corner then keeps the quick app.
    @Test fun missingCornerKeepsTheQuickApp() = assertEquals(Corner.Quick, Corner.of(null))

    @Test fun unknownCornerKeepsTheQuickApp() = assertEquals(Corner.Quick, Corner.of("dial"))

    @Test fun savedNamesReadBack() = Corner.entries.forEach { assertEquals(it, Corner.of(it.name.lowercase())) }

    @Test fun webSearchUrlEncodesTheWords() =
        assertEquals("https://www.google.com/search?q=koi+fish+%26+lilies", webSearchUrl(" koi fish & lilies "))
}
