// app/src/test/java/watsonx/WatsonAIEnhancedTest.kt
package watsonx

import io.mockk.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Basic WatsonAIEnhanced Tests
 * Focus on testing logic components without Android dependencies
 */
class WatsonAIEnhancedTest {

    @Before
    fun setUp() {
        // Clear any previous state
        ContextManager.clearConversationHistory()
    }

    @After
    fun tearDown() {
        // Clean up after tests
        ContextManager.clearConversationHistory()
        unmockkAll()
    }

    // Test JSON parsing logic
    @Test
    fun `extractFunctionCall should correctly parse valid function call JSON`() {
        // Given: Watson AI response containing function call
        val watsonResponse = """
            Based on your request, I need to call a function.
            FUNCTION_CALL: {"name": "get_current_weather", "arguments": {"city": "Taipei"}}
            Let me get that information for you.
        """.trimIndent()

        // When: Extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Verify parsing result
        assertNotNull("Should successfully parse function call", functionCall)
        assertEquals("Function name should be correct", "get_current_weather", functionCall!!.name)
        assertTrue("Arguments should contain city", functionCall.arguments.contains("Taipei"))
    }

    @Test
    fun `extractFunctionCall should handle function calls without parameters`() {
        // Given: Function call without parameters
        val watsonResponse = """
            I'll check your unread messages.
            FUNCTION_CALL: {"name": "read_unread_messages", "arguments": {}}
            Please wait while I retrieve your messages.
        """.trimIndent()

        // When: Extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Verify parsing result
        assertNotNull("Should successfully parse parameterless function call", functionCall)
        assertEquals("Function name should be correct", "read_unread_messages", functionCall!!.name)
        assertEquals("Arguments should be empty object", "{}", functionCall.arguments)
    }

    @Test
    fun `extractFunctionCall should handle complex nested JSON parameters`() {
        // Given: Function call with complex parameters
        val watsonResponse = """
            FUNCTION_CALL: {"name": "search_news", "arguments": {"query": "AI technology", "limit": 10, "language": "en"}}
        """.trimIndent()

        // When: Extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Verify parsing result
        assertNotNull("Should successfully parse complex parameters", functionCall)
        assertEquals("Function name should be correct", "search_news", functionCall!!.name)
        assertTrue("Arguments should contain query", functionCall.arguments.contains("AI technology"))
        assertTrue("Arguments should contain limit", functionCall.arguments.contains("10"))
    }

    @Test
    fun `extractFunctionCall should handle invalid or missing function calls`() {
        // Given: Response without function call
        val watsonResponse = "This is just a normal conversation without any function calls."

        // When: Try to extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Should return null
        assertEquals("Should return null when no function call present", null, functionCall)
    }

    @Test
    fun `extractFunctionCall should handle malformed JSON`() {
        // Given: Response with malformed JSON
        val watsonResponse = """
            FUNCTION_CALL: {"name": "get_weather", "arguments": {invalid json}
        """.trimIndent()

        // When: Try to extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Should gracefully handle error
        assertEquals("Should return null for malformed JSON", null, functionCall)
    }

    // Test prompt generation logic
    @Test
    fun `buildContextualPrompt should correctly generate function calling prompt`() {
        // Given: Add some conversation history
        ContextManager.addConversation(
            "How is the weather today?",
            "Today in Taipei is sunny, 25°C"
        )

        // When: Generate function calling prompt
        val prompt = ContextManager.buildContextualPrompt("Will it rain tomorrow?", isFunction = true)

        // Then: Verify prompt content
        assertTrue("Should contain function call instructions", prompt.contains("FUNCTION_CALL"))
        assertTrue("Should contain weather function description", prompt.contains("get_current_weather"))
        assertTrue("Should contain conversation history", prompt.contains("How is the weather"))
        assertTrue("Should contain new user message", prompt.contains("Will it rain tomorrow"))
    }

    @Test
    fun `buildContextualPrompt should correctly generate normal conversation prompt`() {
        // Given: Add conversation history
        ContextManager.addConversation(
            "Hello",
            "Hello! I'm your AR assistant"
        )

        // When: Generate normal conversation prompt
        val prompt = ContextManager.buildContextualPrompt("Thank you", isFunction = false)

        // Then: Verify prompt content
        assertTrue("Should contain friendly assistant description", prompt.contains("friendly AR pet assistant"))
        assertTrue("Should contain conversation history", prompt.contains("Hello"))
        assertTrue("Should contain new user message", prompt.contains("Thank you"))
        assertFalse("Should not contain function call instructions", prompt.contains("FUNCTION_CALL"))
    }

