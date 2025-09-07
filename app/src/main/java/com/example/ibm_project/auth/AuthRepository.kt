package com.example.ibm_project.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import com.example.ibm_project.R

class AuthRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "AuthRepository"
    }
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    
    init {
        // 監聽認證狀態變化
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _authState.value = if (user != null) AuthState.Authenticated else AuthState.Unauthenticated
        }
        
        // 初始狀態檢查
        _authState.value = if (auth.currentUser != null) AuthState.Authenticated else AuthState.Unauthenticated
    }
    
    /**
     * 獲取 Google 登入 Intent
     */
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    /**
     * 處理 Google 登入結果
     */
    suspend fun handleGoogleSignInResult(data: Intent?): AuthResult {
        return try {
            _authState.value = AuthState.Loading
            
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (account?.idToken != null) {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user!!
                
                // 保存用戶資料到 Firestore
                saveUserToFirestore(user)
                
                Log.d(TAG, "Google 登入成功: ${user.email}")
                AuthResult.Success(user)
            } else {
                Log.e(TAG, "Google 登入失敗: 沒有 ID Token")
                AuthResult.Error("登入失敗：沒有獲得認證資訊")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google 登入失敗: ${e.message}")
            AuthResult.Error("登入失敗：${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "登入過程出錯: ${e.message}")
            AuthResult.Error("登入失敗：${e.message}")
        } finally {
            if (_authState.value == AuthState.Loading) {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }
    
    /**
     * 匿名登入
     */
    suspend fun signInAnonymously(): AuthResult {
        return try {
            _authState.value = AuthState.Loading
            
            val result = auth.signInAnonymously().await()
            val user = result.user!!
            
            Log.d(TAG, "匿名登入成功: ${user.uid}")
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "匿名登入失敗: ${e.message}")
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("匿名登入失敗：${e.message}")
        }
    }
    
    /**
     * 登出
     */
    fun signOut() {
        try {
            auth.signOut()
            googleSignInClient.signOut()
            Log.d(TAG, "用戶已登出")
        } catch (e: Exception) {
            Log.e(TAG, "登出失敗: ${e.message}")
        }
    }
    
    /**
     * 保存用戶資料到 Firestore
     */
    private suspend fun saveUserToFirestore(user: FirebaseUser) {
        try {
            val userData = mapOf(
                "name" to user.displayName,
                "email" to user.email,
                "avatar" to user.photoUrl?.toString(),
                "isAnonymous" to user.isAnonymous,
                "createdAt" to System.currentTimeMillis(),
                "lastLogin" to System.currentTimeMillis()
            )
            
            firestore.collection("users")
                .document(user.uid)
                .set(userData)
                .await()
                
            Log.d(TAG, "用戶資料已保存到 Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "保存用戶資料失敗: ${e.message}")
            // 不拋出異常，因為登入已經成功，只是資料保存失敗
        }
    }
    
    /**
     * 檢查是否已登入
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * 獲取當前用戶
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
}

/**
 * 認證狀態
 */
sealed class AuthState {
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
}

/**
 * 認證結果
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}