// functions/SMSFunctions.kt
package functions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 簡訊資料類
 */
@Serializable
data class SMSMessage(
    val id: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isRead: Boolean
)

/**
 * 簡訊讀取器
 */
class SMSReader(private val context: Context) {
    
    companion object {
        private const val TAG = "SMSReader"
    }
    
    /**
     * 檢查權限
     */
    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 獲取未讀簡訊
     */
    fun getUnreadMessages(): List<SMSMessage> {
        if (!hasPermissions()) {
            Log.w(TAG, "⚠️ 沒有簡訊讀取權限")
            return emptyList()
        }
        
        val messages = mutableListOf<SMSMessage>()
        
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.INBOX,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms.READ} = ?",
                arrayOf("0"), // 0 = 未讀
                "${Telephony.Sms.DATE} DESC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "未知號碼"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val isRead = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    
                    messages.add(SMSMessage(id, sender, body, date, isRead))
                }
            }
            
            Log.d(TAG, "✅ 成功讀取 ${messages.size} 則未讀簡訊")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 讀取未讀簡訊失敗: ${e.message}")
        }
        
        return messages
    }
    
    /**
     * 獲取最近簡訊
     */
    fun getRecentMessages(limit: Int = 10): List<SMSMessage> {
        if (!hasPermissions()) {
            Log.w(TAG, "⚠️ 沒有簡訊讀取權限")
            return emptyList()
        }
        
        val messages = mutableListOf<SMSMessage>()
        
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.INBOX,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "未知號碼"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val isRead = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    
                    messages.add(SMSMessage(id, sender, body, date, isRead))
                }
            }
            
            Log.d(TAG, "✅ 成功讀取 ${messages.size} 則最近簡訊")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 讀取最近簡訊失敗: ${e.message}")
        }
        
        return messages
    }
}

/**
 * 簡訊函數管理器 - 處理所有簡訊相關的 Function Calling
 */
object SMSFunctions {
    
    private const val TAG = "SMSFunctions"
    private lateinit var smsReader: SMSReader
    
    /**
     * 初始化簡訊服務
     */
    fun initialize(context: Context) {
        smsReader = SMSReader(context)
        Log.d(TAG, "✅ 簡訊函數管理器已初始化")
    }
    
    /**
     * 執行簡訊函數
     */
    suspend fun execute(functionName: String, arguments: String): String {
        Log.d(TAG, "🔧 執行簡訊函數: $functionName")
        Log.d(TAG, "📝 參數: $arguments")
        
        return try {
            when (functionName) {
                "read_unread_messages" -> executeReadUnreadMessages()
                "read_recent_messages" -> executeReadRecentMessages(arguments)
                "get_message_summary" -> executeGetMessageSummary()
                "get_message_by_index" -> executeGetMessageByIndex(arguments)
                "get_latest_message" -> executeGetLatestMessage()
                else -> {
                    Log.w(TAG, "⚠️ 未知的簡訊函數: $functionName")
                    "錯誤：未知的簡訊函數 $functionName"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 簡訊函數執行失敗: ${e.message}")
            "錯誤：簡訊功能執行失敗 - ${e.message}"
        }
    }
    
    /**
     * 執行讀取未讀簡訊
     */
    private suspend fun executeReadUnreadMessages(): String {
        Log.d(TAG, "📱 執行讀取未讀簡訊")
        
        if (!smsReader.hasPermissions()) {
            return "抱歉，我需要簡訊讀取權限才能幫您查看簡訊。請到設定中開啟權限。"
        }
        
        val unreadMessages = smsReader.getUnreadMessages()
        
        if (unreadMessages.isEmpty()) {
            Log.d(TAG, "📭 沒有未讀簡訊")
            return "您目前沒有未讀的簡訊，所有簡訊都已經讀過了。😊"
        }
        
        val result = StringBuilder()
        result.append("📱 您有 ${unreadMessages.size} 則未讀簡訊：\n\n")
        
        unreadMessages.take(5).forEachIndexed { index, message ->
            val timeFormatted = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))
            
            result.append("${index + 1}. ")
            result.append("${formatPhoneNumber(message.sender)} ")
            result.append("($timeFormatted)\n")
            
            // 限制每則簡訊內容顯示長度
            val displayContent = if (message.content.length > 50) {
                "${message.content.take(50)}..."
            } else {
                message.content
            }
            result.append("   📝 $displayContent\n\n")
        }
        
        if (unreadMessages.size > 5) {
            result.append("...還有 ${unreadMessages.size - 5} 則未讀簡訊\n\n")
        }
        
        result.append("💡 要我為您朗讀某則簡訊嗎？")
        
        Log.d(TAG, "✅ 未讀簡訊讀取完成，共 ${unreadMessages.size} 則")
        return result.toString()
    }
    
