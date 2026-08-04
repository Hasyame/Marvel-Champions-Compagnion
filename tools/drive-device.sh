#!/bin/bash
#
# Drives the app on a connected device by what is on screen.
#
# Tapping by coordinate does not survive a scroll, a dialog that shifts, or a
# different screen size, and half the "bugs" found while testing this way turn
# out to be the test harness missing its target. This taps by the text a person
# would read instead, so a step either finds its element or says so loudly.
#
# Usage:
#   tools/drive-device.sh text                 # everything readable on screen
#   tools/drive-device.sh tap "Je suis prêt"   # tap the element containing this
#   tools/drive-device.sh field 12             # type into the first text field
#   tools/drive-device.sh scroll               # page down
#   tools/drive-device.sh shot out.png         # screenshot
#
# Matching is on a substring, case-sensitively: "Besoin d'acheter" finds the
# button labelled "Besoin d'acheter quelque chose ?". Exact matching was the
# first version and it failed on every truncated or punctuated label.

set -u

ADB="${ADB:-$(command -v adb || echo /c/Users/$USER/AppData/Local/Android/Sdk/platform-tools/adb.exe)}"
PKG="${PKG:-com.hasyame.marvelchampions}"

dump() { "$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null | tr '<' '\n'; }

# Centre of the first node whose text contains $1, as "x y".
locate() {
  dump \
    | grep -F "text=\"" \
    | grep -F "$1" \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    | grep -oE '[0-9]+' \
    | paste - - - - \
    | head -1 \
    | awk 'NF==4 {print int(($1+$3)/2), int(($2+$4)/2)}'
}

case "${1:-}" in
  text)
    dump | grep -oE 'text="[^"]{2,}"' | sed 's/^text="//; s/"$//'
    ;;

  tap)
    target="${2:?tap needs the text to look for}"
    at="$(locate "$target")"
    if [ -z "$at" ]; then
      # Loud on purpose: a silent miss reads as a broken app rather than a
      # broken test, and that mistake costs far more than this message.
      echo "NOT ON SCREEN: $target" >&2
      exit 1
    fi
    echo "tap '$target' at $at"
    "$ADB" shell input tap $at
    ;;

  field)
    value="${2:?field needs a value}"
    at="$(dump \
      | grep -F 'class="android.widget.EditText"' \
      | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
      | grep -oE '[0-9]+' | paste - - - - | head -1 \
      | awk 'NF==4 {print int(($1+$3)/2), int(($2+$4)/2)}')"
    [ -z "$at" ] && { echo "NO TEXT FIELD ON SCREEN" >&2; exit 1; }
    "$ADB" shell input tap $at; sleep 1
    "$ADB" shell input text "$value"; sleep 1
    # Dismisses the keyboard, which otherwise covers the button underneath.
    "$ADB" shell input keyevent KEYCODE_BACK
    ;;

  scroll)  "$ADB" shell input swipe 540 1700 540 500 300 ;;
  launch)  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 ;;
  shot)    "$ADB" exec-out screencap -p > "${2:?shot needs a path}" ;;
  crash)   "$ADB" logcat -b crash -d | grep -E "FATAL|^.*E AndroidRuntime: [a-z]|at com.hasyame" ;;

  *)
    sed -n '3,25p' "$0"
    exit 1
    ;;
esac
