package dev.danielhood.btscan.domain.system

private const val MAJOR_MASK = 0x1F00
private const val MAJOR_SHIFT = 8
private const val MINOR_MASK = 0x00FC
private const val PERIPHERAL_KIND_MASK = 0x00C0

/** Major device class from the Bluetooth class-of-device field (Assigned Numbers, Baseband). */
enum class MajorClass {
    MISCELLANEOUS,
    COMPUTER,
    PHONE,
    NETWORKING,
    AUDIO_VIDEO,
    PERIPHERAL,
    IMAGING,
    WEARABLE,
    TOY,
    HEALTH,
    UNCATEGORIZED,
    UNKNOWN,
}

/** The sub-class values this app can name. Anything else is left out rather than guessed. */
enum class MinorClass {
    DESKTOP,
    SERVER,
    LAPTOP,
    HANDHELD_PC,
    PALM_PC,
    WEARABLE_COMPUTER,
    CELLULAR_PHONE,
    CORDLESS_PHONE,
    SMARTPHONE,
    MODEM_OR_GATEWAY,
    ISDN_ACCESS,
    WEARABLE_HEADSET,
    HANDSFREE,
    MICROPHONE,
    LOUDSPEAKER,
    HEADPHONES,
    PORTABLE_AUDIO,
    CAR_AUDIO,
    SET_TOP_BOX,
    HIFI_AUDIO,
    VCR,
    VIDEO_CAMERA,
    CAMCORDER,
    VIDEO_MONITOR,
    VIDEO_DISPLAY_AND_LOUDSPEAKER,
    VIDEO_CONFERENCING,
    GAMING_TOY,
    WRIST_WATCH,
    PAGER,
    JACKET,
    HELMET,
    GLASSES,
    BLOOD_PRESSURE,
    THERMOMETER,
    WEIGHING_SCALE,
    GLUCOSE_METER,
    PULSE_OXIMETER,
    HEART_RATE_MONITOR,
    HEALTH_DATA_DISPLAY,
    KEYBOARD,
    POINTING_DEVICE,
    KEYBOARD_AND_POINTING_DEVICE,
}

/** A decoded class of device. [raw] is the platform value (major and minor bits); [minor] is null when not named. */
data class DeviceClassInfo(
    val major: MajorClass,
    val minor: MinorClass?,
    val raw: Int,
) {
    companion object {
        private val majors =
            mapOf(
                0 to MajorClass.MISCELLANEOUS,
                1 to MajorClass.COMPUTER,
                2 to MajorClass.PHONE,
                3 to MajorClass.NETWORKING,
                4 to MajorClass.AUDIO_VIDEO,
                5 to MajorClass.PERIPHERAL,
                6 to MajorClass.IMAGING,
                7 to MajorClass.WEARABLE,
                8 to MajorClass.TOY,
                9 to MajorClass.HEALTH,
                31 to MajorClass.UNCATEGORIZED,
            )

        /** Minor values by major class; the key is the six minor bits already shifted into place (bits 7 to 2). */
        private val minors: Map<MajorClass, Map<Int, MinorClass>> =
            mapOf(
                MajorClass.COMPUTER to
                    mapOf(
                        0x04 to MinorClass.DESKTOP,
                        0x08 to MinorClass.SERVER,
                        0x0C to MinorClass.LAPTOP,
                        0x10 to MinorClass.HANDHELD_PC,
                        0x14 to MinorClass.PALM_PC,
                        0x18 to MinorClass.WEARABLE_COMPUTER,
                    ),
                MajorClass.PHONE to
                    mapOf(
                        0x04 to MinorClass.CELLULAR_PHONE,
                        0x08 to MinorClass.CORDLESS_PHONE,
                        0x0C to MinorClass.SMARTPHONE,
                        0x10 to MinorClass.MODEM_OR_GATEWAY,
                        0x14 to MinorClass.ISDN_ACCESS,
                    ),
                MajorClass.AUDIO_VIDEO to
                    mapOf(
                        0x04 to MinorClass.WEARABLE_HEADSET,
                        0x08 to MinorClass.HANDSFREE,
                        0x10 to MinorClass.MICROPHONE,
                        0x14 to MinorClass.LOUDSPEAKER,
                        0x18 to MinorClass.HEADPHONES,
                        0x1C to MinorClass.PORTABLE_AUDIO,
                        0x20 to MinorClass.CAR_AUDIO,
                        0x24 to MinorClass.SET_TOP_BOX,
                        0x28 to MinorClass.HIFI_AUDIO,
                        0x2C to MinorClass.VCR,
                        0x30 to MinorClass.VIDEO_CAMERA,
                        0x34 to MinorClass.CAMCORDER,
                        0x38 to MinorClass.VIDEO_MONITOR,
                        0x3C to MinorClass.VIDEO_DISPLAY_AND_LOUDSPEAKER,
                        0x40 to MinorClass.VIDEO_CONFERENCING,
                        0x48 to MinorClass.GAMING_TOY,
                    ),
                MajorClass.WEARABLE to
                    mapOf(
                        0x04 to MinorClass.WRIST_WATCH,
                        0x08 to MinorClass.PAGER,
                        0x0C to MinorClass.JACKET,
                        0x10 to MinorClass.HELMET,
                        0x14 to MinorClass.GLASSES,
                    ),
                MajorClass.HEALTH to
                    mapOf(
                        0x04 to MinorClass.BLOOD_PRESSURE,
                        0x08 to MinorClass.THERMOMETER,
                        0x0C to MinorClass.WEIGHING_SCALE,
                        0x10 to MinorClass.GLUCOSE_METER,
                        0x14 to MinorClass.PULSE_OXIMETER,
                        0x18 to MinorClass.HEART_RATE_MONITOR,
                        0x1C to MinorClass.HEALTH_DATA_DISPLAY,
                    ),
            )

        private val peripheralKinds =
            mapOf(
                0x40 to MinorClass.KEYBOARD,
                0x80 to MinorClass.POINTING_DEVICE,
                0xC0 to MinorClass.KEYBOARD_AND_POINTING_DEVICE,
            )

        /** Decodes a platform class value (`BluetoothClass.getDeviceClass()`: major and minor bits). Never throws. */
        fun of(raw: Int): DeviceClassInfo {
            val major = majors[(raw and MAJOR_MASK) shr MAJOR_SHIFT] ?: MajorClass.UNKNOWN
            val minor =
                if (major == MajorClass.PERIPHERAL) {
                    peripheralKinds[raw and PERIPHERAL_KIND_MASK]
                } else {
                    minors[major]?.get(raw and MINOR_MASK)
                }
            return DeviceClassInfo(major, minor, raw)
        }
    }
}
