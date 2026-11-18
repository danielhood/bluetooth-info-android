#!/usr/bin/env bash
# Black-box smoke test of the installed debug APK on the target device (emulator by default).
# Defaults to our headless emulator (started if needed). Exit 0 = all checks passed.
# Artifacts: build/agent/shots/smoke-*.png. Covers roadmap acceptance 1, 6, 7 (device side) and 9.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
S="$REPO_ROOT/scripts"

# Default target is our own emulator (started if needed); set ANDROID_SERIAL to smoke-test another device.
if [ -z "${ANDROID_SERIAL:-}" ]; then
  "$S/emu.sh" start; "$S/emu.sh" wait
  export ANDROID_SERIAL="$BTSCAN_EMU_SERIAL"
fi
resolve_serial
echo "smoke target: $ANDROID_SERIAL"
if adb shell dumpsys window | grep -q 'mDreamingLockscreen=true'; then
  echo "device $ANDROID_SERIAL is locked: unlock the screen (and keep it awake) and re-run" >&2; exit 1
fi

fails=0
check() { # description, command...
  local d="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "PASS  $d"; else echo "FAIL  $d"; fails=$((fails + 1)); fi
}
absent() { ! "$S/ui.sh" find "$1"; }
rows() { "$S/ui.sh" dump | grep -c 'id=device_row_' || true; }
wait_rows() { # min count, timeout seconds
  local deadline=$((SECONDS + $2))
  while [ $SECONDS -lt $deadline ]; do [ "$(rows)" -ge "$1" ] && return 0; sleep 1; done; return 1
}
scan_enabled() { "$S/ui.sh" find scan_button | grep -q 'enabled=true'; }
scan_disabled() { "$S/ui.sh" find scan_button | grep -q 'enabled=false'; }
button_text() { "$S/ui.sh" dump | grep -A1 'id=scan_button' | grep -q "text='$1'"; }
sorted_by_signal() { # rssi values in dump order must be non-increasing; a dump can catch the list mid-update, so retry
  local try
  for try in 1 2 3; do
    "$S/ui.sh" dump | grep -oE -- "text='-[0-9]+ dBm'" | tr -dc '0-9\n-' |
      python3 -I -c 'import sys; v=[int(x) for x in sys.stdin.read().split()]; sys.exit(0 if len(v)>1 and v==sorted(v, reverse=True) else 1)' && return 0
    sleep 1
  done
  return 1
}
count_text() { "$S/ui.sh" find device_count | grep -q "text='$1'"; }
rssi_changes() { # detail rssi text must differ across samples within ~8 s
  local first now i
  first=$("$S/ui.sh" find detail_rssi | grep -oE "text='[^']*'")
  for i in 1 2 3 4 5 6 7 8; do
    sleep 1; now=$("$S/ui.sh" find detail_rssi | grep -oE "text='[^']*'")
    [ "$now" != "$first" ] && return 0
  done
  return 1
}
open_device() { # address: tap its row and confirm that exact device opened (rows can swap as RSSI drifts); retry
  local addr="$1" try
  for try in 1 2 3; do
    "$S/ui.sh" tap "device_row_$addr" >/dev/null 2>&1 || true
    if "$S/ui.sh" wait detail_screen 5 >/dev/null 2>&1 && "$S/ui.sh" find detail_address 2>/dev/null | grep -q "text='$addr'"; then return 0; fi
    "$S/ui.sh" back >/dev/null 2>&1 || true
    "$S/ui.sh" wait device_list 5 >/dev/null 2>&1 || true
  done
  return 1
}
# The scan needs Nearby devices AND Location: the system shows two dialogs in turn (Location offers "While using the
# app", Nearby devices offers "Allow"). Answer whichever is showing until neither is.
tap_allow() {
  local i
  for i in 1 2 3 4; do
    "$S/ui.sh" tap permission_allow_foreground_only_button 2>/dev/null || "$S/ui.sh" tap permission_allow_button 2>/dev/null || return 0
    sleep 1.5
  done
}

"$S/app.sh" install
"$S/app.sh" clear >/dev/null

