package com.huijui.catgirl

import android.app.AndroidAppHelper
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

    // SystemProperties 是隐藏 API，编译期拿不到；这里用 getprop 命令行读取，
    // 带 800ms 缓存，避免每次发送都 fork 进程。
    @Volatile private var cacheTime = 0L
    @Volatile private var cacheMode = false
    @Volatile private var cacheSuffix = "喵"

    private fun refresh() {
        val now = System.currentTimeMillis()
        if (now - cacheTime < 800) return
        cacheTime = now
        cacheMode = getprop("catgirl.mode") == "1"
        cacheSuffix = getprop("catgirl.suffix").ifBlank { "喵" }
    }

    private fun getprop(key: String): String = runCatching {
        val p = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out
    }.getOrDefault("")

    private fun enabled(): Boolean { refresh(); return cacheMode }

    private fun suffix(): String { refresh(); return cacheSuffix.ifBlank { "喵" } }

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
