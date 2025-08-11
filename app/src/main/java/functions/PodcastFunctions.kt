// functions/PodcastFunctions.kt - Complete English Version with iTunes API
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
 * Podcast Functions - AI callable podcast functionality using iTunes API
 */
object PodcastFunctions {
    
    private const val TAG = "PodcastFunctions"
    private const val ITUNES_BASE_URL = "https://itunes.apple.com"
    private const val SEARCH_ENDPOINT = "$ITUNES_BASE_URL/search"
    private const val LOOKUP_ENDPOINT = "$ITUNES_BASE_URL/lookup"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // iTunes podcast genre IDs for elderly-friendly content
    private val podcastGenres = mapOf(
        "health_fitness" to 1512,      // Health & Fitness
        "history" to 1462,             // History  
        "news" to 1489,                // News
        "education" to 1304,           // Education
        "religion_spirituality" to 1314, // Religion & Spirituality
        "science" to 1315,             // Science
        "society_culture" to 1324,     // Society & Culture
        "arts" to 1301,                // Arts
        "business" to 1321,            // Business
        "leisure" to 1502,             // Leisure
        "government" to 1314,          // Government
        "technology" to 1318           // Technology
    )
    
    fun initialize(context: Context) {
        Log.d(TAG, "Podcast Functions initialized")
    }
    
