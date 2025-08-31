package com.feifan.fuckingnjit.service

import com.feifan.fuckingnjit.database.UserData

interface UserManager {
    suspend fun addUser(user: UserData)
    suspend fun deleteUser(id: String): Boolean
    fun getCurrentUser(): UserData
    fun setCurrentUser(id: String)
    fun getAllUsers(): String
}