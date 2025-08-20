package com.feifan.fuckingnjit.service.impl

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.Model.Course
import com.feifan.fuckingnjit.Model.Time
import com.feifan.fuckingnjit.database.UserData
import com.feifan.fuckingnjit.service.WebService
import com.feifan.fuckingnjit.utils.HttpMethod
import com.feifan.fuckingnjit.utils.HttpRequestHelper
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.TimeManager
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.UserBoxUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import okhttp3.OkHttpClient
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.IOException
import java.lang.Integer.parseInt
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.pow


class WebServiceImpl : WebService {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pattern = Pattern.compile(".*?(1ABB.*?)(?<!start)\\b")
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val httpRequestHelper: HttpRequestHelper =
        HttpRequestHelper(okHttpClient, CookieManager.getInstance())

    private fun buildUrl(path: String, vararg params: Pair<String, String>): String {
        val baseUrl = "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}$path"
        if (params.isEmpty()) return baseUrl

        val encodedParams = params.joinToString("&") { (key, value) ->
            "$key=${value}"
        }
        return "$baseUrl?$encodedParams"
    }

    override suspend fun getCurriculum(): JSONObject {
        return try {
            val schoolYearFull = Manager.getTimeManager().getCurrentSchoolYear()
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
            val additionalHeaders = mapOf(
                "Referer" to "${HttpRequestHelper.BASE_URL}/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login",
                "Origin" to HttpRequestHelper.BASE_URL
            )

            val raw = httpRequestHelper.getJsonResponse(url, HttpMethod.GET, additionalHeaders)
            if (raw.endsWith("</html>")||raw.contains("</html>")) {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
                JSONArray()
            }
            val jsonObject = JSON.parseObject(raw)
            if (jsonObject.containsKey("message") && jsonObject.getString("message") == "需要登录") {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
                JSONArray()
            }
            val items = jsonObject.getJSONArray("items")

            val weekdayMap = mapOf(
                "星期一" to 1, "星期二" to 2, "星期三" to 3,
                "星期四" to 4, "星期五" to 5, "星期六" to 6, "星期日" to 7
            )
            val courses = ArrayList<Course>()
            val size: Int = items.size
            for (m in 0..<size) {
                val item = items.getJSONObject(m)
                var teacher = item.getString("jsxx").split("/")[1]
                var classroom = item.getString("jxdd")
                val courseName = item.getString("kcmc")
                val time = item.getString("sksj")

                if(time == null || classroom == null){
                    val course = Course()
                    course.setName(courseName)
                    course.setTeacher(teacher)

                    if(classroom == null){
                        course.setClassroom("未安排地点")
                    }else{
                        course.setClassroom(classroom)
                    }
                    course.setTime(null)
                    courses.add(course)
                    continue
                }

                val times = time.split(";")
                val classrooms = classroom.split(";")
                for ((j, t) in times.withIndex()) {
                    val weekday = t.substring(0, 3)
                    val courseTime = t.substring(t.indexOf("第"), t.indexOf("{"))
                    val weeks = t.substring(t.indexOf("{") + 1, t.indexOf("}"))
                    val timeArray: ArrayList<Int> = Tools.getCourseTime(courseTime)
                    val weekss =
                        weeks.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                    for (week in weekss) {
                        if (!week.contains("-")) {
                            val course = Course()
                            course.setName(courseName)
                            course.setTeacher(teacher)
                            val time1 = Time(
                                weekdayMap[weekday]!!,
                                timeArray,
                                week.substring(0, week.indexOf("周")).toInt()
                            )
                            course.setTime(time1)
                            course.setClassroom(classrooms[j])
                            courses.add(course)
                        } else {
                            val index = week.indexOf("(")
                            val pos = week.indexOf("-")
                            val left = week.substring(0, pos).toInt()
                            val right = week.substring(pos + 1, week.indexOf("周")).toInt()
                            if (index != -1) {
                                val choice = week[index + 1]
                                if (choice == '单') {
                                    for (i in left..right) {
                                        if (i % 2 == 1) {
                                            val course = Course()
                                            course.setName(courseName)
                                            course.setTeacher(teacher)
                                            val time1 =
                                                Time(weekdayMap[weekday]!!, timeArray, i)
                                            course.setTime(time1)
                                            course.setClassroom(classrooms[j])
                                            courses.add(course)
                                        }
                                    }
                                } else if (choice == '双') {
                                    for (i in left..right) {
                                        if (i % 2 == 0) {
                                            val course = Course()
                                            course.setName(courseName)
                                            course.setTeacher(teacher)
                                            val time1 =
                                                Time(weekdayMap[weekday]!!, timeArray, i)
                                            course.setTime(time1)
                                            course.setClassroom(classrooms[j])
                                            courses.add(course)
                                        }
                                    }
                                }
                            } else {
                                for (i in left..right) {
                                    val course = Course()
                                    course.setName(courseName)
                                    course.setTeacher(teacher)
                                    val time1 = Time(weekdayMap[weekday]!!, timeArray, i)
                                    course.setTime(time1)
                                    course.setClassroom(classrooms[j])
                                    courses.add(course)
                                }
                            }
                        }
                    }
                }
            }

            val maxWeek = courses.maxOfOrNull { it.getTime()?.week ?: 0 } ?: 20

            val (validTimeCourses, nullTimeCourses) = courses.partition { it.getTime() != null }

            val validTimeCoursesList = validTimeCourses.groupBy { it.getTime()!!.week }.run {
                Array(maxWeek + 1) { getOrElse(it) { emptyList() } }
            }
            val result = JSONObject()
            result["validTimeCourses"] = JSON.toJSONString(Tools.getTimeTableData(validTimeCoursesList))
            result["nullTimeCourses"] = JSON.toJSONString(nullTimeCourses)
            result
        } catch (e: Exception) {
            // 分类处理异常
            when (e) {
                is IOException -> {
                    // 网络异常
                    Manager.handleException(e, "getCurriculum:网络错误")
                    return JSONObject()
                }

                is JSONException -> {
                    // JSON解析异常
                    Manager.handleException(e, "getCurriculum:数据解析错误")
                    return JSONObject()
                }

                else -> {
                    Handler(Looper.getMainLooper()).post {
                        Manager.startLogin(true)
                    }
                    Manager.handleException(e, "getCurriculum:未知错误")
                    return JSONObject()
                }
            }
        }
    }