    /**
     * 執行讀取最近簡訊
     */
    private suspend fun executeReadRecentMessages(arguments: String): String {
        Log.d(TAG, "📱 執行讀取最近簡訊")
        
        if (!smsReader.hasPermissions()) {
            return "抱歉，我需要簡訊讀取權限才能幫您查看簡訊。"
        }
        
        // 解析參數
        val limit = try {
            if (arguments.isBlank()) {
                5 // 預設顯示5則
            } else {
                // 嘗試解析 JSON 或直接解析數字
                if (arguments.trim().startsWith("{")) {
                    val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
                    jsonArgs["limit"]?.jsonPrimitive?.int ?: 5
                } else {
                    arguments.trim().toIntOrNull() ?: 5
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "參數解析失敗，使用預設值: ${e.message}")
            5
        }
        
        val recentMessages = smsReader.getRecentMessages(limit.coerceIn(1, 10))
        
        if (recentMessages.isEmpty()) {
            return "沒有找到任何簡訊記錄。"
        }
        
        val result = StringBuilder()
        result.append("📱 最近的 ${recentMessages.size} 則簡訊：\n\n")
        
        recentMessages.forEachIndexed { index, message ->
            val timeFormatted = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))
            
            val readStatus = if (message.isRead) "" else " 📩"
            
            result.append("${index + 1}. ")
            result.append("${formatPhoneNumber(message.sender)}$readStatus ")
            result.append("($timeFormatted)\n")
            
            // 限制內容顯示長度
            val displayContent = if (message.content.length > 50) {
                "${message.content.take(50)}..."
            } else {
                message.content
            }
            result.append("   📝 $displayContent\n\n")
        }
        
