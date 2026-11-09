#!/usr/bin/env bash
# UI inspection/interaction via uiautomator. Compose test tags appear as resource ids.
#   ui.sh dump            one line per node that has an id, text or content-desc
#   ui.sh find <query>    matching nodes (exact id/text/desc first, then substring); exit 1 if none
#   ui.sh tap <query>     tap the centre of the first match
#   ui.sh wait <query> [seconds]   wait until a match exists (default 15)
#   ui.sh back | text <string>
set -euo pipefail
. "$(dirname "$0")/lib.sh"
resolve_serial
REMOTE=/data/local/tmp/btscan_ui.xml

fetch_xml() {
  # uiautomator fails with "could not get idle state" while animations run; retry, then try --compressed.
  local try
  for try in 1 2 3; do
    adb shell uiautomator dump "$REMOTE" >/dev/null 2>&1 && { adb exec-out cat "$REMOTE"; return 0; }
    sleep 1
  done
  adb shell uiautomator dump --compressed "$REMOTE" >/dev/null 2>&1 && { adb exec-out cat "$REMOTE"; return 0; }
  echo "uiautomator dump failed (device idle-state problem?)" >&2; return 1
}

render() { # stdin: xml; $1 = query or empty; prints matching nodes, exit 1 if query given and no match
  python3 -I -c '
import sys, re, xml.etree.ElementTree as ET
q = sys.argv[1] if len(sys.argv) > 1 else ""
nodes = []
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    rid = n.get("resource-id", "").split("/")[-1]
    text, desc = n.get("text", ""), n.get("content-desc", "")
    if not (rid or text or desc):
        continue
    l, t, r, b = map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]")))
    nodes.append(dict(id=rid, text=text, desc=desc, cx=(l + r) // 2, cy=(t + b) // 2, b=n.get("bounds"),
                      clickable=n.get("clickable"), enabled=n.get("enabled")))
def line(d):
    return "id=%s text=%r desc=%r bounds=%s center=%d,%d clickable=%s enabled=%s" % (
        d["id"] or "-", d["text"], d["desc"], d["b"], d["cx"], d["cy"], d["clickable"], d["enabled"])
if q:
    exact = [d for d in nodes if q in (d["id"], d["text"], d["desc"])]
    hits = exact or [d for d in nodes if q in d["id"] or q in d["text"] or q in d["desc"]]
    for d in hits:
        print(line(d))
    sys.exit(0 if hits else 1)
for d in nodes:
    print(line(d))
' "${1:-}"
}

cmd="${1:-dump}"; shift || true
case "$cmd" in
  dump) fetch_xml | render "" ;;
  find) fetch_xml | render "$1" ;;
  tap)
    hit=$(fetch_xml | render "$1" | head -1) || { echo "not found: $1" >&2; exit 1; }
    xy=$(sed -E 's/.* center=([0-9]+),([0-9]+) .*/\1 \2/' <<<"$hit")
    adb shell input tap $xy; echo "tapped $1 at $xy" ;;
  wait)
    deadline=$((SECONDS + ${2:-15}))
    while [ $SECONDS -lt $deadline ]; do
      fetch_xml 2>/dev/null | render "$1" && exit 0; sleep 1
    done
    echo "timeout waiting for: $1" >&2; exit 1 ;;
  back) adb shell input keyevent KEYCODE_BACK ;;
  text) adb shell input text "${1// /%s}" ;;
  *) echo "usage: $0 dump|find|tap|wait|back|text" >&2; exit 2 ;;
esac
