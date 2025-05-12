package com.feifan.fuckingnjit

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock

@RunWith(AndroidJUnit4::class)
class UserManagerImplTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    lateinit var mockCookieManager: CookieManager

    private lateinit var userManager: UserManagerImpl

    @Before
    fun setUp() {
//        `when`(context.applicationContext).thenReturn(context)
//        `when`(MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)).thenReturn("testMasterKey")
//        `when`(EncryptedSharedPreferences.create(
//            anyString(),
//            eq("testMasterKey"),
//            eq(context),
//            eq(EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV),
//            eq(EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
//        )).thenReturn(mockSharedPreferences)
//        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
//        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
//        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)
//        `when`(mockEditor.commit()).thenReturn(true)
//        `when`(mockSharedPreferences.getString(eq("current_user"), anyString())).thenReturn("testUser")
//        `when`(mockSharedPreferences.getString(eq("testUser_user"), anyString())).thenReturn("testUser")
//        `when`(mockSharedPreferences.getString(eq("testUser_password"), anyString())).thenReturn("testPassword")

//        CookieManager.setInstance(mockCookieManager)


    }
    @Test
    fun test(){
        userManager = UserManagerImpl(ApplicationProvider.getApplicationContext<Context>())
    }
//    @Test
//    fun testAddUser_成功() {
//        assertTrue(userManager.addUser("testUser", "testPassword"))
//        verify(mockEditor).putString("testUser_user", "testUser")
//        verify(mockEditor).putString("testUser_password", "testPassword")
//        verify(mockEditor).commit()
//    }
//
//    @Test
//    fun testAddUser_异常() {
//        `when`(mockEditor.commit()).thenReturn(false)
//        assertFalse(userManager.addUser("testUser", "testPassword"))
//    }
//
//    @Test
//    fun testGetOriginalPasswordById_正常() {
//        assertEquals("testPassword", userManager.getOriginalPassword("testUser"))
//    }
//
//    @Test
//    fun testGetOriginalPasswordById_用户不存在() {
//        `when`(mockSharedPreferences.getString("nonexistent_user", null)).thenReturn(null)
//        assertEquals("", userManager.getOriginalPassword("nonexistentUser"))
//    }
//
//    @Test
//    fun testGetOriginalPassword_当前用户已设置() {
//        assertEquals("testPassword", userManager.getOriginalPassword())
//    }
//
//    @Test
//    fun testGetOriginalPassword_当前用户未设置() {
//        `when`(mockSharedPreferences.getString("current_user", null)).thenReturn("")
//        assertEquals("", userManager.getOriginalPassword())
//    }
//
//    @Test
//    fun testGetCurrentUser_正常() {
//        assertEquals("testUser", userManager.getCurrentUser())
//    }
//
//    @Test
//    fun testGetCurrentUser_未设置() {
//        `when`(mockSharedPreferences.getString("current_user", null)).thenReturn(null)
//        assertEquals("", userManager.getCurrentUser())
//    }
//
//    @Test
//    fun testSetCurrentUser_正常() {
//        userManager.setCurrentUser("testUser")
//        verify(mockEditor).putString("current_user", "testUser")
//        verify(mockEditor).commit()
//        verify(mockCookieManager).removeAllCookies(null)
//    }
//
//    @Test
//    fun testDeleteUser_成功() {
//        assertTrue(userManager.deleteUser("testUser"))
//        verify(mockEditor).remove("testUser_user")
//        verify(mockEditor).remove("testUser_password")
//        verify(mockEditor).commit()
//    }
//
//    @Test
//    fun testDeleteUser_异常() {
//        `when`(mockEditor.commit()).thenReturn(false)
//        assertFalse(userManager.deleteUser("testUser"))
//    }
//
//    // 边缘测试用例：测试用户ID为空的情况
//    @Test
//    fun testAddUser_用户ID为空() {
//        assertFalse(userManager.addUser("", "testPassword"))
//        verify(mockEditor, never()).putString(anyString(), anyString())
//    }
//
//    @Test
//    fun testGetOriginalPasswordById_用户ID为空() {
//        assertEquals("", userManager.getOriginalPassword(""))
//    }
//
//    @Test
//    fun testDeleteUser_用户ID为空() {
//        assertFalse(userManager.deleteUser(""))
//        verify(mockEditor, never()).remove(anyString())
//    }
//
//    // 边缘测试用例：测试用户密码为空的情况
//    @Test
//    fun testAddUser_密码为空() {
//        assertTrue(userManager.addUser("testUser", ""))
//        verify(mockEditor).putString("testUser_user", "testUser")
//        verify(mockEditor).putString("testUser_password", "")
//        verify(mockEditor).commit()
//    }
//
//    @Test
//    fun testGetOriginalPasswordById_密码为空() {
//        `when`(mockSharedPreferences.getString("testUser_password", null)).thenReturn("")
//        assertEquals("", userManager.getOriginalPassword("testUser"))
//    }
//
//    // 边缘测试用例：测试用户ID和密码都为空的情况
//    @Test
//    fun testAddUser_用户ID和密码都为空() {
//        assertFalse(userManager.addUser("", ""))
//        verify(mockEditor, never()).putString(anyString(), anyString())
//    }
//
//    // 边缘测试用例：测试SharedPreferences操作异常的情况
//    @Test
//    fun testAddUser_SharedPreferences异常() {
//        `when`(mockSharedPreferences.edit()).thenThrow(GeneralSecurityException("Test Exception"))
//        assertFalse(userManager.addUser("testUser", "testPassword"))
//    }
//
//    @Test
//    fun testGetOriginalPasswordById_SharedPreferences异常() {
//        `when`(mockSharedPreferences.getString(anyString(), anyString())).thenThrow(GeneralSecurityException("Test Exception"))
//        assertEquals("", userManager.getOriginalPassword("testUser"))
//    }
//
//    @Test
//    fun testDeleteUser_SharedPreferences异常() {
//        `when`(mockSharedPreferences.edit()).thenThrow(GeneralSecurityException("Test Exception"))
//        assertFalse(userManager.deleteUser("testUser"))
//    }
}
