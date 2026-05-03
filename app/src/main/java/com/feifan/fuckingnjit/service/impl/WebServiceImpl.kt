package com.feifan.fuckingnjit.service.impl

import android.content.Context
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.service.WebService
import com.feifan.fuckingnjit.utils.EduScheduleConfig
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.academic.CourseManager
import com.feifan.fuckingnjit.utils.academic.CourseParser
import com.feifan.fuckingnjit.utils.academic.ScoreManager
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.network.ApiException
import com.feifan.fuckingnjit.utils.network.HttpMethod
import com.feifan.fuckingnjit.utils.network.HttpRequestHelper
import com.feifan.fuckingnjit.utils.network.NetworkStatus
import com.feifan.fuckingnjit.utils.system.SystemActionHelper
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.lang.Integer.parseInt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.pow


/**
 * 教务系统 Web 服务实现类（单例）
 *
 * 通过 WebVPN 通道访问 NJIT 教务系统的各类接口，
 * 完成课表查询、成绩获取、空教室查询、学业进度等功能。
 */
@Suppress("unused")
class WebServiceImpl private constructor() : WebService {
    companion object {
        private val instance_: WebServiceImpl by lazy { WebServiceImpl() }
        fun getInstance(): WebServiceImpl = instance_
    }

    /**
     * 构建带基础路径和查询参数的完整 URL
     *
     * @param path 接口路径（相对于 WebVPN 基地址）
     * @param params 键值对形式的查询参数
     * @return 拼接完成的完整 URL
     */
    private fun buildUrl(path: String, vararg params: Pair<String, String>): String {
        val baseUrl = "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}$path"
        if (params.isEmpty()) return baseUrl

        val encodedParams = params.joinToString("&") { (key, value) ->
            "$key=${value}"
        }
        return "$baseUrl?$encodedParams"
    }

    /**
     * 判断 HTML 文档是否为空壳页面
     *
     * 空壳页面的特征是 html 下只有空 head 和空 body，
     * 通常出现在会话过期被重定向到登录页的场景。
     *
     * @param doc 待检测的 Jsoup Document
     * @return true 表示该文档为空壳
     */
    private fun isShellDocument(doc: Document): Boolean {
        return doc.children().size == 1 &&           // 只有 <html> 一个子节点
                doc.head().children().isEmpty() &&    // <head> 为空
                doc.body().children().isEmpty()       // <body> 为空
    }

    override suspend fun getCurriculum(context: Context): JSONObject {
        // 1. 准备请求 (保持你原有的逻辑)
        val schoolYearFull = EduScheduleConfig.getCurrentSchoolYear()
        val schoolYear = schoolYearFull.split('-')[0]
        val semester = schoolYearFull.split('-')[2]
        val url = buildUrl(
            "/jwglxt/kbcx/xskbqr_cxXskbqrIndex.html",
            "doType" to "query",
            "gnmkdm" to "N2158",
            "xnm" to schoolYear,
            "xqm" to semester,
            "_search" to "false",
            "nd" to "1725346567148",
            "queryModel.showCount" to "200",
            "queryModel.currentPage" to "1",
            "queryModel.sortName" to "",
            "queryModel.sortOrder" to "asc",
            "time" to "1"
        )

        var raw: String
        try {
            raw = HttpRequestHelper.getJsonResponse(url, HttpMethod.GET)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return JSONObject()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取课表失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }

        if (raw.isEmpty()) {
            return JSONObject()
        }
        val jsonObject = JSON.parseObject(raw)
        val items = jsonObject.getJSONArray("items") ?: JSONArray()

        // 2. 容器：存放所有解析好的课程
        var allCourses = ArrayList<Course>()

        // 3. 解析教务系统数据
        for (i in 0 until items.size) {
            val item = items.getJSONObject(i)
            // 调用上面写的 Parser
            val parsedList = CourseParser.parseSystemItem(item)
            allCourses.addAll(parsedList)
        }
        // 1. 获取隐藏名单
        val hiddenMap = CourseManager.getHiddenRules()

        // 3. 高效过滤
        val (hiddenSystemCourses, validSystemCourses) = allCourses.partition { course ->
            // 构造当前课程的 Key
            val specificKey = "${course.id}@${course.day}@${course.startNode}"
            hiddenMap.containsKey(specificKey)
            // partition: true=保留, false=被隐藏
        }
        // 3. 获取本地课程
        val localCourses = CourseManager.getLocalCourses()
        allCourses = ArrayList()
        // 4. 合并
        allCourses.addAll(validSystemCourses)
        allCourses.addAll(localCourses)


        // 6. 最终返回
        val result = JSONObject()

        // 有时间的课程 (用于画课表)
        val validCourses = allCourses.filter { it.hasTime() }
        // 没时间的课程 (用于在下面展示列表，如实践课、毕设)
        val otherCourses = allCourses.filter { !it.hasTime() }

        result["validTimeCourses"] = JSON.toJSON(validCourses)
        result["nullTimeCourses"] = JSON.toJSON(otherCourses)
        result["hiddenCourses"] = JSON.toJSON(hiddenSystemCourses)
        return NetworkStatus.Success.toJsonResult(result)
    }

