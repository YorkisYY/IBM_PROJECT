// TypewriterEffect.kt - Place under utils package
package utils

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.*

/**
 * Typewriter effect data class
 */
data class TypewriterState(
    val displayedText: String = "",
    val isTyping: Boolean = false,
    val progress: Float = 0f,
    val fullText: String = ""
)

/**
 * Typewriter effect manager - Based on React useTypewriter Hook
 * Completely consistent with your React code logic: split by words, display one word every 200ms
 */
class TypewriterManager {
    private var _state = mutableStateOf(TypewriterState())
    val state: State<TypewriterState> = _state
    
    private var currentJob: Job? = null
    
    /**
     * Start typewriter effect
     * @param fullText Complete text
     * @param speed Delay time per word (milliseconds), default 200ms (consistent with React)
     * @param coroutineScope Coroutine scope
     */
    fun startTyping(
        fullText: String, 
        speed: Long = 200L,
        coroutineScope: CoroutineScope
    ) {
        // Stop current typing effect
        stopTyping()
        
        if (fullText.trim().isEmpty()) {
            _state.value = TypewriterState()
            return
        }
        
        // Initialize state
        _state.value = TypewriterState(
            displayedText = "",
            isTyping = true,
            progress = 0f,
            fullText = fullText.trim()
        )
        
        // Split by words (consistent with your React code)
        val words = fullText.trim().split(' ')
        var currentWordIndex = 0
        
        currentJob = coroutineScope.launch {
            try {
                while (currentWordIndex < words.size && _state.value.isTyping) {
                    delay(speed)
                    
                    // Check if cancelled
                    if (!isActive) break
                    
                    // Take first N words (consistent with your React code logic)
                    val wordsToShow = words.take(currentWordIndex + 1)
                    val displayedText = wordsToShow.joinToString(" ")
                    val progress = (currentWordIndex + 1).toFloat() / words.size
                    
                    _state.value = _state.value.copy(
                        displayedText = displayedText,
                        progress = progress
                    )
                    
                    currentWordIndex++
                }
                
                // Typing completed
                if (isActive) {
                    _state.value = _state.value.copy(
                        isTyping = false,
                        progress = 1f
                    )
                }
                
            } catch (e: CancellationException) {
                // Coroutine cancelled, no handling needed
            }
        }
    }
    
    /**
     * Stop typing effect
     */
    fun stopTyping() {
        currentJob?.cancel()
        currentJob = null
        _state.value = _state.value.copy(isTyping = false)
    }
    
    /**
     * Reset state
     */
    fun reset() {
        stopTyping()
        _state.value = TypewriterState()
    }
    
    /**
     * Immediately show complete text (skip typing effect)
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
 * Composable function: Create and manage typewriter effect
 * Usage:
 * ```
 * val typewriter = rememberTypewriterEffect()
 * 
 * // Start typing effect
 * typewriter.startTyping("Hello World!", coroutineScope)
 * 
 * // Display text
 * Text(text = typewriter.state.value.displayedText)
 * ```
 */
@Composable
fun rememberTypewriterEffect(): TypewriterManager {
    return remember { TypewriterManager() }
}

/**
 * Convenient Composable Hook - Similar to your React useTypewriter
 * @param fullText Complete text to display
 * @param speed Typing speed (milliseconds)
 * @param autoStart Whether to auto start
 * @return TypewriterState Typewriter state
 */
@Composable
fun useTypewriterEffect(
    fullText: String,
    speed: Long = 200L,
    autoStart: Boolean = true
): TypewriterState {
    val typewriter = rememberTypewriterEffect()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto start typing when text changes
    LaunchedEffect(fullText, autoStart) {
        if (autoStart && fullText.isNotEmpty()) {
            typewriter.startTyping(fullText, speed, coroutineScope)
        }
    }
    
    // Cleanup when component unmounts
    DisposableEffect(Unit) {
        onDispose {
            typewriter.reset()
        }
    }
    
    return typewriter.state.value
}

/**
 * Simplified typewriter text component
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