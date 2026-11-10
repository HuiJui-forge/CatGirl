package com.huijui.catgirl

import android.content.Context

/** 开关与配置。Xposed 侧通过 MODE_WORLD_READABLE 读取。 */
object Prefs {
    const val NAME = "catgirl_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_SUFFIX = "suffix"

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun suffix(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_SUFFIX, "喵") ?: "喵"
}
