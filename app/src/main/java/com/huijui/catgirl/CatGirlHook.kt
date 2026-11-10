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
        // 不再限定具体应用：只要输入框是「聊天发送」场景，任何 App 都生效。
        // 通过 imeOptions / 发送按钮判定，避免影响搜索框、表单等。

        safe("editorAction") {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView", lpparam.classLoader,
                "onEditorAction", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val action = param.args[0] as? Int ?: return
                        // 6 = IME_ACTION_SEND
                        if (action == 6) apply(param.thisObject)
                    }
                },
            )
        }

        safe("keyDown") {
            XposedHelpers.findAndHookMethod(
                "android.widget.EditText", lpparam.classLoader,
                "onKeyDown", Int::class.javaPrimitiveType, KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val et = param.thisObject as? EditText ?: return
                        if ((param.args[0] as Int) == KeyEvent.KEYCODE_ENTER &&
                            (isChatInput(et) || looksLikeChat(et))
                        ) apply(et)
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
                        val target = v.rootView.findFocus() as? EditText ?: return
                        if (sendTexts.any { label.contains(it) } || isChatInput(target)) apply(target)
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

    /** 输入框声明了「发送」动作：典型的聊天输入框。 */
    private fun isChatInput(et: EditText): Boolean {
        return try {
            (et.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION) ==
                android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        } catch (t: Throwable) {
            false
        }
    }

    /** 兜底：输入框是多行的，且没有声明搜索/网址等类型，视为聊天框。 */
    private fun looksLikeChat(et: EditText): Boolean {
        return try {
            val type = et.inputType and android.text.InputType.TYPE_MASK_CLASS
            val isSearch = (et.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION) ==
                android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            !isSearch && (type == android.text.InputType.TYPE_CLASS_TEXT)
        } catch (t: Throwable) {
            false
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

}