echo "-- launch with fake scanner override"
"$S/app.sh" launch --scenario default >/dev/null
check "screen visible"              "$S/ui.sh" wait device_list_screen 20
check "scan button present"         "$S/ui.sh" find scan_button
check "debug label shows override"  "$S/ui.sh" find "scanner=fake scenario=default"
check "app logged the override"     bash -c "'$S/logs.sh' | grep -q 'launch debugConfig=scanner=fake scenario=default'"
"$S/shot.sh" smoke-fake >/dev/null

echo "-- permission flow (acceptance 6): runtime permission is not granted after pm clear"
check "permission needed is shown"  "$S/ui.sh" wait status_message 10
check "message names the permission" "$S/ui.sh" find "permission"
check "start is disabled"           scan_disabled
"$S/shot.sh" smoke-permission-needed >/dev/null
"$S/ui.sh" tap status_action_button >/dev/null || true
check "system dialog appears"       bash -c "for i in 1 2 3 4 5 6 7 8 9 10; do '$S/ui.sh' find permission_allow_foreground_only_button >/dev/null 2>&1 && exit 0; '$S/ui.sh' find permission_allow_button >/dev/null 2>&1 && exit 0; sleep 1; done; exit 1"
"$S/shot.sh" smoke-permission-dialog >/dev/null
tap_allow >/dev/null || true
check "ready after granting"        "$S/ui.sh" wait empty_message 10
check "granting did not auto-start" button_text "Start scan"
check "start is enabled"            scan_enabled

echo "-- scan to list (acceptance 1)"
"$S/ui.sh" tap scan_button >/dev/null
check "scanning indicator shown"    "$S/ui.sh" wait scan_indicator 5
check "5 devices within 4s"         wait_rows 5 4
check "list sorted by signal"       sorted_by_signal
check "counter matches"             bash -c "'$S/ui.sh' dump | grep -q \"id=device_count text='[56] devices'\""
check "6th device arrives by 6s"    wait_rows 6 3
"$S/shot.sh" smoke-scanning >/dev/null

echo "-- detail screen (acceptance 2, 3, 4)"
THERMO=device_row_C0:FF:EE:00:00:01
"$S/ui.sh" tap "$THERMO" >/dev/null
check "detail screen opens"         "$S/ui.sh" wait detail_screen 10
check "title is the device name"    bash -c "'$S/ui.sh' find detail_name | grep -q \"text='Thermo Sensor'\""
check "address matches the row"     bash -c "'$S/ui.sh' find detail_address | grep -q \"text='C0:FF:EE:00:00:01'\""
check "connectable is yes"          bash -c "'$S/ui.sh' find detail_connectable | grep -q \"text='Yes'\""
check "tx power -4 dBm"             bash -c "'$S/ui.sh' find detail_tx_power | grep -q \"text='-4 dBm'\""
check "rssi changes within 8s"      rssi_changes
"$S/shot.sh" smoke-detail >/dev/null
adb shell input swipe 540 1900 540 1200 300; sleep 1   # the section above the services has grown; bring them into view
check "service name is shown"       "$S/ui.sh" find "Environmental Sensing"
"$S/ui.sh" tap back_button >/dev/null
check "top bar back returns to list" "$S/ui.sh" wait device_list 5
check "scanning continues after back" button_text "Stop scan"
check "devices intact after back"   bash -c "[ \"\$($S/ui.sh dump | grep -c 'id=device_row_')\" -ge 5 ]"
"$S/ui.sh" tap "$THERMO" >/dev/null
"$S/ui.sh" wait detail_screen 10 >/dev/null
adb shell input keyevent KEYCODE_BACK
check "system back returns to list" "$S/ui.sh" wait device_list 5
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "stop keeps the devices"      bash -c "[ \"\$($S/ui.sh dump | grep -c 'id=device_row_')\" -ge 5 ]"
check "button back to start"        button_text "Start scan"
"$S/ui.sh" tap clear_button >/dev/null
check "clear returns to the initial hint" "$S/ui.sh" wait "Tap Start scan" 5
check "counter shows 0"             count_text "0 devices"

