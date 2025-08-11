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
 * SMS data class
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
 * SMS Reader
 */
class SMSReader(private val context: Context) {
    
    companion object {
        private const val TAG = "SMSReader"
    }
    
    /**
     * Check permissions
     */
    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get unread messages
     */
    fun getUnreadMessages(): List<SMSMessage> {
        if (!hasPermissions()) {
            Log.w(TAG, "No SMS read permission")
            return emptyList()
        }
        
        val messages = mutableListOf<SMSMessage>()
        
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms.READ} = ?",
                arrayOf("0"), // 0 = unread
                "${Telephony.Sms.DATE} DESC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown Number"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val isRead = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    
                    messages.add(SMSMessage(id, sender, body, date, isRead))
                }
            }
            
            Log.d(TAG, "Successfully read ${messages.size} unread messages")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read unread messages: ${e.message}")
        }
        
        return messages
    }

    /**
     * Get recent messages
     */
    fun getRecentMessages(limit: Int = 10): List<SMSMessage> {
        if (!hasPermissions()) {
            Log.w(TAG, "No SMS read permission")
            return emptyList()
        }
        
        val messages = mutableListOf<SMSMessage>()
        
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
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
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown Number"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val isRead = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    
                    messages.add(SMSMessage(id, sender, body, date, isRead))
                }
            }
            
            Log.d(TAG, "Successfully read ${messages.size} recent messages")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read recent messages: ${e.message}")
        }
        
        return messages
    }
}

/**
 * SMS Function Manager - handles all SMS-related Function Calling
 */
object SMSFunctions {
    
    private const val TAG = "SMSFunctions"
    private lateinit var smsReader: SMSReader
    
    /**
     * Initialize SMS service
     */
    fun initialize(context: Context) {
        smsReader = SMSReader(context)
        Log.d(TAG, "SMS function manager initialized")
    }
    
    /**
     * Execute SMS function
     */
    suspend fun execute(functionName: String, arguments: String): String {
        Log.d(TAG, "Executing SMS function: $functionName")
        Log.d(TAG, "Parameters: $arguments")
        
        return try {
            when (functionName) {
                "read_unread_messages" -> executeReadUnreadMessages()
                "read_recent_messages" -> executeReadRecentMessages(arguments)
                "get_message_summary" -> executeGetMessageSummary()
                "get_message_by_index" -> executeGetMessageByIndex(arguments)
                "get_latest_message" -> executeGetLatestMessage()
                else -> {
                    Log.w(TAG, "Unknown SMS function: $functionName")
                    "Error: Unknown SMS function $functionName"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS function execution failed: ${e.message}")
            "Error: SMS function execution failed - ${e.message}"
        }
    }
    
    /**
     * Execute read unread messages
     */
    private suspend fun executeReadUnreadMessages(): String {
        Log.d(TAG, "Executing read unread messages")
        
        if (!smsReader.hasPermissions()) {
            return "Sorry, I need SMS read permission to help you view messages. Please enable permission in settings."
        }
        
        val unreadMessages = smsReader.getUnreadMessages()
        
        if (unreadMessages.isEmpty()) {
            Log.d(TAG, "No unread messages")
            return "You currently have no unread messages, all messages have been read."
        }
        
        val result = StringBuilder()
        result.append("You have ${unreadMessages.size} unread messages:\n\n")
        
        unreadMessages.take(5).forEachIndexed { index, message ->
            val timeFormatted = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))
            
            result.append("${index + 1}. ")
            result.append("${formatPhoneNumber(message.sender)} ")
            result.append("($timeFormatted)\n")
            
            // Limit the display length of each message content
            val displayContent = if (message.content.length > 50) {
                "${message.content.take(50)}..."
            } else {
                message.content
            }
            result.append("   $displayContent\n\n")
        }
        
        if (unreadMessages.size > 5) {
            result.append("...and ${unreadMessages.size - 5} more unread messages\n\n")
        }
        
        result.append("Would you like me to read a specific message for you?")
        
        Log.d(TAG, "Unread messages reading completed, total ${unreadMessages.size} messages")
        return result.toString()
    }
    