    override suspend fun getUserData(context: Context): JSONObject {
        val url = buildUrl(
            "/jwglxt/xsxxxggl/xsgrxxwh_cxXsgrxx.html",
            "gnmkdm" to "N100801",
            "layout" to "default"
        )

        var doc: Document
        try {
            doc = HttpRequestHelper.getHtmlResponse(url)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return JSONObject()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取用户信息失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }

        if (isShellDocument(doc)) {
            return NetworkStatus.NotFound.toJsonResult()
        }

        val result = JSONObject()
        val values = doc.select(".col-md-4.col-sm-3.mobile-col")
        if (values.size == 2) {
            val id = values[0].select(".form-control-static").text()
            val name = values[1].select(".form-control-static").text()
            result["id"] = id
            result["name"] = name
        }
        return NetworkStatus.Success.toJsonResult(result)
    }

    override suspend fun getSemesterStartDate(context: Context): String {
        val schoolYearFull = EduScheduleConfig.getCurrentSchoolYear()

        val schoolYear = schoolYearFull.split('-')[0]

        val semester = schoolYearFull.split('-')[2]

        val url = buildUrl(
            "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html",
            "xnm" to schoolYear,
            "xqm" to semester,
            "zs" to "1",
            "gnmkdm" to "N2154",
        )

        var raw: String
        try {
            raw = HttpRequestHelper.getJsonResponse(url, HttpMethod.POST)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return "2025-02-17"
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取学期开始时间失败")
            return "2025-02-17"
        }

        if (raw.isEmpty()) {
            return "2025-02-17"
        }
        return "2025-02-17"
        val jsonObject = JSON.parseObject(raw)
        val rqazcList = jsonObject.getJSONArray("rqazcList")
        if (rqazcList.isEmpty()) {
            SystemActionHelper.showToast(context, "未找到学期开始日期")
            return "2025-02-17"
        }
        val rqazc = rqazcList.getJSONObject(0)
        return rqazc.getString("rq")
    }

