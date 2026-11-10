package com.huijui.catgirl

import android.app.AndroidAppHelper
import android.os.SystemProperties
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 猫娘模式：发送前在输入框末尾补词尾。
 *
 * 状态来源用 system property（catgirl.mode / catgirl.suffix）：
 * - 普通应用可读，不需要跨进程读 SharedPreferences
 * - 开关一改立即生效，不用重启目标应用
 *
 * 开关状态由 App 通过 su 写入 /data/adb/catgirl/mode 并 setprop，
 * 开机时由模块 service.sh 重新 setprop，保证重启后仍然有效。
 */
class CatGirlHook : IXposedHookLoadPackage {

    private val sendTexts = listOf("发送", "Send", "send")

    private fun enabled(): Boolean =
        runCatching { SystemProperties.get("catgirl.mode", "0") == "1" }.getOrDefault(false)

    private fun suffix(): String =
        runCatching { SystemProperties.get("catgirl.suffix", "喵") }.getOrDefault("喵").ifBlank { "喵" }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.tencent.mobileqq" &&
            lpparam.packageName != "com.tencent.mm"
        ) return

        // 注意：这里不在加载时判断开关，保证「开启即生效、关闭即失效」

        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView", lpparam.classLoader,
                "onEditorAction", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        appendSuffix(param.thisObject)
                    }
                },
            )
        }.onFailure { XposedBridge.log("catgirl: editorAction hook failed ${it.message}") }

        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.widget.EditText", lpparam.classLoader,
                "onKeyDown", Int::class.javaPrimitiveType, KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if ((param.args[0] as Int) == KeyEvent.KEYCODE_ENTER) appendSuffix(param.thisObject)
                    }
                },
            )
        }.onFailure { XposedBridge.log("catgirl: keydown hook failed ${it.message}") }

        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.view.View", lpparam.classLoader, "performClick",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val v = param.thisObject as? View ?: return
                        val label = (v as? TextView)?.text?.toString() ?: return
                        if (sendTexts.any { label.contains(it) }) appendSuffix(v.rootView.findFocus())
                    }
                },
            )
        }.onFailure { XposedBridge.log("catgirl: click hook failed ${it.message}") }
    }

    private fun appendSuffix(target: Any?) {
        if (!enabled()) return
        val et = target as? EditText ?: return
        val editable: Editable = et.text ?: return
        val s = suffix()
        val text = editable.toString()
        if (text.isBlank() || text.endsWith(s)) return
        editable.append(s)
    }
}
