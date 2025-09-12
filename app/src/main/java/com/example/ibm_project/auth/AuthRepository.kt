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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.ibm_project.R

class AuthRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "AuthRepository"
    }
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _authState.value = if (user != null) AuthState.Authenticated else AuthState.Unauthenticated
        }
        
        _authState.value = if (auth.currentUser != null) AuthState.Authenticated else AuthState.Unauthenticated
    }
    
    /**
     * Get Google Sign-In Intent
     */
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    /**
     * Handle Google Sign-In result
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
                
                repositoryScope.launch {
                    saveUserToFirestore(user)
                }
                
                Log.d(TAG, "Google sign-in successful: ${user.email}")
                AuthResult.Success(user)
            } else {
                Log.e(TAG, "Google sign-in failed: No ID Token")
                AuthResult.Error("Sign-in failed: No authentication credentials received")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: ${e.message}")
            AuthResult.Error("Sign-in failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error: ${e.message}")
            AuthResult.Error("Sign-in failed: ${e.message}")
        } finally {
            if (_authState.value == AuthState.Loading) {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }
    
    /**
     * Anonymous sign-in
     */
    suspend fun signInAnonymously(): AuthResult {
        return try {
            _authState.value = AuthState.Loading
            
            val result = auth.signInAnonymously().await()
            val user = result.user!!
            
            Log.d(TAG, "Anonymous sign-in successful: ${user.uid}")
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed: ${e.message}")
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("Anonymous sign-in failed: ${e.message}")
        }
    }
    
    /**
     * Sign out
     */
    fun signOut() {
        try {
            auth.signOut()
            googleSignInClient.signOut()
            Log.d(TAG, "User signed out")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed: ${e.message}")
        }
    }
    
    /**
     * Save user data to Firestore
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
                
            Log.d(TAG, "User data saved to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user data: ${e.message}")
        }
    }
    
    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Get current user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
}

/**
 * Authentication state
 */
sealed class AuthState {
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
}

/**
 * Authentication result
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}