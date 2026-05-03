package com.feifan.fuckingnjit.utils.database

import android.content.Context
import com.feifan.fuckingnjit.model.AppSystem
import com.feifan.fuckingnjit.model.ClassFocusRecord
import com.feifan.fuckingnjit.model.ClassFocusRecord_
import com.feifan.fuckingnjit.model.DailyRecord
import com.feifan.fuckingnjit.model.DailyRecord_
import com.feifan.fuckingnjit.model.MyObjectBox
import com.feifan.fuckingnjit.model.SleepSensorRecord
import com.feifan.fuckingnjit.model.SleepSensorRecord_
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.model.User_
import com.feifan.fuckingnjit.utils.database.AppDataCenter.getRecordByDate
import com.feifan.fuckingnjit.utils.database.AppDataCenter.getSystemConfig
import com.feifan.fuckingnjit.utils.database.AppDataCenter.getTodayRecord
import com.feifan.fuckingnjit.utils.database.AppDataCenter.init
import com.feifan.fuckingnjit.utils.database.AppDataCenter.updateSystemConfig
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.time.LocalDate

/**
 * 应用数据访问中心，基于 ObjectBox 本地数据库提供统一的读写入口。
 *
 * 管理五类核心数据：
 * - **系统配置** ([AppSystem])：全局唯一记录（id=1），存储当前激活用户 ID 等应用级状态
 * - **用户信息** ([User])：多租户支持，每个登录过的用户各存一条
 * - **每日记录** ([DailyRecord])：按日期键值对存储，含睡眠、步数、专注度等综合数据
 * - **睡眠传感器** ([SleepSensorRecord])：高频原始采样点（加速度/音频），按时间戳索引
 * - **课程专注度** ([ClassFocusRecord])：每节课一条，支持上传状态标记与批量同步
 *
 * 所有 Box 实例采用 lazy 延迟初始化，物理数据库统一归拢到 `core_database` 文件夹。
 *
 * 典型调用方式：先调用 [init] 初始化数据库，
 * 其余位置通过 companion 方法直接操作，无需手动管理事务。
 */
@Suppress("unused")
object AppDataCenter {
    private var boxStore: BoxStore? = null

    /** 全局系统配置 Box，单例实体 id 固定为 1L */
    private val systemBox by lazy { boxStore!!.boxFor(AppSystem::class.java) }

    /** 用户信息 Box，支持多用户共存 */
    private val userBox by lazy { boxStore!!.boxFor(User::class.java) }

    /** 每日综合健康记录 Box，以 dateStr 为查询键 */
    private val dailyBox by lazy { boxStore!!.boxFor(DailyRecord::class.java) }

    /** 睡眠传感器原始采样数据 Box，按时间戳范围检索 */
    private val sensorBox by lazy { boxStore!!.boxFor(SleepSensorRecord::class.java) }

    /** 课程专注度记录 Box，独立于 DailyRecord 存储以便批量同步 */
    private val focusBox by lazy { boxStore!!.boxFor(ClassFocusRecord::class.java) }

    /**
     * 初始化 ObjectBox 数据库实例。
     *
     * 使用 ApplicationContext 避免内存泄漏
     * 多次调用不会重复创建 BoxStore。必须在任何数据操作之前调用。
     *
     * @param context Application Context
     */
    fun init(context: Context) {
        if (boxStore == null) {
            boxStore = MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .name("core_database") // 所有数据归拢到同一个物理文件夹！
                .build()
        }
    }

    /**
     * 获取底层的 ObjectBox [BoxStore] 实例。
     *
     * 仅在需要跨 Box 联合查询或运行原生事务时使用，常规操作应优先调用本类提供的类型安全方法。
     *
     * @return 已初始化的 BoxStore，未调用 [init] 时返回 null
     */
    fun getBoxStore(): BoxStore? = boxStore

    /**
     * 获取全局系统配置实体。
     *
     * 若数据库中尚不存在（首次启动），会自动创建默认实例（id=1L）并持久化。
     * 该实体是单例的，全局只有一条记录。
     *
     * @return 系统配置对象，永不为 null
     */
    fun getSystemConfig(): AppSystem {
        var sys = systemBox.get(1L) // 全局数据默认分配id为1
        if (sys == null) {
            sys = AppSystem(id = 1L)
            systemBox.put(sys)
        }
        return sys
    }

