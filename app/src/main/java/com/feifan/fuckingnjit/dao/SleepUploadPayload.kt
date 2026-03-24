import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 单个传感器数据点 (对应服务器端的 data 数组内部对象)
data class UploadSensorPoint(
    val time: String,
    val value: Double
) {
    companion object {
        // 提供一个转换器：将你的本地 ObjectBox 实体转为上传实体
        fun fromLocalRecord(record: com.feifan.fuckingnjit.model.SleepSensorRecord): UploadSensorPoint {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTime = sdf.format(Date(record.timestamp))
            return UploadSensorPoint(
                time = formattedTime,
                value = record.mixdata
            )
        }
    }
}

// 完整的上传载荷
data class SleepUploadPayload(
    val userId: String, // 需要传给服务器区分是谁的数据
    val data: List<UploadSensorPoint>
)