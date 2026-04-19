package com.feifan.fuckingnjit.service.impl

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.service.WebService
import com.feifan.fuckingnjit.utils.CourseManager
import com.feifan.fuckingnjit.utils.CourseParser
import com.feifan.fuckingnjit.utils.HttpMethod
import com.feifan.fuckingnjit.utils.HttpRequestHelper
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.NetworkStatus
import com.feifan.fuckingnjit.utils.TimeManager
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.lang.Integer.parseInt
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.pow


class WebServiceImpl private constructor() : WebService {
    companion object {
        private val instance_: WebServiceImpl by lazy { WebServiceImpl() }
        fun getInstance(): WebServiceImpl = instance_
    }

    private fun buildUrl(path: String, vararg params: Pair<String, String>): String {
        val baseUrl = "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}$path"
        if (params.isEmpty()) return baseUrl

        val encodedParams = params.joinToString("&") { (key, value) ->
            "$key=${value}"
        }
        return "$baseUrl?$encodedParams"
    }

    private fun isShellDocument(doc: Document): Boolean {
        return doc.children().size == 1 &&           // 只有 <html> 一个子节点
                doc.head().children().isEmpty() &&    // <head> 为空
                doc.body().children().isEmpty()       // <body> 为空
    }

    override suspend fun getCurriculum(): JSONObject {
        return try {
            // 1. 准备请求 (保持你原有的逻辑)
            val schoolYearFull = TimeManager.getInstance().getCurrentSchoolYear()
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

            val raw = HttpRequestHelper.getJsonResponse(url, HttpMethod.GET)
            if (raw.isEmpty()) {
                JSONArray()
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
            val studentId = BaseDataBoxUtils.getCurrentUserId()
            val hiddenMap = CourseManager.getHiddenRules(studentId)

            // 3. 高效过滤
            val (hiddenSystemCourses, validSystemCourses) = allCourses.partition { course ->
                // 构造当前课程的 Key
                val specificKey = "${course.id}@${course.day}@${course.startNode}"
                hiddenMap.containsKey(specificKey)
                // partition: true=保留, false=被隐藏
            }
            // 3. 获取本地课程
            val localCourses = CourseManager.getLocalCourses(studentId)
            println("localCourses:$localCourses")
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
            result
        } catch (e: Exception) {
            Manager.handleException(e, "获取课表失败")
            return JSONObject()
        }
    }

    override suspend fun getUserData(): JSONObject {
        return try {
            val url = buildUrl(
                "/jwglxt/xsxxxggl/xsgrxxwh_cxXsgrxx.html",
                "gnmkdm" to "N100801",
                "layout" to "default"
            )

            val doc = HttpRequestHelper.getHtmlResponse(url)
            if (isShellDocument(doc)) {
                JSONArray()
            }

            val values = doc.select(".col-md-4.col-sm-3.mobile-col")
            if (values.size == 2) {
                val id = values[0].select(".form-control-static").text()
                val name = values[1].select(".form-control-static").text()
                val result = JSONObject()
                result["id"] = id
                result["name"] = name
                result
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            Manager.handleException(e, "获取用户信息失败")
            JSONObject()
        }
    }

    override suspend fun getSemesterStartDate(): String {
        return try {
            return "2025-02-17"
            val schoolYearFull = TimeManager.getInstance().getCurrentSchoolYear()
            val schoolYear = schoolYearFull.split('-')[0]
            val semester = schoolYearFull.split('-')[2]

            val url = buildUrl(
                "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html",
                "xnm" to schoolYear,
                "xqm" to semester,
                "zs" to "1",
                "gnmkdm" to "N2154",
            )

            val result = HttpRequestHelper.getJsonResponse(url, HttpMethod.POST)
            if (result.isEmpty()) {
                return "2025-02-17"
            }
            val jsonObject = JSON.parseObject(result)
            val rqazcList = jsonObject.getJSONArray("rqazcList")
            if (rqazcList.size == 0) {
                Manager.showToast("未找到学期开始日期")
                return "2025-02-17"
            }
            val rqazc = rqazcList.getJSONObject(0)
            rqazc.getString("rq")
        } catch (e: Exception) {
            Manager.handleException(e, "获取学期开始日期失败")
            "2025-02-17"
        }
    }

    override suspend fun getEmptyClassrooms(
        dateRange: String,
        coursePeriod: String,
        buildingId: String
    ): String {
        val semesterStartDate = TimeManager.getInstance().getSemesterStartDate()

        val dateList = dateRange.split("/")
        if (dateList.size != 2) {
            Manager.showToast("日期格式错误")
            return "{}"
        }
        val timeManager = TimeManager.getInstance()
        val dateMap =
            timeManager.dateChangeSimple(Pair(dateList[0], dateList[1]), semesterStartDate)
        val schoolYearFull = timeManager.getCurrentSchoolYear()
        val schoolYear = schoolYearFull.split("-")[0]
        val semester = schoolYearFull.split("-")[2]
        return try {
            val url = buildUrl(
                "/jwglxt/cdjy/cdjy_cxKxcdlb.html",
                "doType" to "query",
                "gnmkdm" to "N253512"
            )
            val resultObject = JSONObject()
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
                val result =
                    HttpRequestHelper.getJsonResponse(url, HttpMethod.POST, formBody)
                if (result.isEmpty()) {
                    return "{}"
                }
                resultObject.putAll(mapOf(entry.key to result))
            }
            resultObject.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "获取空教室失败")
            "{}"
        }
    }