echo "-- background stops the scan (acceptance 9)"
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "scanning again"              wait_rows 1 5
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
check "back on the list"            "$S/ui.sh" wait device_list_screen 10
check "scan stopped while away"     button_text "Start scan"
check "devices were kept"           wait_rows 1 5

echo "-- environment states (acceptance 7)"
"$S/app.sh" launch --scenario bluetooth-off >/dev/null
check "bluetooth off message"       "$S/ui.sh" wait "Bluetooth is turned off" 10
check "start disabled when off"     scan_disabled
"$S/shot.sh" smoke-bluetooth-off >/dev/null
"$S/app.sh" launch --scenario unsupported >/dev/null
check "unsupported message"         "$S/ui.sh" wait "does not support Bluetooth LE" 10
"$S/app.sh" launch --scenario scan-fails >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
"$S/ui.sh" tap scan_button >/dev/null
check "failure banner after 2s"     "$S/ui.sh" wait failure_banner 8
check "retry offered"               "$S/ui.sh" find retry_button
"$S/shot.sh" smoke-failed >/dev/null

echo "-- beacon decoding (Tier 1)"
"$S/app.sh" launch --scenario beacons >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "four beacon devices"         wait_rows 4 6
open_device C0:FF:EE:00:00:01 || true
check "ibeacon detail opens"        "$S/ui.sh" wait detail_screen 10
check "ibeacon section present"     "$S/ui.sh" wait detail_decoded_ibeacon 5
check "ibeacon uuid decoded"        bash -c "'$S/ui.sh' find decoded_ibeacon_beacon_uuid | grep -q \"text='E2C56DB5-DFFB-48D2-B060-D0F5A71096E0'\""
check "ibeacon major is 1"          bash -c "'$S/ui.sh' find decoded_ibeacon_beacon_major | grep -q \"text='1'\""
check "ibeacon power -59 dBm"       bash -c "'$S/ui.sh' find decoded_ibeacon_measured_power_1m_dbm | grep -q \"text='-59 dBm'\""
"$S/shot.sh" smoke-ibeacon >/dev/null
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:02 || true
check "eddystone detail opens"      "$S/ui.sh" wait detail_screen 10
check "eddystone uid frame shown"   "$S/ui.sh" wait detail_decoded_eddystone_uid 8
check "eddystone url decoded"       bash -c "'$S/ui.sh' wait decoded_eddystone_url_eddystone_url 8 && '$S/ui.sh' find decoded_eddystone_url_eddystone_url | grep -q 'https://example.com/'"
"$S/shot.sh" smoke-eddystone >/dev/null
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:04 || true
check "altbeacon section present"   "$S/ui.sh" wait detail_decoded_altbeacon 8
check "altbeacon uuid decoded"      bash -c "'$S/ui.sh' find decoded_altbeacon_beacon_uuid | grep -q '2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6'"

echo "-- sensor decoding (Tier 1)"
"$S/app.sh" launch --scenario sensors >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "three sensor devices"        wait_rows 3 6
open_device C0:FF:EE:00:00:01 || true
check "ruuvi section present"       "$S/ui.sh" wait detail_decoded_ruuvi_v5 8
check "ruuvi temperature 24.3"      bash -c "'$S/ui.sh' find decoded_ruuvi_v5_temperature_c | grep -q '24.3 '"
check "ruuvi humidity 53.49 %"      bash -c "'$S/ui.sh' find decoded_ruuvi_v5_humidity_percent | grep -q '53.49 %'"
"$S/shot.sh" smoke-ruuvi >/dev/null
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:02 || true
check "bthome temperature 25.06"    bash -c "'$S/ui.sh' wait decoded_bthome_temperature_c 8 && '$S/ui.sh' find decoded_bthome_temperature_c | grep -q '25.06 '"
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:03 || true
check "encrypted bthome flagged"    bash -c "'$S/ui.sh' wait decoded_bthome_confidence 8 && '$S/ui.sh' find decoded_bthome_confidence | grep -q 'Encrypted'"
check "encrypted shows no values"   absent decoded_bthome_temperature_c