        Log.d(TAG, "✅ 最近簡訊讀取完成，共 ${recentMessages.size} 則")
        return result.toString()
    }
    
    /**
     * 執行簡訊摘要
     */
    private suspend fun executeGetMessageSummary(): String {
        Log.d(TAG, "📊 執行簡訊摘要")
        
        if (!smsReader.hasPermissions()) {
            return "抱歉，我需要簡訊讀取權限才能幫您查看簡訊摘要。"
        }
        
        val unreadMessages = smsReader.getUnreadMessages()
        val recentMessages = smsReader.getRecentMessages(10)
        
        val result = StringBuilder()
        result.append("📱 簡訊摘要報告：\n\n")
        
        // 未讀簡訊統計
        if (unreadMessages.isNotEmpty()) {
            result.append("📩 未讀簡訊：${unreadMessages.size} 則\n")
            
            // 顯示最新的未讀簡訊發送者
            val latestUnread = unreadMessages.first()
            val timeFormatted = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
                .format(Date(latestUnread.timestamp))
            result.append("   最新：${formatPhoneNumber(latestUnread.sender)} ($timeFormatted)\n\n")
        } else {
            result.append("✅ 沒有未讀簡訊\n\n")
        }
        
        // 最近活動統計
        result.append("📊 最近 24 小時：\n")
        val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val recentCount = recentMessages.count { it.timestamp > yesterday }
        result.append("   收到 $recentCount 則簡訊\n\n")
        
        // 發送者統計（最近10則）
        if (recentMessages.isNotEmpty()) {
            val senderGroups = recentMessages.groupBy { it.sender }
            val topSenders = senderGroups.entries
                .sortedByDescending { it.value.size }
                .take(3)
            
            result.append("📈 最活躍聯絡人：\n")
            topSenders.forEach { (sender, messages) ->
                result.append("   ${formatPhoneNumber(sender)}：${messages.size} 則\n")
            }
            result.append("\n")
        }
        
        // 建議
        if (unreadMessages.isNotEmpty()) {
            result.append("💡 建議：您有 ${unreadMessages.size} 則未讀簡訊，要我讀給您聽嗎？")
        } else {
            result.append("😊 所有簡訊都已處理完畢！")
        }
        
        Log.d(TAG, "✅ 簡訊摘要完成")
        return result.toString()
    }
    
    /**
     * 根據索引獲取特定簡訊
     */
    private suspend fun executeGetMessageByIndex(arguments: String): String {
        Log.d(TAG, "📱 根據索引獲取簡訊")
        
        if (!smsReader.hasPermissions()) {
            return "抱歉，我需要簡訊讀取權限。"
        }
        
        val index = try {
            if (arguments.trim().startsWith("{")) {
                val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
                jsonArgs["index"]?.jsonPrimitive?.int
            } else {
                arguments.trim().toIntOrNull()
            }
        } catch (e: Exception) {
            null
        }
        
        if (index == null || index < 1) {
            return "請提供正確的簡訊編號（從 1 開始）。例如：「第 1 則簡訊」"
        }
        
        val unreadMessages = smsReader.getUnreadMessages()
        
        if (unreadMessages.isEmpty()) {
            return "您目前沒有未讀簡訊。"
        }
        
        if (index > unreadMessages.size) {
            return "簡訊編號超出範圍，您只有 ${unreadMessages.size} 則未讀簡訊。"
        }
        
        val message = unreadMessages[index - 1]
        val timeFormatted = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
            .format(Date(message.timestamp))
        
        val result = StringBuilder()
        result.append("📱 第 $index 則簡訊詳細內容：\n\n")
        result.append("👤 發送者：${formatPhoneNumber(message.sender)}\n")
        result.append("🕐 時間：$timeFormatted\n")
        result.append("📝 內容：\n${message.content}\n\n")
        result.append("💬 要我為您朗讀這則簡訊嗎？")
        
        return result.toString()
    }
    
    /**
     * 獲取最新簡訊
     */
    private suspend fun executeGetLatestMessage(): String {
        Log.d(TAG, "📱 獲取最新簡訊")
        
        if (!smsReader.hasPermissions()) {
            return "抱歉，我需要簡訊讀取權限。"
        }
        
        val recentMessages = smsReader.getRecentMessages(1)
        
        if (recentMessages.isEmpty()) {
            return "沒有找到任何簡訊。"
        }
        
        val latestMessage = recentMessages.first()
        val timeFormatted = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
            .format(Date(latestMessage.timestamp))
        
        val readStatus = if (latestMessage.isRead) "已讀" else "未讀"
        
        val result = StringBuilder()
        result.append("📱 最新簡訊（$readStatus）：\n\n")
        result.append("👤 發送者：${formatPhoneNumber(latestMessage.sender)}\n")
        result.append("🕐 時間：$timeFormatted\n")
        result.append("📝 內容：\n${latestMessage.content}")
        
        return result.toString()
    }
    
    /**
     * 輔助函數：格式化電話號碼
     */
    private fun formatPhoneNumber(phoneNumber: String): String {
        return when {
            // 台灣國際格式：+886
            phoneNumber.startsWith("+886") -> {
                val localNumber = phoneNumber.substring(4)
                if (localNumber.length == 9 && localNumber.startsWith("9")) {
                    // 手機號碼
                    "0${localNumber.substring(0, 3)}-${localNumber.substring(3, 6)}-${localNumber.substring(6)}"
                } else {
                    phoneNumber
                }
            }
            // 台灣手機號碼：09開頭
            phoneNumber.length == 10 && phoneNumber.startsWith("09") -> {
                "${phoneNumber.substring(0, 4)}-${phoneNumber.substring(4, 7)}-${phoneNumber.substring(7)}"
            }
            // 台灣市話：0開頭但非09
            phoneNumber.length >= 8 && phoneNumber.startsWith("0") && !phoneNumber.startsWith("09") -> {
                if (phoneNumber.length == 9) {
                    // 可能是 02-xxxx-xxxx 格式
                    "${phoneNumber.substring(0, 2)}-${phoneNumber.substring(2, 6)}-${phoneNumber.substring(6)}"
                } else {
                    phoneNumber
                }
            }
            // 長號碼部分隱藏（保護隱私）
            phoneNumber.length > 8 -> {
                "${phoneNumber.substring(0, 4)}****${phoneNumber.takeLast(3)}"
            }
            // 短號碼或特殊號碼
            phoneNumber.length <= 5 -> {
                phoneNumber // 可能是簡碼，直接顯示
            }
            else -> phoneNumber
        }
    }
    
    /**
     * 檢查服務狀態
     */
    fun getServiceStatus(): String {
        return buildString {
            append("📱 簡訊服務狀態：\n")
            if (::smsReader.isInitialized) {
                if (smsReader.hasPermissions()) {
                    append("✅ 權限已授權\n")
                    try {
                        val unreadCount = smsReader.getUnreadMessages().size
                        append("📩 未讀簡訊：$unreadCount 則\n")
                        val recentCount = smsReader.getRecentMessages(10).size
                        append("📊 最近簡訊：$recentCount 則")
                    } catch (e: Exception) {
                        append("⚠️ 讀取簡訊時發生錯誤: ${e.message}")
                    }
                } else {
                    append("❌ 缺少簡訊讀取權限")
                }
            } else {
                append("❌ 服務未初始化")
            }
        }
    }
    
    /**
     * 測試簡訊服務連接
     */
    suspend fun testSMSService(): String {
        return try {
            Log.d(TAG, "🔧 測試簡訊服務")
            
            if (!::smsReader.isInitialized) {
                return "❌ 簡訊服務未初始化"
            }
            
            if (!smsReader.hasPermissions()) {
                return "❌ 缺少簡訊讀取權限，請到設定中開啟權限"
            }
            
            val testResult = StringBuilder("✅ 簡訊服務測試成功！\n\n")
            
            // 測試讀取功能
            val unreadMessages = smsReader.getUnreadMessages()
            testResult.append("📩 未讀簡訊：${unreadMessages.size} 則\n")
            
            val recentMessages = smsReader.getRecentMessages(5)
            testResult.append("📊 最近簡訊：${recentMessages.size} 則\n")
            
            if (recentMessages.isNotEmpty()) {
                val latestMessage = recentMessages.first()
                val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(latestMessage.timestamp))
                testResult.append("📱 最新簡訊：${formatPhoneNumber(latestMessage.sender)} ($timeFormatted)\n")
            }
            
            testResult.append("\n🎉 簡訊功能正常運作，可以開始使用！")
            
            testResult.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 簡訊服務測試失敗: ${e.message}")
            "❌ 簡訊服務測試失敗：${e.message}"
        }
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        Log.d(TAG, "🧹 簡訊服務資源已清理")
    }
}