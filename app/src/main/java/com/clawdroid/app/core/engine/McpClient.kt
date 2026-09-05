package com.clawdroid.app.core.engine

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class McpClient(
    private val context: Context,
    private val serverName: String,
    private val command: String,
    private val args: List<String>,
    private val envVars: Map<String, String> = emptyMap()
) {
    private val TAG = "McpClient-$serverName"
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val writeMutex = Mutex()
    
    @Volatile
    var isConnected = false
        private set

    /**
     * Start the MCP subprocess, configure environment, and run reader thread.
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting MCP server: $command with args: $args")
            
            // Re-use sandbox environment setup from bootstrap module
            val envMap = EnvironmentSetup.build(context).values.toMutableMap()
            envMap.putAll(envVars)

            val pb = ProcessBuilder(listOf(command) + args).apply {
                directory(context.filesDir)
                environment().clear()
                environment().putAll(envMap)
                redirectErrorStream(false) // read stdout and stderr separately
            }

            val proc = pb.start()
            process = proc
            
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream, "UTF-8"))
            reader = BufferedReader(InputStreamReader(proc.inputStream, "UTF-8"))
            
            // Spin up background coroutine to consume stderr logs
            scope.launch {
                val errReader = BufferedReader(InputStreamReader(proc.errorStream, "UTF-8"))
                try {
                    while (isActive) {
                        val line = errReader.readLine() ?: break
                        Log.d(TAG, "[stderr] $line")
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            // Start JSON-RPC response reader loop
            scope.launch {
                readLoop()
            }

            // Execute standard MCP initialization handshake
            val initSuccess = performHandshake()
            isConnected = initSuccess
            Log.i(TAG, "MCP server initialized: $initSuccess")
            return@withContext initSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server", e)
            stop()
            return@withContext false
        }
    }

    /**
     * Shuts down the subprocess and cleans up streams.
     */
    fun stop() {
        isConnected = false
        scope.cancel()
        
        pendingRequests.forEach { (_, deferred) ->
            deferred.cancel(CancellationException("MCP client stopped"))
        }
        pendingRequests.clear()

        runCatching { writer?.close() }
        runCatching { reader?.close() }
        process?.destroy()
        process = null
        
        Log.i(TAG, "MCP server stopped.")
    }

    /**
     * Fetches the list of tools exposed by this MCP server.
     */
    suspend fun listTools(): List<JSONObject> {
        if (!isConnected) return emptyList()
        return try {
            val response = sendRequest("tools/list", JSONObject())
            val toolsArray = response.optJSONArray("tools") ?: return emptyList()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until toolsArray.length()) {
                val tool = toolsArray.optJSONObject(i) ?: continue
                list.add(tool)
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error listing tools", e)
            emptyList()
        }
    }

    /**
     * Calls a tool with arguments on the server.
     */
    suspend fun callTool(name: String, arguments: JSONObject): JSONObject {
        if (!isConnected) {
            return JSONObject().put("error", "MCP server not connected")
        }
        return try {
            val params = JSONObject()
                .put("name", name)
                .put("arguments", arguments)
            sendRequest("tools/call", params)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool: $name", e)
            JSONObject().put("error", e.message ?: "Unknown tool execution error")
        }
    }

    private suspend fun performHandshake(): Boolean {
        return try {
            val params = JSONObject()
                .put("protocolVersion", "2024-11-05")
                .put("capabilities", JSONObject()
                    .put("tools", JSONObject())
                    .put("resources", JSONObject())
                )
                .put("clientInfo", JSONObject()
                    .put("name", "ClawDroid-Client")
                    .put("version", "0.1.0")
                )

            val response = sendRequest("initialize", params)
            val version = response.optString("protocolVersion")
            if (version.isNullOrBlank()) {
                return false
            }

            // Send initialized notification (doesn't expect response)
            sendNotification("notifications/initialized", JSONObject())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed", e)
            false
        }
    }

    private suspend fun sendRequest(method: String, params: JSONObject): JSONObject {
        val id = nextId.getAndIncrement()
        val request = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        writeMutex.withLock {
            val w = writer ?: throw IllegalStateException("Writer is null")
            w.write(request.toString())
            w.newLine()
            w.flush()
        }

        return withTimeout(30_000) {
            deferred.await()
        }
    }

    private suspend fun sendNotification(method: String, params: JSONObject) {
        val notification = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("params", params)

        writeMutex.withLock {
            val w = writer ?: throw IllegalStateException("Writer is null")
            w.write(notification.toString())
            w.newLine()
            w.flush()
        }
    }

    private suspend fun readLoop() {
        val r = reader ?: return
        try {
            while (scope.isActive) {
                val line = r.readLine() ?: break
                Log.d(TAG, "[stdout] $line")
                
                val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (json.has("id")) {
                    val id = json.optInt("id", -1)
                    val deferred = pendingRequests.remove(id)
                    if (deferred != null) {
                        if (json.has("error")) {
                            deferred.complete(json.getJSONObject("error"))
                        } else {
                            deferred.complete(json.optJSONObject("result") ?: JSONObject())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in reader loop", e)
        } finally {
            isConnected = false
        }
    }
}
