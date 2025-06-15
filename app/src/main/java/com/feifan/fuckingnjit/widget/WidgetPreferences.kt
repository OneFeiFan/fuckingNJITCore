package com.feifan.fuckingnjit.widget
//
//import android.annotation.SuppressLint
//import android.content.Context
//import android.content.SharedPreferences
//
//class WidgetPreferences(context: Context) {
//
//    private val preferences: SharedPreferences = context.getSharedPreferences("widget_preferences", Context.MODE_PRIVATE)
//
//    // 使用commit instead of apply as value is needed straight away
//
//
//    fun removeWidget(widgetId: Int) {
//        val editor = preferences.edit()
//        editor.remove("" + widgetId)
//        editor.apply()
//    }
//
//    // 存储ArrayList<HashMap<String, String>>
//    @SuppressLint("ApplySharedPref")
//    fun setWidgetData(widgetId: Int, data: ArrayList<HashMap<String, String>>) {
//        val editor = preferences.edit()
//        val dataString = serializeData(data)
//        editor.putString("" + widgetId, dataString)
//        editor.commit()
//    }
//
//    @SuppressLint("ApplySharedPref")
//    fun setBaseData(data: ArrayList<HashMap<String, String>>) {
//        val editor = preferences.edit()
//        val dataString = serializeData(data)
//        editor.putString("curriculums", dataString)
//        editor.commit()
//    }
//    fun getBaseData(): ArrayList<HashMap<String, String>>? {
//        val dataString = preferences.getString("curriculums", null)
//        if (dataString != null) {
//            return deserializeData(dataString)
//        } else {
//            return ArrayList<HashMap<String, String>>()
//        }
//    }
//    // 获取ArrayList<HashMap<String, String>>
//    fun getWidgetData(widgetId: Int): ArrayList<HashMap<String, String>>? {
//        val dataString = preferences.getString("" + widgetId, null)
//        return if (dataString != null) {
//            deserializeData(dataString)
//        } else {
//            null
//        }
//    }
//
//    // 序列化ArrayList<HashMap<String, String>>为字符串
//    private fun serializeData(data: ArrayList<HashMap<String, String>>): String {
//        return data.joinToString(";") { hashMap ->
//            hashMap.map { "${it.key}=${it.value}" }.joinToString(",")
//        }
//    }
//
//    // 反序列化字符串为ArrayList<HashMap<String, String>>
//    private fun deserializeData(dataString: String): ArrayList<HashMap<String, String>> {
//        val data = ArrayList<HashMap<String, String>>()
//        dataString.split(";").forEach { hashMapString ->
//            val hashMap = HashMap<String, String>()
//            hashMapString.split(",").forEach { keyValue ->
//                val parts = keyValue.split("=")
//                if (parts.size == 2) {  // 确保有键和值两部分
//                    hashMap[parts[0]] = parts[1]
//                }
//            }
//            data.add(hashMap)
//        }
//        return data
//    }
//}
