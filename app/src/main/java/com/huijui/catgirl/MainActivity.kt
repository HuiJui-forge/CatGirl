package com.huijui.catgirl

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val confDir = "/data/adb/catgirl"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        root.addView(TextView(this).apply { text = "猫娘模式"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "开启后，发送消息会在末尾加词尾；关闭立即恢复。不需要重启目标应用。"
            textSize = 14f
            setPadding(0, 16, 0, 32)
        })

        val status = TextView(this).apply { textSize = 13f; setPadding(0, 12, 0, 0) }

        fun refresh() {
            val on = shell("getprop catgirl.mode").trim() == "1"
            status.text = if (on) "当前：已激活 ✳" else "当前：未激活"
        }

        val toggle = Switch(this).apply {
            text = "启用猫娘模式"
            isChecked = shell("getprop catgirl.mode").trim() == "1"
            setOnCheckedChangeListener { _, v ->
                val r = applyState(v)
                status.text = if (r) (if (v) "当前：已激活 ✳" else "当前：未激活") else "切换失败：没有 root 权限？"
                if (!r) isChecked = !v
            }
        }
        root.addView(toggle)

        root.addView(TextView(this).apply {
            text = "词尾（默认「喵」）"; textSize = 14f; setPadding(0, 32, 0, 8)
        })
        val suffix = EditText(this).apply { setText(prefs.getString(Prefs.KEY_SUFFIX, "喵")) }
        root.addView(suffix)

        root.addView(Button(this).apply {
            text = "保存词尾"
            setOnClickListener {
                val s = suffix.text.toString().ifBlank { "喵" }
                prefs.edit().putString(Prefs.KEY_SUFFIX, s).apply()
                val ok = shell("setprop catgirl.suffix '$s'").isNotEmpty() ||
                    shell("setprop catgirl.suffix '$s'; getprop catgirl.suffix").trim() == s
                Toast.makeText(this@MainActivity, if (ok) "已保存：$s" else "保存失败", Toast.LENGTH_SHORT).show()
                suffix.clearFocus()
            }
        })

        root.addView(Button(this).apply {
            text = "刷新状态"
            setOnClickListener { refresh() }
        })

        root.addView(status)
        root.addView(TextView(this).apply {
            text = "\n作用范围：QQ / 微信\n首次使用：LSPosed 里勾选本模块与目标应用，然后结束目标应用进程重开一次。"
            textSize = 13f; setPadding(0, 24, 0, 0)
        })

        setContentView(root)
        refresh()
    }

    /** 写 /data/adb/catgirl/mode 并立即 setprop，实现「开即生效、关即失效」。 */
    private fun applyState(on: Boolean): Boolean {
        val v = if (on) "1" else "0"
        val cmd = "mkdir -p $confDir; echo $v > $confDir/mode; setprop catgirl.mode $v; getprop catgirl.mode"
        return shell(cmd).trim() == v
    }

    private fun shell(cmd: String): String = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(8, TimeUnit.SECONDS)
        out
    } catch (t: Throwable) {
        ""
    }
}