    /**
     * Execute read recent messages
     */
    private suspend fun executeReadRecentMessages(arguments: String): String {
        Log.d(TAG, "Executing read recent messages")
        
        if (!smsReader.hasPermissions()) {
            return "Sorry, I need SMS read permission to help you view messages."
        }
        
        // Parse parameters
        val limit = try {
            if (arguments.isBlank()) {
                5 // Default display 5 messages
            } else {
                // Try to parse JSON or parse number directly
                if (arguments.trim().startsWith("{")) {
                    val jsonArgs = Json.parseToJsonElement(arguments).jsonObject
                    jsonArgs["limit"]?.jsonPrimitive?.int ?: 5
                } else {
                    arguments.trim().toIntOrNull() ?: 5
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parameter parsing failed, using default value: ${e.message}")
            5
        }
        
        val recentMessages = smsReader.getRecentMessages(limit.coerceIn(1, 10))
        
        if (recentMessages.isEmpty()) {
            return "No SMS records found."
        }
        
        val result = StringBuilder()
        result.append("Recent ${recentMessages.size} messages:\n\n")
        
        recentMessages.forEachIndexed { index, message ->
            val timeFormatted = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))
            
            val readStatus = if (message.isRead) "" else " (Unread)"
            
            result.append("${index + 1}. ")
            result.append("${formatPhoneNumber(message.sender)}$readStatus ")
            result.append("($timeFormatted)\n")
            
            // Limit content display length
            val displayContent = if (message.content.length > 50) {
                "${message.content.take(50)}..."
            } else {
                message.content
            }
            result.append("   $displayContent\n\n")
        }
        
        Log.d(TAG, "Recent messages reading completed, total ${recentMessages.size} messages")
        return result.toString()
    }
    
    /**
     * Execute message summary
     */
    private suspend fun executeGetMessageSummary(): String {
        Log.d(TAG, "Executing message summary")
        
        if (!smsReader.hasPermissions()) {
            return "Sorry, I need SMS read permission to help you view message summary."
        }
        
        val unreadMessages = smsReader.getUnreadMessages()
        val recentMessages = smsReader.getRecentMessages(10)
        
        val result = StringBuilder()
        result.append("SMS Summary Report:\n\n")
        
        // Unread message statistics
        if (unreadMessages.isNotEmpty()) {
            result.append("Unread messages: ${unreadMessages.size} messages\n")
            
            // Display the latest unread message sender
            val latestUnread = unreadMessages.first()
            val timeFormatted = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(latestUnread.timestamp))
            result.append("   Latest: ${formatPhoneNumber(latestUnread.sender)} ($timeFormatted)\n\n")
        } else {
            result.append("No unread messages\n\n")
        }
        
        // Recent activity statistics
        result.append("Last 24 hours:\n")
        val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val recentCount = recentMessages.count { it.timestamp > yesterday }
        result.append("   Received $recentCount messages\n\n")
        
        // Sender statistics (recent 10 messages)
        if (recentMessages.isNotEmpty()) {
            val senderGroups = recentMessages.groupBy { it.sender }
            val topSenders = senderGroups.entries
                .sortedByDescending { it.value.size }
                .take(3)
            
            result.append("Most active contacts:\n")
            topSenders.forEach { (sender, messages) ->
                result.append("   ${formatPhoneNumber(sender)}: ${messages.size} messages\n")
            }
            result.append("\n")
        }
        
        // Suggestions
        if (unreadMessages.isNotEmpty()) {
            result.append("Suggestion: You have ${unreadMessages.size} unread messages, would you like me to read them for you?")
        } else {
            result.append("All messages have been processed!")
        }
        
