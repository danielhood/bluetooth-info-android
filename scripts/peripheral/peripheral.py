#!/usr/bin/env python3
"""Advertise profiles from the host's BlueZ controller so the phone can scan known, real advertisements.

  peripheral.py list                      profile names
  peripheral.py run <profile> [...]       register one advertisement per profile; runs until SIGINT/SIGTERM

Needs only the system packages python3-dbus and python3-gi and a running bluetoothd with a controller that
supports LE advertising. Uses the unprivileged D-Bus API; nothing is installed or reconfigured. Advertising only:
this script never accepts connections and never pairs.
"""
import json
import os
import signal
import sys

import dbus
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

BLUEZ = "org.bluez"
ADAPTER = "/org/bluez/hci0"
MANAGER_IFACE = "org.bluez.LEAdvertisingManager1"
ADV_IFACE = "org.bluez.LEAdvertisement1"
PROPS_IFACE = "org.freedesktop.DBus.Properties"
BASE_UUID = "0000{:04x}-0000-1000-8000-00805f9b34fb"
PROFILES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "profiles.json")


def hex_bytes(text):
    clean = text.replace(" ", "")
    return dbus.Array([dbus.Byte(int(clean[i:i + 2], 16)) for i in range(0, len(clean), 2)], signature="y")


def uuid16(text):
    return BASE_UUID.format(int(text, 16))


def load_profiles():
    with open(PROFILES_FILE) as f:
        return {k: v for k, v in json.load(f).items() if not k.startswith("_")}


class Advertisement(dbus.service.Object):
    def __init__(self, bus, index, profile):
        self.path = "/dev/danielhood/btscan/advertisement%d" % index
        self.profile = profile
        super().__init__(bus, self.path)

    def properties(self):
        p = self.profile
        props = {"Type": dbus.String(p.get("type", "broadcast"))}
        if "service_uuids" in p:
            props["ServiceUUIDs"] = dbus.Array([uuid16(u) for u in p["service_uuids"]], signature="s")
        if "manufacturer" in p:
            props["ManufacturerData"] = dbus.Dictionary(
                {dbus.UInt16(int(k, 16)): hex_bytes(v) for k, v in p["manufacturer"].items()}, signature="qv")
        if "service_data" in p:
            props["ServiceData"] = dbus.Dictionary(
                {uuid16(k): hex_bytes(v) for k, v in p["service_data"].items()}, signature="sv")
        if "secondary_channel" in p:
            props["SecondaryChannel"] = dbus.String(p["secondary_channel"])
        if "local_name" in p:
            props["LocalName"] = dbus.String(p["local_name"])
        if "tx_power" in p:
            props["TxPower"] = dbus.Int16(p["tx_power"])
            props["Includes"] = dbus.Array(["tx-power"], signature="s")
        if "discoverable" in p:
            props["Discoverable"] = dbus.Boolean(p["discoverable"])
        if "interval_ms" in p:
            props["MinInterval"] = dbus.UInt32(p["interval_ms"])
            props["MaxInterval"] = dbus.UInt32(p["interval_ms"])
        return props

    @dbus.service.method(PROPS_IFACE, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        return self.properties() if interface == ADV_IFACE else {}

    @dbus.service.method(PROPS_IFACE, in_signature="ss", out_signature="v")
    def Get(self, interface, name):
        return self.properties()[name]

    @dbus.service.method(ADV_IFACE)
    def Release(self):
        print("released by BlueZ: " + self.path, flush=True)


def main(argv):
    profiles = load_profiles()
    if len(argv) >= 2 and argv[1] == "list":
        print("\n".join(sorted(profiles)))
        return 0
    if len(argv) < 3 or argv[1] != "run":
        print(__doc__)
        return 2
    unknown = [n for n in argv[2:] if n not in profiles]
    if unknown:
        print("unknown profile(s): %s (try: list)" % ", ".join(unknown), file=sys.stderr)
        return 2

    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    manager = dbus.Interface(bus.get_object(BLUEZ, ADAPTER), MANAGER_IFACE)
    loop = GLib.MainLoop()
    ads, state = [], {"pending": len(argv) - 2, "failed": False}

    def registered():
        state["pending"] -= 1
        if state["pending"] == 0 and not state["failed"]:
            print("advertising: " + " ".join(argv[2:]), flush=True)

    def failed(err):
        print("registration failed: %s" % err, file=sys.stderr, flush=True)
        state["failed"] = True
        loop.quit()

    for i, name in enumerate(argv[2:]):
        ad = Advertisement(bus, i, profiles[name])
        ads.append(ad)
        manager.RegisterAdvertisement(ad.path, {}, reply_handler=registered, error_handler=failed)

    def stop(*_):
        for ad in ads:
            try:
                manager.UnregisterAdvertisement(ad.path)
            except dbus.DBusException:
                pass
        loop.quit()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    loop.run()
    return 1 if state["failed"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
