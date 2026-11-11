package dev.danielhood.btscan.domain.decode

import dev.danielhood.btscan.domain.model.Advertisement
import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.ServiceData
import dev.danielhood.btscan.domain.parser.AdvertisementParser

internal fun hex(s: String): Bytes {
    val clean = s.replace(" ", "")
    return Bytes(ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
}

internal fun manufacturer(companyId: Int, payloadHex: String) =
    Advertisement(manufacturerData = listOf(ManufacturerData(companyId, hex(payloadHex))))

internal fun serviceData(uuid16: Long, payloadHex: String) =
    Advertisement(serviceData = listOf(ServiceData(AdvertisementParser.uuidFromShort(uuid16), hex(payloadHex))))

internal fun Decoded.field(key: FieldKey): FieldValue? = fields.firstOrNull { it.key == key }?.value

internal fun Decoded.number(key: FieldKey): Double = (field(key) as FieldValue.Number).value

internal fun Decoded.text(key: FieldKey): String = (field(key) as FieldValue.Text).value

internal fun Decoded.hexOf(key: FieldKey): String = (field(key) as FieldValue.Hex).value.toHex()
