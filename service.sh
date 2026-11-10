#!/system/bin/sh
# 开机恢复猫娘模式状态（system property 重启后会丢）
CONF=/data/adb/catgirl
if [ -f "$CONF/mode" ]; then
  setprop catgirl.mode "$(cat "$CONF/mode")"
else
  setprop catgirl.mode 0
fi
if [ -f "$CONF/suffix" ]; then
  setprop catgirl.suffix "$(cat "$CONF/suffix")"
else
  setprop catgirl.suffix "喵"
fi
