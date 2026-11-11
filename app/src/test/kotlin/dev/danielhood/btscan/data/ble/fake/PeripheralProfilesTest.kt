package dev.danielhood.btscan.data.ble.fake

import dev.danielhood.btscan.domain.model.Bytes
import dev.danielhood.btscan.domain.model.ManufacturerData
import dev.danielhood.btscan.domain.model.ServiceData
import dev.danielhood.btscan.domain.parser.AdvertisementParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `scripts/peripheral/profiles.json` is what the host's BlueZ controller transmits for real-radio tests; the fake
 * scenarios are what the JVM and emulator tests assert. This test keeps the two byte-for-byte in step, so a real
 * radio run and a fake run are checked against the same values.
 */
class PeripheralProfilesTest {
    private val profiles: Map<String, JsonObject> =
        Json
            .parseToJsonElement(File("../scripts/peripheral/profiles.json").readText())
            .jsonObject
            .filterKeys { !it.startsWith("_") }
            .mapValues { it.value.jsonObject }

    private val knownKeys =
        setOf(
            "type",
            "manufacturer",
            "service_uuids",
            "service_data",
            "secondary_channel",
            "interval_ms",
            "local_name",
            "tx_power",
            "discoverable",
        )

    private fun hex(text: String): Bytes = hexBytes(text)

    private fun manufacturerOf(profile: JsonObject): List<ManufacturerData> =
        profile["manufacturer"]?.jsonObject.orEmpty().map { (company, data) ->
            ManufacturerData(company.toInt(16), hex(data.jsonPrimitive.content))
        }

    private fun serviceDataOf(profile: JsonObject): List<ServiceData> =
        profile["service_data"]?.jsonObject.orEmpty().map { (uuid, data) ->
            ServiceData(AdvertisementParser.uuidFromShort(uuid.toLong(16)), hex(data.jsonPrimitive.content))
        }

    /** Profiles that mirror a fake scenario payload, by name. */
    private val mirrored: Map<String, Bytes> =
        mapOf(
            "ibeacon" to BeaconFrames.ibeacon(),
            "altbeacon" to BeaconFrames.altBeacon(),
            "eddystone-uid" to BeaconFrames.eddystoneUid(),
            "eddystone-url" to BeaconFrames.eddystoneUrl(),
            "eddystone-tlm" to BeaconFrames.eddystoneTlm(),
            "ruuvi" to SensorFrames.ruuvi(),
            "bthome" to SensorFrames.bthome(),
            "bthome-encrypted" to SensorFrames.bthomeEncrypted(),
        )

    @Test
    fun `profiles that mirror a fake carry exactly the same manufacturer and service data`() {
        mirrored.forEach { (name, fakeBytes) ->
            val profile = requireNotNull(profiles[name]) { "profile '$name' missing from profiles.json" }
            val fake = AdvertisementParser.parse(fakeBytes)
            assertEquals("$name manufacturer data", fake.manufacturerData, manufacturerOf(profile))
            assertEquals("$name service data", fake.serviceData, serviceDataOf(profile))
        }
    }

    @Test
    fun `every profile uses only keys the host script understands and a valid type`() {
        assertTrue(profiles.isNotEmpty())
        profiles.forEach { (name, profile) ->
            assertTrue("$name has unknown keys ${profile.keys - knownKeys}", knownKeys.containsAll(profile.keys))
            assertTrue("$name type", profile["type"]?.jsonPrimitive?.content in setOf("peripheral", "broadcast"))
        }
    }
}
