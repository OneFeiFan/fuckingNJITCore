package com.feifan.fuckingnjit.utils.database

import com.feifan.fuckingnjit.model.YiBan
import com.feifan.fuckingnjit.model.YiBan_
import io.objectbox.Box

object YiBanBoxUtils : BaseBoxUtils() {
    override fun getDatabaseName() = "YIBAN"
    private val UserBox: Box<YiBan> by lazy {
        getBox(YiBan::class.java)
    }

    // 插入/更新单个用户数据
    fun insertUser(User: YiBan?) = User?.let {
        UserBox.put(it)
    }

//    // 批量插入用户数据
//    fun insertUserList(list: List<YiBan>) {
//        UserBox.put(list)
//    }

//    // 获取全部数据
//    fun getAllUser(): List<User> {
//        return UserBox.all
//    }

    //    // 通过 UUID 查询
    fun getUserByUuid(uuid: Long): YiBan? {
        return UserBox.get(uuid)
    }

    // 通过用户ID查询（假设id是唯一字段）
    fun getUserById(id: String): YiBan? {
        return UserBox
            .query(YiBan_.id.equal(id))
            .build()
            .findFirst()
    }

    // 更新用户数据
//    fun updateUser(User: YiBan) {
//        UserBox.put(User)
//    }

    // 删除单个用户
    fun deleteUser(User: YiBan) {
        UserBox.remove(User)
    }

    // 删除所有用户数据
    fun deleteAllUser() {
        UserBox.removeAll()
    }
}