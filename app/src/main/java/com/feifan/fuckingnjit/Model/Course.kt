package com.feifan.fuckingnjit.Model

class Course {
    private var name: String = ""
    private var time: Time? = Time()
    private var classroom: String = ""
    private var teacher: String = ""

    fun getName(): String {
        return name
    }

    fun setName(name: String) {
        this.name = name
    }

    fun getTime(): Time? {
        return time
    }

    fun setTime(time: Time?) {
        this.time = time
    }

    fun getClassroom(): String {
        return classroom
    }

    fun setClassroom(classroom: String) {
        this.classroom = classroom
    }

    fun getTeacher(): String {
        return teacher
    }

    fun setTeacher(teacher: String) {
        this.teacher = teacher
    }
}