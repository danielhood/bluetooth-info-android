package dev.danielhood.btscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugConfigTest {
    @Test
    fun `no overrides gives no label`() {
        assertNull(DebugConfig.NONE.label)
    }

    @Test
    fun `fake scanner with scenario is summarised in the label`() {
        assertEquals("scanner=fake scenario=default", DebugConfig(scanner = "fake", scenario = "default").label)
    }

    @Test
    fun `scenario alone defaults scanner to real`() {
        assertEquals("scanner=real scenario=many", DebugConfig(scenario = "many").label)
    }
}