        Log.d(TAG, "Message summary completed")
        return result.toString()
    }
    
    /**
     * Get specific message by index
     */
    private suspend fun executeGetMessageByIndex(arguments: String): String {
        Log.d(TAG, "Getting message by index")
        
        if (!smsReader.hasPermissions()) {
            return "Sorry, I need SMS read permission."
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
            return "Please provide the correct message number (starting from 1). For example: \"Message number 1\""
        }
        
        val unreadMessages = smsReader.getUnreadMessages()
        
        if (unreadMessages.isEmpty()) {
            return "You currently have no unread messages."
        }
        
        if (index > unreadMessages.size) {
            return "Message number out of range, you only have ${unreadMessages.size} unread messages."
        }
        
        val message = unreadMessages[index - 1]
        val timeFormatted = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(message.timestamp))
        
        val result = StringBuilder()
        result.append("Message $index detailed content:\n\n")
        result.append("Sender: ${formatPhoneNumber(message.sender)}\n")
        result.append("Time: $timeFormatted\n")
        result.append("Content:\n${message.content}\n\n")
        result.append("Would you like me to read this message for you?")
        
        return result.toString()
    }
    
    /**
     * Get latest message
     */
    private suspend fun executeGetLatestMessage(): String {
        Log.d(TAG, "Getting latest message")
        
        if (!smsReader.hasPermissions()) {
            return "Sorry, I need SMS read permission."
        }
        
        val recentMessages = smsReader.getRecentMessages(1)
        
        if (recentMessages.isEmpty()) {
            return "No messages found."
        }
        
        val latestMessage = recentMessages.first()
        val timeFormatted = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(latestMessage.timestamp))
        
        val readStatus = if (latestMessage.isRead) "Read" else "Unread"
        
        val result = StringBuilder()
        result.append("Latest message ($readStatus):\n\n")
        result.append("Sender: ${formatPhoneNumber(latestMessage.sender)}\n")
        result.append("Time: $timeFormatted\n")
        result.append("Content:\n${latestMessage.content}")
        
        return result.toString()
    }
    
    /**
     * Helper function: Format phone number
     */
    private fun formatPhoneNumber(phoneNumber: String): String {
        return when {
            // Taiwan international format: +886
            phoneNumber.startsWith("+886") -> {
                val localNumber = phoneNumber.substring(4)
                if (localNumber.length == 9 && localNumber.startsWith("9")) {
                    // Mobile number
                    "0${localNumber.substring(0, 3)}-${localNumber.substring(3, 6)}-${localNumber.substring(6)}"
                } else {
                    phoneNumber
                }
            }
            // Taiwan mobile number: starts with 09
            phoneNumber.length == 10 && phoneNumber.startsWith("09") -> {
                "${phoneNumber.substring(0, 4)}-${phoneNumber.substring(4, 7)}-${phoneNumber.substring(7)}"
            }
            // Taiwan landline: starts with 0 but not 09
            phoneNumber.length >= 8 && phoneNumber.startsWith("0") && !phoneNumber.startsWith("09") -> {
                if (phoneNumber.length == 9) {
                    // Could be 02-xxxx-xxxx format
                    "${phoneNumber.substring(0, 2)}-${phoneNumber.substring(2, 6)}-${phoneNumber.substring(6)}"
                } else {
                    phoneNumber
                }
            }
            // Long number partially hidden (privacy protection)
            phoneNumber.length > 8 -> {
                "${phoneNumber.substring(0, 4)}****${phoneNumber.takeLast(3)}"
            }
            // Short number or special number
            phoneNumber.length <= 5 -> {
                phoneNumber // Could be short code, display directly
            }
            else -> phoneNumber
        }
    }
    
    /**
     * Check service status
     */
    fun getServiceStatus(): String {
        return buildString {
            append("SMS Service Status:\n")
            if (::smsReader.isInitialized) {
                if (smsReader.hasPermissions()) {
                    append("Permission granted\n")
                    try {
                        val unreadCount = smsReader.getUnreadMessages().size
                        append("Unread messages: $unreadCount messages\n")
                        val recentCount = smsReader.getRecentMessages(10).size
                        append("Recent messages: $recentCount messages")
                    } catch (e: Exception) {
                        append("Error reading messages: ${e.message}")
                    }
                } else {
                    append("Missing SMS read permission")
                }
            } else {
                append("Service not initialized")
            }
        }
    }
    
    /**
     * Test SMS service connection
     */
    suspend fun testSMSService(): String {
        return try {
            Log.d(TAG, "Testing SMS service")
            
            if (!::smsReader.isInitialized) {
                return "SMS service not initialized"
            }
            
            if (!smsReader.hasPermissions()) {
                return "Missing SMS read permission, please enable permission in settings"
            }
            
            val testResult = StringBuilder("SMS service test successful!\n\n")
            
            // Test reading functionality
            val unreadMessages = smsReader.getUnreadMessages()
            testResult.append("Unread messages: ${unreadMessages.size} messages\n")
            
            val recentMessages = smsReader.getRecentMessages(5)
            testResult.append("Recent messages: ${recentMessages.size} messages\n")
            
            if (recentMessages.isNotEmpty()) {
                val latestMessage = recentMessages.first()
                val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(latestMessage.timestamp))
                testResult.append("Latest message: ${formatPhoneNumber(latestMessage.sender)} ($timeFormatted)\n")
            }
            
            testResult.append("\nSMS functionality working normally, ready to use!")
            
            testResult.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS service test failed: ${e.message}")
            "SMS service test failed: ${e.message}"
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "SMS service resources cleaned up")
    }
}