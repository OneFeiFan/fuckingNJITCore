package com.feifan.fuckingnjit.service

import com.feifan.fuckingnjit.Model.User

interface UserManager {
    fun addUser(user: User)
    suspend  fun deleteUser(id: String): Boolean
    fun getOriginalPassword(id: String): String
//    fun getOriginalPassword(): String
    fun getCurrentUser(): User
    fun setCurrentUser(id: String)
//    fun setCookie(cookie: String)
//    fun getCookie(): String
    fun getAllUsers(): String
    fun reStoreUserList()
}