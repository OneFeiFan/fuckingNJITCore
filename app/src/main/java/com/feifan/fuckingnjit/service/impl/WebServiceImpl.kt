package com.feifan.fuckingnjit.service.impl

import android.webkit.CookieManager
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.Model.Course
import com.feifan.fuckingnjit.Model.Time
import com.feifan.fuckingnjit.service.WebService
import com.feifan.fuckingnjit.utils.HttpMethod
import com.feifan.fuckingnjit.utils.HttpRequestHelper
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.TimeManager
import com.feifan.fuckingnjit.utils.Tools
import okhttp3.OkHttpClient
import org.jsoup.select.Elements
import java.lang.Integer.parseInt
import kotlin.math.pow


// 1. 首先创建网络请求工具类


// 3. WebService实现类优化
class WebServiceImpl : WebService {
    private val httpRequestHelper: HttpRequestHelper =
        HttpRequestHelper(OkHttpClient(), CookieManager.getInstance())

    override suspend fun getCurriculum(): String {
        return try {
            val url =
                "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/jwglxt/kbcx/xskbqr_cxXskbqrIndex.html" +
                        "?doType=query&gnmkdm=N2158&enlink-vpn&xnm=2024&xqm=12&_search=false" +
                        "&nd=1725346567148&queryModel.showCount=200&queryModel.currentPage=1" +
                        "&queryModel.sortName=&queryModel.sortOrder=asc&time=1"

            val additionalHeaders = mapOf(
                "Referer" to "${HttpRequestHelper.BASE_URL}/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login",
                "Origin" to HttpRequestHelper.BASE_URL
            )

            val raw = httpRequestHelper.getJsonResponse(url, HttpMethod.GET, additionalHeaders)
            println("课表数据：$raw")
            if (raw.endsWith("</html>")) {
//                Handler(Looper.getMainLooper()).post {
                    Manager.showToast("需要登录")
//                }

                Manager.startLogin(true)
                """{"state":"error","message":"需要登录"}"""
            } else {
                val jsonObject = JSON.parseObject(raw);
                if(jsonObject.containsKey("message")&&jsonObject.getString("message")=="需要登录"){
                    return """{"state":"error","message":"需要登录"}"""
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
                    var teacher = item.getString("jsxx")
                    var classroom = item.getString("jxdd")
                    val courseName = item.getString("kcmc")
                    val time = item.getString("sksj") ?: continue
                    if (classroom == null) {
                        classroom = "上课地点未定"
                    }
                    val times = time.split(";")
                    val classrooms = classroom.split(";")
                    teacher = teacher.split("/")[1]
                    var j = 0
                    for (t in times) {
                        println(t)
                        val weekday = t.substring(0, 3)
                        println(weekday)
                        val courseTime = t.substring(t.indexOf("第"), t.indexOf("{"))
                        println(courseTime)
                        val weeks = t.substring(t.indexOf("{") + 1, t.indexOf("}"))

                        val timeArray: ArrayList<Int> = Tools.getCourseTime(courseTime)
                        println()
                        //System.out.println(courseName+" "+weekday+" "+courseTime+" "+weeks);
                        val weekss =
                            weeks.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        //
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
                        j++
                    }
                }

                val maxWeek = courses.maxOfOrNull { it.getTime().week } ?: 20
                val result = courses.groupBy { it.getTime().week }.run {
                    Array(maxWeek + 1) { getOrElse(it) { emptyList() } }
                }
                    JSONArray.parseArray(JSONObject.toJSONString(Tools.getTimeTableData(result)))
                        .toJSONString()
            }
        } catch (e: Exception) {
            e.printStackTrace() // 显示完整堆栈轨迹
            Manager.startLogin(true)
            """{"state":"error","message":"${e.message}"}"""
        }
    }

