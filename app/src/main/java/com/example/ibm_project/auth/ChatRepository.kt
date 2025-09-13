package com.example.ibm_project.chat

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val userMessage: String = "",
    val aiResponse: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ChatRepository focused on context cleaning and intelligent summarization
 * Supports Firebase for authenticated users and local storage for anonymous users
 * Enhanced with emotion, health, and personal information detection
 */
class ChatRepository {
    
    companion object {
        private const val TAG = "ChatRepository"
        private const val CHAT_COLLECTION = "chat_history"
        private const val MAX_STORED_MESSAGES = 50
        
        // Context quality control parameters
        private const val MAX_CONTEXT_MESSAGES = 4
        private const val MIN_QUALITY_SCORE = 0.3
        private const val MAX_SUMMARY_LENGTH = 300
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Local memory storage for anonymous users
    private val _localChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    // Firebase storage for authenticated users
    private val _firebaseChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    val chatHistory: StateFlow<List<ChatMessage>> get() = 
        if (isUserAuthenticated()) _firebaseChatHistory else _localChatHistory
    
    // === Function name compatibility - support all previously used function names ===
    
    /**
     * Legacy function name compatibility
     */
    fun getConversationContext(): String {
        return getQueryAwareContext("")
    }
    
    /**
     * Another legacy function name compatibility
     */
    fun getSmartConversationContext(currentQuery: String, includeCurrentQuery: Boolean = true): String {
        return getQueryAwareContext(currentQuery)
    }
    
    /**
     * Get query-aware context summary - returns intelligent summary instead of full conversation
     * This is the main method called by WatsonAIEnhanced
     */
    fun getQueryAwareContext(currentQuery: String = ""): String {
        val allMessages = chatHistory.value
        if (allMessages.isEmpty()) return ""
        
        // Step 1: Filter polluted messages
        val cleanMessages = allMessages.filter { !isPollutedMessage(it) }
        
        if (cleanMessages.isEmpty()) {
            Log.d(TAG, "No clean messages available for context")
            return ""
        }
        
        // Step 2: Evaluate message quality and select best ones
        val qualityMessages = cleanMessages
            .takeLast(MAX_CONTEXT_MESSAGES * 2)
            .map { message -> 
                MessageWithScore(message, calculateMessageQuality(message, currentQuery))
            }
            .filter { it.qualityScore >= MIN_QUALITY_SCORE }
            .sortedByDescending { it.qualityScore }
            .take(MAX_CONTEXT_MESSAGES)
            .sortedBy { it.message.timestamp }
        
        if (qualityMessages.isEmpty()) {
            Log.d(TAG, "No high-quality messages for context")
            return ""
        }
        
        // Step 3: Create intelligent summary instead of full context
        val summary = createIntelligentSummary(qualityMessages.map { it.message }, currentQuery)
        
        Log.d(TAG, "Context summary created: ${summary.length} chars from ${qualityMessages.size} messages")
        
        return summary
    }
    
    /**
     * Create intelligent summary - extract key information only
     */
    private fun createIntelligentSummary(messages: List<ChatMessage>, currentQuery: String): String {
        val summaryParts = mutableListOf<String>()
        
        messages.forEach { message ->
            // Extract user intent
            val userIntent = extractUserIntent(message.userMessage)
            if (userIntent.isNotEmpty()) {
                summaryParts.add("User asked: $userIntent")
            }
            
            // Extract key data from AI response
            val keyData = extractKeyData(message.aiResponse)
            if (keyData.isNotEmpty()) {
                summaryParts.add("Data: $keyData")
            }
            
            // Check length limit
            val currentSummary = summaryParts.joinToString(". ")
            if (currentSummary.length > MAX_SUMMARY_LENGTH) {
                return@forEach
            }
        }
        
        // Adjust summary for current query if relevant
        val finalSummary = if (currentQuery.isNotEmpty()) {
            adjustSummaryForQuery(summaryParts.joinToString(". "), currentQuery)
        } else {
            summaryParts.joinToString(". ")
        }
        
        return finalSummary.take(MAX_SUMMARY_LENGTH)
    }
    
    /**
     * Extract user intent from message
     */
    private fun extractUserIntent(userMessage: String): String {
        val message = userMessage.lowercase()
        
        return when {
            message.contains("weather") || message.contains("temperature") -> 
                "weather info"
            message.contains("message") || message.contains("sms") -> 
                "check messages"
            message.contains("news") -> 
                "get news"
            message.contains("podcast") -> 
                "find podcasts"
            message.contains("location") || message.contains("where") -> 
                "location query"
            message.contains("time") -> 
                "time query"
            message.contains("how") && (message.contains("feel") || message.contains("doing")) ->
                "check wellbeing"
            message.contains("family") || message.contains("daughter") || message.contains("son") ->
                "family info"
            message.contains("health") || message.contains("doctor") ->
                "health query"
            else -> {
                // Extract verb + noun patterns
                val actionWords = listOf("get", "find", "show", "tell", "check", "search")
                val action = actionWords.find { message.contains(it) }
                if (action != null) {
                    val words = message.split(" ")
                    val actionIndex = words.indexOfFirst { it.contains(action) }
                    val nextWords = words.drop(actionIndex + 1).take(2)
                    "$action ${nextWords.joinToString(" ")}"
                } else ""
            }
        }.take(50) // Limit intent description length
    }
    
    /**
     * Extract key data from AI response - Enhanced with emotion and personal info detection
     * Keeps within 100 character limit for summary efficiency
     */
    private fun extractKeyData(aiResponse: String): String {
        val keyInfo = mutableListOf<String>()
        
        // Extract numbers (temperature, quantities, etc.)
        val numbers = "\\b\\d+(\\.\\d+)?\\s*(°C|°F|%|degrees?|items?|messages?)\\b"
            .toRegex(RegexOption.IGNORE_CASE)
            .findAll(aiResponse)
            .map { it.value }
            .take(2) // Reduced from 3 to 2
        keyInfo.addAll(numbers)
        
        // Extract locations
        val locations = "\\b[A-Z][a-z]+\\s?(City|Street|Road|Avenue|Area|District)\\b"
            .toRegex()
            .findAll(aiResponse)
            .map { it.value }
            .take(1) // Reduced from 2 to 1
        keyInfo.addAll(locations)
        
        // Extract status words
        val statusWords = listOf("sunny", "cloudy", "rainy", "hot", "cold", "available", "found", "sent")
        val foundStatus = statusWords.filter { aiResponse.lowercase().contains(it) }.take(2)
        keyInfo.addAll(foundStatus)
        
        // Extract time-related information
        val timePattern = "\\b(today|tomorrow|yesterday|\\d{1,2}:\\d{2}|morning|afternoon|evening)\\b"
            .toRegex(RegexOption.IGNORE_CASE)
            .findAll(aiResponse)
            .map { it.value }
            .take(1) // Reduced from 2 to 1
        keyInfo.addAll(timePattern)
        
        // Extract person names (but filter common words)
        val names = "\\b[A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,})?\\b"
            .toRegex()
            .findAll(aiResponse)
            .map { it.value.trim() }
            .filter { !isCommonWord(it) }
            .distinct()
            .take(2)
        keyInfo.addAll(names)
        
        // Extract emotional states and feelings (prioritized list)
        val emotions = listOf(
            // High priority emotions (most relevant for elderly care)
            "happy", "sad", "worried", "good", "bad", "fine", "well", "better", "sick", "tired",
            "excited", "upset", "calm", "stressed", "lonely", "cheerful", "anxious", "content",
            
            // Health-related states
            "healthy", "unwell", "recovering", "weak", "strong"
        )
        val foundEmotions = emotions.filter { emotion ->
            aiResponse.lowercase().contains(emotion)
        }.distinct().take(2)
        keyInfo.addAll(foundEmotions)
        
        // Extract health-related information (simplified)
        val healthKeywords = listOf(
            "doctor", "hospital", "medicine", "appointment", "pain", "headache", 
            "fever", "checkup", "medication", "treatment", "pill"
        )
        val healthInfo = healthKeywords.filter { keyword ->
            aiResponse.lowercase().contains(keyword)
        }.distinct().take(1) // Only most important health info
        keyInfo.addAll(healthInfo)
        
        // Extract family/relationship information (simplified)
        val relationshipWords = listOf(
            "daughter", "son", "family", "grandchild", "wife", "husband",
            "friend", "visit", "called", "birthday"
        )
        val relationshipInfo = relationshipWords.filter { word ->
            aiResponse.lowercase().contains(word)
        }.distinct().take(1) // Only most important relationship info
        keyInfo.addAll(relationshipInfo)
        
        return keyInfo.distinct().joinToString(" ").take(100) // Keep at 100 chars as requested
    }
    
    /**
     * Check if a word is a common word that shouldn't be treated as a name
     */
    private fun isCommonWord(word: String): Boolean {
        val commonWords = listOf(
            // Days and time
            "Today", "Tomorrow", "Yesterday", "Morning", "Afternoon", "Evening",
            
            // Common phrases  
            "There", "This", "That", "The", "You", "Your", "Here", "Where", "When", "What", "How",
            "Good", "Bad", "Nice", "Great", "Well", "Better", "Fine", "Okay", "Sure", "Yes", "No",
            
            // Places (generic)
            "Home", "Work", "School", "Store", "Hospital", "Park",
            
            // Weather and conditions
            "Sunny", "Cloudy", "Rainy", "Hot", "Cold", "Warm", "Cool",
            
            // Technology/Apps
            "Google", "Facebook", "Email", "Phone", "Computer"
        )
        
        return word in commonWords
    }
    
    /**
     * Adjust summary focus based on current query
     */
    private fun adjustSummaryForQuery(summary: String, currentQuery: String): String {
        val queryWords = currentQuery.lowercase().split(" ").filter { it.length > 2 }
        if (queryWords.isEmpty()) return summary
        
        // Extract parts relevant to current query
        val sentences = summary.split(". ")
        val relevantSentences = sentences.filter { sentence ->
            queryWords.any { word -> sentence.lowercase().contains(word) }
        }
        
        return if (relevantSentences.isNotEmpty()) {
            relevantSentences.joinToString(". ").take(200)
        } else {
            summary.take(200) // Return truncated original if no relevant content
        }
    }
    
    /**
     * Detect polluted messages - strict filtering for content that affects AI quality
     */
    private fun isPollutedMessage(message: ChatMessage): Boolean {
        val aiResponse = message.aiResponse.lowercase().trim()
        val userMessage = message.userMessage.lowercase().trim()
        
        // 1. Error message pollution
        val errorKeywords = listOf(
            "sorry", "problem", "error", "failed", "connection", "technical difficulties",
            "try again", "reopen", "restart", "experiencing", "unable to", "cannot process"
        )
        val errorCount = errorKeywords.count { aiResponse.contains(it) }
        if (errorCount >= 2) {
            Log.d(TAG, "Message filtered: multiple error keywords")
            return true
        }
        
        // 2. Incomplete responses
        if (aiResponse.length < 15 || (aiResponse.endsWith("...") && aiResponse.length < 60)) {
            Log.d(TAG, "Message filtered: incomplete response")
            return true
        }
        
        // 3. Repetitive or meaningless content
        if (isRepetitiveContent(aiResponse)) {
            Log.d(TAG, "Message filtered: repetitive content")
            return true
        }
        
        // 4. System prompt leakage
        val systemLeakPatterns = listOf(
            "as an ai", "i am an ai", "language model", "i don't have", "i cannot"
        )
        if (systemLeakPatterns.any { aiResponse.contains(it) }) {
            Log.d(TAG, "Message filtered: system prompt leak")
            return true
        }
        
        // 5. Vague responses
        if (isVagueResponse(aiResponse)) {
            Log.d(TAG, "Message filtered: vague response")
            return true
        }
        
        // 6. Extremely long responses (possible prompt injection)
        if (aiResponse.length > 2000) {
            Log.d(TAG, "Message filtered: extremely long response (${aiResponse.length} chars)")
            return true
        }
        
        return false
    }
    
    /**
     * Calculate message quality score
     */
    private fun calculateMessageQuality(message: ChatMessage, currentQuery: String): Double {
        var score = 0.5 // Base score
        
        val aiResponse = message.aiResponse.trim()
        val userMessage = message.userMessage.trim()
        
        // 1. Length appropriateness (20%)
        score += when (aiResponse.length) {
            in 20..150 -> 0.2
            in 151..300 -> 0.15
            in 301..500 -> 0.1
            else -> 0.0
        }
        
        // 2. Content completeness (25%)
        if (!aiResponse.contains("...") && aiResponse.length > 20) {
            score += 0.25
        }
        
        // 3. Relevance assessment (30%)
        if (currentQuery.isNotEmpty()) {
            val relevanceScore = calculateRelevance(message, currentQuery)
            score += relevanceScore * 0.3
        } else {
            score += 0.15 // Base score when no query
        }
        
        // 4. Freshness (25%)
        val ageInHours = (System.currentTimeMillis() - message.timestamp) / (1000 * 60 * 60)
        val freshnessScore = when {
            ageInHours < 1 -> 0.25
            ageInHours < 6 -> 0.2
            ageInHours < 24 -> 0.15
            ageInHours < 168 -> 0.1 // Within a week
            else -> 0.05
        }
        score += freshnessScore
        
        return score.coerceIn(0.0, 1.0)
    }
    
    // === Helper detection methods ===
    
    private fun isRepetitiveContent(text: String): Boolean {
        val words = text.split("\\s+".toRegex()).filter { it.length > 2 }
        if (words.size < 5) return false
        
        val uniqueWords = words.toSet().size
        val repetitionRatio = uniqueWords.toDouble() / words.size
        return repetitionRatio < 0.5 // If more than 50% are repeated words
    }
    
    private fun isVagueResponse(response: String): Boolean {
        val vaguePatterns = listOf(
            "i understand", "i see", "okay", "alright", "sure", "yes", "no problem"
        )
        
        return response.length < 25 && vaguePatterns.any { response.lowercase().contains(it) }
    }
    
    private fun calculateRelevance(message: ChatMessage, currentQuery: String): Double {
        val messageText = "${message.userMessage} ${message.aiResponse}".lowercase()
        val queryWords = currentQuery.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }
        
        if (queryWords.isEmpty()) return 0.5
        
        val matchingWords = queryWords.count { word -> messageText.contains(word) }
        return matchingWords.toDouble() / queryWords.size
    }
    
