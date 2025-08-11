package watsonx

import android.util.Log

/**
 * ContextManager - Specialized for managing conversation context and history records
 * Single responsibility: Only responsible for context management, not prompt generation
 */
object ContextManager {
    
    private const val TAG = "ContextManager"
    private const val MAX_CONTEXT_LENGTH = 3000
    private const val MAX_HISTORY_SIZE = 20
    
    // Store conversation history
    private val conversationHistory = mutableListOf<ConversationItem>()
    
    data class ConversationItem(
        val timestamp: Long,
        val content: String,
        val role: String, // "user" or "assistant"
        val functionCalls: List<String> = emptyList()
    )
    
    /**
     * Add message to history
     */
    fun addToHistory(content: String, role: String) {
        val item = ConversationItem(
            timestamp = System.currentTimeMillis(),
            content = content,
            role = role
        )
        
        conversationHistory.add(item)
        
        // Maintain history size limit
        if (conversationHistory.size > MAX_HISTORY_SIZE) {
            conversationHistory.removeAt(0)
        }
        
        Log.d(TAG, "Added to history - Role: $role, Content length: ${content.length}")
    }
    
    /**
     * Add conversation pair to history
     */
    fun addConversation(userMessage: String, assistantResponse: String, functionCalls: List<String> = emptyList()) {
        addToHistory(userMessage, "user")
        addToHistory(assistantResponse, "assistant")
        Log.d(TAG, "Added conversation pair to history. Total items: ${conversationHistory.size}")
    }
    
    /**
     * Get formatted context string - only returns context data, does not generate prompts
     */
    fun getContextString(): String {
        if (conversationHistory.isEmpty()) {
            return "Previous conversations: None"
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Previous conversations:\n")
        
        // Only take recent conversations
        val recentConversations = conversationHistory.takeLast(10)
        
        recentConversations.forEach { item ->
            when (item.role) {
                "user" -> contextBuilder.append("User: ${item.content}\n")
                "assistant" -> contextBuilder.append("Assistant: ${item.content}\n")
            }
        }
        
        // Ensure context is not too long
        val context = contextBuilder.toString()
        return if (context.length > MAX_CONTEXT_LENGTH) {
            context.substring(0, MAX_CONTEXT_LENGTH) + "...(truncated)"
        } else {
            context
        }
    }
    
    /**
     * Build contextual prompt - uses other managers to generate prompts
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
     * Clear conversation history
     */
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Conversation history cleared")
    }
    
    /**
     * Get conversation summary
     */
    fun getConversationSummary(): String {
        val historySize = conversationHistory.size
        val userMessages = conversationHistory.count { it.role == "user" }
        val assistantMessages = conversationHistory.count { it.role == "assistant" }
        
        return "Conversation History: $historySize total messages ($userMessages user, $assistantMessages assistant)"
    }
    
    /**
     * Get history size
     */
    fun getHistorySize(): Int {
        return conversationHistory.size
    }
    
    /**
     * Clear all history - alias method
     */
    fun clearHistory() {
        clearConversationHistory()
    }
    
    /**
     * Get last conversation
     */
    fun getLastConversation(): ConversationItem? {
        return conversationHistory.lastOrNull()
    }
    
    /**
     * Check if there is relevant historical context
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