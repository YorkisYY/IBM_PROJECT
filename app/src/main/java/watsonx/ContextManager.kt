package watsonx

import android.util.Log

/**
 * ContextManager - 專門管理對話上下文和歷史記錄
 * 單一職責：只負責上下文管理，不處理提示詞生成
 */
object ContextManager {
    
    private const val TAG = "ContextManager"
    private const val MAX_CONTEXT_LENGTH = 3000
    private const val MAX_HISTORY_SIZE = 20
    
    // 存儲對話歷史
    private val conversationHistory = mutableListOf<ConversationItem>()
    
    data class ConversationItem(
        val timestamp: Long,
        val content: String,
        val role: String, // "user" or "assistant"
        val functionCalls: List<String> = emptyList()
    )
    
    /**
     * 添加消息到歷史記錄
     */
    fun addToHistory(content: String, role: String) {
        val item = ConversationItem(
            timestamp = System.currentTimeMillis(),
            content = content,
            role = role
        )
        
        conversationHistory.add(item)
        
        // 保持歷史記錄大小限制
        if (conversationHistory.size > MAX_HISTORY_SIZE) {
            conversationHistory.removeAt(0)
        }
        
        Log.d(TAG, "Added to history - Role: $role, Content length: ${content.length}")
    }
    
    /**
     * 添加對話對到歷史記錄
     */
    fun addConversation(userMessage: String, assistantResponse: String, functionCalls: List<String> = emptyList()) {
        addToHistory(userMessage, "user")
        addToHistory(assistantResponse, "assistant")
        Log.d(TAG, "Added conversation pair to history. Total items: ${conversationHistory.size}")
    }
    
    /**
     * 獲取格式化的上下文字符串 - 只返回上下文數據，不生成提示詞
     */
    fun getContextString(): String {
        if (conversationHistory.isEmpty()) {
            return "Previous conversations: None"
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Previous conversations:\n")
        
        // 只取最近的幾條對話
        val recentConversations = conversationHistory.takeLast(10)
        
        recentConversations.forEach { item ->
            when (item.role) {
                "user" -> contextBuilder.append("User: ${item.content}\n")
                "assistant" -> contextBuilder.append("Assistant: ${item.content}\n")
            }
        }
        
        // 確保上下文不會太長
        val context = contextBuilder.toString()
        return if (context.length > MAX_CONTEXT_LENGTH) {
            context.substring(0, MAX_CONTEXT_LENGTH) + "...(truncated)"
        } else {
            context
        }
    }
    
    /**
     * 構建帶上下文的提示詞 - 使用其他管理器生成提示詞
     */
    fun buildContextualPrompt(userMessage: String, isFunction: Boolean): String {
        val contextStr = getContextString()
        val promptManager = PromptManager()
        
        return if (isFunction) {
            promptManager.buildFunctionCallingPrompt(userMessage, contextStr)
        } else {
            promptManager.buildNormalPrompt(userMessage, contextStr)
        }
    }
    
    /**
     * 清除對話歷史
     */
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Conversation history cleared")
    }
    
    /**
     * 獲取對話摘要
     */
    fun getConversationSummary(): String {
        val historySize = conversationHistory.size
        val userMessages = conversationHistory.count { it.role == "user" }
        val assistantMessages = conversationHistory.count { it.role == "assistant" }
        
        return "Conversation History: $historySize total messages ($userMessages user, $assistantMessages assistant)"
    }
    
    /**
     * 獲取歷史記錄大小
     */
    fun getHistorySize(): Int {
        return conversationHistory.size
    }
    
    /**
     * 清除所有歷史記錄 - 別名方法
     */
    fun clearHistory() {
        clearConversationHistory()
    }
    
    /**
     * 獲取最後一條對話
     */
    fun getLastConversation(): ConversationItem? {
        return conversationHistory.lastOrNull()
    }
    
    /**
     * 檢查是否有相關的歷史上下文
     */
    fun hasRelevantContext(userMessage: String): Boolean {
        if (conversationHistory.isEmpty()) return false
        
        val keywords = userMessage.lowercase().split(" ")
        val recentMessages = conversationHistory.takeLast(6)
        
        return recentMessages.any { item ->
            val previousText = item.content.lowercase()
            keywords.any { keyword -> previousText.contains(keyword) && keyword.length > 2 }
        }
    }
}