    // Test conversation history management
    @Test
    fun `conversation history should be managed correctly`() {
        // Given: Start with empty history
        ContextManager.clearConversationHistory()
        assertEquals("Should start with empty history", 0, ContextManager.getHistorySize())

        // When: Add conversations
        ContextManager.addConversation("Hello", "Hi there")
        ContextManager.addConversation("How are you?", "I'm doing well")

        // Then: Verify history tracking
        assertEquals("Should track conversation history", 4, ContextManager.getHistorySize()) // 2 conversations = 4 messages
    }

    @Test
    fun `conversation history clear function should work properly`() {
        // Given: Have conversation history
        ContextManager.addConversation("Test1", "Response1")
        ContextManager.addConversation("Test2", "Response2")
        assertTrue("Should have history before clearing", ContextManager.getHistorySize() > 0)

        // When: Clear history
        ContextManager.clearConversationHistory()

        // Then: Verify clearing result
        assertEquals("Should have no history after clearing", 0, ContextManager.getHistorySize())
    }

    @Test
    fun `context manager should provide conversation summaries`() {
        // Given: Add some conversation history
        ContextManager.clearConversationHistory()
        ContextManager.addConversation("Test question", "Test answer")

        // When: Get conversation summary
        val summary = ContextManager.getConversationSummary()

        // Then: Verify summary content
        assertTrue("Should contain conversation info", summary.contains("Conversation"))
        assertTrue("Should contain message count", summary.contains("2")) // 1 conversation = 2 messages
    }

    // Test function detection logic
    @Test
    fun `function detection should correctly identify weather keywords`() {
        val promptManager = PromptManager()

        // Weather keyword tests
        assertTrue("Should detect weather keywords", promptManager.mightNeedFunctionCall("How is the weather today?"))
        assertTrue("Should detect temperature keywords", promptManager.mightNeedFunctionCall("What is the temperature now?"))
        assertTrue("Should detect rain keywords", promptManager.mightNeedFunctionCall("Will it rain?"))
        assertTrue("Should detect wind keywords", promptManager.mightNeedFunctionCall("How windy is it?"))
    }

    @Test
    fun `function detection should correctly identify SMS keywords`() {
        val promptManager = PromptManager()

        // SMS keyword tests
        assertTrue("Should detect message keywords", promptManager.mightNeedFunctionCall("Do I have new messages?"))
        assertTrue("Should detect unread keywords", promptManager.mightNeedFunctionCall("Any unread SMS?"))
        assertTrue("Should detect SMS keywords", promptManager.mightNeedFunctionCall("Check my messages"))
        assertTrue("Should detect msg keywords", promptManager.mightNeedFunctionCall("Read my msg"))
    }

    @Test
    fun `function detection should correctly identify location keywords`() {
        val promptManager = PromptManager()

        // Location keyword tests
        assertTrue("Should detect location keywords", promptManager.mightNeedFunctionCall("Where am I?"))
        assertTrue("Should detect current location keywords", promptManager.mightNeedFunctionCall("What is my location?"))
        assertTrue("Should detect position keywords", promptManager.mightNeedFunctionCall("My current position"))
        assertTrue("Should detect address keywords", promptManager.mightNeedFunctionCall("What's my address?"))
    }

    @Test
    fun `function detection should correctly identify news keywords`() {
        val promptManager = PromptManager()

        // News keyword tests
        assertTrue("Should detect news keywords", promptManager.mightNeedFunctionCall("Any news?"))
        assertTrue("Should detect latest news keywords", promptManager.mightNeedFunctionCall("What's the latest news?"))
        assertTrue("Should detect headline keywords", promptManager.mightNeedFunctionCall("Show me headlines"))
    }

    @Test
    fun `function detection should not trigger on normal conversation`() {
        val promptManager = PromptManager()

        // Normal conversation should not trigger functions
        assertFalse("Normal conversation should not trigger functions", promptManager.mightNeedFunctionCall("How are you?"))
        assertFalse("Casual chat should not trigger functions", promptManager.mightNeedFunctionCall("I feel good today"))
        assertFalse("Greetings should not trigger functions", promptManager.mightNeedFunctionCall("Hello there"))
        assertFalse("Thank you should not trigger functions", promptManager.mightNeedFunctionCall("Thank you"))
    }

