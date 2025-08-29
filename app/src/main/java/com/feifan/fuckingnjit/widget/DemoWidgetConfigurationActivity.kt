// 定义小部件配置Activity，继承自AppCompatActivity
package com.feifan.fuckingnjit.widget

// 导入所需的Android库
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

//import androidx.appcompat.app.AppCompatActivity

// 小部件配置Activity类
class DemoWidgetConfigurationActivity : Activity() {

    // 存储小部件ID，初始值为无效ID
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    // Activity创建时的回调方法
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置布局文件
//        setContentView(R.layout.activity_configuration)
        val widgetIDs = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, DemoWidgetProvider::class.java))
        if (widgetIDs.isNotEmpty()) {
            Toast.makeText(this, "只能创建一个小部件", Toast.LENGTH_SHORT).show()
            finish()
        }
        // 初始化界面元素

        // 从Intent中获取额外数据
        val intent = intent
        val extras = intent.extras
        // 检查是否有额外数据
        if (extras != null) {
            // 尝试获取小部件ID，如果没有则使用默认值INVALID_APPWIDGET_ID
            widgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // 检查获取到的小部件ID是否有效
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // 如果无效，直接结束Activity
            finish()
        } else {
            save()  // 点击时调用save方法
        }
    }

    // 保存小部件配置的方法
    private fun save() {
        // 检查用户是否输入了小部件名称

        // 保存小部件配置到偏好设置
//        preferences.setWidgetValues(widgetId, widgetName.text.toString())
        // 获取AppWidgetManager实例
        val appWidgetManager = AppWidgetManager.getInstance(this)
        // 更新小部件UI
        DemoWidgetProvider.updateWidgetUI(this, appWidgetManager, widgetId)
        // 完成配置并返回结果
        finishWithResult()
    }

    // 完成配置并返回结果的方法
    private fun finishWithResult() {
        // 创建结果Intent
        val resultValue = Intent()
        // 将小部件ID放入结果Intent中
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // 设置结果为RESULT_OK，并附带结果Intent
        setResult(Activity.RESULT_OK, resultValue)
        // 结束当前Activity
        finish()
    }
}