    override suspend fun getUserData(): JSONObject {
        return try {
            val url = buildUrl(
                "/jwglxt/xsxxxggl/xsgrxxwh_cxXsgrxx.html",
                "gnmkdm" to "N100801",
                "layout" to "default"
            )

            val doc = httpRequestHelper.getHtmlResponse(url)

            if (doc.title().contains("登录")) {
//                Manager.showToast("需要登录")
//                Manager.startLogin(true)
                throw Exception("需要登录")
            }

            val values = doc.select(".col-md-4.col-sm-3.mobile-col")
            if (values.size == 2) {
                val id = values[0].select(".form-control-static").text()
                val name = values[1].select(".form-control-static").text()
                JSONObject.parseObject(
                    """
                    {
                        "state":"success",
                        "data":{"id":"$id","name":"$name"}
                    }
                """
                )
            } else {
                JSONObject.parseObject("""{"state":"error","message":"获取个人信息失败"}""")
            }
        } catch (e: Exception) {
            e.message?.let { Manager.showToast(it) }
            Manager.startLogin()
            JSONObject.parseObject("""{"state":"error","message":"${e.message}"}""")
        }
    }

    override suspend fun getSemesterStartDate(): String {
        return try {
            val schoolYearFull = Manager.getTimeManager().getCurrentSchoolYear()
            val schoolYear = schoolYearFull.split('-')[0]
            val semester = schoolYearFull.split('-')[2]

            val additionalHeaders = mapOf(
                "Referer" to "${HttpRequestHelper.BASE_URL}/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login",
                "Origin" to HttpRequestHelper.BASE_URL
            )

            val url = buildUrl(
                "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html",
                "xnm" to schoolYear,
                "xqm" to semester,
                "zs" to "1",
                "gnmkdm" to "N2154",
            )

            val result = httpRequestHelper.getJsonResponse(url, HttpMethod.POST, additionalHeaders)
            if (result.endsWith("</html>")) {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
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
            """{"state":"error","message":"${e.message}"}"""
        }
    }

    //buildingId
    override suspend fun getEmptyClassrooms(
        dateRange: String,
        coursePeriod: String,
        buildingId: String
    ): String {
        val semesterStartDate = Manager.getSemesterStartDate()
        if (semesterStartDate == "") {
//            Handler(Looper.getMainLooper()).post {
            Manager.showToast("获取学期开始日期失败")
//            }
            return """{"state":"error","message":"获取学期开始日期失败"}"""
        }
        val dateList = dateRange.split("/")
        if (dateList.size != 2) {
//            Handler(Looper.getMainLooper()).post {
            Manager.showToast("日期格式错误")
//            }
            return """{"state":"error","message":"日期格式错误"}"""
        }
        val timeManager = TimeManager()
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

            val headers = mapOf(
                "Host" to "casb.njit.edu.cn",
                "Origin" to "https://casb.njit.edu.cn",
                "Referer" to "https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/new/index.html"
            )
            val resultObject = JSONObject.parseObject("{}")
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
                    httpRequestHelper.getJsonResponse(url, HttpMethod.POST, headers, formBody)
                if (result.endsWith("</html>")) {
//                    Handler(Looper.getMainLooper()).post {
                    Manager.showToast("需要登录")
//                    }
                    Manager.startLogin(true)
                    return """{"state":"error","message":"需要登录"}"""
                }
                resultObject.putAll(mapOf(entry.key to result))
            }
            resultObject.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "获取空教室失败")
            """{"state":"error","message":"${e.message}"}"""
        }
    }

    override suspend fun getAllSorces(): JSONObject {
        return try {
            val url = buildUrl(
                "/jwglxt/cjcx/cjcx_cxXsgrcj.html",
                "doType" to "query",
                "gnmkdm" to "N305005",
                "xnm" to "",
                "xqm" to "",
                "kcbj" to "",
                "_search" to "false",
                "nd" to System.currentTimeMillis().toString(),
                "queryModel.showCount" to "500",
                "queryModel.currentPage" to "1",
                "queryModel.sortName" to "+",
                "queryModel.sortOrder" to "desc",
                "time" to "1"
            )
            val headers = mapOf(
                "Host" to "casb.njit.edu.cn",
                "Origin" to "https://casb.njit.edu.cn",
                "Referer" to "https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/new/index.html"
            )
            val raw = httpRequestHelper.getJsonResponse(url, HttpMethod.GET, headers)
            if (raw.endsWith("</html>")) {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
                return JSONObject()
            }
            val result = JSONObject()
            result["data"] = Tools.getScores(JSONObject.parseObject(raw))
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
    ): String {
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

            val doc = httpRequestHelper.getHtmlResponse(url)

            if (doc.title().contains("登录")) {
//                Handler(Looper.getMainLooper()).post {
                Manager.showToast("需要登录")
//                }
                Manager.startLogin(true)
                return """{"state":"error","message":"需要登录"}"""
            }

            // 解析HTML
//            val doc = Jsoup.parse(html)


            // 选择所有的tr元素
            val rows: Elements = doc.select("#subtab tbody tr")


            // 创建一个列表来存储结果
            val details: MutableList<Map<String, String>> = ArrayList()

            // 遍历每一行
            for (row in rows) {
                // 选择所有的td元素
                val tds = row.select("td")

                // 提取数据
                val scoreItem = tds[0].text().replace("【", "").replace("】", "")
                val percentage = tds[1].text()
                val score = tds[2].text()

                // 创建一个映射来存储每一行的数据
                val scoreMap = mapOf<String, String>(
                    "scoreItem" to scoreItem,
                    "percentage" to percentage,
                    "score" to score
                )

                // 将映射添加到列表中
                details.add(scoreMap)
            }

            val result = JSONObject.parseObject(
                """
                    {
                        "state":"success"
                    }
                """
            )
            result["data"] = JSONArray.parseArray(JSON.toJSONString(details))
            result.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "获取成绩详情失败")
            JSONObject.parseObject("""{"state":"error","message":"${e.message}"}""").toJSONString()
        }
    }

    override suspend fun getNoticeInformation(): String {
        return try {
            val url = buildUrl("/sso/jziotlogin")

            val doc = httpRequestHelper.getHtmlResponse(url)

            if (doc.title().contains("登录")) {
//                Handler(Looper.getMainLooper()).post {
                Manager.showToast("需要登录")
//                }
                Manager.startLogin(true)
                return """{"state":"error","message":"需要登录"}"""
            }

            val content = doc.selectFirst(".col-md-12.col-sm-12")
            val ps = content?.select("p")
            var text = ""
            if (ps != null) {
                for (p in ps) {
                    val temp = p.text()
                    if (!temp.equals("")) {
                        text += p.text() + "\n"
                    }
                }
            }
            val result = JSONObject()
            result["state"] = "success"
            result["data"] = text
            result.toJSONString()
        } catch (e: Exception) {
            when (e) {
                is IOException -> {
                    Manager.handleException(e, "getNoticeInformation:网络错误")
                    return """{"state":"network_error","message":"网络请求失败"}"""
                }

                is JSONException -> {
                    Manager.handleException(e, "getNoticeInformation:数据解析错误")
                    return """{"state":"parse_error","message":"数据解析失败"}"""
                }

                else -> {
                    Handler(Looper.getMainLooper()).post {
                        Manager.startLogin(true)
                    }
                    Manager.handleException(e, "getNoticeInformation:未知错误")
                    return """{"state":"error","message":"需要重新登录"}"""
                }
            }
        } finally {
            // Test.test()
        }
    }

    override suspend fun getAcademicProgress(refresh: Boolean): String {
        return try {
            val id = Manager.getUserManager()?.getCurrentUser()?.id ?: ""
            var userData = UserBoxUtils.getUserById(id)
            if (userData == null) {
                userData = UserData()
                userData.id = id
                UserBoxUtils.insertUserData(userData)
            }
            if (!refresh) {
                val result = userData.academicProgress
                if (!result.isEmpty()) {
                    return result.toJSONString()
                }
            }
            val url = buildUrl(
                "/jwglxt/jxzxjhgl/jxzxjhck_cxJxzxjhckIndex.html",
                "gnmkdm" to "N153540",
                "layout" to "default"
            )

            val doc = httpRequestHelper.getHtmlResponse(url)
            if (doc.title().contains("登录")) {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
                return """{"state":"error","message":"需要登录"}"""
            }

            val jg_id = doc.select("#jg_id option[selected]").attr("value") ?: ""
            val njdm_id = doc.select("#nj_cx option[selected]").attr("value") ?: ""
            val zyh_id = doc.select("#zyh_id_cx option[selected]").attr("value") ?: ""


            val url1 = buildUrl(
                "/jwglxt/jxzxjhgl/jxzxjhck_cxJxzxjhckIndex.html",
                "doType" to "query",
                "gnmkdm" to "N153540",
                "jg_id" to jg_id,
                "njdm_id" to njdm_id,
                "zyh_id" to zyh_id,
                "_search" to "false",
                "nd" to System.currentTimeMillis().toString(),
                "queryModel.showCount" to "200",
                "queryModel.currentPage" to "1",
                "queryModel.sortName" to "",
                "queryModel.sortOrder" to "asc",
                "time" to "0"
            )
            val additionalHeaders = mapOf(
                "Host" to "casb.njit.edu.cn",
                "Referer" to "${HttpRequestHelper.BASE_URL}/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login",
                "Origin" to HttpRequestHelper.BASE_URL
            )

            val raw = httpRequestHelper.getJsonResponse(url1, HttpMethod.POST, additionalHeaders)
            if (raw.endsWith("</html>")) {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
                return """{"state":"error","message":"需要登录"}"""
            }
            val items = JSONObject.parseObject(raw)["items"] as JSONArray
            val item = items[0] as JSONObject
            val jxzxjhxx_id = item["jxzxjhxx_id"] as String


            val cookies = CookieManager.getInstance()
                .getCookie(HttpRequestHelper.BASE_URL)
                .orEmpty()
                .splitToSequence(";")  // 改用 sequence 优化内存
                .map { it.trim() }
                .associateTo(mutableMapOf()) { cookie ->
                    cookie.split("=", limit = 2).let {
                        it.first() to it.getOrNull(1).orEmpty()
                    }
                }

            val connection: Connection =
                Jsoup.connect(
                    "https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a/jwglxt/jxzxjhgl/jxzxjhck_cxJxzxjhxdyqIndex.html?jxzxjhxx_id=$jxzxjhxx_id&_=" + System.currentTimeMillis()
                        .toString() + "&gnmkdm=N153540"
                )
            connection.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:29.0) Gecko/20100101 Firefox/29.0"
            )
            val response1 =
                connection.cookies(cookies).method(Connection.Method.GET).ignoreContentType(true)
                    .execute()
            val doc1 = Jsoup.parse(response1.body())

            //        System.out.println(doc);

            val matcher: Matcher = pattern.matcher(doc1.select("script:not([src])").html())

            val uniqueResults: MutableSet<String> = HashSet() // 用于去重

            while (matcher.find()) {
                matcher.group(1)?.let {
                    // 正则已保证包含1ABB且不以start结尾
                    uniqueResults.add(it)
                }
            }

            uniqueResults.add("qtkcxfyq")
            val deferredResults = uniqueResults.map { id ->
                coroutineScope.async(Dispatchers.IO) {  // 使用传入的scope而不是GlobalScope
                    val url0 = buildUrl(
                        "/jwglxt/xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html",
                        "gnmkdm" to "N105515",
                        "xfyqjd_id" to id
                    )
                    val raw0 =
                        httpRequestHelper.getJsonResponse(url0, HttpMethod.POST, additionalHeaders)
                    if (raw0.endsWith("</html>")) {
                        Manager.showToast("需要登录")
                        Manager.startLogin(true)
                        return@async JSONArray()
                    }

                    var items0 = JSONArray.parseArray(raw0, JSONObject::class.java)
                    if (items0.size == 0) {
                        val url00 = buildUrl(
                            "/jwglxt/xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html",
                            "gnmkdm" to "N105515",
                            "xfyqjd_id" to id
                        )
                        val raw00 = httpRequestHelper.getJsonResponse(
                            url00,
                            HttpMethod.POST,
                            additionalHeaders
                        )
                        if (raw00.endsWith("</html>")) {
                            Manager.showToast("需要登录")
                            Manager.startLogin(true)
                            return@async JSONArray()
                        }
                        items0 = JSONArray.parseArray(raw00, JSONObject::class.java)
                    }
                    return@async items0
                }
            }

            val result = JSONObject()
            val results = deferredResults.awaitAll()

            results.forEach { items0 ->
                items0 as ArrayList
                for (item0 in items0) {
                    item0 as JSONObject
                    val completed = item0["XDZT"] as String == "4"
                    val kclbmc = item0["KCLBMC"] as String
                    if (!result.containsKey(kclbmc)) {
                        result[kclbmc] = JSONObject.parseObject(
                            """
                    {
                        "name": "$kclbmc",
                        "completed": 0,
                        "total":0
                    }
                    """
                        )
                    }
                    val kclb_obj = result[kclbmc] as JSONObject
                    kclb_obj["total"] = (kclb_obj["total"] as Int) + 1
                    if (completed) {
                        kclb_obj["completed"] = (kclb_obj["completed"] as Int) + 1
                    }
                }
            }
//            println(result.toJSONString())
            userData.academicProgress = result
            UserBoxUtils.updateUserData(userData)
            result.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "academicProgress:未知错误")
            """{"state":"error","message":"${e.message}"}"""
        }
    }
}