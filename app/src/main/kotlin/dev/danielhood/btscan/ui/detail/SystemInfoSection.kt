package dev.danielhood.btscan.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.danielhood.btscan.R
import dev.danielhood.btscan.domain.model.AddressType
import dev.danielhood.btscan.domain.parser.ServiceUuids
import dev.danielhood.btscan.domain.system.BondState
import dev.danielhood.btscan.domain.system.DeviceClassInfo
import dev.danielhood.btscan.domain.system.DeviceType
import dev.danielhood.btscan.domain.system.MajorClass
import dev.danielhood.btscan.domain.system.MinorClass
import dev.danielhood.btscan.domain.system.ProfileUuids
import dev.danielhood.btscan.domain.system.SystemDeviceInfo
import dev.danielhood.btscan.ui.common.ProfileTags
import dev.danielhood.btscan.ui.common.TestTags
import dev.danielhood.btscan.ui.common.mergedTestTag
import java.util.UUID

/**
 * "System information": what Android already knows about the device, read without contacting it. Needs a permission
 * that is only asked for when the user taps the button here; scanning and the rest of the screen never depend on it.
 */
@Composable
internal fun SystemInfoSection(
    state: SystemInfoState,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    lookup: ProfileLookupUi = ProfileLookupUi(),
    lookupActions: ProfileLookupActions = ProfileLookupActions(),
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_system_info),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(TestTags.SYSTEM_INFO_SECTION),
        )
        when (state) {
            is SystemInfoState.NeedsPermission -> {
                NeedsPermission(state, onAllow, onOpenAppSettings)
            }

            SystemInfoState.Loading -> {
                Text(
                    stringResource(R.string.system_info_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(TestTags.SYSTEM_INFO_LOADING),
                )
            }

            SystemInfoState.Unavailable -> {
                Text(
                    stringResource(R.string.system_info_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(TestTags.SYSTEM_INFO_UNAVAILABLE),
                )
            }

            is SystemInfoState.Loaded -> {
                Loaded(state.info)
                if (lookup.canLookUp) ProfileLookupBlock(lookup, lookupActions)
            }
        }
    }
}

/** What the profile lookup block needs: its state, whether it is offered, and the device's label for the dialog. */
internal data class ProfileLookupUi(
    val state: ProfileLookupState = ProfileLookupState.Idle,
    val canLookUp: Boolean = false,
    val deviceLabel: String = "",
)

internal class ProfileLookupActions(
    val onLookUp: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * Tier 2c: query the device for the profiles it supports. This contacts the device, so the button only opens a dialog
 * naming the device; nothing is queried until the user confirms it, every time.
 */
@Composable
private fun ProfileLookupBlock(lookup: ProfileLookupUi, actions: ProfileLookupActions) {
    val busy = lookup.state is ProfileLookupState.Running || lookup.state is ProfileLookupState.Confirming
    OutlinedButton(onClick = actions.onLookUp, enabled = !busy, modifier = Modifier.testTag(ProfileTags.BUTTON)) {
        Text(stringResource(R.string.profiles_button))
    }
    when (val state = lookup.state) {
        ProfileLookupState.Running -> {
            Text(
                stringResource(R.string.profiles_running),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ProfileTags.RUNNING),
            )
        }

        ProfileLookupState.TimedOut -> {
            Text(
                stringResource(R.string.profiles_timeout),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ProfileTags.TIMEOUT),
            )
        }

        ProfileLookupState.NotStarted -> {
            Text(
                stringResource(R.string.profiles_not_started),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ProfileTags.NOT_STARTED),
            )
        }

        is ProfileLookupState.Done -> {
            ProfileResult(state)
        }

        ProfileLookupState.Idle, ProfileLookupState.Confirming -> {
            // Nothing to show yet: the dialog below handles Confirming.
        }
    }
    if (lookup.state == ProfileLookupState.Confirming) ProfileLookupDialog(lookup.deviceLabel, actions)
}

