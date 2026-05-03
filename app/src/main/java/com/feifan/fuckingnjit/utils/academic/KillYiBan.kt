package com.feifan.fuckingnjit.utils.academic

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.system.SystemActionHelper
import com.feifan.yiban.Apis.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KillYiBan : AppCompatActivity() {

    private fun showToast(context: Activity, msg: String) {
        runOnUiThread {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {

            // 获取当前登录的教务用户
            val currentUser = AppDataCenter.getCurrentUser()

            // 校验用户是否存在以及是否绑定了易班账号
            if (currentUser == null || currentUser.yibanId.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showToast(this@KillYiBan, "未找到易班账号信息")
                }
                this@KillYiBan.finish()
                return@launch
            }

            // 执行登录逻辑
            lateinit var task: Task
            try {
                withContext(Dispatchers.Main) {
                    SystemActionHelper.openDialog(
                        "正在登录易班...",
                        this@KillYiBan
                    )
                }
                withContext(Dispatchers.IO) {
                    task = Task(this@KillYiBan)
                    // 从 User 实体中提取易班凭据
                    task.init(currentUser.yibanId, currentUser.yibanPassword)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    SystemActionHelper.dismissDialog()
                    SystemActionHelper.handleException(this@KillYiBan, e, "登录易班失败")
                    showToast(this@KillYiBan, "登录失败，请检查网络连接或密码是否正确")
                }
                this@KillYiBan.finish()
            }

            try {
                withContext(Dispatchers.Main) {
                    SystemActionHelper.openDialog(
                        "正在尝试签到...",
                        this@KillYiBan
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    task.submitSignFeedback()
                }
                withContext(Dispatchers.Main) {
                    SystemActionHelper.dismissDialog()
                    showToast(this@KillYiBan, result)
                }
                this@KillYiBan.finish()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    SystemActionHelper.dismissDialog()
                    SystemActionHelper.handleException(this@KillYiBan, e, "签到失败")
                    showToast(this@KillYiBan, "签到失败，请检查网络连接或密码是否正确")
                }
                this@KillYiBan.finish()
            }
        }
    }
}