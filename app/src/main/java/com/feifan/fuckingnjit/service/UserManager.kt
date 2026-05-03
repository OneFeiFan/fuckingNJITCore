package com.feifan.fuckingnjit.service

import android.content.Context
import com.feifan.fuckingnjit.model.User

/**
 * 用户管理接口
 *
 * 定义用户账号的增删改查以及切换操作，
 * 所有涉及网络请求的方法均为挂起函数。
 */
interface UserManager {
    /**
     * 添加用户（登录后自动获取基础信息、成绩、课表并持久化）
     *
     * @param context 应用上下文
     * @param user 待添加的用户实体（至少包含学号和密码）
     */
    suspend fun addUser(context: Context, user: User)

    /**
     * 删除指定用户
     *
     * @param context 应用上下文
     * @param id 目标用户的学号
     * @return 是否删除成功
     */
    suspend fun deleteUser(context: Context, id: String): Boolean

    /**
     * 获取当前登录用户
     *
     * @return 当前 User 实例，无用户时返回空 User
     */
    fun getCurrentUser(): User

    /**
     * 切换当前活跃用户
     *
     * @param context 应用上下文
     * @param id 目标用户的学号
     */
    fun setCurrentUser(context: Context, id: String)

    /**
     * 获取所有已保存用户的简要信息列表
     *
     * @param context 应用上下文
     * @return JSON 格式的用户列表字符串
     */
    fun getAllUsers(context: Context): String
}