@Composable
private fun ProfileResult(state: ProfileLookupState.Done) {
    Text(
        stringResource(R.string.profiles_header),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.testTag(ProfileTags.HEADER),
    )
    if (state.uuids.isEmpty()) {
        Text(
            stringResource(R.string.profiles_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(ProfileTags.EMPTY),
        )
    }
    state.uuids.forEachIndexed { i, uuid ->
        Column {
            uuidName(uuid)?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(ProfileTags.name(i)))
            }
            Text(
                uuid.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag(ProfileTags.uuid(i)),
            )
        }
    }
    Text(
        stringResource(R.string.profiles_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProfileLookupDialog(deviceLabel: String, actions: ProfileLookupActions) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.profiles_dialog_title)) },
        text = { Text(stringResource(R.string.profiles_dialog_text, deviceLabel)) },
        confirmButton = {
            TextButton(onClick = actions.onConfirm, modifier = Modifier.testTag(ProfileTags.CONFIRM)) {
                Text(stringResource(R.string.profiles_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss, modifier = Modifier.testTag(ProfileTags.CANCEL)) {
                Text(stringResource(R.string.profiles_dialog_cancel))
            }
        },
        // A dialog is its own window, so it needs its own opt-in for test tags to reach `ui.sh dump`.
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(ProfileTags.DIALOG),
    )
}

@Composable
private fun NeedsPermission(
    state: SystemInfoState.NeedsPermission,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val message =
        if (state.permanentlyDenied) R.string.system_info_permission_denied else R.string.system_info_permission_message
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(TestTags.SYSTEM_INFO_MESSAGE),
    )
    if (state.permanentlyDenied) {
        Button(onClick = onOpenAppSettings, modifier = Modifier.testTag(TestTags.SYSTEM_INFO_SETTINGS_BUTTON)) {
            Text(stringResource(R.string.system_info_open_settings))
        }
    } else {
        Button(onClick = onAllow, modifier = Modifier.testTag(TestTags.SYSTEM_INFO_ALLOW_BUTTON)) {
            Text(stringResource(R.string.system_info_allow))
        }
    }
}

