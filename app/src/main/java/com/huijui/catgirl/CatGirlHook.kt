package com.huijui.catgirl

import android.app.AndroidAppHelper
import android.content.Context
import android.text.Editable
import android.view.View
import android.widget.EditText
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 猫娘模式：发送前在输入框末尾追加词尾。
 *
 * 说明：
 * - 不写死 QQ 内部类，只用 Android 框架类做通用钩子，升级 QQ 也不容易失效。
 * - 命中「发送」时，先改文本再放行原方法，所以对方收到的是带「喵」的内容。
 * - 关掉开关后行为完全恢复，不改任何持久数据。
 */
class CatGirlHook : IXposedHookLoadPackage {

    private val sendTexts = listOf("发送", "Send", "send", "发送键")

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.tencent.mobileqq" &&
            lpparam.packageName != "com.tencent.mm"
        ) return

        val ctx: Context = AndroidAppHelper.currentApplication() ?: return
        if (!Prefs.enabled(ctx)) return
        val suffix = Prefs.suffix(ctx)

        // 1) 键盘上的「发送」动作
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView", lpparam.classLoader,
                "onEditorAction", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        appendSuffix(param.thisObject as? TextView ?: return)
                    }
                },
            )
        }.onFailure { XposedBridge.log("catgirl: editorAction hook failed ${it.message}") }

        // 2) 回车键发送
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.widget.EditText", lpparam.classLoader,
                "onKeyDown", Int::class.javaPrimitiveType, android.view.KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val code = param.args[0] as Int
                        if (code == android.view.KeyEvent.KEYCODE_ENTER) {
                            appendSuffix(param.thisObject as? EditText ?: return)
                        }
                    }
                },
            )
        }.onFailure { XposedBridge.log("catgirl: keydown hook failed ${it.message}") }

        // 3) 点击「发送」按钮
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

    /** 给输入框补词尾；已有则不重复追加。 */
    private fun appendSuffix(target: Any?) {
        val et = target as? EditText ?: return
        val editable: Editable = et.text ?: return
        val ctx: Context = AndroidAppHelper.currentApplication() ?: return
        if (!Prefs.enabled(ctx)) return
        val suffix = Prefs.suffix(ctx)
        val text = editable.toString()
        if (text.isBlank() || text.endsWith(suffix)) return
        editable.append(suffix)
    }
}