    override suspend fun getEmptyClassrooms(
        context: Context,
        dateRange: String,
        coursePeriod: String,
        buildingId: String
    ): JSONObject {
        val semesterStartDate = EduScheduleConfig.getSemesterStartDate()

        val dateList = dateRange.split("/")
        if (dateList.size != 2) {
            SystemActionHelper.showToast(context, "日期格式错误")
            return JSONObject()
        }

        val dateMap =
            Tools.dateChangeSimple(Pair(dateList[0], dateList[1]), semesterStartDate)
        val schoolYearFull = EduScheduleConfig.getCurrentSchoolYear()
        val schoolYear = schoolYearFull.split("-")[0]
        val semester = schoolYearFull.split("-")[2]

        val url = buildUrl(
            "/jwglxt/cdjy/cdjy_cxKxcdlb.html",
            "doType" to "query",
            "gnmkdm" to "N253512"
        )
        val result = JSONObject()
        for (entry in dateMap) {
            val formBody = mapOf(
                "zcd" to (2.0).pow(parseInt(entry.key).toDouble() - 1)
                    .toString(),          // 周次
                "xqj" to entry.value.joinToString(","),          // 星期几
                "jcd" to coursePeriod,          // 节数
                "cdlb_id" to "",
                "fwzt" to "cx",
                "xqh_id" to "1",
                "xnm" to schoolYear,          // 学年
                "xqm" to semester,          // 学期
                "cdejlb_id" to "",
                "qszws" to "",
                "jszws" to "",
                "cdmc" to "",
                "lh" to buildingId,            // 楼栋
                "jyfs" to "0",
                "cdjylx" to "",
                "sfbhkc" to "",
                "_search" to "false",
                "nd" to System.currentTimeMillis().toString(),
                "queryModel.showCount" to "100",
                "queryModel.currentPage" to "1",
                "queryModel.sortName" to "cdbh",
                "queryModel.sortOrder" to "asc",
                "time" to "1"
            )
            var raw: String
            try {
                raw = HttpRequestHelper.getJsonResponse(url, HttpMethod.POST, formBody)
            } catch (e: ApiException) {
                // 拦截网络/认证异常
                // 如果底层未授权，就在这里调起 UI
                if (e.status == NetworkStatus.Unauthorized) {
                    SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                    SystemActionHelper.startLogin(context, true)
                } else {
                    // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                    SystemActionHelper.showToast(context, e.message)
                }
                return JSONObject()
            } catch (e: Exception) {
                SystemActionHelper.handleException(context, e, "获取空教室失败")
                return NetworkStatus.UnknownError.toJsonResult()
            }

            if (raw.isEmpty()) {
                return NetworkStatus.NotFound.toJsonResult()
            }
            result.putAll(mapOf(entry.key to raw))
        }
        return NetworkStatus.Success.toJsonResult(result)
    }

    override suspend fun getAllSorces(context: Context, xnm: String, xqm: String): JSONObject {
        val url = buildUrl(
            "/jwglxt/cjcx/cjcx_cxXsgrcj.html",
            "doType" to "query",
            "gnmkdm" to "N305005",
            "xnm" to xnm,//学年
            "xqm" to xqm,//学期
            "kcbj" to "",
            "_search" to "false",
            "nd" to System.currentTimeMillis().toString(),
            "queryModel.showCount" to "500",
            "queryModel.currentPage" to "1",
            "queryModel.sortName" to "+",
            "queryModel.sortOrder" to "desc",
            "time" to "1"
        )

        var raw: String
        try {
            raw = HttpRequestHelper.getJsonResponse(url, HttpMethod.GET)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return JSONObject()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取成绩失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }

        if (raw.isEmpty()) {
            return NetworkStatus.NotFound.toJsonResult()
        }

        return NetworkStatus.Success.toJsonResult(ScoreManager.getScores(JSONObject.parseObject(raw)))
    }

