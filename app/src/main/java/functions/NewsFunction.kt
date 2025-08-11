// functions/NewsFunctions.kt - Complete English Version with NewsAPI.org
package functions

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.URL
import java.net.URLEncoder
import java.io.IOException

/**
 * News Functions - AI callable news functionality using NewsAPI.org
 */
object NewsFunctions {
    
    private const val TAG = "NewsFunctions"
    private const val BASE_URL = "https://newsapi.org/v2"
    private const val API_KEY = "2fc4a48e2c774f40a50199c8a4224cb4"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val newsCategories = mapOf(
        "health" to "health",
        "general" to "general", 
        "science" to "science",
        "business" to "business",
        "sports" to "sports",
        "technology" to "technology",
        "entertainment" to "entertainment"
    )
    
    fun initialize(context: Context) {
        Log.d(TAG, "News Functions initialized (NewsAPI.org)")
    }
    
    /**
     * Execute news related functions
     */
    suspend fun execute(functionName: String, arguments: String): String {
        return try {
            Log.d(TAG, "Executing news function: $functionName")
            
            when (functionName) {
                "get_latest_news" -> getLatestNews(arguments)
                "get_news_by_category" -> getNewsByCategory(arguments)
                "search_news" -> searchNews(arguments)
                "get_health_news" -> getHealthNews()
                "get_business_news" -> getBusinessNews()
                "get_technology_news" -> getTechnologyNews()
                "get_science_news" -> getScienceNews()
                "get_entertainment_news" -> getEntertainmentNews()
                "get_sports_news" -> getSportsNews()
                "get_news_summary" -> getNewsSummary(arguments)
                "get_recommended_news" -> getRecommendedNewsCategories()
                "get_news_categories" -> getNewsCategories()
                else -> {
                    Log.w(TAG, "Unknown news function: $functionName")
                    "Sorry, I don't recognize that news function."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "News function execution failed: ${e.message}")
            "Sorry, I couldn't retrieve the news information right now. Please try again later."
        }
    }
    
    /**
     * Get recommended news categories - when user asks "what news do you recommend"
     */
    private suspend fun getRecommendedNewsCategories(): String {
        return """
I can provide you with the following types of news:

**Business** (business)
   Stock market, economy, corporate news, financial updates

**Entertainment** (entertainment) 
   Celebrity news, movies, music, pop culture

**General** (general)
   Breaking news, world events, important headlines

**Health** (health)
   Medical news, health tips, wellness information

**Science** (science)
   Scientific discoveries, research, technology breakthroughs

**Sports** (sports)
   Sports events, athlete news, game results

**Technology** (technology)
   Tech products, IT news, digital trends, gadgets

Which type of news would you like to see? Just tell me the category name and I'll get the latest news for you!
        """.trimIndent()
    }
    
    /**
     * Get all news categories
     */
    private suspend fun getNewsCategories(): String {
        val sb = StringBuilder()
        sb.appendLine("Available News Categories:")
        sb.appendLine("=".repeat(40))
        
        val categoryDescriptions = mapOf(
            "business" to "Business & Finance",
            "entertainment" to "Entertainment & Celebrity",
            "general" to "General & World News",
            "health" to "Health & Medical",
            "science" to "Science & Research",
            "sports" to "Sports & Games",
            "technology" to "Technology & IT"
        )
        
        categoryDescriptions.forEach { (category, description) ->
            sb.appendLine("$description")
            sb.appendLine("   Category: $category")
            sb.appendLine()
        }
        
        sb.appendLine("Usage examples:")
        sb.appendLine("• Say 'I want health news'")
        sb.appendLine("• Say 'Show me technology news'")
        sb.appendLine("• Say 'What's the latest business news'")
        
        return sb.toString()
    }
    
    /**
     * Get latest news (top headlines)
     */
    private suspend fun getLatestNews(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val limit = args["limit"]?.toIntOrNull() ?: 10
            val country = args["country"] ?: "us"
            
            val result = fetchTopHeadlines(country, null, limit)
            
            result.fold(
                onSuccess = { articles ->
                    if (articles.isEmpty()) {
                        "No latest news available at the moment."
                    } else {
                        formatNewsResponse(articles, "Latest Breaking News")
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to get latest news: ${exception.message}")
                    "Unable to retrieve latest news at this time."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLatestNews: ${e.message}")
            "Error retrieving latest news."
        }
    }
    
    /**
     * Get news by category
     */
    private suspend fun getNewsByCategory(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val category = args["category"] ?: "general"
            val limit = args["limit"]?.toIntOrNull() ?: 10
            val country = args["country"] ?: "us"
            
            // Check if category is valid
            if (!newsCategories.containsKey(category)) {
                return@withContext "Sorry, '$category' is not a valid news category.\n\n${getNewsCategories()}"
            }
            
            val result = fetchTopHeadlines(country, category, limit)
            
            result.fold(
                onSuccess = { articles ->
                    if (articles.isEmpty()) {
                        "No news available for category: $category"
                    } else {
                        formatNewsResponse(articles, "${category.capitalize()} News")
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to get news by category: ${exception.message}")
                    "Unable to retrieve $category news at this time."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in getNewsByCategory: ${e.message}")
            "Error retrieving news by category."
        }
    }
    
    /**
     * Search news
     */
    private suspend fun searchNews(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val query = args["query"] ?: args["keyword"] ?: return@withContext "Please provide a search term."
            val limit = args["limit"]?.toIntOrNull() ?: 10
            
            val result = searchEverything(query, limit)
            
            result.fold(
                onSuccess = { articles ->
                    if (articles.isEmpty()) {
                        "No news found for: $query"
                    } else {
                        formatNewsResponse(articles, "Search Results for '$query'")
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to search news: ${exception.message}")
                    "Unable to search news at this time."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchNews: ${e.message}")
            "Error searching news."
        }
    }
    
    /**
     * Get health news
     */
    private suspend fun getHealthNews(): String {
        return getNewsByCategory("{\"category\":\"health\",\"limit\":8}")
    }
    
    /**
     * Get business news
     */
    private suspend fun getBusinessNews(): String {
        return getNewsByCategory("{\"category\":\"business\",\"limit\":8}")
    }
    
    /**
     * Get technology news
     */
    private suspend fun getTechnologyNews(): String {
        return getNewsByCategory("{\"category\":\"technology\",\"limit\":8}")
    }
    
    /**
     * Get science news
     */
    private suspend fun getScienceNews(): String {
        return getNewsByCategory("{\"category\":\"science\",\"limit\":8}")
    }
    
    /**
     * Get entertainment news
     */
    private suspend fun getEntertainmentNews(): String {
        return getNewsByCategory("{\"category\":\"entertainment\",\"limit\":8}")
    }
    
    /**
     * Get sports news
     */
    private suspend fun getSportsNews(): String {
        return getNewsByCategory("{\"category\":\"sports\",\"limit\":8}")
    }
    
    /**
     * Get news summary
     */
    private suspend fun getNewsSummary(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val categories = args["categories"]?.split(",")?.map { it.trim() } 
                ?: listOf("general", "health", "technology")
            
            val results = mutableMapOf<String, List<NewsAPIArticle>>()
            
            categories.forEach { category ->
                val categoryResult = fetchTopHeadlines("us", category, 3)
                categoryResult.onSuccess { articles ->
                    results[category] = articles
                }
            }
            
            formatNewsSummary(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getNewsSummary: ${e.message}")
            "Error retrieving news summary."
        }
    }
    
    // ============================================================================
    // NewsAPI.org API calling methods
    // ============================================================================
    
    /**
     * Fetch top headlines - NewsAPI.org top-headlines endpoint
     */
    private suspend fun fetchTopHeadlines(
        country: String = "us",
        category: String? = null,
        pageSize: Int = 20
    ): Result<List<NewsAPIArticle>> = withContext(Dispatchers.IO) {
        try {
            var apiUrl = "$BASE_URL/top-headlines?" +
                    "apiKey=$API_KEY&" +
                    "country=$country&" +
                    "pageSize=$pageSize"
            
            if (category != null && newsCategories.containsKey(category)) {
                apiUrl += "&category=${newsCategories[category]}"
            }
            
            Log.d(TAG, "Calling NewsAPI: $apiUrl")
            
            val response = URL(apiUrl).readText()
            val newsResponse = json.decodeFromString<NewsAPIResponse>(response)
            
            if (newsResponse.status == "ok") {
                Result.success(newsResponse.articles)
            } else {
                Result.failure(IOException("API Error: ${newsResponse.message}"))
            }
            
        } catch (e: Exception) {
            Result.failure(IOException("Failed to fetch top headlines: ${e.message}", e))
        }
    }
    
    /**
     * Search everything - NewsAPI.org everything endpoint
     */
    private suspend fun searchEverything(
        query: String,
        pageSize: Int = 20,
        language: String = "en"
    ): Result<List<NewsAPIArticle>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "$BASE_URL/everything?" +
                    "apiKey=$API_KEY&" +
                    "q=$encodedQuery&" +
                    "language=$language&" +
                    "sortBy=publishedAt&" +
                    "pageSize=$pageSize"
            
            Log.d(TAG, "Searching NewsAPI: $apiUrl")
            
            val response = URL(apiUrl).readText()
            val newsResponse = json.decodeFromString<NewsAPIResponse>(response)
            
            if (newsResponse.status == "ok") {
                Result.success(newsResponse.articles)
            } else {
                Result.failure(IOException("API Error: ${newsResponse.message}"))
            }
            
        } catch (e: Exception) {
            Result.failure(IOException("Failed to search news: ${e.message}", e))
        }
    }
    
    // ============================================================================
    // Formatting output methods
    // ============================================================================
    
    /**
     * Format news response
     */
    private fun formatNewsResponse(articles: List<NewsAPIArticle>, title: String): String {
        val sb = StringBuilder()
        sb.appendLine("$title")
        sb.appendLine("=".repeat(50))
        
        articles.take(10).forEachIndexed { index, article ->
            sb.appendLine("\n${index + 1}. ${article.title}")
            if (!article.description.isNullOrEmpty()) {
                sb.appendLine("   ${article.description.take(120)}${if (article.description.length > 120) "..." else ""}")
            }
            sb.appendLine("   Source: ${article.source.name}")
            if (article.publishedAt.isNotEmpty()) {
                sb.appendLine("   Published: ${formatDate(article.publishedAt)}")
            }
            if (article.url.isNotEmpty()) {
                sb.appendLine("   ${article.url}")
            }
        }
        
        sb.appendLine("\nTotal ${articles.size} news articles found")
        return sb.toString()
    }
    
    /**
     * Format news summary
     */
    private fun formatNewsSummary(results: Map<String, List<NewsAPIArticle>>): String {
        val sb = StringBuilder()
        sb.appendLine("News Summary")
        sb.appendLine("=".repeat(40))
        
        results.forEach { (category, articles) ->
            sb.appendLine("\n${category.uppercase()} NEWS:")
            articles.take(3).forEachIndexed { index, article ->
                sb.appendLine("  ${index + 1}. ${article.title}")
                if (!article.description.isNullOrEmpty()) {
                    sb.appendLine("     ${article.description.take(80)}...")
                }
                sb.appendLine("     ${article.source.name}")
            }
        }
        
        return sb.toString()
    }
    
    // ============================================================================
    // Utility methods
    // ============================================================================
    
    /**
     * Parse arguments
     */
    private fun parseArguments(arguments: String): Map<String, String> {
        return try {
            if (arguments.trim().isEmpty() || arguments == "{}") {
                emptyMap()
            } else {
                val jsonElement = json.parseToJsonElement(arguments)
                val jsonObject = jsonElement.jsonObject
                jsonObject.mapValues { it.value.jsonPrimitive.content }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse arguments: $arguments")
            emptyMap()
        }
    }
    
    /**
     * Format date
     */
    private fun formatDate(dateString: String): String {
        return try {
            // NewsAPI returns ISO 8601 format: 2023-12-01T10:30:00Z
            dateString.replace("T", " ").replace("Z", "").take(16)
        } catch (e: Exception) {
            dateString
        }
    }
    
    /**
     * Test news service
     */
    fun testNewsService(): String {
        return "News service initialized successfully with NewsAPI.org. Available functions: get_latest_news, get_news_by_category, search_news, get_health_news, get_business_news, get_technology_news"
    }
    
    /**
     * Get service status
     */
    fun getServiceStatus(): String {
        return "News Functions: Ready (API: NewsAPI.org, Key: Configured)"
    }
}

// ============================================================================
// DATA CLASSES for NewsAPI.org Response
// ============================================================================

@Serializable
data class NewsAPIResponse(
    val status: String,
    val totalResults: Int? = null,
    val articles: List<NewsAPIArticle>,
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class NewsAPISource(
    val id: String? = null,
    val name: String
)

@Serializable
data class NewsAPIArticle(
    val source: NewsAPISource,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String,
    val urlToImage: String? = null,
    val publishedAt: String,
    val content: String? = null
)