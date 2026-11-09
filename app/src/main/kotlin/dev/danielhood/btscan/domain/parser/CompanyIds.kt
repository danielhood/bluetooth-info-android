package dev.danielhood.btscan.domain.parser

/** Curated subset of Bluetooth SIG company identifiers (manufacturer data prefix). Unknown ids return null. */
object CompanyIds {
    private val names: Map<Int, String> =
        mapOf(
            0x0000 to "Ericsson Technology Licensing",
            0x0001 to "Nokia Mobile Phones",
            0x0002 to "Intel Corp.",
            0x0003 to "IBM Corp.",
            0x0004 to "Toshiba Corp.",
            0x0006 to "Microsoft",
            0x0008 to "Motorola",
            0x000F to "Broadcom Corporation",
            0x004C to "Apple, Inc.",
            0x0059 to "Nordic Semiconductor ASA",
            0x0075 to "Samsung Electronics Co. Ltd.",
            0x0087 to "Garmin International, Inc.",
            0x009E to "Bose Corporation",
            0x00E0 to "Google",
            0x012D to "Sony Corporation",
            0x0157 to "Anhui Huami Information Technology Co., Ltd.",
            0x0171 to "Amazon.com Services, Inc.",
            0x02E5 to "Espressif Incorporated",
            0x0499 to "Ruuvi Innovations Ltd.",
        )

    fun nameOf(companyId: Int): String? = names[companyId]
}