    override suspend fun getAllSorces(xnm: String, xqm: String): JSONObject {
        return try {
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
            val raw = HttpRequestHelper.getJsonResponse(url, HttpMethod.GET)
            val result = JSONObject()

            if (raw.isNotEmpty()) {
                result["data"] = Tools.getScores(JSONObject.parseObject(raw))
            }
            result
        } catch (e: Exception) {
            Manager.handleException(e, "获取全部成绩失败")
            JSONObject()
        }

    }

    override suspend fun getSorcesDetail(
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): JSONObject {
        return try {
            val url = buildUrl(
                "/jwglxt/cjcx/cjcx_cxCjxqGjh.html",
                "time" to System.currentTimeMillis().toString(),
                "gnmkdm" to "N305005",
                "jxb_id" to classId,
                "xnm" to schoolYear,
                "xqm" to semester,
                "kcmc" to courseName
            )

            val doc = HttpRequestHelper.getHtmlResponse(url)

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
            NetworkStatus.Success.toJsonResult(details)
        } catch (e: Exception) {
            Manager.handleException(e, "获取成绩详情失败")
            NetworkStatus.UnknownError.toJsonResult(e.message)
        }
    }

    override suspend fun getNoticeInformation(): JSONObject {
        return try {
            val url = buildUrl("/sso/jziotlogin")

            val doc = HttpRequestHelper.getHtmlResponse(url)

            if (isShellDocument(doc)) {
                return NetworkStatus.NotFound.toJsonResult()
            }
            val content = doc.selectFirst(".col-md-12.col-sm-12")
            val ps = content?.select("p")
            var text = ""
            if (ps != null) {
                for (p in ps) {
                    val temp = p.text()
                    if (temp != "") {
                        text += p.text() + "\n"
                    }
                }
            }
            NetworkStatus.Success.toJsonResult(text)
        } catch (e: Exception) {
            Manager.handleException(e, "获取通知信息失败")
            NetworkStatus.UnknownError.toJsonResult(e.message)
        }
    }

