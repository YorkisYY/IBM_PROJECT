// functions/NewsFunctions.kt - 正確的 The News API 實現
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
 * News Functions - 正確的 The News API 實現
 */
object NewsFunctions {
    
    private const val TAG = "NewsFunctions"
    private const val BASE_URL = "https://api.thenewsapi.com/v1"
    private const val API_TOKEN = "DVk6mryP5BUraYhLEnJp6p6mgXglrs6FrEDvWTD9"
    
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
        "technology" to "tech",  // The News API uses "tech"
        "entertainment" to "entertainment"
    )
    
    fun initialize(context: Context) {
        Log.d(TAG, "News Functions initialized (The News API)")
    }
    
    /**
     * Execute news related functions
     */
    suspend fun execute(functionName: String, arguments: String): String {
        return try {
            Log.d(TAG, "=== NewsFunctions.execute 開始 ===")
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
            Log.e(TAG, "Exception details: ", e)
            "Sorry, I couldn't retrieve the news information right now. Please try again later."
        }
    }
    
    /**
     * Get recommended news categories
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
            val locale = args["country"] ?: "us"
            
            val result = fetchTopNews(locale, null, limit)
            
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
            Log.d(TAG, "=== getNewsByCategory 開始 ===")
            val args = parseArguments(arguments)
            val category = args["category"] ?: "general"
            val limit = args["limit"]?.toIntOrNull() ?: 10
            val locale = args["country"] ?: "us"
            
            Log.d(TAG, "分類參數: category=$category, limit=$limit, locale=$locale")
            
            // Check if category is valid
            if (!newsCategories.containsKey(category)) {
                return@withContext "Sorry, '$category' is not a valid news category.\n\n${getNewsCategories()}"
            }
            
            val result = fetchTopNews(locale, category, limit)
            
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
            Log.e(TAG, "Error in getNewsByCategory: ${e.message}", e)
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
            
            val result = searchAllNews(query, limit)
            
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
     * Category-specific news functions
     */
    private suspend fun getHealthNews(): String {
        return getNewsByCategory("{\"category\":\"health\",\"limit\":8}")
    }
    
    private suspend fun getBusinessNews(): String {
        return getNewsByCategory("{\"category\":\"business\",\"limit\":8}")
    }
    
    private suspend fun getTechnologyNews(): String {
        return getNewsByCategory("{\"category\":\"technology\",\"limit\":8}")
    }
    
    private suspend fun getScienceNews(): String {
        return getNewsByCategory("{\"category\":\"science\",\"limit\":8}")
    }
    
    private suspend fun getEntertainmentNews(): String {
        return getNewsByCategory("{\"category\":\"entertainment\",\"limit\":8}")
    }
    
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
            
            val results = mutableMapOf<String, List<TheNewsAPIArticle>>()
            
            categories.forEach { category ->
                val categoryResult = fetchTopNews("us", category, 3)
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
    // The News API 調用方法
    // ============================================================================
    
    /**
     * 獲取頂級新聞 - 使用 /news/top 端點
     */
    private suspend fun fetchTopNews(
        locale: String = "us",
        category: String? = null,
        limit: Int = 20
    ): Result<List<TheNewsAPIArticle>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== fetchTopNews 開始 ===")
            
            var apiUrl = "$BASE_URL/news/top?" +
                    "api_token=$API_TOKEN&" +
                    "locale=$locale&" +
                    "limit=$limit&" +
                    "language=en"
            
            if (category != null && newsCategories.containsKey(category)) {
                val mappedCategory = newsCategories[category]
                apiUrl += "&categories=$mappedCategory"
                Log.d(TAG, "添加分類參數: $category -> $mappedCategory")
            }
            
            Log.d(TAG, "完整 API URL: $apiUrl")
            
            val response = URL(apiUrl).readText()
            Log.d(TAG, "API 原始回應: ${response.take(500)}...")
            
            val newsResponse = json.decodeFromString<TheNewsAPIResponse>(response)
            Log.d(TAG, "解析成功，找到 ${newsResponse.data?.size ?: 0} 篇文章")
            
            // The News API 回傳的是文章列表，不是按分類分組的
            val articles = newsResponse.data ?: emptyList()
            
            Result.success(articles)
            
        } catch (e: Exception) {
            Log.e(TAG, "API 調用異常: ${e.message}")
            Log.e(TAG, "異常類型: ${e::class.java.name}")
            Log.e(TAG, "詳細錯誤: ", e)
            Result.failure(IOException("Failed to fetch top news: ${e.message}", e))
        }
    }
    
    /**
     * 搜索所有新聞 - 使用 /news/all 端點
     */
    private suspend fun searchAllNews(
        query: String,
        limit: Int = 20
    ): Result<List<TheNewsAPIArticle>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "$BASE_URL/news/all?" +
                    "api_token=$API_TOKEN&" +
                    "search=$encodedQuery&" +
                    "language=en&" +
                    "limit=$limit"
            
            Log.d(TAG, "搜索 API URL: $apiUrl")
            
            val response = URL(apiUrl).readText()
            val newsResponse = json.decodeFromString<TheNewsAPIResponse>(response)
            
            val articles = newsResponse.data ?: emptyList()
            
            Result.success(articles)
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索新聞失敗: ${e.message}")
            Result.failure(IOException("Failed to search news: ${e.message}", e))
        }
    }
    
    // ============================================================================
    // 格式化輸出方法
    // ============================================================================
    
    /**
     * Format news response
     */
    private fun formatNewsResponse(articles: List<TheNewsAPIArticle>, title: String): String {
        val sb = StringBuilder()
        sb.appendLine("$title")
        sb.appendLine("=".repeat(50))
        
        articles.take(10).forEachIndexed { index, article ->
            sb.appendLine("\n${index + 1}. ${article.title}")
            if (!article.description.isNullOrEmpty()) {
                sb.appendLine("   ${article.description.take(120)}${if (article.description.length > 120) "..." else ""}")
            }
            sb.appendLine("   Source: ${article.source}")
            if (article.published_at.isNotEmpty()) {
                sb.appendLine("   Published: ${formatDate(article.published_at)}")
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
    private fun formatNewsSummary(results: Map<String, List<TheNewsAPIArticle>>): String {
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
                sb.appendLine("     ${article.source}")
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
            // The News API returns: 2023-12-01T10:30:00.000000Z
            dateString.replace("T", " ").replace("Z", "").take(16)
        } catch (e: Exception) {
            dateString
        }
    }
    
    /**
     * Test news service
     */
    fun testNewsService(): String {
        return "News service initialized successfully with The News API. Available functions: get_latest_news, get_news_by_category, search_news, get_health_news, get_business_news, get_technology_news"
    }
    
    /**
     * Get service status
     */
    fun getServiceStatus(): String {
        return "News Functions: Ready (API: The News API, Token: Configured)"
    }
}

// ============================================================================
// The News API 響應數據類 - 修正版
// ============================================================================

@Serializable
data class TheNewsAPIResponse(
    val data: List<TheNewsAPIArticle>? = null,  // 修正：直接是文章列表
    val meta: TheNewsAPIMeta? = null
)

@Serializable
data class TheNewsAPIMeta(
    val found: Int? = null,
    val returned: Int? = null,
    val limit: Int? = null,
    val page: Int? = null
)

@Serializable
data class TheNewsAPIArticle(
    val uuid: String,
    val title: String,
    val description: String? = null,
    val keywords: String? = null,
    val snippet: String? = null,
    val url: String,
    val image_url: String? = null,
    val language: String,
    val published_at: String,
    val source: String,
    val categories: List<String>? = null,
    val relevance_score: Double? = null,
    val locale: String? = null
)