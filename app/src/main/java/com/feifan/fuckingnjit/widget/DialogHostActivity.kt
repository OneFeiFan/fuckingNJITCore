// DialogHostActivity.kt
package com.feifan.fuckingnjit.widget

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.feifan.fuckingnjit.utils.XiaomiUtilities


class DialogHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置为透明背景
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 从 Intent 中获取对话框参数
        val title = intent.getStringExtra("title") ?: "提示"
        val message = intent.getStringExtra("message") ?: ""

        // 显示对话框
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确认") { _, _ ->
                // 跳转到小米权限设置页
                startActivity(XiaomiUtilities.getPermissionManagerIntent(this))
                finish()
            }
            .setNegativeButton("取消") { _, _ -> finish() }
            .setOnDismissListener { finish() } // 对话框关闭时结束 Activity
            .show()
    }
}
