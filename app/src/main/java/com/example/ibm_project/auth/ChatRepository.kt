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

class ChatRepository {
    
    companion object {
        private const val TAG = "ChatRepository"
        private const val CHAT_COLLECTION = "chat_history"
        private const val MAX_MESSAGES = 50 // Maximum messages per user
        private const val MESSAGES_TO_KEEP = 30 // Keep this many when cleaning up
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Local memory storage for anonymous users
    private val _localChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    // Firebase storage for authenticated users
    private val _firebaseChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    val chatHistory: StateFlow<List<ChatMessage>> get() = 
        if (isUserAuthenticated()) _firebaseChatHistory else _localChatHistory
    
    /**
     * Check if user is authenticated (Google sign-in)
     */
    private fun isUserAuthenticated(): Boolean {
        val currentUser = auth.currentUser
        return currentUser != null && !currentUser.isAnonymous
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
            
            if (isUserAuthenticated()) {
                // Google authenticated user - save to Firebase
                saveToFirebase(chatMessage)
            } else {
                // Anonymous user - save to local memory only
                saveToLocalMemory(chatMessage)
            }
            
            Log.d(TAG, "Chat message saved successfully (${if (isUserAuthenticated()) "Firebase" else "Local"})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat message: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Save to Firebase for authenticated users
     */
    private suspend fun saveToFirebase(chatMessage: ChatMessage) {
        val docRef = firestore.collection(CHAT_COLLECTION)
            .add(chatMessage)
            .await()
        
        val updatedMessage = chatMessage.copy(id = docRef.id)
        val currentHistory = _firebaseChatHistory.value.toMutableList()
        currentHistory.add(updatedMessage)
        _firebaseChatHistory.value = currentHistory
        
        // Check if cleanup is needed for Firebase
        checkAndCleanupFirebaseMessages()
    }
    
    /**
     * Save to local memory for anonymous users
     */
    private fun saveToLocalMemory(chatMessage: ChatMessage) {
        val updatedMessage = chatMessage.copy(id = "local_${System.currentTimeMillis()}")
        val currentHistory = _localChatHistory.value.toMutableList()
        currentHistory.add(updatedMessage)
        
        // Auto cleanup for local storage
        if (currentHistory.size > MAX_MESSAGES) {
            val messagesToKeep = currentHistory.takeLast(MESSAGES_TO_KEEP)
            _localChatHistory.value = messagesToKeep
            Log.d(TAG, "Local chat history cleaned up: kept ${messagesToKeep.size} messages")
        } else {
            _localChatHistory.value = currentHistory
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
                // For anonymous users, local memory is already loaded
                Result.success(_localChatHistory.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chat history: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Load chat history from Firebase for authenticated users
     */
    private suspend fun loadFromFirebase(): Result<List<ChatMessage>> {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.isAnonymous) {
            return Result.failure(Exception("User not authenticated for Firebase"))
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
        Log.d(TAG, "Loaded ${messages.size} chat messages from Firebase")
        
        return Result.success(messages)
    }
    
    /**
     * Get conversation context for AI (recent messages)
     */
    fun getConversationContext(): String {
        val recentMessages = chatHistory.value.takeLast(10) // Last 10 exchanges
        
        if (recentMessages.isEmpty()) {
            return ""
        }
        
        return recentMessages.joinToString("\n\n") { message ->
            "User: ${message.userMessage}\nAssistant: ${message.aiResponse}"
        }
    }
    
    /**
     * Clear all chat history
     */
    suspend fun clearChatHistory(): Result<Unit> {
        return try {
            if (isUserAuthenticated()) {
                clearFirebaseHistory()
            } else {
                clearLocalHistory()
            }
            
            Log.d(TAG, "Chat history cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chat history: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Clear Firebase chat history for authenticated users
     */
    private suspend fun clearFirebaseHistory() {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.isAnonymous) return
        
        val querySnapshot = firestore.collection(CHAT_COLLECTION)
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .await()
        
        // Delete all messages in batch
        val batch = firestore.batch()
        querySnapshot.documents.forEach { document ->
            batch.delete(document.reference)
        }
        batch.commit().await()
        
        // Clear local state
        _firebaseChatHistory.value = emptyList()
    }
    
    /**
     * Clear local chat history for anonymous users
     */
    private fun clearLocalHistory() {
        _localChatHistory.value = emptyList()
    }
    
    /**
     * Check if cleanup is needed for Firebase and remove old messages
     */
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
                Log.d(TAG, "Firebase cleanup needed: $totalMessages messages (max: $MAX_MESSAGES)")
                
                // Get messages to delete (oldest ones)
                val messagesToDelete = querySnapshot.documents.drop(MESSAGES_TO_KEEP)
                
                // Delete in batches (Firestore batch limit is 500)
                val batchSize = 500
                messagesToDelete.chunked(batchSize).forEach { batch ->
                    val firestoreBatch = firestore.batch()
                    batch.forEach { document ->
                        firestoreBatch.delete(document.reference)
                    }
                    firestoreBatch.commit().await()
                }
                
                Log.d(TAG, "Cleaned up ${messagesToDelete.size} old Firebase messages")
                
                // Reload chat history
                loadFromFirebase()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old Firebase messages: ${e.message}")
        }
    }
    
    /**
     * Get chat statistics
     */
    suspend fun getChatStats(): ChatStats {
        return try {
            val messages = chatHistory.value
            val messageCount = messages.size
            val firstMessageTime = messages.firstOrNull()?.timestamp ?: 0L
            val storageType = if (isUserAuthenticated()) "Firebase" else "Local Memory"
            
            ChatStats(messageCount, firstMessageTime, storageType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chat stats: ${e.message}")
            ChatStats(0, 0L, "Unknown")
        }
    }
    
    /**
     * Switch storage when user authentication changes
     */
    suspend fun onAuthenticationChanged() {
        try {
            if (isUserAuthenticated()) {
                // User signed in with Google - load Firebase history
                loadFromFirebase()
                Log.d(TAG, "Switched to Firebase storage")
            } else {
                // User signed out or using anonymous - clear Firebase history from memory
                _firebaseChatHistory.value = emptyList()
                Log.d(TAG, "Switched to local storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch storage: ${e.message}")
        }
    }
}

data class ChatStats(
    val totalMessages: Int,
    val firstMessageTime: Long,
    val storageType: String
)