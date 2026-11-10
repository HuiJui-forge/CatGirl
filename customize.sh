#!/system/bin/sh
SKIPUNZIP=0

ui_print "- 安装猫娘模式"

if [ -f "$MODPATH/system/app/CatGirl/CatGirl.apk" ]; then
  ui_print "- APK 已放入 system/app，重启后生效"
else
  ui_print "! 未找到 APK，安装包可能损坏"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
ui_print "- 完成，重启设备后在 LSPosed 里启用「猫娘模式」并勾选 QQ / 微信"
