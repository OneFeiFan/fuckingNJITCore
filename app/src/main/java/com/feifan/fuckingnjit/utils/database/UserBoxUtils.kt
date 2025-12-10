package com.feifan.fuckingnjit.utils.database

import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.model.User_
import io.objectbox.Box

object UserBoxUtils : BaseBoxUtils() {
    override fun getDatabaseName() = "USER"
    private val UserBox: Box<User> by lazy {
        getBox(User::class.java)
    }

    // 插入/更新单个用户数据
    fun insertUser(User: User?) = User?.let {
        UserBox.put(it)
    }

    // 批量插入用户数据
    fun insertUserList(list: List<User>) {
        UserBox.put(list)
    }

    // 获取全部数据
    fun getAllUser(): List<User> {
        return UserBox.all
    }

    // 通过 UUID 查询
    fun getUserByUuid(uuid: Long): User? {
        return UserBox.get(uuid)
    }

    // 通过用户ID查询（假设id是唯一字段）
    fun getUserById(id: String): User? {
        return UserBox
            .query(User_.id.equal(id))
            .build()
            .findFirst()
    }

    // 更新用户数据
    fun updateUser(User: User) {
        UserBox.put(User)
    }

    // 删除单个用户
    fun deleteUser(User: User) {
        UserBox.remove(User)
    }

    // 删除所有用户数据
    fun deleteAllUser() {
        UserBox.removeAll()
    }
}