echo "-- vendor hints (Tier 1)"
"$S/app.sh" launch --scenario vendors >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "vendor devices appear"       wait_rows 4 8
open_device C0:FF:EE:00:00:01 || true
check "find my hint section"        "$S/ui.sh" wait detail_decoded_tracker_hint 8
check "hint says unofficial"        bash -c "'$S/ui.sh' find decoded_tracker_hint_confidence | grep -q 'Unofficial hint'"
check "title says unverified"       bash -c "'$S/ui.sh' find detail_decoded_tracker_hint | grep -q 'unverified'"
"$S/shot.sh" smoke-vendor-hint >/dev/null
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:05 || true
check "fast pair model id shown"    bash -c "'$S/ui.sh' wait decoded_fast_pair_fast_pair_model_id 8 && '$S/ui.sh' find decoded_fast_pair_fast_pair_model_id | grep -q '2A 3B 4C'"

echo "-- radio metadata, estimates and kind chips (Tier 1)"
"$S/app.sh" launch --scenario radio >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "five radio devices"          wait_rows 5 8
check "ibeacon row has a chip"      bash -c "'$S/ui.sh' find kind_chip_C0:FF:EE:00:00:05 | grep -q \"text='iBeacon'\""
check "plain row has no chip"       absent kind_chip_C0:FF:EE:00:00:01
open_device C0:FF:EE:00:00:01 || true
check "address type from the scan"  bash -c "'$S/ui.sh' wait detail_address_type 8 && '$S/ui.sh' find detail_address_type | grep -q \"text='Random'\""
check "radio section phy shown"     "$S/ui.sh" wait detail_radio_primary_phy 8
check "secondary phy is LE 2M"      bash -c "'$S/ui.sh' find detail_radio_secondary_phy | grep -q 'LE 2M'"
adb shell input swipe 540 1900 540 1100 300
sleep 1
check "advertising set id is 3"     bash -c "'$S/ui.sh' find detail_radio_sid | grep -q \"text='3'\""
check "periodic interval 100 ms"    bash -c "'$S/ui.sh' find detail_radio_periodic_interval | grep -q '100 ms'"
check "interval estimate appears"   bash -c "'$S/ui.sh' wait detail_radio_interval 10 && '$S/ui.sh' find detail_radio_interval | grep -q 'ms between packets'"
"$S/shot.sh" smoke-radio >/dev/null
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:02 || true
check "coded phy shown"             bash -c "'$S/ui.sh' wait detail_radio_primary_phy 8 && '$S/ui.sh' find detail_radio_primary_phy | grep -q 'Coded'"
check "no distance without calibration" absent detail_radio_distance
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:05 || true
adb shell input swipe 540 1900 540 400 300
sleep 0.5
adb shell input swipe 540 1900 540 400 300
sleep 1
check "calibrated beacon has a distance" bash -c "'$S/ui.sh' wait detail_radio_distance 10"