    // Test FunctionCallManager logic
    @Test
    fun `FunctionCallManager should detect function needs correctly`() {
        // Test weather detection
        assertTrue("Should need function for weather", FunctionCallManager.mightNeedFunctionCall("How is the weather?"))
        
        // Test SMS detection
        assertTrue("Should need function for SMS", FunctionCallManager.mightNeedFunctionCall("Check my messages"))
        
        // Test location detection
        assertTrue("Should need function for location", FunctionCallManager.mightNeedFunctionCall("Where am I?"))
        
        // Test normal conversation
        assertFalse("Should not need function for normal chat", FunctionCallManager.mightNeedFunctionCall("Hello"))
    }

    @Test
    fun `FunctionCallManager should determine function types correctly`() {
        assertEquals("Should identify weather type", "weather", FunctionCallManager.getFunctionType("get_current_weather"))
        assertEquals("Should identify SMS type", "SMS", FunctionCallManager.getFunctionType("read_unread_messages"))
        assertEquals("Should identify location type", "location", FunctionCallManager.getFunctionType("get_current_location"))
        assertEquals("Should identify function type for unknown", "function", FunctionCallManager.getFunctionType("unknown_function"))
    }

    @Test
    fun `FunctionCallManager should generate appropriate fallback responses`() {
        val weatherResult = "Sunny, 25°C"
        val smsResult = "You have 2 new messages"
        val locationResult = "You are in Taipei"

        val weatherResponse = FunctionCallManager.generateFallbackResponse("weather", weatherResult)
        val smsResponse = FunctionCallManager.generateFallbackResponse("SMS", smsResult)
        val locationResponse = FunctionCallManager.generateFallbackResponse("location", locationResult)

        assertTrue("Weather response should contain result", weatherResponse.contains(weatherResult))
        assertTrue("SMS response should contain result", smsResponse.contains(smsResult))
        assertTrue("Location response should contain result", locationResponse.contains(locationResult))
    }

    // Test string processing and edge cases
    @Test
    fun `extractFunctionCall should handle multiple function calls in response`() {
        // Given: Response with multiple function calls (should take first one)
        val watsonResponse = """
            FUNCTION_CALL: {"name": "get_weather", "arguments": {}}
            FUNCTION_CALL: {"name": "read_messages", "arguments": {}}
        """.trimIndent()

        // When: Extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Should extract first function call
        assertNotNull("Should extract first function call", functionCall)
        assertEquals("Should extract first function", "get_weather", functionCall!!.name)
    }

    @Test
    fun `extractFunctionCall should handle function call with nested braces`() {
        // Given: Function call with nested JSON structures
        val watsonResponse = """
            FUNCTION_CALL: {"name": "search_data", "arguments": {"filter": {"type": "news", "limit": 5}}}
        """.trimIndent()

        // When: Extract function call
        val functionCall = FunctionCallManager.extractFunctionCall(watsonResponse)

        // Then: Should handle nested structures
        assertNotNull("Should handle nested JSON", functionCall)
        assertEquals("Function name should be correct", "search_data", functionCall!!.name)
        assertTrue("Should contain nested structure", functionCall.arguments.contains("news"))
    }

    @Test
    fun `context string generation should handle empty history`() {
        // Given: Empty conversation history
        ContextManager.clearConversationHistory()

        // When: Get context string
        val contextString = ContextManager.getContextString()

        // Then: Should provide appropriate default
        assertTrue("Should indicate no previous conversations", contextString.contains("None"))
    }

    @Test
    fun `context string generation should limit length appropriately`() {
        // Given: Add many conversations to test length limiting
        ContextManager.clearConversationHistory()
        repeat(20) { i ->
            ContextManager.addConversation("Question $i", "Answer $i with some longer text to test length limits")
        }

        // When: Get context string
        val contextString = ContextManager.getContextString()

        // Then: Should be reasonable length
        assertTrue("Context should not be empty", contextString.isNotEmpty())
        assertTrue("Context should not be excessively long", contextString.length < 5000)
    }
}

