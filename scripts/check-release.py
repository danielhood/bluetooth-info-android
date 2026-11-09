"""Release-safety check: debug-only code must be absent from the release APK and present in the debug APK
(so the check cannot pass vacuously). Usage: check-release.py <debug.apk> <release.apk>"""
import sys
import zipfile

MARKER = b"BTSCAN_DEBUG_ONLY_MARKER"


def has_marker(apk: str) -> bool:
    with zipfile.ZipFile(apk) as z:
        return any(MARKER in z.read(n) for n in z.namelist() if n.endswith(".dex"))


debug_apk, release_apk = sys.argv[1], sys.argv[2]
if not has_marker(debug_apk):
    sys.exit("check is vacuous: marker not found in debug APK")
if has_marker(release_apk):
    sys.exit("debug-only code leaked into the release APK")
print("release APK is free of debug-only code")
