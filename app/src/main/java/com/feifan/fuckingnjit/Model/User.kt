package com.feifan.fuckingnjit.Model

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.annotation.JSONField
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.SecureUtil

class User {
    private var name: String = ""
    private var id: String = ""
    private var password: String = ""
    private var current: Boolean = false
    private var semesterStartDate: String = ""
    private var GPA: String = ""
    private var allSorces: String = ""
    private var curriculums: String = ""

    fun getName(): String {
        return name
    }

    fun setName(name: String): User {
        this.name = name
        return this
    }

    fun getId(): String {
        return id
    }

    fun setId(id: String): User {
        this.id = id
        return this
    }
    @JSONField(name = "password")
    fun getPasswordForJson(): String = password

    @JSONField(name = "password")
    fun setPasswordForJson(password: String) {
        this.password = password
    }
    fun getPassword(): String {
        return SecureUtil.rsaDecrypt(password)
    }

    fun setPassword(password: String): User {
        this.password = SecureUtil.rsaEncrypt(password)
        return this
    }

    fun getCurrent(): Boolean {
        return current
    }

    fun setCurrent(current: Boolean): User {
        this.current = current
        return this
    }

    fun getSemesterStartDate(): String {
        return semesterStartDate
    }

    fun setSemesterStartDate(semesterStartDate: String): User {
        this.semesterStartDate = semesterStartDate
        Manager.getUserManager()?.reStoreUserList()
        return this
    }

    fun getGPA(): String {
        return GPA
    }

    fun setGPA(GPA: String): User {
        this.GPA = GPA
        return this
    }

    fun getAllSorces(): String {
        return allSorces
    }

    fun setAllSorces(allSorces: String): User {
        this.allSorces = allSorces
        if (allSorces != "") {
            this.setGPA(
                Tools.calculateAverageGPA(
                    JSONObject.parseObject(allSorces)["data"] as JSONArray
                )
            )
        }
        return this
    }

    fun getCurriculums(): String {
        return curriculums
    }

    fun setCurriculums(curriculums: String): User {
        if (curriculums.isBlank()) {
            this.curriculums = ""
            return this
        }
        // 1. 首先验证是否是有效的JSON
        if (curriculums.startsWith("{") && curriculums.endsWith("}")) {
            this.curriculums = ""
            return this
        } else if (curriculums.startsWith("<") && curriculums.endsWith(">")) {
            this.curriculums = ""
            return this
        }

        // 3. 如果验证通过，设置值
        this.curriculums = curriculums
        Manager.getUserManager()?.reStoreUserList()
        return this
    }

    override fun toString(): String {
        return "User(name='$name', id='$id', password='$password', current=$current, semesterStartDate='$semesterStartDate', GPA='$GPA', allSorces='$allSorces', curriculums='$curriculums')"
    }
}
