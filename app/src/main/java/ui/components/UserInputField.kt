package ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 用戶輸入框組件（可重複使用）
 * 適用於底部輸入、搜尋框等各種輸入場景
 */
@Composable
fun UserInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "輸入訊息...",
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    maxLines: Int = 4,
    sendButtonText: String = "➤"
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 輸入框
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = Color.Gray
                    )
                },
                enabled = isEnabled && !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 120.dp), // 自動擴展高度
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (value.trim().isNotEmpty() && !isLoading) {
                            onSendClick()
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color.LightGray,
                    disabledBorderColor = Color.LightGray.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = maxLines
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 發送按鈕
            FloatingActionButton(
                onClick = {
                    if (value.trim().isNotEmpty() && !isLoading) {
                        onSendClick()
                        keyboardController?.hide()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = if (value.trim().isNotEmpty() && !isLoading) {
                    Color(0xFF2196F3)
                } else {
                    Color.LightGray
                },
                contentColor = Color.White
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = sendButtonText,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 簡化版輸入框（無卡片樣式）
 */
@Composable
fun SimpleUserInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "輸入訊息..."
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp, max = 120.dp),
            shape = RoundedCornerShape(28.dp),
            maxLines = 3
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        FloatingActionButton(
            onClick = onSendClick,
            modifier = Modifier.size(56.dp)
        ) {
            Text("➤")
        }
    }
}

/**
 * 搜尋框版本
 */
@Composable
fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜尋...",
    isLoading: Boolean = false
) {
    UserInputField(
        value = value,
        onValueChange = onValueChange,
        onSendClick = onSearchClick,
        modifier = modifier,
        placeholder = placeholder,
        isLoading = isLoading,
        maxLines = 1,
        sendButtonText = "🔍"
    )
}