    override suspend fun getAcademicProgress(): JSONObject {
        return try {
            //获取基础的3个参数
            val url = buildUrl(
                "/jwglxt/xjyj/xjyj_cxXjyjIndex.html",
                "gnmkdm" to "N105505",
                "layout" to "default"
            )

            val doc = HttpRequestHelper.getHtmlResponse(url)
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

            var raw = HttpRequestHelper.getJsonResponse(url1, HttpMethod.POST)
            if (raw.isEmpty()) {
                NetworkStatus.NotFound.toJsonResult()
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
            result
            NetworkStatus.Success.toJsonResult(result)
        } catch (e: Exception) {
            Manager.handleException(e, "academicProgress:未知错误")
            NetworkStatus.UnknownError.toJsonResult()
        }
    }

    fun getDate(): String {
        try {
            val result = JSONObject()
            val dateMs = BaseDataBoxUtils.getSemesterStartDate()
            if (dateMs != 0L) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                result["startDate"] = Instant.ofEpochMilli(dateMs)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter)
            } else {
                result["startDate"] = "2025-02-17"
            }
            result["currentWeek"] = BaseDataBoxUtils.getCurrentWeek()
            return result.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "时间获取失败：getDate")
            return "{}"
        }
    }

    fun saveCourse(courseJson: String, hideRule: String?): JSONObject {
        try {
            val studentId = BaseDataBoxUtils.getCurrentUserId()
            // 1. 如果有 hideId (说明是修改系统课程)，先隐藏原课程
            if (!hideRule.isNullOrEmpty()) {
                val rule = JSONObject.parseObject(hideRule)
                val id = rule.getString("id")
                val day = rule.getInteger("day")
                val start = rule.getInteger("start")

                // 添加到隐藏规则列表
                if (!id.isNullOrEmpty()) {
                    CourseManager.addHiddenRule(studentId, id, day, start)
                }
            }
            // 2. 保存新课程
            val course = JSONObject.parseObject(courseJson, Course::class.java)

            if (course.step == 0 || course.weekList.isEmpty()) {
                return NetworkStatus.UnknownError.toJsonResult("课程数据结构异常")
            }
            val success = CourseManager.saveLocalCourse(studentId, course)

            if (success) {
                return NetworkStatus.Success.toJsonResult("保存成功")
            } else {
                return NetworkStatus.UnknownError.toJsonResult("保存失败，请重试")
            }
        } catch (e: Exception) {
            Manager.handleException(e, "saveCourse:未知错误")
            return NetworkStatus.InternalError.toJsonResult("保存失败，请重试")
        }
    }

    fun deleteCourse(courseId: String, isSystem: Boolean, day: Int?, start: Int?): JSONObject {
        try {
            val studentId = BaseDataBoxUtils.getCurrentUserId()
            if (courseId.isEmpty()) {
                return NetworkStatus.BadRequest.toJsonResult("参数错误：课程ID为空")
            }

            // 2. 调用之前的 CourseManager 逻辑
            var success = false

            if (isSystem) {
                //如果是系统课程，进行“精准隐藏”
                // 前端必须传 day 和 start
                success = CourseManager.addHiddenRule(studentId, courseId, day!!, start!!)
            } else {
                // 如果是本地课程，直接物理删除
                success = CourseManager.deleteLocalCourse(studentId, courseId)
            }
            // 3. 返回结果
            if (success) {
                return NetworkStatus.Success.toJsonResult("删除成功")
            } else {
                return NetworkStatus.UnknownError.toJsonResult("删除失败，请重试")
            }
        } catch (e: Exception) {
            Manager.handleException(e, "deleteCourse:未知错误")
            return NetworkStatus.InternalError.toJsonResult("服务器内部错误: ${e.message}")
        }
    }

    fun restoreCourse(courseId: String, day: Int, start: Int): JSONObject {
        try {
            val studentId = BaseDataBoxUtils.getCurrentUserId()
            val success = CourseManager.removeHiddenRule(studentId, courseId, day, start)
            if (success) {
                return NetworkStatus.Success.toJsonResult("恢复成功")
            } else {
                return NetworkStatus.UnknownError.toJsonResult("恢复失败")
            }
        } catch (e: Exception) {
            Manager.handleException(e, "restoreCourse:未知错误")
            return NetworkStatus.InternalError.toJsonResult("恢复异常: ${e.message}")
        }
    }