echo "-- system information (Tier 2a): the connect permission is asked for on demand"
adb shell pm revoke "$PKG" android.permission.BLUETOOTH_CONNECT >/dev/null 2>&1 || true
"$S/app.sh" launch --scenario system-info >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "four system-info devices"    wait_rows 4 6
open_device C0:FF:EE:00:00:01 || true
check "section asks for permission" "$S/ui.sh" wait system_info_allow_button 8
check "no values before allowing"   absent system_name
"$S/shot.sh" smoke-system-info-ask >/dev/null
"$S/ui.sh" tap system_info_allow_button >/dev/null
tap_allow >/dev/null || true
check "headset system name shown"   bash -c "'$S/ui.sh' wait system_name 10 && '$S/ui.sh' find system_name | grep -q \"text='Buds Pro'\""
check "alias shown"                 bash -c "'$S/ui.sh' find system_alias | grep -q \"text='My buds'\""
check "paired shown"                bash -c "'$S/ui.sh' find system_bond_state | grep -q \"text='Paired'\""
"$S/shot.sh" smoke-system-info >/dev/null
adb shell input swipe 540 1900 540 700 300; sleep 1
check "device class shown"          bash -c "'$S/ui.sh' find system_class | grep -q 'Audio or video: Headphones'"
echo "-- profile lookup (Tier 2c): contacts the device, so a confirmation naming it comes first, every time"
adb shell input swipe 540 1900 540 1300 300; sleep 1
check "lookup offered for a dual-mode device" "$S/ui.sh" wait profiles_button 6
check "nothing asked unprompted"    absent profiles_header
"$S/ui.sh" tap profiles_button >/dev/null
check "confirmation is shown"       "$S/ui.sh" wait profiles_confirm_dialog 6
check "it names the device"         bash -c "'$S/ui.sh' find 'BT Scan will briefly contact' | grep -q 'Buds Pro'"
sleep 3
check "nothing asked behind the dialog" absent profiles_header
"$S/shot.sh" smoke-profiles-confirm >/dev/null
"$S/ui.sh" tap profiles_cancel >/dev/null
sleep 2
check "cancel asks nothing"         absent profiles_header
check "cancel closes the dialog"    absent profiles_confirm_dialog
sleep 1.5
"$S/ui.sh" tap profiles_button >/dev/null
"$S/ui.sh" wait profiles_confirm_dialog 6 >/dev/null
check "asked again, not remembered" "$S/ui.sh" find profiles_confirm
"$S/ui.sh" tap profiles_confirm >/dev/null
check "the device answers"          "$S/ui.sh" wait profiles_header 8
check "friendly profile names"      bash -c "'$S/ui.sh' find profile_name_0 | grep -q 'A2DP Audio Sink'"
"$S/shot.sh" smoke-profiles >/dev/null
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:03 || true
check "pairing state shown"         bash -c "'$S/ui.sh' wait system_bond_state 10 && '$S/ui.sh' find system_bond_state | grep -q 'Pairing in progress'"
adb shell input swipe 540 1900 540 1300 300; sleep 1
check "lookup offered for a classic device" "$S/ui.sh" wait profiles_button 6
"$S/ui.sh" tap profiles_button >/dev/null
"$S/ui.sh" wait profiles_confirm_dialog 6 >/dev/null
"$S/ui.sh" tap profiles_confirm >/dev/null
check "asking shows progress"       "$S/ui.sh" wait profiles_running 6
check "a silent device times out"   "$S/ui.sh" wait profiles_timeout 16
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:02 || true
check "ble only device has no lookup" bash -c "'$S/ui.sh' wait system_info_message 8 >/dev/null 2>&1 || '$S/ui.sh' wait system_name 8 >/dev/null 2>&1; ! '$S/ui.sh' find profiles_button >/dev/null 2>&1"
"$S/ui.sh" back >/dev/null
"$S/ui.sh" wait device_list 5 >/dev/null
open_device C0:FF:EE:00:00:04 || true
check "unknown device says so"      "$S/ui.sh" wait system_info_unavailable 10
check "and has no lookup"           absent profiles_button

