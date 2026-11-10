#!/system/bin/sh
CONF=/data/adb/catgirl
[ -f "$CONF/mode" ]   && setprop catgirl.mode   "$(cat "$CONF/mode")"   || setprop catgirl.mode 0
[ -f "$CONF/suffix" ] && setprop catgirl.suffix "$(cat "$CONF/suffix")" || setprop catgirl.suffix "喵"
setprop catgirl.style "1"
