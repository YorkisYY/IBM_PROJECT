package com.example.ibm_project.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    authRepository: AuthRepository = AuthRepository(LocalContext.current)
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            isLoading = true
            val authResult = authRepository.handleGoogleSignInResult(result.data)
            
            when (authResult) {
                is AuthResult.Success -> {
                    isLoading = false
                    onLoginSuccess()
                }
                is AuthResult.Error -> {
                    isLoading = false
                    errorMessage = authResult.message
                }
            }
        }
    }
    
    val authState by authRepository.authState.collectAsState()
    
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogoSection()
            
            Spacer(modifier = Modifier.height(48.dp))
            
            LoginButtonsSection(
                isLoading = isLoading,
                onGoogleSignIn = {
                    val signInIntent = authRepository.getGoogleSignInIntent()
                    googleSignInLauncher.launch(signInIntent)
                },
                onAnonymousSignIn = {
                    scope.launch {
                        isLoading = true
                        val result = authRepository.signInAnonymously()
                        
                        when (result) {
                            is AuthResult.Success -> {
                                isLoading = false
                                onLoginSuccess()
                            }
                            is AuthResult.Error -> {
                                isLoading = false
                                errorMessage = result.message
                            }
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            errorMessage?.let { message ->
                ErrorMessageCard(
                    message = message,
                    onDismiss = { errorMessage = null }
                )
            }
        }
        
        if (isLoading) {
            LoadingOverlay()
        }
    }
}

@Composable
private fun AppLogoSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AI Weather Art",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Start conversation with your AR assistant",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoginButtonsSection(
    isLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onAnonymousSignIn: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LoginButton(
            text = "Sign in with Google",
            onClick = onGoogleSignIn,
            enabled = !isLoading,
            backgroundColor = Color(0xFF2196F3),
            textColor = Color.White,
            leadingIcon = null
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(
                modifier = Modifier.weight(1f),
                color = Color.Gray.copy(alpha = 0.5f)
            )
            Text(
                text = "  or  ",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Divider(
                modifier = Modifier.weight(1f),
                color = Color.Gray.copy(alpha = 0.5f)
            )
        }
        
        LoginButton(
            text = "Continue as Guest",
            onClick = onAnonymousSignIn,
            enabled = !isLoading,
            backgroundColor = Color.Transparent,
            textColor = Color(0xFF2196F3),
            leadingIcon = Icons.Default.Person,
            isOutlined = true
        )
    }
}

@Composable
private fun LoginButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    backgroundColor: Color,
    textColor: Color,
    leadingIcon: ImageVector? = null,
    isOutlined: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = if (isOutlined) 0.dp else 8.dp,
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        border = if (isOutlined) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3))
        } else null,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isOutlined) 0.dp else 4.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ErrorMessageCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Color(0xFFD32F2F),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = Color(0xFFD32F2F)
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF2196F3)
                )
            }
        }
    }
}