//    suspend fun getAcademicProgressOld(refresh: Boolean): String {
//        return try {
//            val userData = Manager.getUserManager().getCurrentUser()
//            if (!refresh) {
//                val result = userData.academicProgress
//                if (result.isNotEmpty()) {
//                    return result.toJSONString()
//                }
//            }
//            //获取基础的3个参数
//            val url = buildUrl(
//                "/jwglxt/jxzxjhgl/jxzxjhck_cxJxzxjhckIndex.html",
//                "gnmkdm" to "N153540",
//                "layout" to "default"
//            )
//
//            val doc = HttpRequestHelper.getHtmlResponse(url)
//            if (isShellDocument(doc)) {
//                return "{}"
//            }
//
//            val jg_id = doc.select("#jg_id option[selected]").attr("value") ?: ""
//            val njdm_id = doc.select("#nj_cx option[selected]").attr("value") ?: ""//年级
//            val zyh_id = doc.select("#zyh_id_cx option[selected]").attr("value") ?: ""//专业
//
//
//            val url1 = buildUrl(
//                "/jwglxt/jxzxjhgl/jxzxjhck_cxJxzxjhckIndex.html",
//                "doType" to "query",
//                "gnmkdm" to "N153540",
//                "jg_id" to jg_id,
//                "njdm_id" to njdm_id,
//                "zyh_id" to zyh_id,
//                "_search" to "false",
//                "nd" to System.currentTimeMillis().toString(),
//                "queryModel.showCount" to "200",
//                "queryModel.currentPage" to "1",
//                "queryModel.sortName" to "",
//                "queryModel.sortOrder" to "asc",
//                "time" to "0"
//            )
//            val additionalHeaders = mapOf(
//                "Referer" to "${HttpRequestHelper.BASE_URL}/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login",
//                "Origin" to HttpRequestHelper.BASE_URL
//            )
//
//            val raw = HttpRequestHelper.getJsonResponse(url1, HttpMethod.POST, additionalHeaders)
//            if (raw.isEmpty()) {
//                return "{}"
//            }
//            val items = JSONObject.parseObject(raw)["items"] as JSONArray
//            val item = items[0] as JSONObject
//            val jxzxjhxx_id = item["jxzxjhxx_id"] as String
//
//
////            val cookies = CookieManager.getInstance()
////                .getCookie(HttpRequestHelper.BASE_URL)
////                .orEmpty()
////                .splitToSequence(";")  // 改用 sequence 优化内存
////                .map { it.trim() }
////                .associateTo(mutableMapOf()) { cookie ->
////                    cookie.split("=", limit = 2).let {
////                        it.first() to it.getOrNull(1).orEmpty()
////                    }
////                }
//
//            val url_ = buildUrl(
//                "/jwglxt/jxzxjhgl/jxzxjhck_cxJxzxjhxdyqIndex.html",
//                "jxzxjhxx_id" to jxzxjhxx_id,
//                "_" to System.currentTimeMillis().toString(),
//                "gnmkdm" to "N153540"
//            )
//
//            val doc1 = HttpRequestHelper.getHtmlResponse(url_)
//            if (isShellDocument(doc1)) {
//                return "{}"
//            }
//
////            val connection: Connection =
////                Jsoup.connect(
////                    "https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a?
////                )
////            connection.header(
////                "User-Agent",
////                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:29.0) Gecko/20100101 Firefox/29.0"
////            )
////            val response1 =
////                connection.cookies(cookies).method(Connection.Method.GET).ignoreContentType(true)
////                    .execute()
////            val doc1 = Jsoup.parse(response1.body())
//
//            //        System.out.println(doc);
//
//            val matcher: Matcher = pattern.matcher(doc1.select("script:not([src])").html())
//
//            val uniqueResults: MutableSet<String> = HashSet() // 用于去重
//
//            while (matcher.find()) {
//                matcher.group(1)?.let {
//                    // 正则已保证包含1ABB且不以start结尾
//                    uniqueResults.add(it)
//                }
//            }
//
//            uniqueResults.add("qtkcxfyq")
//            val deferredResults = uniqueResults.map { id ->
//                coroutineScope.async(Dispatchers.IO) {  // 使用传入的scope而不是GlobalScope
//                    val url0 = buildUrl(
//                        "/jwglxt/xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html",
//                        "gnmkdm" to "N105515",
//                        "xfyqjd_id" to id
//                    )
//                    val raw0 =
//                        HttpRequestHelper.getJsonResponse(url0, HttpMethod.POST, additionalHeaders)
//                    if (raw0.isEmpty()) {
//                        return@async JSONArray()
//                    }
//
//                    var items0 = JSONArray.parseArray(raw0, JSONObject::class.java)
//                    if (items0.size == 0) {
//                        val url00 = buildUrl(
//                            "/jwglxt/xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html",
//                            "gnmkdm" to "N105515",
//                            "xfyqjd_id" to id
//                        )
//                        val raw00 = HttpRequestHelper.getJsonResponse(
//                            url00,
//                            HttpMethod.POST,
//                            additionalHeaders
//                        )
//                        if (raw00.isEmpty()) {
//                            return@async JSONArray()
//                        }
//                        items0 = JSONArray.parseArray(raw00, JSONObject::class.java)
//                    }
//                    return@async items0
//                }
//            }
//
//            val result = JSONObject()
//            val results = deferredResults.awaitAll()
//
//            results.forEach { items0 ->
//                items0 as ArrayList
//                for (item0 in items0) {
//                    item0 as JSONObject
//                    val completed = item0["XDZT"] as String == "4"
//                    val kclbmc = item0["KCLBMC"] as String
//                    if (!result.containsKey(kclbmc)) {
//                        result[kclbmc] = JSONObject.parseObject(
//                            """
//                    {
//                        "name": "$kclbmc",
//                        "completed": 0,
//                        "total":0
//                    }
//                    """
//                        )
//                    }
//                    val kclb_obj = result[kclbmc] as JSONObject
//                    kclb_obj["total"] = (kclb_obj["total"] as Int) + 1
//                    if (completed) {
//                        kclb_obj["completed"] = (kclb_obj["completed"] as Int) + 1
//                    }
//                }
//            }
////            println(result.toJSONString())
//            userData.academicProgress = result
//            UserBoxUtils.updateUserData(userData)
//            result.toJSONString()
//        } catch (e: Exception) {
//            Manager.handleException(e, "academicProgress:未知错误")
//            "{}"
//        }
//    }
}