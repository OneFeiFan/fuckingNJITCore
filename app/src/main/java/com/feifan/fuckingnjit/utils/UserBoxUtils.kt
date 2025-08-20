package com.feifan.fuckingnjit.utils

import android.content.Context
import com.feifan.fuckingnjit.database.MyObjectBox
import com.feifan.fuckingnjit.database.UserData
import com.feifan.fuckingnjit.database.UserData_
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File

object UserBoxUtils {

    private lateinit var boxStore: BoxStore
    private lateinit var userDataBox: Box<UserData>

    // 初始化必须在 Application 中调用
    fun init(context: Context) {
        boxStore = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .directory(File(context.filesDir, "USER"))
            .build()
        userDataBox = boxStore.boxFor(UserData::class.java)
    }

    // 返回储存的 BoxStore 对象
    fun getBoxStore(): Box<UserData> = userDataBox

    // 插入/更新单个用户数据
    fun insertUserData(userData: UserData?) = userData?.let {
        userDataBox.put(it)
    }

    // 批量插入用户数据
    fun insertUserDataList(list: List<UserData>) {
        userDataBox.put(list)
    }

    // 获取全部数据
    fun getAllUserData(): List<UserData> {
        return userDataBox.all
    }

    // 通过 UUID 查询
    fun getUserByUuid(uuid: Long): UserData? {
        return userDataBox.get(uuid)
    }

    // 通过用户ID查询（假设id是唯一字段）
    fun getUserById(id: String): UserData? {
        return userDataBox
            .query(UserData_.id.equal(id))
            .build()
            .findFirst()
    }

    // 更新用户数据
    fun updateUserData(userData: UserData) {
        userDataBox.put(userData)
    }

    // 删除单个用户
    fun deleteUserData(userData: UserData) {
        userDataBox.remove(userData)
    }

    // 删除所有用户数据
    fun deleteAllUserData() {
        userDataBox.removeAll()
    }

    // 关闭数据库
    fun close() {
        boxStore.close()
    }
}
