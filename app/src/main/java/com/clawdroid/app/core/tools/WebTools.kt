package com.clawdroid.app.core.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object BrowseWebTool {
    suspend fun execute(url: String): JSONObject = withContext(Dispatchers.IO) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val html = fetch(normalized)
        JSONObject()
            .put("url", normalized)
            .put("content", html.toPlainText().take(8_000))
    }
}

object WebSearchTool {
    suspend fun execute(query: String): JSONObject = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://duckduckgo.com/html/?q=$encoded"
        val html = fetch(url)
        JSONObject()
            .put("query", query)
            .put("results", parseDuckDuckGoResults(html))
    }
}

private fun fetch(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "ClawDroid/0.1 (+https://clawdroid.local)")
    }
    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
    return stream.bufferedReader().use { it.readText() }
}

private fun String.toPlainText(): String = this
    .replace(Regex("(?is)<script.*?</script>"), " ")
    .replace(Regex("(?is)<style.*?</style>"), " ")
    .replace(Regex("(?s)<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun parseDuckDuckGoResults(html: String): JSONArray {
    val results = JSONArray()
    val resultRegex = Regex(
        "(?is)<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>"
    )
    resultRegex.findAll(html).take(5).forEach { match ->
        results.put(
            JSONObject()
                .put("url", match.groupValues[1])
                .put("title", match.groupValues[2].toPlainText())
                .put("snippet", match.groupValues[3].toPlainText())
        )
    }
    return results
}