    override suspend fun getUserData(): JSONObject {
        return try {
            val url =
                "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/jwglxt/xsxxxggl/xsgrxxwh_cxXsgrxx.html" +
                        "?gnmkdm=N100801&layout=default"

            val doc = httpRequestHelper.getHtmlResponse(url)

            if (doc.title().contains("登录")) {
//                Manager.showToast("需要登录")
//                Manager.startLogin(true)
                throw Exception("需要登录")
            }

            val values = doc.select(".col-md-4.col-sm-3.mobile-col")
            println(values.text())
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
            val url =
                "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/jwglxt/xtgl/index_cxAreaFive.html" +
                        "?localeKey=zh_CN&gnmkdm=index&enlink-vpn"
            val doc = httpRequestHelper.getHtmlResponse(url)
            val values = doc.select(".tab-th-1")[0].text()
            values.split("(")[1].split("至")[0]
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
            val url =
                "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/jwglxt/cdjy/cdjy_cxKxcdlb.html" +
                        "?doType=query&gnmkdm=N253512&enlink-vpn="

            val headers = mapOf(
                "Host" to "casb.njit.edu.cn",
                "Origin" to "https://casb.njit.edu.cn",
                "Referer" to "https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/new/index.html"
            )
            val resultObject = JSONObject.parseObject("{}")
            for (entry in dateMap) {
                val formBody = mapOf<String, String>(
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
//                println("${entry.key} ${entry.value.joinToString(",")} $coursePeriod $schoolYear $semester $buildingId")
//                println(result)
            }
//            println(resultObject.toJSONString())
            resultObject.toJSONString()
        } catch (e: Exception) {
            e.message?.let {
                println(it)
//                Handler(Looper.getMainLooper()).post {
                    Manager.showToast(it)
//                }
            }
            """{"state":"error","message":"${e.message}"}"""
        }
    }

    override suspend fun getAllSorces(): String {
        return try {
            val url =
                "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/jwglxt/cjcx/cjcx_cxXsgrcj.html" +
                        "?doType=query&gnmkdm=N305005&enlink-vpn&xnm=&xqm=&kcbj=&_search=false&nd=${System.currentTimeMillis()}&queryModel.showCount=500&queryModel.currentPage=1&queryModel.sortName=+&queryModel.sortOrder=desc&time=1"

            val headers = mapOf(
                "Host" to "casb.njit.edu.cn",
                "Origin" to "https://casb.njit.edu.cn",
                "Referer" to "https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/new/index.html"
            )
            val result = httpRequestHelper.getJsonResponse(url, HttpMethod.GET, headers)
            if (result.endsWith("</html>")) {
//                Handler(Looper.getMainLooper()).post {
                    Manager.showToast("需要登录")
//                }
                Manager.startLogin(true)
                return """{"state":"error","message":"需要登录"}"""
            }


           val resultObject = JSONObject.parseObject(
                """
                    {
                        "state":"success"
                    }
                """
            )
            resultObject["data"] = Tools.getScores(JSONObject.parseObject(result))
            resultObject.toJSONString()
        } catch (e: Exception) {
            e.message?.let {
//                Handler(Looper.getMainLooper()).post {
                    Manager.showToast(it)
//                }
            }
            """{"state":"error","message":"${e.message}"}"""
        }

    }

    override suspend fun getSorcesDetail(
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): String {
        return try {
            val url = "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/jwglxt/cjcx/cjcx_cxCjxqGjh.html" +
                        "?time=${System.currentTimeMillis()}&gnmkdm=N305005&enlink-vpn&jxb_id=${classId}&xnm=${schoolYear}&xqm=${semester}&kcmc=${courseName}"
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
                val scoreMap =mapOf<String, String>(
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
            e.message?.let {
//                Handler(Looper.getMainLooper()).post {
                    Manager.showToast(it)
//                }
            }
            Manager.startLogin()
            JSONObject.parseObject("""{"state":"error","message":"${e.message}"}""").toJSONString()
        }
    }
    override suspend fun getNoticeInformation(): String {
        return try {
            val url = "${HttpRequestHelper.BASE_URL}${HttpRequestHelper.WEBVPN_PATH}/sso/jziotlogin"
            val doc = httpRequestHelper.getHtmlResponse(url)

            if (doc.title().contains("登录")) {
//                Handler(Looper.getMainLooper()).post {
                Manager.showToast("需要登录")
//                }
                Manager.startLogin(true)
                return """{"state":"error","message":"需要登录"}"""
            }

            val content = doc.selectFirst(".col-md-12.col-sm-12")
            val ps = content.select("p")
            var text = ""
            for (p in ps) {
                val temp = p.text()
                if(!temp.equals("")){
                    text += p.text() + "\n"
                }
            }
            val result = JSONObject.parseObject(
                """
                    {
                        "state":"success"
                    }
                """
            )
            result["data"] = text
            result.toJSONString()
        }catch (e: Exception) {
            e.message?.let {
                Manager.showToast(it)
            }
            Manager.startLogin()
            JSONObject.parseObject("""{"state":"error","message":"${e.message}"}""").toJSONString()
        }

        //https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a/sso/jziotlogin
    }
}

//.col-md-12.col-sm-12

//<div class="col-md-12 col-sm-12">
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//<span style="color:#1A1A1A;font-size:21.0px;font-weight:700;"> </span>
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 style="text-align:left;font-size:36.0px;font-weight:normal;color:#666666;">
//</h2>
//<h2 style="font-size:36.0px;font-weight:normal;text-align:center;color:#666666;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="font-family:inherit , serif;color:#013155;font-size:22.5pt;text-align:center;background-color:#FFFFFF;">
//<p class="MsoNormal" align="center" style="text-align:center;">
//<span style="font-size:14.0px;font-family:SimSun;"> </span>
//</p>
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//<span style="color:#1A1A1A;font-size:21.0px;font-weight:700;">
//<div class="link_16" style="margin:0.0px;padding:0.0px;color:#1A1A1A;font-size:21.0px;font-weight:bold;text-align:center;">
//</div>
//</span>
//</h2>
//<h2 style="font-size:36.0px;font-weight:normal;text-align:center;color:#666666;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:30.0px;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="color:#013155;font-size:18.0pt;text-align:center;background-color:#FFFFFF;">
//</h2>
//<h2 class="title" style="font-family:inherit;color:#013155;font-size:30px;text-align:center;">
//</h2>
//<h2 class="title" style="font-family:微软雅黑, Verdana, Arial, Helvetica, sans-serif;color:#013155;font-size:30px;text-align:center;background-color:#FFFFFF;">
//</h2><h2 class="title" style="font-family:微软雅黑, Verdana, Arial, Helvetica, sans-serif;color:#013155;font-size:30px;text-align:center;background-color:#FFFFFF;">
//关于组织2025届毕业生学历电子注册信息核查工作的通知
//</h2>
//
//<div class="author" style="text-align:center;color:#777777;font-family:微软雅黑, Verdana, Arial, Helvetica, sans-serif;font-size:14px;background-color:#FFFFFF;">
//<p>
//<br>
//</p>
//</div>
//<div id="vsb_content" style="color:#333333;font-family:微软雅黑, Verdana, Arial, Helvetica, sans-serif;font-size:14px;background-color:#FFFFFF;">
//<div class="v_news_content">
//<div class="WordSection1">
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:&quot;Times New Roman&quot;, serif;">
//<span style="font-size:12pt;font-family:微软雅黑, sans-serif;">
//</span></p><p style="font-size:12pt;font-family:宋体;color:#333333;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">各教育教学单位：</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">为做好<span>2025</span>届毕业生学位审核及电子注册工作，现将<span>2025</span>届毕业生学历电子注册信息核查工作，具体安排如下：</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-weight:700;"><span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;font-weight:normal;">一、工作总体要求</span></span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">各教学单位要高度重视核查工作，要安排专人负责本单位的核查工作，将所有工作落实到人，责任到人，并为每个班级指定专门的核查负责人，核查负责人不得为学生。</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">各教育教学单位在向学生、班级发放《<span>2025</span>届毕业生电子注册信息核对表》（附件<span>1</span>、按学院单独下发）的同时，要做好政策宣传工作，并指导每名学生认真仔细进行核对，确保信息核查工作的顺利开展。并于<span>5</span>月<span>10</span>日前将《<span>2025</span>届毕业生电子注册信息核对表》（附件<span>1</span>）、《学历电子注册信息核查情况汇总表》（附件<span>2</span>）送教务处教务科。</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">学历电子注册信息经核查全部无误的学生，由教务处在教育部学籍学历管理平台上进行<span>2025</span>年预计毕业生注册。</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-weight:700;"><span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;font-weight:normal;">二、学生信息核查流程</span></span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">《<span>2025</span>届毕业生电子注册信息核对表》中所列的姓名、性别、出生日期、民族、身份证号五项信息均来自学生高考信息。</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">1</span><span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">、如任何信息有误，学生需用彩色水笔在错误项旁边标注，同时在备注栏里进行修改，并填写《学历电子注册信息核查问题反馈表》（附件<span>3</span>）。</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">如姓名、性别、出生日期、民族、身份证号等五项个人信息有误，需携本人身份证到教务科（南<span>B203</span>）核查；</span>
//</p>
//<p style="font-size:12pt;font-family:宋体;color:#333333;text-indent:24pt;background:white;">
//<span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">2</span><span style="font-size:14pt;font-family:微软雅黑, sans-serif;color:black;">、表中各项信息均核查完成后，学生请在签名处签名表示本人已确认信息核对无误。全班学生核查完成后，请班主任老师在表中班主任签名栏签字。</span>
//</p>
//
//<p></p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:&quot;Times New Roman&quot;, serif;text-indent:24pt;">
//<span style="font-size:12pt;font-family:微软雅黑, sans-serif;"></span><span style="font-size:12pt;font-family:微软雅黑, sans-serif;"></span><span><a href="mailto:%E8%AF%B7%E5%8F%91%E9%80%81%E9%82%AE%E4%BB%B6%E5%88%B0jwk@njit.edu.cn"><span style="font-size:12pt;font-family:微软雅黑, sans-serif;"></span></a></span><span style="font-size:12pt;font-family:微软雅黑, sans-serif;"></span>
//</p>
//</div>
//</div>
//</div>
//<br>
//<br>
//<div class="link_16" style="margin:0.0px;padding:0.0px;color:#1A1A1A;font-size:21.0px;font-weight:bold;text-align:center;">
//<div class="author" style="text-align:center;color:#777777;font-size:14.0px;background-color:#FFFFFF;">
//<p style="text-align:left;font-size:14.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//<p style="text-align:left;font-size:14.0px;text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"><span style="line-height:26.0px;"></span></span>
//</p>
//<p style="text-align:left;font-size:14.0px;text-indent:37.0px;">
//<strong><span style="font-size:19.0px;line-height:26.0px;"></span></strong>
//</p>
//<p style="text-align:left;font-size:14.0px;text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span><span style="font-size:19.0px;line-height:26.0px;"><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span></span>
//</p>
//<p style="text-align:left;font-size:14.0px;text-indent:37.0px;">
//<strong><span style="font-size:19.0px;line-height:26.0px;"></span></strong>
//</p>
//<p style="text-align:left;font-size:14.0px;text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span><span style="font-size:19.0px;line-height:26.0px;"><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span><span style="line-height:26.0px;"></span></span>
//</p>
//<div style="text-align:left;">
//</div>
//</div>
//<div id="vsb_content" style="color:#333333;font-size:14.0px;background-color:#FFFFFF;">
//<div class="v_news_content">
//<div class="WordSection1">
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:Calibri , sans-serif;">
//<span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:Calibri , sans-serif;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span><span></span></span><span style="font-size:12.0pt;"><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:Calibri , sans-serif;">
//<span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:Calibri , sans-serif;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;font-family:Calibri , sans-serif;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span></span><span style="font-size:12.0pt;"><span></span></span><span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span><span><span style="font-size:12.0pt;"></span></span><span style="font-size:12.0pt;"></span>
//</p>
//</div>
//</div>
//</div>
//</div>
//<div id="vsb_content" style="margin:0.0px;padding:0.0px;font-size:14.0px;">
//<div class="v_news_content" style="margin:0.0px;padding:0.0px;">
//<p style="text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="line-height:26.0px;"><strong><span style="font-size:19.0px;line-height:26.0px;"></span></strong></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"><span style="line-height:26.0px;"></span></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="line-height:26.0px;"><strong><span style="font-size:19.0px;line-height:26.0px;"></span></strong></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"><span style="line-height:26.0px;"></span></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="line-height:26.0px;"><strong><span style="font-size:19.0px;line-height:26.0px;"></span></strong></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//<p style="text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//<p style="text-align:right;text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//<p style="text-align:right;text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//<p style="text-align:right;text-indent:37.0px;">
//<span style="font-size:19.0px;line-height:26.0px;"></span>
//</p>
//</div>
//</div>
//<div id="vsb_content" style="color:#333333;font-size:14.0px;background-color:#FFFFFF;">
//<div class="v_news_content">
//<div class="WordSection1">
//<p class="Default" style="font-size:12.0pt;color:black;">
//<span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-weight:700;"><span style="font-size:12.0pt;"></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"><span></span><span></span><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-weight:700;"><span style="font-size:12.0pt;"><span></span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-weight:700;"><span style="font-size:12.0pt;"></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span><span></span><span></span><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-weight:700;"><span style="font-size:12.0pt;"></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"></span><span><span style="font-size:12.0pt;"></span></span><span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:justify;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:right;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span>
//</p>
//<p class="MsoNormal" style="text-align:right;font-size:10.5pt;text-indent:24.0pt;">
//<span style="font-size:12.0pt;"></span><span style="font-size:12.0pt;"><span></span><span></span></span>
//</p>
//</div>
//</div>
//</div>
//</div>