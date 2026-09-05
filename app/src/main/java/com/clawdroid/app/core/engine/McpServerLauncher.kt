package com.clawdroid.app.core.engine

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.service.GoogleAuthManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

object McpServerLauncher {
    private const val TAG = "McpServerLauncher"
    private val activeClients = mutableMapOf<String, McpClient>()
    private val mutex = Mutex()

    /**
     * Launch all configured and enabled MCP servers in the background.
     */
    suspend fun startAll(context: Context) = mutex.withLock {
        Log.i(TAG, "Starting all enabled MCP servers...")
        
        // 1. Get a fresh Google Access Token if connected
        val googleToken = if (GoogleAuthManager.isGoogleConnected) {
            GoogleAuthManager.getOrRefreshAccessToken()
        } else null

        // 2. Parse configurations
        val configStr = AppConfigManager.mcpServersConfig.takeIf { it.isNotBlank() } ?: getDefaultConfig()
        val serversJson = runCatching { JSONObject(configStr).optJSONObject("mcpServers") }.getOrNull() ?: JSONObject()

        val keys = serversJson.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val server = serversJson.getJSONObject(name)
            val enabled = server.optBoolean("enabled", true)
            if (!enabled) continue

            val command = server.getString("command")
            val argsArray = server.optJSONArray("args") ?: JSONArray()
            val argsList = mutableListOf<String>()
            for (i in 0 until argsArray.length()) {
                argsList.add(argsArray.getString(i))
            }

            val envJson = server.optJSONObject("env") ?: JSONObject()
            val envMap = mutableMapOf<String, String>()
            val envKeys = envJson.keys()
            while (envKeys.hasNext()) {
                val ek = envKeys.next()
                envMap[ek] = envJson.getString(ek)
            }

            // Inject Google credentials into the environment if configured
            if (googleToken != null) {
                envMap["GMAIL_ACCESS_TOKEN"] = googleToken
                envMap["GOOGLE_CALENDAR_ACCESS_TOKEN"] = googleToken
            }

            // Create and start client
            val client = McpClient(
                context = context,
                serverName = name,
                command = command,
                args = argsList,
                envVars = envMap
            )
            
            val success = client.start()
            if (success) {
                activeClients[name] = client
            } else {
                Log.e(TAG, "Failed to start MCP server: $name")
            }
        }
    }

    /**
     * Stops all active MCP server processes.
     */
    suspend fun stopAll() = mutex.withLock {
        Log.i(TAG, "Stopping all MCP servers...")
        activeClients.forEach { (_, client) ->
            client.stop()
        }
        activeClients.clear()
    }

    /**
     * Gathers all tools from active MCP servers.
     */
    suspend fun getMcpTools(): List<JSONObject> = mutex.withLock {
        val allTools = mutableListOf<JSONObject>()
        activeClients.forEach { (name, client) ->
            val tools = client.listTools()
            Log.i(TAG, "Fetched ${tools.size} tools from MCP server: $name")
            allTools.addAll(tools)
        }
        return allTools
    }

    /**
     * Executes a tool on the appropriate MCP server.
     */
    suspend fun executeMcpTool(name: String, arguments: JSONObject): JSONObject? {
        mutex.withLock {
            // Find which client exposes this tool
            for ((serverName, client) in activeClients) {
                val tools = client.listTools()
                if (tools.any { it.getString("name") == name }) {
                    Log.i(TAG, "Routing tool call '$name' to server '$serverName'")
                    return client.callTool(name, arguments)
                }
            }
        }
        return null
    }

    private fun getDefaultConfig(): String {
        return JSONObject()
            .put("mcpServers", JSONObject()
                .put("filesystem", JSONObject()
                    .put("enabled", true)
                    .put("command", "npx")
                    .put("args", JSONArray().put("-y").put("@anthropic/mcp-filesystem-server").put("/data/data/com.clawdroid.app/files/home/projects"))
                )
                .put("github", JSONObject()
                    .put("enabled", false)
                    .put("command", "npx")
                    .put("args", JSONArray().put("-y").put("@modelcontextprotocol/server-github"))
                    .put("env", JSONObject().put("GITHUB_TOKEN", ""))
                )
                .put("fetch", JSONObject()
                    .put("enabled", true)
                    .put("command", "npx")
                    .put("args", JSONArray().put("-y").put("@modelcontextprotocol/server-fetch"))
                )
            ).toString(2)
    }
}