    /**
     * Execute podcast related functions
     */
    suspend fun execute(functionName: String, arguments: String): String {
        return try {
            Log.d(TAG, "Executing podcast function: $functionName")
            
            when (functionName) {
                "get_podcasts_by_category" -> getPodcastsByCategory(arguments)
                "search_podcasts" -> searchPodcasts(arguments)
                "get_health_podcasts" -> getHealthPodcasts()
                "get_history_podcasts" -> getHistoryPodcasts()
                "get_education_podcasts" -> getEducationPodcasts()
                "get_news_podcasts" -> getNewsPodcasts()
                "get_business_podcasts" -> getBusinessPodcasts()
                "get_science_podcasts" -> getSciencePodcasts()
                "get_technology_podcasts" -> getTechnologyPodcasts()
                "get_podcast_episodes" -> getPodcastEpisodes(arguments)
                "get_recommended_podcasts" -> getRecommendedPodcasts(arguments)
                "get_podcast_categories" -> getPodcastCategories()
                else -> {
                    Log.w(TAG, "Unknown podcast function: $functionName")
                    "Sorry, I don't recognize that podcast function."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Podcast function execution failed: ${e.message}")
            "Sorry, I couldn't retrieve the podcast information right now. Please try again later."
        }
    }
    
    /**
     * Get recommended podcast categories - when user asks "what podcasts do you recommend"
     */
    private suspend fun getRecommendedPodcasts(arguments: String): String {
        val args = parseArguments(arguments)
        val interests = args["interests"]?.split(",")?.map { it.trim() }
        
        // If no specific interests provided, show categories
        if (interests.isNullOrEmpty()) {
            return """
I can recommend podcasts from these categories:

**Health & Fitness** (health_fitness)
   Health tips, exercise guidance, wellness advice, medical insights

**History** (history) 
   Historical stories, documentaries, cultural heritage, past events

**Education** (education)
   Learning new skills, practical knowledge, tutorials, courses

**News** (news)
   Current events, news analysis, political discussions, world updates

**Religion & Spirituality** (religion_spirituality)
   Spiritual guidance, meditation, personal growth, faith discussions

**Science** (science)
   Scientific discoveries, research findings, nature documentaries

**Arts** (arts)
   Art appreciation, cultural discussions, creative processes

**Business** (business)
   Business insights, entrepreneurship, investment advice, career tips

**Technology** (technology)
   Tech news, gadget reviews, digital trends, innovation

**Society & Culture** (society_culture)
   Social issues, cultural trends, human interest stories

**Leisure** (leisure)
   Hobbies, entertainment, travel, lifestyle content

Which category interests you most? Just tell me the category name and I'll find great podcasts for you!
            """.trimIndent()
        } else {
            // Get personalized recommendations based on interests
            return getPersonalizedPodcasts(interests, args["limit"]?.toIntOrNull() ?: 15)
        }
    }
    
    /**
     * Get all podcast categories
     */
    private suspend fun getPodcastCategories(): String {
        val categories = getRecommendedPodcastCategories()
        val sb = StringBuilder()
        sb.appendLine("Available Podcast Categories")
        sb.appendLine("=".repeat(40))
        
        categories.forEach { category ->
            sb.appendLine("\n${category.name}")
            sb.appendLine("   ID: ${category.id}")
            sb.appendLine("   Category: ${category.english}")
            sb.appendLine("   ${category.description}")
        }
        
        sb.appendLine("\nUsage examples:")
        sb.appendLine("• Say 'I want health podcasts'")
        sb.appendLine("• Say 'Show me history podcasts'")
        sb.appendLine("• Say 'Find education podcasts'")
        
        return sb.toString()
    }
    
    /**
     * Get podcasts by category
     */
    private suspend fun getPodcastsByCategory(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val category = args["category"] ?: "education"
            val limit = args["limit"]?.toIntOrNull() ?: 15
            
            // Check if category is valid
            if (!podcastGenres.containsKey(category)) {
                return@withContext "Sorry, '$category' is not a valid podcast category.\n\n${getPodcastCategories()}"
            }
            
            val result = fetchPodcastsByCategory(category, limit)
            
            result.fold(
                onSuccess = { podcasts ->
                    if (podcasts.isEmpty()) {
                        "No podcasts available for category: $category"
                    } else {
                        formatPodcastResponse(podcasts, "${category.replace("_", " ").capitalize()} Podcasts")
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to get podcasts by category: ${exception.message}")
                    "Unable to retrieve $category podcasts at this time."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPodcastsByCategory: ${e.message}")
            "Error retrieving podcasts by category."
        }
    }
    
    /**
     * Search podcasts
     */
    private suspend fun searchPodcasts(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val query = args["query"] ?: args["keyword"] ?: return@withContext "Please provide a search term."
            val limit = args["limit"]?.toIntOrNull() ?: 15
            
            val result = searchPodcastsByKeyword(query, limit)
            
            result.fold(
                onSuccess = { podcasts ->
                    if (podcasts.isEmpty()) {
                        "No podcasts found for: $query"
                    } else {
                        formatPodcastResponse(podcasts, "Search Results for '$query'")
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to search podcasts: ${exception.message}")
                    "Unable to search podcasts at this time."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchPodcasts: ${e.message}")
            "Error searching podcasts."
        }
    }
    
    /**
     * Get health podcasts
     */
    private suspend fun getHealthPodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"health_fitness\",\"limit\":10}")
    }
    
    /**
     * Get history podcasts
     */
    private suspend fun getHistoryPodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"history\",\"limit\":10}")
    }
    
    /**
     * Get education podcasts
     */
    private suspend fun getEducationPodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"education\",\"limit\":10}")
    }
    
