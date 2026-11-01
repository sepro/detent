package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CfgTest {
    @Test fun freshSetupUsesTheNodesLayout() = assertEquals("flat · radial · nodes", LAYOUTS[Cfg().layout].name)

    @Test fun freshSetupUsesKoiWatercolor() = assertEquals("koi-watercolor", THEMES[Cfg().theme].name)

    @Test fun freshSetupPutsTheDialOnTheRight() = assertEquals(Side.Right, Cfg().side)
}
