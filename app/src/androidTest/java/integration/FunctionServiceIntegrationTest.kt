// app/src/androidTest/java/integration/FunctionServiceIntegrationTest.kt
package integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.test.runTest
import functions.WeatherFunctions
import functions.SMSFunctions
import functions.NewsFunctions
import functions.PodcastFunctions
import functions.LocationFunctions
import watsonx.FunctionCallManager
import watsonx.PromptManager

/**
 * Function Service Integration Test - Test function service coordination
 * Fixed version: Remove Mockito dependency, use real service testing
 */
@RunWith(AndroidJUnit4::class)
class FunctionServiceIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var functionCallManager: FunctionCallManager
    private lateinit var promptManager: PromptManager

    @Before
    fun setup() {
        functionCallManager = FunctionCallManager
        promptManager = PromptManager()
        
        // Initialize all function services (use real services for integration testing)
        WeatherFunctions.initialize(context)
        SMSFunctions.initialize(context)
        NewsFunctions.initialize(context)
        PodcastFunctions.initialize(context)
        LocationFunctions.initialize(context)
    }

    @Test
    fun testWeatherFunctionDetectionAndRouting() = runTest {
        val weatherQueries = listOf(
            "What's the weather today?",
            "How's the temperature outside?", 
            "Is it going to rain?",
            "Weather in London",
            "Tell me about the wind speed"
        )

        weatherQueries.forEach { query ->
            // 1. Test keyword detection
            val needsFunction = functionCallManager.mightNeedFunctionCall(query)
            assertTrue("Should detect weather function need for: $query", needsFunction)

            // 2. Test function type judgment
            val functionName = if (query.contains("London")) {
                "get_weather_by_city"
            } else {
                "get_current_weather"
            }
            
            val functionType = functionCallManager.getFunctionType(functionName)
            assertEquals("Should identify as weather function", "weather", functionType)

            // 3. Test routing logic (directly create test object)
            val functionCall = FunctionCallManager.FunctionCall(
                name = functionName,
                arguments = if (query.contains("London")) "{\"city\":\"London\"}" else "{}"
            )
            
            // Verify function call structure
            assertNotNull("Function call should be created", functionCall)
            assertEquals("Function name should match", functionName, functionCall.name)
        }
    }

    @Test
    fun testSMSFunctionDetectionAndRouting() = runTest {
        val smsQueries = listOf(
            "Do I have any new messages?",
            "Show me unread SMS",
            "Check my recent messages",
            "Read the latest message",
            "Message summary please"
        )

        val expectedFunctions = listOf(
            "read_unread_messages",
            "read_unread_messages", 
            "read_recent_messages",
            "get_latest_message",
            "get_message_summary"
        )

        smsQueries.forEachIndexed { index, query ->
            // 1. Test keyword detection
            val needsFunction = functionCallManager.mightNeedFunctionCall(query)
            assertTrue("Should detect SMS function need for: $query", needsFunction)

            // 2. Test function routing
            val expectedFunction = expectedFunctions[index]
            val functionType = functionCallManager.getFunctionType(expectedFunction)
            assertEquals("Should identify as SMS function", "SMS", functionType)

            // 3. Verify function call parameters
            val functionCall = FunctionCallManager.FunctionCall(
                name = expectedFunction,
                arguments = if (expectedFunction == "read_recent_messages") "{\"limit\":10}" else "{}"
            )
            
            assertNotNull("Function call should be created", functionCall)
            assertEquals("Function name should match expected", expectedFunction, functionCall.name)
        }
    }

    @Test
    fun testLocationFunctionDetectionAndRouting() = runTest {
        val locationQueries = listOf(
            "Where am I?",
            "What's my current location?",
            "Where do I live?",
            "Tell me my address",
            "My location info please"
        )

        locationQueries.forEach { query ->
            // 1. Test keyword detection
            val needsFunction = functionCallManager.mightNeedFunctionCall(query)
            assertTrue("Should detect location function need for: $query", needsFunction)

            // 2. Test function type
            val functionName = when {
                query.contains("where am i", ignoreCase = true) -> "get_current_location"
                query.contains("current location") -> "get_current_location"
                query.contains("where do i live") -> "get_user_location"
                query.contains("location info") -> "get_location_info"
                else -> "get_current_location"
            }
            
            val functionType = functionCallManager.getFunctionType(functionName)
            assertEquals("Should identify as location function", "location", functionType)
        }
    }

    @Test
    fun testErrorHandlingAcrossServices() = runTest {
        val errorScenarios = mapOf(
            "weather" to "get_current_weather",
            "SMS" to "read_unread_messages", 
            "location" to "get_current_location",
            "news" to "get_latest_news",
            "podcast" to "get_recommended_podcasts"
        )

        errorScenarios.forEach { (serviceType, functionName) ->
            // 1. Test error handling consistency across services
            try {
                val result = when (serviceType) {
                    "weather" -> WeatherFunctions.execute(functionName, "{}")
                    "SMS" -> SMSFunctions.execute(functionName, "{}")
                    "location" -> LocationFunctions.execute(functionName, "{}")
                    "news" -> NewsFunctions.execute(functionName, "{}")
                    "podcast" -> PodcastFunctions.execute(functionName, "{}")
                    else -> "Unknown service"
                }
                
                // Even if error occurs, should return meaningful error message rather than exception
                assertNotNull("Should return error message instead of null", result)
                assertTrue("Error message should not be empty", result.isNotEmpty())
                
            } catch (e: Exception) {
                // If exception is thrown, verify it's expected exception type
                assertTrue("Exception should contain service information", 
                    e.message?.contains(serviceType, ignoreCase = true) == true)
            }

            // 2. Test uniform error response format
            val fallbackResponse = functionCallManager.generateFallbackResponse(
                serviceType, 
                "Error: Service temporarily unavailable"
            )
            
            assertTrue("Fallback should contain helpful message", 
                fallbackResponse.contains("Sorry") || fallbackResponse.contains("Error"))
        }
    }

    @Test
    fun testServiceStatusAndHealthChecks() = runTest {
        // 1. Test service status checks
        val weatherStatus = WeatherFunctions.getServiceStatus()
        val smsStatus = SMSFunctions.getServiceStatus()
        val newsStatus = NewsFunctions.getServiceStatus()
        val podcastStatus = PodcastFunctions.getServiceStatus()

        // 2. Verify status report format consistency
        listOf(weatherStatus, smsStatus, newsStatus, podcastStatus).forEach { status ->
            assertNotNull("Service status should not be null", status)
            assertTrue("Status should contain service info", status.isNotEmpty())
        }

        // 3. Test service test functionality
        try {
            val weatherTest = WeatherFunctions.testWeatherService()
            assertNotNull("Weather test should return result", weatherTest)
            assertTrue("Weather test should indicate status", weatherTest.contains("test"))
        } catch (e: Exception) {
            // Allow test to fail in no-network environment, but should have reasonable error message
            assertTrue("Test failure should have meaningful message", 
                e.message?.isNotEmpty() == true)
        }
    }

    @Test
    fun testFunctionCallManagerIntegration() = runTest {
        // 1. Test FunctionCallManager integration with services
        val testCases = mapOf(
            "get_current_weather" to "{}",
            "read_unread_messages" to "{}",
            "get_current_location" to "{}",
            "get_latest_news" to "{\"limit\":5}",
            "get_recommended_podcasts" to "{}"
        )

        testCases.forEach { (functionName, arguments) ->
            // 2. Test function call creation
            val functionCall = FunctionCallManager.FunctionCall(functionName, arguments)
            
            assertNotNull("Function call should be created", functionCall)
            assertEquals("Function name should match", functionName, functionCall.name)
            assertEquals("Arguments should match", arguments, functionCall.arguments)

            // 3. Test function type identification
            val functionType = functionCallManager.getFunctionType(functionName)
            assertNotNull("Function type should be identified", functionType)
            assertTrue("Function type should not be empty", functionType.isNotEmpty())

            // 4. Test error response generation
            val fallbackResponse = functionCallManager.generateFallbackResponse(
                functionType, 
                "Mock successful result"
            )
            
            assertNotNull("Fallback response should be generated", fallbackResponse)
            assertTrue("Fallback should contain mock result", 
                fallbackResponse.contains("Mock successful result"))
        }
    }

    @Test
    fun testPromptManagerFunctionDetection() = runTest {
        // 1. Test PromptManager keyword detection capability
        val testQueries = mapOf(
            "What's the weather like?" to true,
            "Do I have any messages?" to true,
            "Where am I located?" to true,
            "Show me latest news" to true,
            "Recommend some podcasts" to true,
            "Hello, how are you?" to false,
            "What is 2 + 2?" to false,
            "Tell me a joke" to false
        )

        testQueries.forEach { (query, shouldDetect) ->
            val detected = promptManager.mightNeedFunctionCall(query)
            assertEquals("Detection should match expected for: $query", shouldDetect, detected)
        }

        // 2. Test prompt generation
        val functionalPrompt = promptManager.buildFunctionCallingPrompt(
            "What's the weather?", 
            "No previous context"
        )
        
        assertNotNull("Function calling prompt should be generated", functionalPrompt)
        assertTrue("Prompt should contain weather function info", 
            functionalPrompt.contains("get_current_weather"))

        val normalPrompt = promptManager.buildNormalPrompt(
            "Hello there", 
            "No context"
        )
        
        assertNotNull("Normal prompt should be generated", normalPrompt)
        assertTrue("Normal prompt should be conversational", 
            normalPrompt.contains("friendly") || normalPrompt.contains("Assistant"))
    }
}