    // === Firebase and local storage logic ===
    
    /**
     * Save chat message with context awareness
     */
    suspend fun saveChatMessage(userMessage: String, aiResponse: String): Result<Unit> {
        return try {
            val chatMessage = if (isUserAuthenticated()) {
                val currentUser = auth.currentUser!!
                ChatMessage(
                    userId = currentUser.uid,
                    userMessage = userMessage,
                    aiResponse = aiResponse,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                ChatMessage(
                    userId = "anonymous",
                    userMessage = userMessage,
                    aiResponse = aiResponse,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            if (isUserAuthenticated()) {
                saveToFirebase(chatMessage)
            } else {
                saveToLocalMemory(chatMessage)
            }
            
            Log.d(TAG, "Message saved successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Load chat history
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
    
    // Private helper methods
    private fun isUserAuthenticated(): Boolean {
        val currentUser = auth.currentUser
        return currentUser != null && !currentUser.isAnonymous
    }
    
    private suspend fun saveToFirebase(chatMessage: ChatMessage) {
        val docRef = firestore.collection(CHAT_COLLECTION).add(chatMessage).await()
        val updatedMessage = chatMessage.copy(id = docRef.id)
        val currentHistory = _firebaseChatHistory.value.toMutableList()
        currentHistory.add(updatedMessage)
        
        if (currentHistory.size > MAX_STORED_MESSAGES) {
            _firebaseChatHistory.value = currentHistory.takeLast(MAX_STORED_MESSAGES)
        } else {
            _firebaseChatHistory.value = currentHistory
        }
    }
    
    private fun saveToLocalMemory(chatMessage: ChatMessage) {
        val updatedMessage = chatMessage.copy(id = "local_${System.currentTimeMillis()}")
        val currentHistory = _localChatHistory.value.toMutableList()
        currentHistory.add(updatedMessage)
        
        if (currentHistory.size > MAX_STORED_MESSAGES) {
            _localChatHistory.value = currentHistory.takeLast(MAX_STORED_MESSAGES)
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
            .limit(MAX_STORED_MESSAGES.toLong())
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
                Log.w(TAG, "Failed to parse message: ${e.message}")
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
    
    /**
     * Get context quality statistics
     */
    fun getContextQualityStats(): Map<String, Any> {
        val allMessages = chatHistory.value
        val cleanMessages = allMessages.filter { !isPollutedMessage(it) }
        val pollutedCount = allMessages.size - cleanMessages.size
        
        return mapOf(
            "totalMessages" to allMessages.size,
            "cleanMessages" to cleanMessages.size,
            "pollutedMessages" to pollutedCount,
            "pollutionRate" to if (allMessages.isNotEmpty()) 
                String.format("%.1f%%", (pollutedCount.toDouble() / allMessages.size) * 100) else "0%",
            "storageType" to if (isUserAuthenticated()) "Firebase" else "Local"
        )
    }
    
    /**
     * Get chat statistics for debugging
     */
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
}

/**
 * Chat statistics data class
 */
data class ChatStats(
    val totalMessages: Int,
    val firstMessageTime: Long,
    val storageType: String
)

/**
 * Message with quality score for internal processing
 */
data class MessageWithScore(
    val message: ChatMessage,
    val qualityScore: Double
)