    override suspend fun getSorcesDetail(
        context: Context,
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): JSONObject {

        val url = buildUrl(
            "/jwglxt/cjcx/cjcx_cxCjxqGjh.html",
            "time" to System.currentTimeMillis().toString(),
            "gnmkdm" to "N305005",
            "jxb_id" to classId,
            "xnm" to schoolYear,
            "xqm" to semester,
            "kcmc" to courseName
        )


        var doc: Document
        try {
            doc = HttpRequestHelper.getHtmlResponse(url)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return JSONObject()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取成绩信息失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }

        if (isShellDocument(doc)) {
            return NetworkStatus.NotFound.toJsonResult()
        }
        // 选择所有的tr元素
        val rows: Elements = doc.select("#subtab tbody tr")
        // 创建一个列表来存储结果
        val details: ArrayList<Map<String, String>> = ArrayList()
        // 遍历每一行
        for (row in rows) {
            // 选择所有的td元素
            val tds = row.select("td")

            // 提取数据
            val scoreItem = tds[0].text().replace("【", "").replace("】", "")
            val percentage = tds[1].text()
            val score = tds[2].text()
            // 将映射添加到列表中
            details.add(
                mapOf(
                    "scoreItem" to scoreItem,
                    "percentage" to percentage,
                    "score" to score
                )
            )
        }
        return NetworkStatus.Success.toJsonResult(details)
    }

//    override suspend fun getNoticeInformation(context: Context): JSONObject {
//        return try {
//            val url = buildUrl("/sso/jziotlogin")
//
//            val doc = HttpRequestHelper.getHtmlResponse(url)
//
//            if (isShellDocument(doc)) {
//                return NetworkStatus.NotFound.toJsonResult()
//            }
//            val content = doc.selectFirst(".col-md-12.col-sm-12")
//            val ps = content?.select("p")
//            var text = ""
//            if (ps != null) {
//                for (p in ps) {
//                    val temp = p.text()
//                    if (temp != "") {
//                        text += p.text() + "\n"
//                    }
//                }
//            }
//            NetworkStatus.Success.toJsonResult(text)
//        } catch (e: Exception) {
//            SystemActionHelper.handleException(context,e, "获取通知信息失败")
//            NetworkStatus.UnknownError.toJsonResult(e.message)
//        }
//    }

    override suspend fun getAcademicProgress(context: Context): JSONObject {

        //获取基础的3个参数
        val url = buildUrl(
            "/jwglxt/xjyj/xjyj_cxXjyjIndex.html",
            "gnmkdm" to "N105505",
            "layout" to "default"
        )

        var doc: Document
        try {
            doc = HttpRequestHelper.getHtmlResponse(url)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return JSONObject()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取成绩信息失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }
        if (isShellDocument(doc)) {
            return NetworkStatus.NotFound.toJsonResult()
        }

        val jg_id = doc.select("#jg_id option[selected]").attr("value") ?: ""
        val njdm_id = doc.select("#njdm_id option[selected]").attr("value") ?: ""//年级
        val zyh_id = doc.select("#zyh_id option[selected]").attr("value") ?: ""//专业

        val url1 = buildUrl(
            "/jwglxt/xjyj/xjyj_cxXjyjjdlb.html",
            "gnmkdm" to "N105505",
            "jg_id" to jg_id,
            "njdm_id" to njdm_id,
            "zyh_id" to zyh_id,
        )

        var raw: String
        try {
            raw = HttpRequestHelper.getJsonResponse(url1, HttpMethod.POST)
        } catch (e: ApiException) {
            // 拦截网络/认证异常
            // 如果底层未授权，就在这里调起 UI
            if (e.status == NetworkStatus.Unauthorized) {
                SystemActionHelper.showToast(context, "登录已失效，请重新登录")
                SystemActionHelper.startLogin(context, true)
            } else {
                // 其他网络错误（如 404, 500），弹一个普通 Toast 告知用户
                SystemActionHelper.showToast(context, e.message)
            }
            return JSONObject()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取学业进度信息失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }


        if (raw.isEmpty()) {
            return NetworkStatus.NotFound.toJsonResult()
        }
        val results = JSONArray.parseArray(raw, JSONObject::class.java)

        val result = JSONObject()

        results.forEach { value ->
            val items = value.getJSONArray("kcList") //课程
            val kclbmc = value["xfyqjdmc"] as String // 课程类别名称
            val yqzdxf = value.getFloatValue("yqzdxf") // 最低学分
            var completed = 0f // 已完成课程学分

            if (!items.isNullOrEmpty()) {
                for (item in items) {
                    item as JSONObject
                    val cj = item.getString("cj") //成绩
                    val xf = item.getFloatValue("xf") //学分
                    if (!cj.isNullOrEmpty()) {
                        completed += xf
                    }
                }
            }
            result[kclbmc] = JSONObject().apply {
                put("name", kclbmc)
                put("completed", completed)
                put("total", yqzdxf)
            }
        }
        return NetworkStatus.Success.toJsonResult(result)

    }

