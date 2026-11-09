package dev.danielhood.btscan.data.ble.android

import android.bluetooth.le.ScanCallback
import dev.danielhood.btscan.domain.model.ScanFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFailureMappingTest {
    @Test
    fun `every documented platform error code maps to its kind and keeps the code`() {
        val expected =
            mapOf(
                ScanCallback.SCAN_FAILED_ALREADY_STARTED to ScanFailure.Kind.ALREADY_STARTED,
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED to ScanFailure.Kind.REGISTRATION_FAILED,
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR to ScanFailure.Kind.INTERNAL_ERROR,
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED to ScanFailure.Kind.FEATURE_UNSUPPORTED,
                ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES to ScanFailure.Kind.OUT_OF_HARDWARE_RESOURCES,
                ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY to ScanFailure.Kind.SCANNING_TOO_FREQUENTLY,
            )
        expected.forEach { (code, kind) ->
            val failure = scanFailureFor(code)
            assertEquals("code $code", kind, failure.kind)
            assertEquals(code, failure.platformCode)
        }
    }

    @Test
    fun `an unknown code becomes unknown but is not lost`() {
        val failure = scanFailureFor(99)
        assertEquals(ScanFailure.Kind.UNKNOWN, failure.kind)
        assertEquals(99, failure.platformCode)
    }
}