    /**
     * Get news podcasts
     */
    private suspend fun getNewsPodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"news\",\"limit\":10}")
    }
    
    /**
     * Get business podcasts
     */
    private suspend fun getBusinessPodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"business\",\"limit\":10}")
    }
    
    /**
     * Get science podcasts
     */
    private suspend fun getSciencePodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"science\",\"limit\":10}")
    }
    
    /**
     * Get technology podcasts
     */
    private suspend fun getTechnologyPodcasts(): String {
        return getPodcastsByCategory("{\"category\":\"technology\",\"limit\":10}")
    }
    
    /**
     * Get podcast episodes
     */
    private suspend fun getPodcastEpisodes(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = parseArguments(arguments)
            val feedUrl = args["feedUrl"] ?: args["feed_url"] ?: return@withContext "Please provide a podcast feed URL."
            val limit = args["limit"]?.toIntOrNull() ?: 10
            
            val result = fetchPodcastEpisodes(feedUrl, limit)
            
            result.fold(
                onSuccess = { episodes ->
                    if (episodes.isEmpty()) {
                        "No episodes available for this podcast."
                    } else {
                        formatEpisodesResponse(episodes, "Podcast Episodes")
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to get podcast episodes: ${exception.message}")
                    "Unable to retrieve podcast episodes at this time."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPodcastEpisodes: ${e.message}")
            "Error retrieving podcast episodes."
        }
    }
    
    /**
     * Get personalized podcast recommendations
     */
    private suspend fun getPersonalizedPodcasts(
        interests: List<String>,
        limit: Int = 15
    ): String = withContext(Dispatchers.IO) {
        try {
            val allPodcasts = mutableListOf<Podcast>()
            val podcastsPerInterest = limit / interests.size
            
            interests.forEach { interest ->
                val searchResult = searchPodcastsByKeyword(interest, podcastsPerInterest)
                searchResult.onSuccess { podcasts ->
                    allPodcasts.addAll(podcasts)
                }
            }
            
            // Remove duplicates and shuffle for variety
            val uniquePodcasts = allPodcasts.distinctBy { it.trackId }.shuffled().take(limit)
            
            if (uniquePodcasts.isEmpty()) {
                "No personalized recommendations available for your interests."
            } else {
                formatPodcastResponse(uniquePodcasts, "Personalized Podcast Recommendations")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getPersonalizedPodcasts: ${e.message}")
            "Error getting personalized recommendations."
        }
    }
    
    // ============================================================================
    // API calling methods
    // ============================================================================
    
    /**
     * Fetch podcasts by category - API call
     */
    private suspend fun fetchPodcastsByCategory(
        category: String,
        limit: Int = 20,
        country: String = "US"
    ): Result<List<Podcast>> = withContext(Dispatchers.IO) {
        try {
            val genreId = podcastGenres[category] ?: 1304 // Default to Education
            val apiUrl = "$SEARCH_ENDPOINT?" +
                    "term=podcast&" +
                    "media=podcast&" +
                    "entity=podcast&" +
                    "genreId=$genreId&" +
                    "limit=$limit&" +
                    "country=$country&" +
                    "explicit=No"  // Filter out explicit content for elderly users
            
            Log.d(TAG, "Calling iTunes API: $apiUrl")
            
            val response = URL(apiUrl).readText()
            val podcastResponse = json.decodeFromString<PodcastResponse>(response)
            
            // Filter podcasts that have RSS feed URLs
            val validPodcasts = podcastResponse.results.filter { 
                !it.feedUrl.isNullOrEmpty() && it.kind == "podcast"
            }
            
            Result.success(validPodcasts)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to fetch podcasts by category: ${e.message}", e))
        }
    }
    
    /**
     * Search podcasts - API call
     */
    private suspend fun searchPodcastsByKeyword(
        query: String,
        limit: Int = 15,
        country: String = "US"
    ): Result<List<Podcast>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "$SEARCH_ENDPOINT?" +
                    "term=$encodedQuery&" +
                    "media=podcast&" +
                    "entity=podcast&" +
                    "limit=$limit&" +
                    "country=$country&" +
                    "explicit=No"
            
            Log.d(TAG, "Searching iTunes API: $apiUrl")
            
            val response = URL(apiUrl).readText()
            val podcastResponse = json.decodeFromString<PodcastResponse>(response)
            
            val validPodcasts = podcastResponse.results.filter { 
                !it.feedUrl.isNullOrEmpty() && it.kind == "podcast"
            }
            
            Result.success(validPodcasts)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to search podcasts: ${e.message}", e))
        }
    }
    
    /**
     * Fetch podcast episodes - RSS parsing
     */
    private suspend fun fetchPodcastEpisodes(
        feedUrl: String,
        limit: Int = 10
    ): Result<List<PodcastEpisode>> = withContext(Dispatchers.IO) {
        try {
            val rssContent = URL(feedUrl).readText()
            val episodes = parseRSSFeed(rssContent, limit)
            Result.success(episodes)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to fetch podcast episodes: ${e.message}", e))
        }
    }
    
    /**
     * Get recommended podcast categories
     */
    private fun getRecommendedPodcastCategories(): List<CategoryRecommendation> {
        return listOf(
            CategoryRecommendation(
                id = "health_fitness",
                name = "Health & Fitness",
                english = "health_fitness",
                description = "Health tips, exercise guidance, and wellness advice"
            ),
            CategoryRecommendation(
                id = "history",
                name = "History",
                english = "history",
                description = "Historical stories, documentaries, and cultural heritage"
            ),
            CategoryRecommendation(
                id = "education",
                name = "Educational Content",
                english = "education",
                description = "Learning new skills and practical knowledge"
            ),
            CategoryRecommendation(
                id = "religion_spirituality",
                name = "Spirituality & Mindfulness",
                english = "religion_spirituality",
                description = "Spiritual guidance, meditation, and personal growth"
            ),
            CategoryRecommendation(
                id = "news",
                name = "News & Current Events",
                english = "news",
                description = "Stay informed with news analysis and current events"
            ),
            CategoryRecommendation(
                id = "science",
                name = "Science & Research",
                english = "science",
                description = "Scientific discoveries and research findings"
            ),
            CategoryRecommendation(
                id = "business",
                name = "Business & Finance",
                english = "business",
                description = "Business insights, entrepreneurship, and investment advice"
            ),
            CategoryRecommendation(
                id = "technology",
                name = "Technology",
                english = "technology",
                description = "Tech news, gadget reviews, and digital innovation"
            ),
            CategoryRecommendation(
                id = "arts",
                name = "Arts & Culture",
                english = "arts",
                description = "Art appreciation and cultural discussions"
            ),
            CategoryRecommendation(
                id = "leisure",
                name = "Leisure & Hobbies",
                english = "leisure",
                description = "Gardening, cooking, crafts, and recreational activities"
            )
        )
    }
    
    // ============================================================================
    // Formatting output methods
    // ============================================================================
    
    /**
     * Format podcast response
     */
    private fun formatPodcastResponse(podcasts: List<Podcast>, title: String): String {
        val sb = StringBuilder()
        sb.appendLine("$title")
        sb.appendLine("=".repeat(50))
        
        podcasts.take(15).forEachIndexed { index, podcast ->
            sb.appendLine("\n${index + 1}. ${podcast.trackName}")
            sb.appendLine("   Host: ${podcast.artistName}")
            if (podcast.primaryGenreName?.isNotEmpty() == true) {
                sb.appendLine("   Category: ${podcast.primaryGenreName}")
            }
            if (podcast.trackCount != null && podcast.trackCount > 0) {
                sb.appendLine("   Episodes: ${podcast.trackCount}")
            }
            podcast.feedUrl?.let { feedUrl ->
                sb.appendLine("   Feed: ${feedUrl.take(60)}...")
            }
            podcast.trackViewUrl?.let { url ->
                sb.appendLine("   iTunes: ${url}")
            }
        }
        
        sb.appendLine("\nTotal ${podcasts.size} podcasts found")
        return sb.toString()
    }
    
    /**
     * Format episodes response
     */
    private fun formatEpisodesResponse(episodes: List<PodcastEpisode>, title: String): String {
        val sb = StringBuilder()
        sb.appendLine("$title")
        sb.appendLine("=".repeat(50))
        
        episodes.take(10).forEachIndexed { index, episode ->
            sb.appendLine("\n${index + 1}. ${episode.title}")
            sb.appendLine("   ${episode.description.take(150)}${if (episode.description.length > 150) "..." else ""}")
            episode.duration?.let { duration ->
                sb.appendLine("   Duration: $duration")
            }
            episode.pubDate?.let { pubDate ->
                sb.appendLine("   Published: ${formatDate(pubDate)}")
            }
            episode.audioUrl?.let { audioUrl ->
                sb.appendLine("   Audio: ${audioUrl.take(60)}...")
            }
        }
        
        sb.appendLine("\nTotal ${episodes.size} episodes found")
        return sb.toString()
    }
    
    // ============================================================================
    // RSS parsing and utility methods
    // ============================================================================
    
    /**
     * Simple RSS feed parser
     */
    private fun parseRSSFeed(rssContent: String, limit: Int): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        try {
            // Split by <item> tags and process each episode
            val items = rssContent.split("<item>").drop(1).take(limit)
            
            for (item in items) {
                val title = extractBetween(item, "<title>", "</title>") ?: "Unknown Title"
                val description = extractBetween(item, "<description>", "</description>") ?: "No description available"
                val audioUrl = extractAudioUrl(item)
                val duration = extractBetween(item, "<itunes:duration>", "</itunes:duration>")
                val pubDate = extractBetween(item, "<pubDate>", "</pubDate>")
                val guid = extractBetween(item, "<guid>", "</guid>") ?: ""
                
                episodes.add(PodcastEpisode(
                    title = cleanHtml(title),
                    description = cleanHtml(description).take(300) + if (description.length > 300) "..." else "",
                    audioUrl = audioUrl,
                    duration = duration,
                    pubDate = pubDate,
                    guid = guid
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "RSS parsing error: ${e.message}")
        }
        return episodes
    }
    
    private fun extractBetween(text: String, start: String, end: String): String? {
        val startIndex = text.indexOf(start, ignoreCase = true)
        if (startIndex == -1) return null
        val endIndex = text.indexOf(end, startIndex + start.length, ignoreCase = true)
        if (endIndex == -1) return null
        return text.substring(startIndex + start.length, endIndex).trim()
    }
    
    private fun extractAudioUrl(item: String): String? {
        // Look for enclosure tag with audio URL
        val enclosurePattern = """url="([^"]*\.(mp3|m4a|wav|aac)(?:\?[^"]*)?)"=""".toRegex(RegexOption.IGNORE_CASE)
        val match = enclosurePattern.find(item)
        return match?.groupValues?.get(1)
    }
    
    private fun cleanHtml(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "") // Remove HTML tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
    
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
            // Simple date formatting
            dateString.take(16) // Take first 16 characters
        } catch (e: Exception) {
            dateString
        }
    }
    
    /**
     * Test podcast service
     */
    fun testPodcastService(): String {
        return "Podcast service initialized successfully. Available functions: get_podcasts_by_category, search_podcasts, get_health_podcasts, get_history_podcasts, get_education_podcasts"
    }
    
    /**
     * Get service status
     */
    fun getServiceStatus(): String {
        return "Podcast Functions: Ready (iTunes API - Free)"
    }
}

// ============================================================================
// DATA CLASSES for iTunes Search API Response
// ============================================================================

@Serializable
data class PodcastResponse(
    val resultCount: Int,
    val results: List<Podcast>
)

@Serializable
data class Podcast(
    val wrapperType: String = "",
    val kind: String = "",
    val artistId: Long? = null,
    val trackId: Long,
    val artistName: String = "",
    val trackName: String = "",
    val feedUrl: String? = null,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    @SerialName("artworkUrl600") val artworkUrl600: String? = null,
    val genres: List<String> = emptyList(),
    val primaryGenreName: String? = null,
    val trackViewUrl: String? = null,
    val releaseDate: String? = null,
    val country: String? = null,
    val contentAdvisoryRating: String? = null,
    val trackCount: Int? = null,
    val trackTimeMillis: Long? = null
)

@Serializable
data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String? = null,
    val duration: String? = null,
    val pubDate: String? = null,
    val guid: String = ""
)

@Serializable
data class CategoryRecommendation(
    val id: String,
    val name: String,
    val english: String,
    val description: String
)