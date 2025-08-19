package com.feifan.fuckingnjit.service

import com.feifan.fuckingnjit.database.UserData

interface UserManager {
    fun addUser(user: UserData)
    suspend  fun deleteUser(id: String): Boolean
//    fun getOriginalPassword(id: String): String
//    fun getOriginalPassword(): String
    fun getCurrentUser(): UserData
    fun setCurrentUser(id: String)
//    fun setCookie(cookie: String)
//    fun getCookie(): String
    fun getAllUsers(): String
    fun reStoreUserList()
}