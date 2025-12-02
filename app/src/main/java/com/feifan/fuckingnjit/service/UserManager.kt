package com.feifan.fuckingnjit.service

import com.feifan.fuckingnjit.model.User


interface UserManager {
    suspend fun addUser(user: User)
    suspend fun deleteUser(id: String): Boolean
    fun getCurrentUser(): User
    fun setCurrentUser(id: String)
    fun getAllUsers(): String
}