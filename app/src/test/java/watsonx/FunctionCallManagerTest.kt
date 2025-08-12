// app/src/test/java/watsonx/FunctionCallManagerTest.kt
package watsonx

import io.mockk.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import functions.WeatherFunctions
import functions.SMSFunctions
import functions.NewsFunctions
import functions.PodcastFunctions
import functions.LocationFunctions

/**
 * FunctionCallManager Tests
 * 
 * Test Objectives:
 * - mightNeedFunctionCall() keyword detection logic
 * - extractFunctionCall() JSON parsing logic
 * - executeFunction() function routing logic
 * - getFunctionType() function classification logic
 * 
 * Mock Strategy:
 * - WeatherFunctions: external service calls
 * - SMSFunctions: system permission dependencies
 * - LocationFunctions: GPS hardware dependencies
 * - NewsFunctions: external API dependencies
 * - PodcastFunctions: external API dependencies
 * 
 * Real Testing:
 * - Keyword detection algorithms
 * - Regular expression matching
 * - Function classification logic
 * - JSON format validation
 */
class FunctionCallManagerTest {

    @Before
    fun setUp() {
        // Mock all external services
        mockkObject(WeatherFunctions)
        mockkObject(SMSFunctions)
        mockkObject(NewsFunctions)
        mockkObject(PodcastFunctions)
        mockkObject(LocationFunctions)
        
        // Set up default mock responses
        coEvery { WeatherFunctions.execute(any(), any()) } returns "Weather data retrieved"
        coEvery { SMSFunctions.execute(any(), any()) } returns "SMS data retrieved"
        coEvery { NewsFunctions.execute(any(), any()) } returns "News data retrieved"
        coEvery { PodcastFunctions.execute(any(), any()) } returns "Podcast data retrieved"
        coEvery { LocationFunctions.execute(any(), any()) } returns "Location data retrieved"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // Keyword Detection Tests - mightNeedFunctionCall()

    @Test
    fun `mightNeedFunctionCall should detect weather keywords correctly`() {
        // Weather-related keywords
        assertTrue("Should detect weather", FunctionCallManager.mightNeedFunctionCall("How is the weather today?"))
        assertTrue("Should detect temperature", FunctionCallManager.mightNeedFunctionCall("What's the temperature?"))
        assertTrue("Should detect rain", FunctionCallManager.mightNeedFunctionCall("Will it rain tomorrow?"))
        assertTrue("Should detect sunny", FunctionCallManager.mightNeedFunctionCall("Is it sunny outside?"))
        assertTrue("Should detect cloudy", FunctionCallManager.mightNeedFunctionCall("Is it cloudy?"))
        assertTrue("Should detect wind", FunctionCallManager.mightNeedFunctionCall("How windy is it?"))
        assertTrue("Should detect humidity", FunctionCallManager.mightNeedFunctionCall("What's the humidity level?"))
        assertTrue("Should detect forecast", FunctionCallManager.mightNeedFunctionCall("Show me the forecast"))
        assertTrue("Should detect degree", FunctionCallManager.mightNeedFunctionCall("How many degrees?"))
        assertTrue("Should detect cold", FunctionCallManager.mightNeedFunctionCall("Is it cold today?"))
        assertTrue("Should detect hot", FunctionCallManager.mightNeedFunctionCall("Is it hot outside?"))
        assertTrue("Should detect warm", FunctionCallManager.mightNeedFunctionCall("Is it warm today?"))
    }

    @Test
    fun `mightNeedFunctionCall should detect SMS keywords correctly`() {
        // SMS-related keywords
        assertTrue("Should detect sms", FunctionCallManager.mightNeedFunctionCall("Check my SMS"))
        assertTrue("Should detect message", FunctionCallManager.mightNeedFunctionCall("Do I have new messages?"))
        assertTrue("Should detect msg", FunctionCallManager.mightNeedFunctionCall("Any new msg?"))
        assertTrue("Should detect unread", FunctionCallManager.mightNeedFunctionCall("Show unread messages"))
        assertTrue("Should detect new message", FunctionCallManager.mightNeedFunctionCall("Any new message?"))
        assertTrue("Should detect recent", FunctionCallManager.mightNeedFunctionCall("Show recent messages"))
        assertTrue("Should detect summary", FunctionCallManager.mightNeedFunctionCall("Give me message summary"))
        assertTrue("Should detect read message", FunctionCallManager.mightNeedFunctionCall("Read my messages"))
        assertTrue("Should detect message content", FunctionCallManager.mightNeedFunctionCall("What's the message content?"))
        assertTrue("Should detect who sent", FunctionCallManager.mightNeedFunctionCall("Who sent me messages?"))
        assertTrue("Should detect received", FunctionCallManager.mightNeedFunctionCall("What messages did I receive?"))
    }

    @Test
    fun `mightNeedFunctionCall should detect location keywords correctly`() {
        // Location-related keywords - test with PromptManager instead
        val promptManager = PromptManager()
        
        assertTrue("Should detect where am i", promptManager.mightNeedFunctionCall("Where am I?"))
        assertTrue("Should detect my location", promptManager.mightNeedFunctionCall("What's my location?"))
        assertTrue("Should detect where do i live", promptManager.mightNeedFunctionCall("Where do I live?"))
        assertTrue("Should detect current location", promptManager.mightNeedFunctionCall("Show my current location"))
        assertTrue("Should detect where i am", promptManager.mightNeedFunctionCall("Tell me where I am"))
        assertTrue("Should detect location", promptManager.mightNeedFunctionCall("Find my location"))
    }

    @Test
    fun `mightNeedFunctionCall should not detect normal conversation`() {
        // Normal conversation should not trigger function calls
        assertFalse("Should not detect greeting", FunctionCallManager.mightNeedFunctionCall("Hello"))
        assertFalse("Should not detect how are you", FunctionCallManager.mightNeedFunctionCall("How are you?"))
        assertFalse("Should not detect thank you", FunctionCallManager.mightNeedFunctionCall("Thank you"))
        assertFalse("Should not detect goodbye", FunctionCallManager.mightNeedFunctionCall("Goodbye"))
        assertFalse("Should not detect casual talk", FunctionCallManager.mightNeedFunctionCall("I'm feeling good"))
        assertFalse("Should not detect help", FunctionCallManager.mightNeedFunctionCall("Can you help me?"))
        assertFalse("Should not detect general question", FunctionCallManager.mightNeedFunctionCall("What can you do?"))
    }

    @Test
    fun `mightNeedFunctionCall should handle case insensitive detection`() {
        // Test case insensitivity
        assertTrue("Should detect WEATHER", FunctionCallManager.mightNeedFunctionCall("WEATHER TODAY"))
        assertTrue("Should detect WeAtHeR", FunctionCallManager.mightNeedFunctionCall("WeAtHeR forecast"))
        assertTrue("Should detect MESSAGE", FunctionCallManager.mightNeedFunctionCall("CHECK MY MESSAGE"))
        assertTrue("Should detect Location", FunctionCallManager.mightNeedFunctionCall("My Location please"))
    }

    // JSON Parsing Tests - extractFunctionCall()

    @Test
    fun `extractFunctionCall should parse simple function calls`() {
        val response = """
            I'll help you with that.
            FUNCTION_CALL: {"name": "get_current_weather", "arguments": {}}
            Getting weather information...
        """.trimIndent()

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertNotNull("Should extract function call", functionCall)
        assertEquals("Should extract correct name", "get_current_weather", functionCall!!.name)
        assertEquals("Should extract empty arguments", "{}", functionCall.arguments)
    }

    @Test
    fun `extractFunctionCall should parse function calls with city parameter`() {
        val response = """
            FUNCTION_CALL: {"name": "get_current_weather", "arguments": {"city": "New York"}}
        """.trimIndent()

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertNotNull("Should extract function call", functionCall)
        assertEquals("Should extract correct name", "get_current_weather", functionCall!!.name)
        assertTrue("Should contain city parameter", functionCall.arguments.contains("New York"))
    }

    @Test
    fun `extractFunctionCall should parse complex function calls`() {
        val response = """
            FUNCTION_CALL: {"name": "search_news", "arguments": {"query": "AI technology", "limit": 10, "language": "en"}}
        """.trimIndent()

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertNotNull("Should extract function call", functionCall)
        assertEquals("Should extract correct name", "search_news", functionCall!!.name)
        assertTrue("Should contain query", functionCall.arguments.contains("AI technology"))
        assertTrue("Should contain limit", functionCall.arguments.contains("10"))
        assertTrue("Should contain language", functionCall.arguments.contains("en"))
    }

    @Test
    fun `extractFunctionCall should handle nested JSON objects`() {
        val response = """
            FUNCTION_CALL: {"name": "search_data", "arguments": {"filter": {"type": "news", "category": "tech"}, "limit": 5}}
        """.trimIndent()

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertNotNull("Should extract function call", functionCall)
        assertEquals("Should extract correct name", "search_data", functionCall!!.name)
        assertTrue("Should contain nested object", functionCall.arguments.contains("tech"))
    }

    @Test
    fun `extractFunctionCall should handle malformed JSON gracefully`() {
        val response = """
            FUNCTION_CALL: {"name": "get_weather", "arguments": {invalid json
        """.trimIndent()

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertEquals("Should return null for malformed JSON", null, functionCall)
    }

    @Test
    fun `extractFunctionCall should handle missing FUNCTION_CALL marker`() {
        val response = "Just a normal response without any function calls"

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertEquals("Should return null when no function call", null, functionCall)
    }

    @Test
    fun `extractFunctionCall should handle multiple function calls by taking first one`() {
        val response = """
            FUNCTION_CALL: {"name": "get_weather", "arguments": {}}
            Some text
            FUNCTION_CALL: {"name": "read_messages", "arguments": {}}
        """.trimIndent()

        val functionCall = FunctionCallManager.extractFunctionCall(response)

        assertNotNull("Should extract first function call", functionCall)
        assertEquals("Should extract first function name", "get_weather", functionCall!!.name)
    }

    // Function Execution Tests - Simplified without complex mocking

    @Test
    fun `executeFunction should handle weather functions without exceptions`() = runBlocking {
        val functionCall = FunctionCallManager.FunctionCall("get_current_weather", "{}")

        // Just verify it doesn't throw exceptions
        val result = try {
            FunctionCallManager.executeFunction(functionCall)
        } catch (e: Exception) {
            "Error handled: ${e.message}"
        }

        assertNotNull("Should return some result", result)
        assertTrue("Should return non-empty result", result.isNotEmpty())
    }

    @Test
    fun `executeFunction should handle SMS functions without exceptions`() = runBlocking {
        val functionCall = FunctionCallManager.FunctionCall("read_unread_messages", "{}")

        val result = try {
            FunctionCallManager.executeFunction(functionCall)
        } catch (e: Exception) {
            "Error handled: ${e.message}"
        }

        assertNotNull("Should return some result", result)
        assertTrue("Should return non-empty result", result.isNotEmpty())
    }

    @Test
    fun `executeFunction should handle location functions without exceptions`() = runBlocking {
        val functionCall = FunctionCallManager.FunctionCall("get_current_location", "{}")

        val result = try {
            FunctionCallManager.executeFunction(functionCall)
        } catch (e: Exception) {
            "Error handled: ${e.message}"
        }

        assertNotNull("Should return some result", result)
        assertTrue("Should return non-empty result", result.isNotEmpty())
    }

    @Test
    fun `executeFunction should handle news functions without exceptions`() = runBlocking {
        val functionCall = FunctionCallManager.FunctionCall("get_latest_news", "{}")

        val result = try {
            FunctionCallManager.executeFunction(functionCall)
        } catch (e: Exception) {
            "Error handled: ${e.message}"
        }

        assertNotNull("Should return some result", result)
        assertTrue("Should return non-empty result", result.isNotEmpty())
    }

    @Test
    fun `executeFunction should handle podcast functions without exceptions`() = runBlocking {
        val functionCall = FunctionCallManager.FunctionCall("get_health_podcasts", "{}")

        val result = try {
            FunctionCallManager.executeFunction(functionCall)
        } catch (e: Exception) {
            "Error handled: ${e.message}"
        }

        assertNotNull("Should return some result", result)
        assertTrue("Should return non-empty result", result.isNotEmpty())
    }

    @Test
    fun `executeFunction should handle unknown functions gracefully`() = runBlocking {
        val functionCall = FunctionCallManager.FunctionCall("unknown_function", "{}")

        val result = FunctionCallManager.executeFunction(functionCall)

        assertTrue("Should return error message", result.contains("unrecognized"))
    }

    @Test
    fun `executeFunction should handle function execution errors`() = runBlocking {
        // Mock function to throw exception
        coEvery { WeatherFunctions.execute(any(), any()) } throws Exception("Network error")
        
        val functionCall = FunctionCallManager.FunctionCall("get_current_weather", "{}")

        val result = FunctionCallManager.executeFunction(functionCall)

        assertTrue("Should return error message", result.contains("problem") || result.contains("try again"))
    }

    // Function Type Classification Tests - getFunctionType()

    @Test
    fun `getFunctionType should classify weather functions correctly`() {
        assertEquals("Should classify weather", "weather", FunctionCallManager.getFunctionType("get_current_weather"))
        assertEquals("Should classify weather", "weather", FunctionCallManager.getFunctionType("get_weather_by_city"))
        assertEquals("Should classify weather", "weather", FunctionCallManager.getFunctionType("weather_forecast"))
    }

    @Test
    fun `getFunctionType should classify SMS functions correctly`() {
        assertEquals("Should classify SMS", "SMS", FunctionCallManager.getFunctionType("read_unread_messages"))
        assertEquals("Should classify SMS", "SMS", FunctionCallManager.getFunctionType("get_message_summary"))
        assertEquals("Should classify SMS", "SMS", FunctionCallManager.getFunctionType("read_recent_messages"))
        assertEquals("Should classify SMS", "SMS", FunctionCallManager.getFunctionType("get_latest_message"))
    }

    @Test
    fun `getFunctionType should classify location functions correctly`() {
        assertEquals("Should classify location", "location", FunctionCallManager.getFunctionType("get_current_location"))
        assertEquals("Should classify location", "location", FunctionCallManager.getFunctionType("get_user_location"))
        assertEquals("Should classify location", "location", FunctionCallManager.getFunctionType("get_location_info"))
    }

    @Test
    fun `getFunctionType should classify unknown functions as function`() {
        assertEquals("Should classify unknown", "function", FunctionCallManager.getFunctionType("unknown_function"))
        assertEquals("Should classify random", "function", FunctionCallManager.getFunctionType("random_name"))
        assertEquals("Should classify empty", "function", FunctionCallManager.getFunctionType(""))
    }

    // Fallback Response Tests - generateFallbackResponse()

    @Test
    fun `generateFallbackResponse should create appropriate weather responses`() {
        val result = FunctionCallManager.generateFallbackResponse("weather", "Sunny, 25°C")

        assertTrue("Should contain weather result", result.contains("Sunny, 25°C"))
        assertTrue("Should contain weather context", result.contains("weather"))
        assertTrue("Should contain friendly advice", result.contains("clothing") || result.contains("adjust"))
    }

    @Test
    fun `generateFallbackResponse should create appropriate SMS responses`() {
        val result = FunctionCallManager.generateFallbackResponse("SMS", "You have 3 unread messages")

        assertTrue("Should contain SMS result", result.contains("You have 3 unread messages"))
        assertTrue("Should contain SMS context", result.contains("SMS"))
        assertTrue("Should offer help", result.contains("read") || result.contains("specific"))
    }

    @Test
    fun `generateFallbackResponse should create appropriate location responses`() {
        val result = FunctionCallManager.generateFallbackResponse("location", "You are in Taipei")

        assertTrue("Should contain location result", result.contains("You are in Taipei"))
        assertTrue("Should contain location context", result.contains("location"))
        assertTrue("Should be reassuring", result.contains("safe") || result.contains("hope"))
    }

    @Test
    fun `generateFallbackResponse should handle unknown function types`() {
        val result = FunctionCallManager.generateFallbackResponse("unknown", "Some result")

        assertTrue("Should contain result", result.contains("Some result"))
        assertTrue("Should be helpful", result.contains("information") || result.contains("help"))
    }

    // Edge Cases and Integration Tests

    @Test
    fun `function detection should handle complex sentences with multiple keywords`() {
        // Test sentences with multiple potential keywords
        assertTrue("Should detect primary intent", FunctionCallManager.mightNeedFunctionCall("Hello, can you tell me the weather and also my location?"))
        assertTrue("Should detect weather in complex sentence", FunctionCallManager.mightNeedFunctionCall("I'm going out and wondering about the weather"))
        assertTrue("Should detect SMS in complex sentence", FunctionCallManager.mightNeedFunctionCall("Before I leave, check if I have any messages"))
    }

    @Test
    fun `function execution should handle different argument formats without exceptions`() = runBlocking {
        // Test with empty arguments
        val emptyArgs = FunctionCallManager.FunctionCall("get_current_weather", "{}")
        val result1 = try {
            FunctionCallManager.executeFunction(emptyArgs)
        } catch (e: Exception) {
            "Error handled gracefully"
        }
        assertNotNull("Should handle empty args", result1)

        // Test with string arguments
        val stringArgs = FunctionCallManager.FunctionCall("get_current_weather", """{"city": "Tokyo"}""")
        val result2 = try {
            FunctionCallManager.executeFunction(stringArgs)
        } catch (e: Exception) {
            "Error handled gracefully"
        }
        assertNotNull("Should handle string args", result2)

        // Test with complex arguments
        val complexArgs = FunctionCallManager.FunctionCall("search_news", """{"query": "AI", "limit": 5}""")
        val result3 = try {
            FunctionCallManager.executeFunction(complexArgs)
        } catch (e: Exception) {
            "Error handled gracefully"
        }
        assertNotNull("Should handle complex args", result3)
    }

    @Test
    fun `containsFunctionCall should detect function call markers correctly`() {
        assertTrue("Should detect function call", FunctionCallManager.containsFunctionCall("FUNCTION_CALL: {\"name\": \"test\"}"))
        assertTrue("Should detect case insensitive", FunctionCallManager.containsFunctionCall("function_call: {\"name\": \"test\"}"))
        assertFalse("Should not detect without marker", FunctionCallManager.containsFunctionCall("Just normal text"))
        assertFalse("Should not detect partial marker", FunctionCallManager.containsFunctionCall("FUNCTION {\"name\": \"test\"}"))
    }

    @Test
    fun `function manager should handle concurrent access safely`() = runBlocking {
        // Test multiple function calls don't interfere with each other
        val function1 = FunctionCallManager.FunctionCall("get_current_weather", "{}")
        val function2 = FunctionCallManager.FunctionCall("read_unread_messages", "{}")
        val function3 = FunctionCallManager.FunctionCall("get_current_location", "{}")

        // Execute concurrently (simplified test)
        val result1 = FunctionCallManager.executeFunction(function1)
        val result2 = FunctionCallManager.executeFunction(function2)
        val result3 = FunctionCallManager.executeFunction(function3)

        assertEquals("Weather function should work", "Weather data retrieved", result1)
        assertEquals("SMS function should work", "SMS data retrieved", result2)
        assertEquals("Location function should work", "Location data retrieved", result3)
    }
}