    /**
     * 以读写事务方式更新系统配置。
     *
     * 内部先取出当前实体，执行 [updater] 回调修改字段，然后写回数据库。
     * 相比直接调用 [getSystemConfig] 再手动 put，此方法更简洁且保证原子性。
     *
     * @param updater 配置修改回调，接收当前可变 AppSystem 实例
     */
    fun updateSystemConfig(updater: (AppSystem) -> Unit) {
        val sys = getSystemConfig()
        updater(sys)
        systemBox.put(sys)
    }

    /**
     * 根据系统配置中的 [AppSystem.currentUserId] 获取当前激活的用户实体。
     *
     * @return 用户对象，未设置当前用户或数据库中找不到对应记录时返回 null
     */
    fun getCurrentUser(): User? {
        val userId = getSystemConfig().currentUserId
        if (userId.isEmpty()) return null
        return userBox.query().equal(User_.id, userId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().findFirst()
    }

    /**
     * 新增或更新用户信息（upsert 语义）。
     *
     * 若传入实体的 ObjectBox ID 与已有记录一致则覆盖，否则作为新记录插入。
     *
     * @param user 待保存的用户实体
     */
    fun saveUser(user: User) {
        userBox.put(user)
    }

    /** 获取数据库中所有用户记录，通常用于账号切换或管理界面 */
    fun getAllUsers(): List<User> = userBox.all

    /**
     * 从数据库中删除指定用户记录。
     *
     * 注意：仅删除 User 实体本身，不会级联清理其关联的 DailyRecord 或 ClassFocusRecord，
     * 如需完整清除请额外调用相关清理方法。
     *
     * @param user 待删除的用户实体
     */
    fun deleteUser(user: User) {
        userBox.remove(user)
    }

    /**
     * 获取今天的每日记录，不存在时自动创建并持久化。
     *
     * 以 [LocalDate.now] 格式化为 `yyyy-MM-dd` 字符串作为查询键，
     * 是传感器数据、睡眠结果等所有当日数据的聚合载体。
     *
     * @return 今日的 DailyRecord 实例，永不为 null
     */
    fun getTodayRecord(): DailyRecord {
        val todayStr = LocalDate.now().toString()
        var record = dailyBox.query()
            .equal(DailyRecord_.dateStr, todayStr, QueryBuilder.StringOrder.CASE_SENSITIVE).build()
            .findFirst()
        if (record == null) {
            record = DailyRecord(dateStr = todayStr)
            dailyBox.put(record)
        }
        return record
    }

    /**
     * 以读写事务方式更新今日记录。
     *
     * 先取出 [getTodayRecord]，执行 [updater] 修改后写回，语义同 [updateSystemConfig]。
     *
     * @param updater 每日数据修改回调
     */
    fun updateTodayRecord(updater: (DailyRecord) -> Unit) {
        val record = getTodayRecord()
        updater(record)
        dailyBox.put(record)
    }

    /**
     * 根据日期字符串获取对应的每日记录，不存在时自动创建空记录。
     *
     * @param dateStr 日期字符串，格式为 `yyyy-MM-dd`
     * @return 指定日期的 DailyRecord 实例，永不为 null
     */
    fun getRecordByDate(dateStr: String): DailyRecord {
        var record = dailyBox.query()
            .equal(DailyRecord_.dateStr, dateStr, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
        if (record == null) {
            // 如果不存在数据对象，那么就返回空数据
            record = DailyRecord(dateStr = dateStr)
            dailyBox.put(record)
        }
        return record
    }

    /**
     * 将一次完整的睡眠结果写入指定日期的每日记录。
     *
     * 内部调用 [getRecordByDate] 获取目标日期的 DailyRecord，
     * 然后覆盖其三个睡眠字段并持久化。
     *
     * @param dateStr       目标日期，格式 `yyyy-MM-dd`
     * @param sleepStartMs  入睡时间戳（毫秒）
     * @param wakeUpMs      醒来时间戳（毫秒）
     * @param durationMins  总睡眠时长（分钟）
     */
    fun saveSleepResult(dateStr: String, sleepStartMs: Long, wakeUpMs: Long, durationMins: Int) {
        // getRecordByDate 会自动处理查询和新日期的初始化
        val record = getRecordByDate(dateStr)

        record.sleepStartTimeMs = sleepStartMs
        record.wakeUpTimeMs = wakeUpMs
        record.totalSleepMinutes = durationMins

        // 执行持久化
        dailyBox.put(record)
    }

    /**
     * 获取所有包含有效睡眠数据的历史记录，按日期倒序排列。
     *
     * 过滤条件：[DailyRecord.totalSleepMinutes] > 0，即排除未完成睡眠记录的空白天数。
     *
     * @return 有效睡眠记录列表，最新的排在最前面
     */
    fun getValidSleepRecordsForUI(): List<DailyRecord> {
        return dailyBox.query()
            // 只查 totalSleepMinutes 大于 0 的记录，过滤掉没同步睡眠的空白天数
            .greater(DailyRecord_.totalSleepMinutes, 0)
            .orderDesc(DailyRecord_.dateStr) // 按日期倒序排列，最近的在前
            .build()
            .find()
    }

    /**
     * 为指定课程累加一次分心时长（违规使用电子产品的毫秒数）。
     *
     * @param courseId   课程唯一标识
     * @param courseName 课程显示名称
     * @param startTime  上课开始时间戳（毫秒）
     * @param endTime    预计下课时间戳（毫秒）
     * @param addedMills 本次检测到的分心时长（毫秒），<=0 时直接跳过不处理
     */
    fun addDistractionTime(
        courseId: String,
        courseName: String,
        startTime: Long,
        endTime: Long,
        addedMills: Long
    ) {
        if (addedMills <= 0 || courseId.isEmpty()) return

        val todayRecord = getTodayRecord()

        // 在今天的记录下寻找这节课的明细
        // 上课专注度是每日记录的一个子对象
        var targetFocus = todayRecord.focusRecords.find {
            it.courseId == courseId && it.startTime == startTime
        }

        if (targetFocus == null) {
            targetFocus = ClassFocusRecord(
                courseId = courseId, courseName = courseName,
                startTime = startTime, endTime = endTime
            )
            // 建立双向绑定
            targetFocus.dailyRecord.target = todayRecord
            todayRecord.focusRecords.add(targetFocus)
        }

        // 累加数据并重置上传状态
        targetFocus.distractionDurationMills += addedMills
        targetFocus.isUploaded = false //更新上传状态（false为待更新）

        // ObjectBox 特性：保存父对象会自动保存所有挂载的子对象
        dailyBox.put(todayRecord)
    }

    /**
     * 批量插入睡眠传感器采样记录。
     *
     * 利用 ObjectBox 的批量 put 能力一次写入，适用于高频采集场景下的周期性刷盘。
     *
     * @param records 待写入的传感器记录列表
     */
    fun insertSensorBatch(records: List<SleepSensorRecord>) = sensorBox.put(records)

    /**
     * 清理指定时间戳之前的旧传感器数据，防止数据库无限膨胀。
     *
     * @param timestamp 截止时间戳，早于此时间的记录将被删除
     */
    fun clearOldSensorsBefore(timestamp: Long) {
        sensorBox.query().less(SleepSensorRecord_.timestamp, timestamp).build().remove()
    }

    /**
     * 按时间戳范围查询睡眠传感器记录，结果按时间升序排列。
     *
     * @param start 起始时间戳（含）
     * @param end   结束时间戳（含）
     * @return 区间内的传感器采样记录列表
     */
    fun getSensorRecordsBetween(start: Long, end: Long): List<SleepSensorRecord> {
        return sensorBox.query()
            .between(SleepSensorRecord_.timestamp, start, end)
            .order(SleepSensorRecord_.timestamp)
            .build()
            .find()
    }

    /**
     * 查询所有尚未同步到服务端的课程专注度记录。
     *
     * 筛选条件：[ClassFocusRecord.isUploaded] == false，
     *
     * @return 未上传的专注度记录列表
     */
    fun getUnuploadedFocusRecords(): List<ClassFocusRecord> {
        return focusBox.query()
            .equal(ClassFocusRecord_.isUploaded, false)
            .build()
            .find()
    }

    /**
     * 将一批专注度记录标记为已上传状态，避免重复同步。
     *
     * 利用 ObjectBox 批量 put 能力一次性写回，空列表时直接跳过。
     *
     * @param records 服务端已确认接收的记录列表
     */
    fun markFocusRecordsAsUploaded(records: List<ClassFocusRecord>) {
        if (records.isEmpty()) return
        records.forEach { it.isUploaded = true }
        // ObjectBox 支持极速批量 put
        focusBox.put(records)
    }
}