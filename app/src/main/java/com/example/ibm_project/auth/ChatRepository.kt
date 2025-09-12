package com.example.ibm_project.chat

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val userMessage: String = "",
    val aiResponse: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class ChatRepository {
    
    companion object {
        private const val TAG = "ChatRepository"
        private const val CHAT_COLLECTION = "chat_history"
        private const val MAX_MESSAGES = 50
        private const val MESSAGES_TO_KEEP = 30
        private const val CONTEXT_MESSAGES = 5  // 用於上下文的訊息數
        private const val SUGGESTION_COOLDOWN = 10 * 60 * 1000L // 10分鐘冷卻期
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Local memory storage for anonymous users
    private val _localChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    // Firebase storage for authenticated users
    private val _firebaseChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    val chatHistory: StateFlow<List<ChatMessage>> get() = 
        if (isUserAuthenticated()) _firebaseChatHistory else _localChatHistory
    
    // 推薦追蹤系統
    private val suggestionHistory = mutableMapOf<String, Long>()
    private val recentlyMentionedFeatures = mutableSetOf<String>()
    
    /**
     * Check if user is authenticated (Google sign-in)
     */
    private fun isUserAuthenticated(): Boolean {
        val currentUser = auth.currentUser
        return currentUser != null && !currentUser.isAnonymous
    }
    
    /**
     * 核心方法：獲取智能對話上下文（包含 timestamp 和所有邏輯）
     */
    fun getConversationContext(): String {
        val currentQuery = "" // 這個方法只返回歷史
        return getSmartConversationContext(currentQuery, false)
    }
    
    /**
     * 智能上下文生成 - 主要方法
     */
    fun getSmartConversationContext(currentQuery: String, includeCurrentQuery: Boolean = true): String {
        // 1. 獲取並排序歷史訊息
        val recentMessages = chatHistory.value
            .sortedBy { it.timestamp }
            .takeLast(CONTEXT_MESSAGES)
        
        // 2. 分析對話模式
        val pattern = analyzeConversationPattern(recentMessages)
        
        // 3. 決定推薦策略
        val suggestionStrategy = if (includeCurrentQuery) {
            determineSuggestionStrategy(currentQuery, pattern)
        } else {
            SuggestionStrategy.NONE
        }
        
        // 4. 生成動態規則
        val dynamicRules = generateDynamicRules(currentQuery, pattern, suggestionStrategy)
        
        // 5. 組裝完整上下文
        return buildCompleteContext(
            recentMessages,
            currentQuery,
            dynamicRules,
            suggestionStrategy,
            includeCurrentQuery
        )
    }
    
    /**
     * 分析對話模式
     */
    private fun analyzeConversationPattern(messages: List<ChatMessage>): ConversationPattern {
        if (messages.isEmpty()) {
            return ConversationPattern()
        }
        
        val recentTopics = mutableSetOf<String>()
        val mentionedFunctions = mutableListOf<String>()
        
        messages.forEach { msg ->
            // 從訊息中提取主題
            val topics = extractTopics(msg.userMessage)
            recentTopics.addAll(topics)
            
            // 從 AI 回應中提取提及的功能
            val mentions = extractFunctionMentions(msg.aiResponse)
            mentionedFunctions.addAll(mentions)
        }
        
        // 檢查是否重複詢問
        val isRepetitive = messages.size >= 2 && 
            messages.takeLast(2).all { msg ->
                extractMainTopic(msg.userMessage) == extractMainTopic(messages.last().userMessage)
            }
        
        // 檢測用戶情緒
        val mood = detectUserMood(messages)
        
        return ConversationPattern(
            recentTopics = recentTopics,
            recentlyMentionedFunctions = mentionedFunctions.distinct(),
            isRepetitiveQuery = isRepetitive,
            userMood = mood,
            messageCount = messages.size
        )
    }
    
    /**
     * 決定推薦策略
     */
    private fun determineSuggestionStrategy(
        query: String,
        pattern: ConversationPattern
    ): SuggestionStrategy {
        val lowerQuery = query.lowercase()
        
        // 用戶明確不要推薦
        if (lowerQuery.contains("just") || 
            lowerQuery.contains("only") ||
            lowerQuery.contains("don't suggest") ||
            lowerQuery.contains("stop recommending")) {
            return SuggestionStrategy.NONE
        }
        
        // 用戶情緒不佳或重複詢問同一件事
        if (pattern.userMood == UserMood.FRUSTRATED || pattern.isRepetitiveQuery) {
            Log.d(TAG, "User seems frustrated or asking repeatedly - no suggestions")
            return SuggestionStrategy.NONE
        }
        
        // 檢查冷卻期
        val suggestions = getPossibleSuggestions(query, pattern)
        val validSuggestion = suggestions.firstOrNull { canSuggest(it) }
        
        return if (validSuggestion != null) {
            SuggestionStrategy.GENTLE(validSuggestion)
        } else {
            SuggestionStrategy.NONE
        }
    }
    
    /**
     * 生成動態規則（根據當前查詢和歷史）
     */
    private fun generateDynamicRules(
        query: String,
        pattern: ConversationPattern,
        strategy: SuggestionStrategy
    ): String {
        val rules = mutableListOf<String>()
        val lowerQuery = query.lowercase()
        
        rules.add("1. Answer the CURRENT USER QUESTION first and completely")
        rules.add("2. Use conversation history for context only, do not repeat it")
        rules.add("3. Be concise and focused on what the user is asking")
        
        // Check what user is currently asking about
        val currentTopic = when {
            lowerQuery.contains("podcast") -> "podcast"
            lowerQuery.contains("message") || lowerQuery.contains("sms") -> "message"
            lowerQuery.contains("weather") -> "weather"
            lowerQuery.contains("news") -> "news"
            lowerQuery.contains("location") || lowerQuery.contains("where") -> "location"
            else -> "general"
        }
        
        // Add positive instruction for current topic
        when (currentTopic) {
            "podcast" -> {
                rules.add("CRITICAL: User is asking about PODCASTS")
                rules.add("You MUST call get_recommended_podcasts or appropriate podcast function")
                rules.add("DO NOT talk about messages or weather")
            }
            "message" -> {
                rules.add("CRITICAL: User is asking about MESSAGES")
                rules.add("You MUST call appropriate message function")
                rules.add("DO NOT talk about podcasts or weather")
            }
            "weather" -> {
                rules.add("CRITICAL: User is asking about WEATHER")
                rules.add("You MUST call get_current_weather or appropriate weather function")
                rules.add("DO NOT talk about messages or podcasts")
            }
            "news" -> {
                rules.add("CRITICAL: User is asking about NEWS")
                rules.add("You MUST call appropriate news function")
            }
            "location" -> {
                rules.add("CRITICAL: User is asking about LOCATION")
                rules.add("You MUST call appropriate location function")
            }
        }
        
        // Only restrict topics NOT being asked about
        if (currentTopic != "weather" && pattern.recentlyMentionedFunctions.contains("weather")) {
            rules.add("DO NOT mention weather unless specifically asked")
        }
        
        if (currentTopic != "podcast" && pattern.recentlyMentionedFunctions.contains("podcast")) {
            rules.add("DO NOT mention podcasts unless specifically asked")
        }
        
        if (currentTopic != "message" && pattern.recentlyMentionedFunctions.contains("message")) {
            rules.add("DO NOT mention messages unless specifically asked")
        }
        
        // Handle suggestion strategy
        when (strategy) {
            is SuggestionStrategy.GENTLE -> {
                if (currentTopic == "general") {
                    rules.add("After answering, you MAY gently suggest: ${strategy.feature}")
                }
            }
            SuggestionStrategy.NONE -> {
                if (currentTopic == "general") {
                    rules.add("DO NOT suggest additional features")
                }
            }
        }
        
        return rules.joinToString("\n")
    }
        
    /**
     * 建立完整的結構化上下文
     */
    private fun buildCompleteContext(
        messages: List<ChatMessage>,
        currentQuery: String,
        rules: String,
        strategy: SuggestionStrategy,
        includeCurrentQuery: Boolean
    ): String {
        val context = StringBuilder()
        
        // 1. 系統指令
        context.append("=== SYSTEM INSTRUCTIONS ===\n")
        context.append("You are a helpful AI assistant for elderly users.\n")
        context.append("Be warm, caring, but focused and clear.\n")
        context.append("Current time: ${formatTimestamp(System.currentTimeMillis())}\n\n")
        
        // 2. 歷史對話（包含 timestamp）
        if (messages.isNotEmpty()) {
            context.append("=== CONVERSATION HISTORY (Reference Only) ===\n")
            messages.forEach { msg ->
                val timeStr = formatTimestamp(msg.timestamp)
                val timeDiff = getTimeDifference(msg.timestamp)
                
                context.append("[$timeStr - $timeDiff ago]\n")
                context.append("User: ${msg.userMessage}\n")
                
                // 限制 AI 回應長度
                val truncatedResponse = if (msg.aiResponse.length > 150) {
                    "${msg.aiResponse.take(150)}..."
                } else {
                    msg.aiResponse
                }
                context.append("Assistant: $truncatedResponse\n")
                context.append("---\n")
            }
            context.append("=== END OF HISTORY ===\n\n")
        }
        
        // 3. 當前問題（如果需要）
        if (includeCurrentQuery && currentQuery.isNotEmpty()) {
            context.append("=== CURRENT USER QUESTION (Answer This ONLY) ===\n")
            context.append("[${formatTimestamp(System.currentTimeMillis())}] User: $currentQuery\n\n")
        }
        
        // 4. 行為規則
        context.append("=== BEHAVIOR RULES ===\n")
        context.append(rules)
        context.append("\n")
        
        // 5. 推薦指引（如果有）
        if (strategy is SuggestionStrategy.GENTLE) {
            context.append("\n=== SUGGESTION GUIDANCE ===\n")
            context.append(getSuggestionTemplate(strategy.feature))
            context.append("\n")
        }
        
        return context.toString()
    }
    
    /**
     * 格式化時間戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * 計算時間差
     */
    private fun getTimeDifference(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes min"
            else -> "$seconds sec"
        }
    }
    
    /**
     * 提取主題
     */
    private fun extractTopics(text: String): Set<String> {
        val topics = mutableSetOf<String>()
        val lowerText = text.lowercase()
        
        if (lowerText.contains("weather") || lowerText.contains("temperature")) topics.add("weather")
        if (lowerText.contains("message") || lowerText.contains("sms")) topics.add("message")
        if (lowerText.contains("podcast") || lowerText.contains("listen")) topics.add("podcast")
        if (lowerText.contains("news")) topics.add("news")
        if (lowerText.contains("location") || lowerText.contains("where am i")) topics.add("location")
        
        return topics
    }
    
    /**
     * 提取主要主題
     */
    private fun extractMainTopic(text: String): String {
        val topics = extractTopics(text)
        return topics.firstOrNull() ?: "general"
    }
    
    /**
     * 從 AI 回應提取提及的功能
     */
    private fun extractFunctionMentions(response: String): List<String> {
        val mentions = mutableListOf<String>()
        val lowerResponse = response.lowercase()
        
        if (lowerResponse.contains("weather") || lowerResponse.contains("temperature")) {
            mentions.add("weather")
        }
        if (lowerResponse.contains("podcast")) {
            mentions.add("podcast")
        }
        if (lowerResponse.contains("message") || lowerResponse.contains("sms")) {
            mentions.add("message")
        }
        if (lowerResponse.contains("news")) {
            mentions.add("news")
        }
        
        return mentions
    }
    
    /**
     * 檢測用戶情緒
     */
    private fun detectUserMood(messages: List<ChatMessage>): UserMood {
        if (messages.isEmpty()) return UserMood.NEUTRAL
        
        val lastMessages = messages.takeLast(3)
        
        // 檢查是否有挫折跡象
        val frustrationKeywords = listOf("don't", "stop", "no", "wrong", "not what i")
        val hasFrustration = lastMessages.any { msg ->
            frustrationKeywords.any { keyword ->
                msg.userMessage.lowercase().contains(keyword)
            }
        }
        
        if (hasFrustration) return UserMood.FRUSTRATED
        
        // 檢查是否專注於特定任務
        val topics = lastMessages.flatMap { extractTopics(it.userMessage) }
        if (topics.distinct().size == 1 && topics.size >= 2) {
            return UserMood.FOCUSED
        }
        
        return UserMood.NEUTRAL
    }
    
    /**
     * 獲取可能的推薦
     */
    private fun getPossibleSuggestions(query: String, pattern: ConversationPattern): List<String> {
        val suggestions = mutableListOf<String>()
        val lowerQuery = query.lowercase()
        
        // 智能推薦邏輯
        when {
            // 讀完訊息可能想知道天氣
            (lowerQuery.contains("message") || lowerQuery.contains("sms")) -> {
                if (!pattern.recentlyMentionedFunctions.contains("weather")) {
                    suggestions.add("weather")
                }
            }
            // 看完天氣可能想聽 podcast
            lowerQuery.contains("weather") -> {
                if (!pattern.recentlyMentionedFunctions.contains("podcast")) {
                    suggestions.add("podcast")
                }
            }
            // 看完新聞可能想查天氣
            lowerQuery.contains("news") -> {
                if (!pattern.recentlyMentionedFunctions.contains("weather")) {
                    suggestions.add("weather")
                }
            }
        }
        
        return suggestions
    }
    
    /**
     * 檢查是否可以推薦
     */
    private fun canSuggest(feature: String): Boolean {
        val lastSuggested = suggestionHistory[feature] ?: 0L
        val timeSince = System.currentTimeMillis() - lastSuggested
        return timeSince > SUGGESTION_COOLDOWN
    }
    
    /**
     * 記錄推薦
     */
    fun recordSuggestion(feature: String) {
        suggestionHistory[feature] = System.currentTimeMillis()
        recentlyMentionedFeatures.add(feature)
        Log.d(TAG, "Recorded suggestion for: $feature at ${System.currentTimeMillis()}")
    }
    
    /**
     * 獲取推薦模板
     */
    private fun getSuggestionTemplate(feature: String): String {
        return when (feature) {
            "weather" -> "Gently ask: 'Would you like me to check the weather for you?'"
            "podcast" -> "Softly suggest: 'I can find a nice podcast if you'd like.'"
            "news" -> "Politely offer: 'Would you like to hear today's news?'"
            else -> "Make a helpful suggestion if appropriate"
        }
    }
    
    /**
     * Save new chat message and response
     */
    suspend fun saveChatMessage(userMessage: String, aiResponse: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("User not authenticated"))
            }
            
            val chatMessage = ChatMessage(
                userId = currentUser.uid,
                userMessage = userMessage,
                aiResponse = aiResponse,
                timestamp = System.currentTimeMillis()
            )
            
            // 更新推薦追蹤
            val mentionedFeatures = extractFunctionMentions(aiResponse)
            mentionedFeatures.forEach { feature ->
                recordSuggestion(feature)
            }
            
            if (isUserAuthenticated()) {
                saveToFirebase(chatMessage)
            } else {
                saveToLocalMemory(chatMessage)
            }
            
            Log.d(TAG, "Chat message saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat message: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Load chat history based on user authentication status
     */
    suspend fun loadChatHistory(): Result<List<ChatMessage>> {
        return try {
            if (isUserAuthenticated()) {
                loadFromFirebase()
            } else {
                Result.success(_localChatHistory.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chat history: ${e.message}")
            Result.failure(e)
        }
    }
    
    // === 以下為原有方法，保持不變 ===
    
    private suspend fun saveToFirebase(chatMessage: ChatMessage) {
        val docRef = firestore.collection(CHAT_COLLECTION)
            .add(chatMessage)
            .await()
        
        val updatedMessage = chatMessage.copy(id = docRef.id)
        val currentHistory = _firebaseChatHistory.value.toMutableList()
        currentHistory.add(updatedMessage)
        _firebaseChatHistory.value = currentHistory
        
        checkAndCleanupFirebaseMessages()
    }
    
    private fun saveToLocalMemory(chatMessage: ChatMessage) {
        val updatedMessage = chatMessage.copy(id = "local_${System.currentTimeMillis()}")
        val currentHistory = _localChatHistory.value.toMutableList()
        currentHistory.add(updatedMessage)
        
        if (currentHistory.size > MAX_MESSAGES) {
            val messagesToKeep = currentHistory.takeLast(MESSAGES_TO_KEEP)
            _localChatHistory.value = messagesToKeep
            Log.d(TAG, "Local chat history cleaned up")
        } else {
            _localChatHistory.value = currentHistory
        }
    }
    
    private suspend fun loadFromFirebase(): Result<List<ChatMessage>> {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.isAnonymous) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        val querySnapshot = firestore.collection(CHAT_COLLECTION)
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .await()
        
        val messages = querySnapshot.documents.mapNotNull { document ->
            try {
                ChatMessage(
                    id = document.id,
                    userId = document.getString("userId") ?: "",
                    userMessage = document.getString("userMessage") ?: "",
                    aiResponse = document.getString("aiResponse") ?: "",
                    timestamp = document.getLong("timestamp") ?: 0L
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse chat message: ${e.message}")
                null
            }
        }
        
        _firebaseChatHistory.value = messages
        Log.d(TAG, "Loaded ${messages.size} messages from Firebase")
        
        return Result.success(messages)
    }
    
    suspend fun clearChatHistory(): Result<Unit> {
        return try {
            if (isUserAuthenticated()) {
                clearFirebaseHistory()
            } else {
                clearLocalHistory()
            }
            
            // 清空推薦記錄
            suggestionHistory.clear()
            recentlyMentionedFeatures.clear()
            
            Log.d(TAG, "Chat history cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chat history: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun clearFirebaseHistory() {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.isAnonymous) return
        
        val querySnapshot = firestore.collection(CHAT_COLLECTION)
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .await()
        
        val batch = firestore.batch()
        querySnapshot.documents.forEach { document ->
            batch.delete(document.reference)
        }
        batch.commit().await()
        
        _firebaseChatHistory.value = emptyList()
    }
    
    private fun clearLocalHistory() {
        _localChatHistory.value = emptyList()
    }
    
    private suspend fun checkAndCleanupFirebaseMessages() {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null || currentUser.isAnonymous) return
            
            val querySnapshot = firestore.collection(CHAT_COLLECTION)
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val totalMessages = querySnapshot.size()
            
            if (totalMessages > MAX_MESSAGES) {
                Log.d(TAG, "Cleanup needed: $totalMessages messages")
                
                val messagesToDelete = querySnapshot.documents.drop(MESSAGES_TO_KEEP)
                
                messagesToDelete.chunked(500).forEach { batch ->
                    val firestoreBatch = firestore.batch()
                    batch.forEach { document ->
                        firestoreBatch.delete(document.reference)
                    }
                    firestoreBatch.commit().await()
                }
                
                Log.d(TAG, "Cleaned up ${messagesToDelete.size} old messages")
                loadFromFirebase()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup: ${e.message}")
        }
    }
    
    suspend fun getChatStats(): ChatStats {
        return try {
            val messages = chatHistory.value
            val messageCount = messages.size
            val firstMessageTime = messages.firstOrNull()?.timestamp ?: 0L
            val storageType = if (isUserAuthenticated()) "Firebase" else "Local"
            
            ChatStats(messageCount, firstMessageTime, storageType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stats: ${e.message}")
            ChatStats(0, 0L, "Unknown")
        }
    }
    
    suspend fun onAuthenticationChanged() {
        try {
            if (isUserAuthenticated()) {
                loadFromFirebase()
                Log.d(TAG, "Switched to Firebase storage")
            } else {
                _firebaseChatHistory.value = emptyList()
                Log.d(TAG, "Switched to local storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch storage: ${e.message}")
        }
    }
    
    // 資料類別定義
    data class ConversationPattern(
        val recentTopics: Set<String> = emptySet(),
        val recentlyMentionedFunctions: List<String> = emptyList(),
        val isRepetitiveQuery: Boolean = false,
        val userMood: UserMood = UserMood.NEUTRAL,
        val messageCount: Int = 0
    )
    
    enum class UserMood {
        NEUTRAL,
        FOCUSED,
        FRUSTRATED,
        EXPLORING
    }
    
    sealed class SuggestionStrategy {
        object NONE : SuggestionStrategy()
        data class GENTLE(val feature: String) : SuggestionStrategy()
    }
}

data class ChatStats(
    val totalMessages: Int,
    val firstMessageTime: Long,
    val storageType: String
)