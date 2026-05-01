package com.feifan.fuckingnjit.service

import android.content.Context
import com.feifan.fuckingnjit.model.User


interface UserManager {
    suspend fun addUser(context: Context,user: User)
    suspend fun deleteUser(context: Context,id: String): Boolean
    fun getCurrentUser(): User
    fun setCurrentUser(context: Context,id: String)
    fun getAllUsers(context : Context): String
}