echo "-- classic scan (Tier 2b): gated by the UI, never starts by itself"
"$S/app.sh" launch --scenario classic-mixed >/dev/null
"$S/ui.sh" wait device_list_screen 10 >/dev/null
sleep 2
check "no inquiry on launch"        absent classic_scan_indicator
check "classic button present"      "$S/ui.sh" find classic_scan_button
"$S/ui.sh" tap classic_scan_button >/dev/null
check "gate notice is shown"        "$S/ui.sh" wait classic_notice_dialog 8
check "notice warns about audio"    "$S/ui.sh" find "disturb audio"
sleep 3
check "nothing started behind the notice" absent classic_scan_indicator
check "no devices before confirming" bash -c "[ \"\$($S/ui.sh dump | grep -c 'id=device_row_')\" -eq 0 ]"
"$S/shot.sh" smoke-classic-notice >/dev/null
"$S/ui.sh" tap classic_notice_cancel >/dev/null
sleep 2
check "cancel starts nothing"       absent classic_scan_indicator
check "cancel closes the notice"    absent classic_notice_dialog
sleep 1.5
"$S/ui.sh" tap classic_scan_button >/dev/null
"$S/ui.sh" wait classic_notice_dialog 8 >/dev/null
"$S/ui.sh" tap classic_notice_confirm >/dev/null
check "inquiry starts after confirming" "$S/ui.sh" wait classic_scan_indicator 6
check "four classic-mixed devices"  wait_rows 4 10
check "classic only badge"          bash -c "'$S/ui.sh' find source_badge_C0:FF:EE:00:00:0A | grep -q \"text='Classic'\""
check "paired car audio line"       bash -c "'$S/ui.sh' dump | grep -q 'Paired'"
"$S/shot.sh" smoke-classic-scanning >/dev/null
check "inquiry ends by itself"      bash -c "for i in \$(seq 1 20); do '$S/ui.sh' find classic_scan_indicator >/dev/null 2>&1 || exit 0; sleep 1; done; exit 1"
check "found devices are kept"      bash -c "[ \"\$($S/ui.sh dump | grep -c 'id=device_row_')\" -ge 4 ]"
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "ble scan merges the shared device" bash -c "for i in \$(seq 1 12); do '$S/ui.sh' find source_badge_C0:FF:EE:00:00:01 | grep -q 'BLE + Classic' && exit 0; sleep 1; done; exit 1"
"$S/ui.sh" tap source_filter_classic >/dev/null
sleep 1
check "classic filter hides ble only" bash -c "! '$S/ui.sh' dump | grep -q 'id=device_row_C0:FF:EE:00:00:02'"
"$S/ui.sh" tap source_filter_all >/dev/null
sleep 1.5
"$S/ui.sh" tap classic_scan_button >/dev/null
check "second inquiry needs no second notice" "$S/ui.sh" wait classic_scan_indicator 6
check "ble scan was stopped for it" "$S/ui.sh" wait list_notice 5
"$S/shot.sh" smoke-classic-note >/dev/null
"$S/ui.sh" tap classic_scan_button >/dev/null

echo "-- launch without overrides"
"$S/app.sh" launch >/dev/null
check "screen visible"              "$S/ui.sh" wait device_list_screen 20
check "no debug label"              absent debug_label
check "app logged no override"      bash -c "'$S/logs.sh' | grep -q 'launch debugConfig=none'"

echo "-- real scanner path starts and stops cleanly (no devices expected on an emulator)"
"$S/app.sh" grant >/dev/null
"$S/app.sh" launch >/dev/null
"$S/ui.sh" wait scan_button 15 >/dev/null
check "start enabled in real mode"  scan_enabled
"$S/ui.sh" tap scan_button >/dev/null
check "real scan indicator shown"   "$S/ui.sh" wait scan_indicator 5
sleep 2
check "no failure banner"           absent failure_banner
check "platform scan started"       bash -c "'$S/logs.sh' | grep -q 'BtScan/Scan.*scan started'"
sleep 1
"$S/ui.sh" tap scan_button >/dev/null
check "real scan stops"             button_text "Start scan"
check "platform scan stopped"       bash -c "'$S/logs.sh' | grep -q 'BtScan/Scan.*scan stopped'"

echo "-- Bluetooth toggled while scanning (real scanner, emulator adapter)"
sleep 1.5
"$S/ui.sh" tap scan_button >/dev/null
check "scanning again"              "$S/ui.sh" wait scan_indicator 5
adb shell svc bluetooth disable
check "bluetooth off message"       "$S/ui.sh" wait "Bluetooth is turned off" 20
check "scan stopped, not phantom"   absent scan_indicator
adb shell svc bluetooth enable
check "idle again when bluetooth returns" "$S/ui.sh" wait empty_message 30
check "start offered again"         scan_enabled
check "no phantom scanning"         absent scan_indicator

check "no crash in logs"            bash -c "! '$S/logs.sh' | grep -qE 'FATAL EXCEPTION|E/AndroidRuntime'"

[ "$fails" -eq 0 ] && echo "SMOKE OK" || { echo "SMOKE FAILED ($fails)"; exit 1; }
