package dev.danielhood.btscan.domain.system

import dev.danielhood.btscan.domain.parser.AdvertisementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Values are `BluetoothClass.getDeviceClass()` (major and minor bits) as Android defines them in
 * `BluetoothClass.Device`, which follow the Bluetooth Assigned Numbers class-of-device tables.
 */
class DeviceClassTest {
    private fun of(raw: Int) = DeviceClassInfo.of(raw)

    @Test
    fun `audio video minors`() {
        assertEquals(DeviceClassInfo(MajorClass.AUDIO_VIDEO, MinorClass.WEARABLE_HEADSET, 0x0404), of(0x0404))
        assertEquals(MinorClass.HANDSFREE, of(0x0408).minor)
        assertEquals(MinorClass.MICROPHONE, of(0x0410).minor)
        assertEquals(MinorClass.LOUDSPEAKER, of(0x0414).minor)
        assertEquals(MinorClass.HEADPHONES, of(0x0418).minor)
        assertEquals(MinorClass.PORTABLE_AUDIO, of(0x041C).minor)
        assertEquals(MinorClass.CAR_AUDIO, of(0x0420).minor)
        assertEquals(MinorClass.SET_TOP_BOX, of(0x0424).minor)
        assertEquals(MinorClass.HIFI_AUDIO, of(0x0428).minor)
        assertEquals(MinorClass.VIDEO_CONFERENCING, of(0x0440).minor)
        assertEquals(MinorClass.GAMING_TOY, of(0x0448).minor)
    }

    @Test
    fun `phone computer wearable and health minors`() {
        assertEquals(MinorClass.SMARTPHONE, of(0x020C).minor)
        assertEquals(MajorClass.PHONE, of(0x020C).major)
        assertEquals(MinorClass.LAPTOP, of(0x010C).minor)
        assertEquals(MinorClass.DESKTOP, of(0x0104).minor)
        assertEquals(MinorClass.WRIST_WATCH, of(0x0704).minor)
        assertEquals(MinorClass.GLASSES, of(0x0714).minor)
        assertEquals(MinorClass.HEART_RATE_MONITOR, of(0x0918).minor)
        assertEquals(MinorClass.THERMOMETER, of(0x0908).minor)
    }

    @Test
    fun `peripheral kinds come from the two high minor bits`() {
        assertEquals(MinorClass.KEYBOARD, of(0x0540).minor)
        assertEquals(MinorClass.POINTING_DEVICE, of(0x0580).minor)
        assertEquals(MinorClass.KEYBOARD_AND_POINTING_DEVICE, of(0x05C0).minor)
        assertEquals("a keyboard with other minor bits set", MinorClass.KEYBOARD, of(0x0544).minor)
        assertNull(of(0x0500).minor)
    }

    @Test
    fun `unnamed minors keep the major class and no minor`() {
        assertEquals(DeviceClassInfo(MajorClass.COMPUTER, null, 0x0100), of(0x0100))
        assertNull(of(0x0444).minor)
        assertEquals(MajorClass.AUDIO_VIDEO, of(0x0444).major)
        assertNull(of(0x0000).minor)
    }

    @Test
    fun `other majors and unknown values never throw`() {
        assertEquals(MajorClass.MISCELLANEOUS, of(0x0000).major)
        assertEquals(MajorClass.NETWORKING, of(0x0300).major)
        assertEquals(MajorClass.IMAGING, of(0x0600).major)
        assertEquals(MajorClass.TOY, of(0x0800).major)
        assertEquals(MajorClass.UNCATEGORIZED, of(0x1F00).major)
        assertEquals(MajorClass.UNKNOWN, of(0x0A00).major)
        listOf(-1, Int.MAX_VALUE, Int.MIN_VALUE, 0x0001, 0x1FFF).forEach { of(it) }
    }

    @Test
    fun `profile uuids are named and unknown ones are not`() {
        assertEquals("A2DP Audio Sink", ProfileUuids.nameOf(AdvertisementParser.uuidFromShort(0x110B)))
        assertEquals("Hands-Free", ProfileUuids.nameOf(AdvertisementParser.uuidFromShort(0x111E)))
        assertEquals("Human Interface Device", ProfileUuids.nameOf(AdvertisementParser.uuidFromShort(0x1124)))
        assertNull(ProfileUuids.nameOf(AdvertisementParser.uuidFromShort(0x0001)))
    }
}
