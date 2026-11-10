package com.huijui.catgirl

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "猫娘模式"
            textSize = 26f
        })

        root.addView(TextView(this).apply {
            text = "发送消息时在末尾加上「喵」；关掉后恢复原样。"
            textSize = 14f
            setPadding(0, 16, 0, 32)
        })

        val toggle = Switch(this).apply {
            text = "启用猫娘模式"
            isChecked = prefs.getBoolean(Prefs.KEY_ENABLED, true)
            setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(Prefs.KEY_ENABLED, v).apply()
            }
        }
        root.addView(toggle)

        root.addView(TextView(this).apply {
            text = "词尾（默认「喵」）"
            textSize = 14f
            setPadding(0, 32, 0, 8)
        })

        val suffix = EditText(this).apply {
            setText(prefs.getString(Prefs.KEY_SUFFIX, "喵"))
            hint = "喵"
        }
        root.addView(suffix)

        root.addView(Button(this).apply {
            text = "保存"
            setOnClickListener {
                prefs.edit()
                    .putString(Prefs.KEY_SUFFIX, suffix.text.toString().ifBlank { "喵" })
                    .apply()
                suffix.clearFocus()
                android.widget.Toast.makeText(this@MainActivity, "已保存", android.widget.Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(TextView(this).apply {
            text = "\n生效范围：QQ / 微信\n改动后需要结束目标应用进程再打开。"
            textSize = 13f
            setPadding(0, 40, 0, 0)
        })

        setContentView(root)
    }
}