    fun getDate(context: Context): String {
        try {
            val result = JSONObject()
            val dateMs = AppDataCenter.getSystemConfig().semesterStartDateMs
            if (dateMs != 0L) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                result["startDate"] = Instant.ofEpochMilli(dateMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter)
            } else {
                result["startDate"] = "2025-02-17"
            }
            result["currentWeek"] = TodayScheduleManager.getCurrentWeek()
            return result.toJSONString()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "时间获取失败：getDate")
            return NetworkStatus.UnknownError.toJsonResult().toString()
        }
    }

    fun saveCourse(context: Context, courseJson: String, hideRule: String?): JSONObject {
        try {
            // 1. 如果有 hideId (说明是修改系统课程)，先隐藏原课程
            if (!hideRule.isNullOrEmpty()) {
                val rule = JSONObject.parseObject(hideRule)
                val id = rule.getString("id")
                val day = rule.getInteger("day")
                val start = rule.getInteger("start")

                // 添加到隐藏规则列表
                if (!id.isNullOrEmpty()) {
                    CourseManager.addHiddenRule(id, day, start)
                }
            }
            // 2. 保存新课程
            val course = JSONObject.parseObject(courseJson, Course::class.java)

            if (course.step == 0 || course.weekList.isEmpty()) {
                return NetworkStatus.UnknownError.toJsonResult("课程数据结构异常")
            }
            val success = CourseManager.saveLocalCourse(course)

            return if (success) {
                NetworkStatus.Success.toJsonResult("保存成功")
            } else {
                NetworkStatus.UnknownError.toJsonResult("保存失败，请重试")
            }
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "saveCourse:未知错误")
            return NetworkStatus.InternalError.toJsonResult("保存失败，请重试")
        }
    }

    fun deleteCourse(
        context: Context,
        courseId: String,
        isSystem: Boolean,
        day: Int?,
        start: Int?
    ): JSONObject {
        try {
            if (courseId.isEmpty()) {
                return NetworkStatus.BadRequest.toJsonResult("参数错误：课程ID为空")
            }

            // 2. 调用之前的 CourseManager 逻辑
            val success = if (isSystem) {
                //如果是系统课程，进行“精准隐藏”
                // 前端必须传 day 和 start
                CourseManager.addHiddenRule(courseId, day!!, start!!)
            } else {
                // 如果是本地课程，直接物理删除
                CourseManager.deleteLocalCourse(courseId)
            }
            // 3. 返回结果
            return if (success) {
                NetworkStatus.Success.toJsonResult("删除成功")
            } else {
                NetworkStatus.UnknownError.toJsonResult("删除失败，请重试")
            }
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "deleteCourse:未知错误")
            return NetworkStatus.InternalError.toJsonResult("服务器内部错误: ${e.message}")
        }
    }

    fun restoreCourse(context: Context, courseId: String, day: Int, start: Int): JSONObject {
        try {
            val success = CourseManager.removeHiddenRule(courseId, day, start)
            return if (success) {
                NetworkStatus.Success.toJsonResult("恢复成功")
            } else {
                NetworkStatus.UnknownError.toJsonResult("恢复失败")
            }
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "restoreCourse:未知错误")
            return NetworkStatus.InternalError.toJsonResult("恢复异常: ${e.message}")
        }
    }
}