// TypewriterEffect.kt - 放在 utils 包下
package utils

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.*

/**
 * 打字機效果數據類
 */
data class TypewriterState(
    val displayedText: String = "",
    val isTyping: Boolean = false,
    val progress: Float = 0f,
    val fullText: String = ""
)

/**
 * 打字機效果管理器 - 基於 React useTypewriter Hook
 * 與您的 React 代碼邏輯完全一致：按單詞分割，每200ms顯示一個單詞
 */
class TypewriterManager {
    private var _state = mutableStateOf(TypewriterState())
    val state: State<TypewriterState> = _state
    
    private var currentJob: Job? = null
    
    /**
     * 開始打字機效果
     * @param fullText 完整文本
     * @param speed 每個單詞的延遲時間（毫秒），默認200ms（與React一致）
     * @param coroutineScope 協程作用域
     */
    fun startTyping(
        fullText: String, 
        speed: Long = 200L,
        coroutineScope: CoroutineScope
    ) {
        // 停止當前的打字效果
        stopTyping()
        
        if (fullText.trim().isEmpty()) {
            _state.value = TypewriterState()
            return
        }
        
        // 初始化狀態
        _state.value = TypewriterState(
            displayedText = "",
            isTyping = true,
            progress = 0f,
            fullText = fullText.trim()
        )
        
        // 按單詞分割（與您的 React 代碼一致）
        val words = fullText.trim().split(' ')
        var currentWordIndex = 0
        
        currentJob = coroutineScope.launch {
            try {
                while (currentWordIndex < words.size && _state.value.isTyping) {
                    delay(speed)
                    
                    // 檢查是否被取消
                    if (!isActive) break
                    
                    // 取前 N 個單詞（與您的 React 代碼邏輯一致）
                    val wordsToShow = words.take(currentWordIndex + 1)
                    val displayedText = wordsToShow.joinToString(" ")
                    val progress = (currentWordIndex + 1).toFloat() / words.size
                    
                    _state.value = _state.value.copy(
                        displayedText = displayedText,
                        progress = progress
                    )
                    
                    currentWordIndex++
                }
                
                // 打字完成
                if (isActive) {
                    _state.value = _state.value.copy(
                        isTyping = false,
                        progress = 1f
                    )
                }
                
            } catch (e: CancellationException) {
                // 協程被取消，不需要處理
            }
        }
    }
    
    /**
     * 停止打字效果
     */
    fun stopTyping() {
        currentJob?.cancel()
        currentJob = null
        _state.value = _state.value.copy(isTyping = false)
    }
    
    /**
     * 重置狀態
     */
    fun reset() {
        stopTyping()
        _state.value = TypewriterState()
    }
    
    /**
     * 立即顯示完整文本（跳過打字效果）
     */
    fun showCompleteText() {
        stopTyping()
        _state.value = _state.value.copy(
            displayedText = _state.value.fullText,
            isTyping = false,
            progress = 1f
        )
    }
}

/**
 * Composable 函數：創建和管理打字機效果
 * 使用方式：
 * ```
 * val typewriter = rememberTypewriterEffect()
 * 
 * // 開始打字效果
 * typewriter.startTyping("Hello World!", coroutineScope)
 * 
 * // 顯示文字
 * Text(text = typewriter.state.value.displayedText)
 * ```
 */
@Composable
fun rememberTypewriterEffect(): TypewriterManager {
    return remember { TypewriterManager() }
}

/**
 * 便捷的 Composable Hook - 類似您的 React useTypewriter
 * @param fullText 要顯示的完整文本
 * @param speed 打字速度（毫秒）
 * @param autoStart 是否自動開始
 * @return TypewriterState 打字機狀態
 */
@Composable
fun useTypewriterEffect(
    fullText: String,
    speed: Long = 200L,
    autoStart: Boolean = true
): TypewriterState {
    val typewriter = rememberTypewriterEffect()
    val coroutineScope = rememberCoroutineScope()
    
    // 當文本變化時自動開始打字
    LaunchedEffect(fullText, autoStart) {
        if (autoStart && fullText.isNotEmpty()) {
            typewriter.startTyping(fullText, speed, coroutineScope)
        }
    }
    
    // 組件卸載時清理
    DisposableEffect(Unit) {
        onDispose {
            typewriter.reset()
        }
    }
    
    return typewriter.state.value
}

/**
 * 簡化版打字機文本組件
 */
@Composable
fun TypewriterText(
    text: String,
    speed: Long = 200L,
    style: TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier
) {
    val typewriterState = useTypewriterEffect(
        fullText = text,
        speed = speed,
        autoStart = true
    )
    
    Text(
        text = typewriterState.displayedText,
        style = style,
        modifier = modifier
    )
}