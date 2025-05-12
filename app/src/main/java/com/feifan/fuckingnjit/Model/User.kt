package com.feifan.fuckingnjit.Model

class User {
    private var name: String = ""
    private var id:String = ""
    private var password:String = ""
    private var current:Boolean = false
    private var semesterStartDate:String = ""
    private var GPA:String = ""
    private var allSorces:String = ""

    fun getName(): String {
        return name
    }

    fun setName(name: String):User {
        this.name = name
        return this
    }
    fun getId(): String {
        return id
    }

    fun setId(id: String):User {
        this.id = id
        return this
    }

    fun getPassword(): String {
        return password
    }

    fun setPassword(password: String):User {
        this.password = password
        return this
    }
    fun getCurrent(): Boolean {
        return current
    }
    fun setCurrent(current: Boolean):User {
        this.current = current
        return this
    }
    fun getSemesterStartDate(): String {
        return semesterStartDate
    }
    fun setSemesterStartDate(semesterStartDate: String):User {
        this.semesterStartDate = semesterStartDate
        return this
    }
    fun getGPA(): String {
        return GPA
    }
    fun setGPA(GPA: String):User {
        this.GPA = GPA
        return this
    }
    fun getAllSorces(): String {
        return allSorces
    }
    fun setAllSorces(allSorces: String):User {
        this.allSorces = allSorces
        return this
    }
    override fun toString(): String {
        return "User(name='$name', id='$id', password='$password', current=$current)"
    }
}