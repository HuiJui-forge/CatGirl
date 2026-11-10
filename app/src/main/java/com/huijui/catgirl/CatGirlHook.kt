package com.huijui.catgirl

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
 * 猫娘模式 v1.0
 *
 * 设计原则：
 * 1. 只在「发送」这一瞬间改文本，不碰输入过程，避免影响输入法候选、光标与撤销。
 * 2. 所有 hook 都包在 try/catch 里，任何一个失败都不影响目标应用正常使用。
 * 3. 开关状态通过 system property 实时读取（800ms 缓存），开即生效、关即失效。
 * 4. 文本超长（>2000 字）不处理，避免长文卡顿。
 */
class CatGirlHook : IXposedHookLoadPackage {

    private val sendTexts = listOf("发送", "Send", "send")

    @Volatile private var cacheTime = 0L
    @Volatile private var cacheMode = false
    @Volatile private var cacheStyle = true
    @Volatile private var cacheSuffix = "喵"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in SCOPES) return

        safe("editorAction") {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView", lpparam.classLoader,
                "onEditorAction", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = apply(param.thisObject)
                },
            )
        }

        safe("keyDown") {
            XposedHelpers.findAndHookMethod(
                "android.widget.EditText", lpparam.classLoader,
                "onKeyDown", Int::class.javaPrimitiveType, KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if ((param.args[0] as Int) == KeyEvent.KEYCODE_ENTER) apply(param.thisObject)
                    }
                },
            )
        }

        safe("click") {
            XposedHelpers.findAndHookMethod(
                "android.view.View", lpparam.classLoader, "performClick",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val v = param.thisObject as? View ?: return
                        val label = (v as? TextView)?.text?.toString() ?: return
                        if (sendTexts.any { label.contains(it) }) apply(v.rootView.findFocus())
                    }
                },
            )
        }
    }

    private fun safe(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XposedBridge.log("catgirl/$tag: ${t.message}")
        }
    }

    /** 发送前处理：读状态 → 生成猫娘文本 → 原地替换。 */
    private fun apply(target: Any?) {
        try {
            val et = target as? EditText ?: return
            refresh()
            if (!cacheMode) return
            val editable: Editable = et.text ?: return
            val raw = editable.toString()
            if (raw.isBlank() || raw.length > 2000) return

            val result = render(raw)
            if (result == raw) return

            val start = et.selectionStart
            val end = et.selectionEnd
            editable.replace(0, editable.length, result)
            runCatching {
                val max = editable.length
                et.setSelection(start.coerceIn(0, max), end.coerceIn(0, max))
            }
        } catch (t: Throwable) {
            XposedBridge.log("catgirl/apply: ${t.message}")
        }
    }

    /** 猫娘化：可选替换词 + 词尾；已带词尾则不重复。 */
    private fun render(input: String): String {
        var t = input
        if (cacheStyle) {
            t = t.replace("我", "本喵")
                .replace("你", "泥")
                .replace("是", "系")
                .replace("不", "表")
                .replace("很", "超")
                .replace("吗", "嘛")
                .replace("了", "惹")
                .replace("的", "哒")
        }
        val s = cacheSuffix
        if (s.isNotBlank() && !t.endsWith(s)) t += s
        return t
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        if (now - cacheTime < 800) return
        cacheTime = now
        cacheMode = getprop("catgirl.mode") == "1"
        cacheStyle = getprop("catgirl.style").ifBlank { "1" } == "1"
        cacheSuffix = getprop("catgirl.suffix").ifBlank { "喵" }
    }

    private fun getprop(key: String): String = try {
        val p = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out
    } catch (t: Throwable) {
        ""
    }

    companion object {
        private val SCOPES = setOf("com.tencent.mobileqq", "com.tencent.mm")
    }
}