@Composable
private fun Loaded(info: SystemDeviceInfo) {
    val notAvailable = stringResource(R.string.system_not_available)
    InfoField(R.string.system_name, info.name ?: notAvailable, TestTags.SYSTEM_NAME)
    InfoField(R.string.system_alias, info.alias ?: notAvailable, TestTags.SYSTEM_ALIAS)
    InfoField(
        R.string.system_bond_state,
        stringResource(bondLabel(info.bondState)),
        TestTags.SYSTEM_BOND_STATE,
        note = R.string.system_bond_note,
    )
    val typeText = info.deviceType?.let { stringResource(typeLabel(it)) } ?: notAvailable
    InfoField(R.string.system_type, typeText, TestTags.SYSTEM_TYPE)
    InfoField(R.string.system_class, classText(info.deviceClass) ?: notAvailable, TestTags.SYSTEM_CLASS)
    Text(stringResource(R.string.system_cached_services), style = MaterialTheme.typography.labelMedium)
    if (info.cachedUuids.isEmpty()) {
        Text(stringResource(R.string.system_none_cached), style = MaterialTheme.typography.bodyMedium)
    }
    info.cachedUuids.forEachIndexed { i, uuid ->
        Column(Modifier.mergedTestTag(TestTags.systemUuid(i))) {
            uuidName(uuid)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(uuid.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
    if (info.cachedUuids.isNotEmpty()) {
        Text(
            stringResource(R.string.system_cached_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(R.string.system_info_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoField(label: Int, value: String, tag: String, note: Int? = null) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun classText(info: DeviceClassInfo?): String? =
    info?.let {
        val major = stringResource(majorLabel(it.major))
        it.minor?.let { minor ->
            stringResource(R.string.class_format, major, stringResource(minorLabel(minor)))
        } ?: major
    }

private fun uuidName(uuid: UUID): String? = ServiceUuids.nameOf(uuid) ?: ProfileUuids.nameOf(uuid)

internal fun bondLabel(state: BondState): Int =
    when (state) {
        BondState.NONE -> R.string.bond_none
        BondState.BONDING -> R.string.bond_bonding
        BondState.BONDED -> R.string.bond_bonded
    }

internal fun typeLabel(type: DeviceType): Int =
    when (type) {
        DeviceType.CLASSIC -> R.string.type_classic
        DeviceType.LE -> R.string.type_le
        DeviceType.DUAL -> R.string.type_dual
        DeviceType.UNKNOWN -> R.string.type_unknown
    }

internal fun addressLabel(type: AddressType): Int =
    when (type) {
        AddressType.PUBLIC -> R.string.address_public
        AddressType.RANDOM -> R.string.address_random
        AddressType.ANONYMOUS -> R.string.address_anonymous
        AddressType.UNKNOWN -> R.string.address_unknown
    }

private val MAJOR_LABELS: Map<MajorClass, Int> =
    mapOf(
        MajorClass.MISCELLANEOUS to R.string.major_miscellaneous,
        MajorClass.COMPUTER to R.string.major_computer,
        MajorClass.PHONE to R.string.major_phone,
        MajorClass.NETWORKING to R.string.major_networking,
        MajorClass.AUDIO_VIDEO to R.string.major_audio_video,
        MajorClass.PERIPHERAL to R.string.major_peripheral,
        MajorClass.IMAGING to R.string.major_imaging,
        MajorClass.WEARABLE to R.string.major_wearable,
        MajorClass.TOY to R.string.major_toy,
        MajorClass.HEALTH to R.string.major_health,
        MajorClass.UNCATEGORIZED to R.string.major_uncategorized,
        MajorClass.UNKNOWN to R.string.major_unknown,
    )

private val MINOR_LABELS: Map<MinorClass, Int> =
    mapOf(
        MinorClass.DESKTOP to R.string.minor_desktop,
        MinorClass.SERVER to R.string.minor_server,
        MinorClass.LAPTOP to R.string.minor_laptop,
        MinorClass.HANDHELD_PC to R.string.minor_handheld_pc,
        MinorClass.PALM_PC to R.string.minor_palm_pc,
        MinorClass.WEARABLE_COMPUTER to R.string.minor_wearable_computer,
        MinorClass.CELLULAR_PHONE to R.string.minor_cellular_phone,
        MinorClass.CORDLESS_PHONE to R.string.minor_cordless_phone,
        MinorClass.SMARTPHONE to R.string.minor_smartphone,
        MinorClass.MODEM_OR_GATEWAY to R.string.minor_modem_or_gateway,
        MinorClass.ISDN_ACCESS to R.string.minor_isdn_access,
        MinorClass.WEARABLE_HEADSET to R.string.minor_wearable_headset,
        MinorClass.HANDSFREE to R.string.minor_handsfree,
        MinorClass.MICROPHONE to R.string.minor_microphone,
        MinorClass.LOUDSPEAKER to R.string.minor_loudspeaker,
        MinorClass.HEADPHONES to R.string.minor_headphones,
        MinorClass.PORTABLE_AUDIO to R.string.minor_portable_audio,
        MinorClass.CAR_AUDIO to R.string.minor_car_audio,
        MinorClass.SET_TOP_BOX to R.string.minor_set_top_box,
        MinorClass.HIFI_AUDIO to R.string.minor_hifi_audio,
        MinorClass.VCR to R.string.minor_vcr,
        MinorClass.VIDEO_CAMERA to R.string.minor_video_camera,
        MinorClass.CAMCORDER to R.string.minor_camcorder,
        MinorClass.VIDEO_MONITOR to R.string.minor_video_monitor,
        MinorClass.VIDEO_DISPLAY_AND_LOUDSPEAKER to R.string.minor_video_display_and_loudspeaker,
        MinorClass.VIDEO_CONFERENCING to R.string.minor_video_conferencing,
        MinorClass.GAMING_TOY to R.string.minor_gaming_toy,
        MinorClass.WRIST_WATCH to R.string.minor_wrist_watch,
        MinorClass.PAGER to R.string.minor_pager,
        MinorClass.JACKET to R.string.minor_jacket,
        MinorClass.HELMET to R.string.minor_helmet,
        MinorClass.GLASSES to R.string.minor_glasses,
        MinorClass.BLOOD_PRESSURE to R.string.minor_blood_pressure,
        MinorClass.THERMOMETER to R.string.minor_thermometer,
        MinorClass.WEIGHING_SCALE to R.string.minor_weighing_scale,
        MinorClass.GLUCOSE_METER to R.string.minor_glucose_meter,
        MinorClass.PULSE_OXIMETER to R.string.minor_pulse_oximeter,
        MinorClass.HEART_RATE_MONITOR to R.string.minor_heart_rate_monitor,
        MinorClass.HEALTH_DATA_DISPLAY to R.string.minor_health_data_display,
        MinorClass.KEYBOARD to R.string.minor_keyboard,
        MinorClass.POINTING_DEVICE to R.string.minor_pointing_device,
        MinorClass.KEYBOARD_AND_POINTING_DEVICE to R.string.minor_keyboard_and_pointing_device,
    )

/** `SystemInfoLabelsTest` asserts every major and minor class has a label. */
internal fun majorLabel(major: MajorClass): Int = MAJOR_LABELS.getValue(major)

internal fun minorLabel(minor: MinorClass): Int = MINOR_LABELS.getValue(minor)
