package com.feifan.fuckingnjit.utils.system

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.text.TextUtils


// MIUI. Redefining Android.
// (not in the very best way I'd say)
object XiaomiUtilities {
    // custom permissions
    const val OP_ACCESS_XIAOMI_ACCOUNT: Int = 10015
    const val OP_AUTO_START: Int = 10008
    const val OP_BACKGROUND_START_ACTIVITY: Int = 10021
    const val OP_BLUETOOTH_CHANGE: Int = 10002
    const val OP_BOOT_COMPLETED: Int = 10007
    const val OP_DATA_CONNECT_CHANGE: Int = 10003
    const val OP_DELETE_CALL_LOG: Int = 10013
    const val OP_DELETE_CONTACTS: Int = 10012
    const val OP_DELETE_MMS: Int = 10011
    const val OP_DELETE_SMS: Int = 10010
    const val OP_EXACT_ALARM: Int = 10014
    const val OP_GET_INSTALLED_APPS: Int = 10022
    const val OP_GET_TASKS: Int = 10019
    const val OP_INSTALL_SHORTCUT: Int = 10017
    const val OP_NFC: Int = 10016
    const val OP_NFC_CHANGE: Int = 10009
    const val OP_READ_MMS: Int = 10005
    const val OP_READ_NOTIFICATION_SMS: Int = 10018
    const val OP_SEND_MMS: Int = 10004
    const val OP_SERVICE_FOREGROUND: Int = 10023
    const val OP_SHOW_WHEN_LOCKED: Int = 10020
    const val OP_WIFI_CHANGE: Int = 10001
    const val OP_WRITE_MMS: Int = 10006

    val isMIUI: Boolean
        get() = !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"))

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String? {
        try {
            val props = Class.forName("android.os.SystemProperties")
            return props.getMethod("get", String::class.java).invoke(null, key) as String
        } catch (ignore: Exception) {
        }
        return null
    }

    @TargetApi(19)
    fun isCustomPermissionGranted(context: Context, permission: Int): Boolean {
        try {
            val mgr = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val m = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val result = m.invoke(mgr, permission, Process.myUid(), context.packageName) as Int
            return result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
//            Logger.INSTANCE.log("XiaomiUtils", e.toString(), Logger.IMPORTANCE.DEFAULT, true)
        }
        return true
    }

    fun getPermissionManagerIntent(context: Context): Intent {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
        intent.putExtra("extra_package_uid", Process.myUid())
        intent.putExtra("extra_pkgname", context.packageName)
        intent.putExtra("extra_package_name", context.packageName)
        return intent
    }
}