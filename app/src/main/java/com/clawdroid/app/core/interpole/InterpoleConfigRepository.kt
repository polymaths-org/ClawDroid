package com.clawdroid.app.core.interpole

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clawdroid.app.core.config.AppConfigManager

enum class DesktopEnvironment(val label: String, val wmType: String) {
    HYPRLAND("Hyprland (Wayland)", "wayland"),
    KDE("KDE Plasma (Wayland/X11)", "wayland"),
    GNOME("GNOME (Wayland/X11)", "wayland"),
    XFCE("Xfce (X11)", "x11"),
    I3("i3/Sway (WM)", "x11"),
    GENERIC("Generic", "x11"),
}

data class DesktopToolchain(
    val windowManager: String?,
    val inputTool: String,
    val waylandType: String,
    val screenshotTool: String?,
    val mediaTool: String,
    val notificationTool: String,
    val terminalEmulator: String,
    val envVars: Map<String, String>,
)

data class InterpoleConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "http://100.x.x.x:8765",
    val deviceId: String = "",
    val desktopEnv: DesktopEnvironment = DesktopEnvironment.HYPRLAND,
    val fileTransferPort: Int = 8787,
    val downloadPath: String = "/home/paris",
    val autoStartFileServer: Boolean = true,
    val tailscaleIp: String = "",
    val allowExecute: Boolean = true,
    val commandTimeout: Int = 60,
    val desktopHarnessEnabled: Boolean = true,
    val desktopWebPanelEnabled: Boolean = true,
    val cliInterfaceEnabled: Boolean = true,
    val memorySyncEnabled: Boolean = true,
    val memoryAutoSyncEnabled: Boolean = false,
    val memorySyncIntervalMinutes: Int = 60,
) {
    val rpcBaseUrl: String get() = baseUrl.trimEnd('/')
}

class InterpoleConfigRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getConfig(): InterpoleConfig {
        val envName = prefs.getString(KEY_DESKTOP_ENV, DesktopEnvironment.HYPRLAND.name)
            ?: DesktopEnvironment.HYPRLAND.name
        val appHost = AppConfigManager.interpoleHost.trim()
        val appBaseUrl = if (appHost.isNotBlank()) {
            "http://$appHost:${AppConfigManager.interpolePort}"
        } else {
            ""
        }
        return InterpoleConfig(
            enabled = AppConfigManager.interpoleEnabled || prefs.getBoolean(KEY_ENABLED, false),
            baseUrl = appBaseUrl.ifBlank {
                prefs.getString(KEY_BASE_URL, "http://100.x.x.x:8765") ?: "http://100.x.x.x:8765"
            },
            deviceId = AppConfigManager.interpoleDeviceId.ifBlank {
                prefs.getString(KEY_DEVICE_ID, "") ?: ""
            },
            desktopEnv = runCatching { DesktopEnvironment.valueOf(envName) }.getOrDefault(DesktopEnvironment.HYPRLAND),
            fileTransferPort = prefs.getInt(KEY_FILE_TRANSFER_PORT, 8787),
            downloadPath = prefs.getString(KEY_DOWNLOAD_PATH, "/home/paris") ?: "/home/paris",
            autoStartFileServer = prefs.getBoolean(KEY_AUTO_START_FILE_SERVER, true),
            tailscaleIp = AppConfigManager.interpoleHost
                .takeIf { AppConfigManager.interpoleConnectionType == "tailscale" && it.isNotBlank() }
                ?: (prefs.getString(KEY_TAILSCALE_IP, "") ?: ""),
            allowExecute = AppConfigManager.interpoleAllowExecute || prefs.getBoolean(KEY_ALLOW_EXECUTE, true),
            commandTimeout = prefs.getInt(KEY_COMMAND_TIMEOUT, 60),
            desktopHarnessEnabled = AppConfigManager.interpoleDesktopHarnessEnabled,
            desktopWebPanelEnabled = AppConfigManager.interpoleDesktopWebPanelEnabled,
            cliInterfaceEnabled = AppConfigManager.interpoleCliInterfaceEnabled,
            memorySyncEnabled = AppConfigManager.memorySyncEnabled,
            memoryAutoSyncEnabled = AppConfigManager.memoryAutoSyncEnabled,
            memorySyncIntervalMinutes = AppConfigManager.memorySyncIntervalMinutes,
        )
    }

    fun getDeviceToken(): String = AppConfigManager.interpoleDeviceToken.ifBlank {
        securePrefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
    }

    fun saveConfig(config: InterpoleConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_BASE_URL, config.baseUrl.trimEnd('/'))
            .putString(KEY_DEVICE_ID, config.deviceId)
            .putString(KEY_DESKTOP_ENV, config.desktopEnv.name)
            .putInt(KEY_FILE_TRANSFER_PORT, config.fileTransferPort.coerceIn(1024, 65535))
            .putString(KEY_DOWNLOAD_PATH, config.downloadPath)
            .putBoolean(KEY_AUTO_START_FILE_SERVER, config.autoStartFileServer)
            .putString(KEY_TAILSCALE_IP, config.tailscaleIp)
            .putBoolean(KEY_ALLOW_EXECUTE, config.allowExecute)
            .putInt(KEY_COMMAND_TIMEOUT, config.commandTimeout.coerceIn(1, 3600))
            .apply()
        val url = runCatching { java.net.URL(config.rpcBaseUrl) }.getOrNull()
        AppConfigManager.interpoleEnabled = config.enabled
        AppConfigManager.interpoleHost = url?.host ?: config.baseUrl.removePrefix("http://").removePrefix("https://").substringBefore(':')
        AppConfigManager.interpolePort = url?.port?.takeIf { it > 0 } ?: 8765
        AppConfigManager.interpoleConnectionType = if (config.tailscaleIp.isNotBlank()) "tailscale" else AppConfigManager.interpoleConnectionType
        AppConfigManager.interpoleAllowExecute = config.allowExecute
        AppConfigManager.interpoleDesktopHarnessEnabled = config.desktopHarnessEnabled
        AppConfigManager.interpoleDesktopWebPanelEnabled = config.desktopWebPanelEnabled
        AppConfigManager.interpoleCliInterfaceEnabled = config.cliInterfaceEnabled
        AppConfigManager.memorySyncEnabled = config.memorySyncEnabled
        AppConfigManager.memoryAutoSyncEnabled = config.memoryAutoSyncEnabled
        AppConfigManager.memorySyncIntervalMinutes = config.memorySyncIntervalMinutes
    }

    fun saveCredentials(deviceId: String, deviceToken: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putBoolean(KEY_ENABLED, deviceId.isNotBlank() && deviceToken.isNotBlank())
            .apply()
        securePrefs.edit().putString(KEY_DEVICE_TOKEN, deviceToken).apply()
        AppConfigManager.interpoleDeviceId = deviceId
        AppConfigManager.interpoleDeviceToken = deviceToken
        AppConfigManager.interpoleEnabled = deviceId.isNotBlank() && deviceToken.isNotBlank()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .putBoolean(KEY_ENABLED, false)
            .apply()
        securePrefs.edit().remove(KEY_DEVICE_TOKEN).apply()
        AppConfigManager.interpoleDeviceId = ""
        AppConfigManager.interpoleDeviceToken = ""
        AppConfigManager.interpoleEnabled = false
    }

    companion object {
        private const val PREFS = "interpole_settings"
        private const val SECURE_PREFS = "interpole_secure"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DESKTOP_ENV = "desktop_env"
        private const val KEY_FILE_TRANSFER_PORT = "file_transfer_port"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val KEY_AUTO_START_FILE_SERVER = "auto_start_file_server"
        private const val KEY_TAILSCALE_IP = "tailscale_ip"
        private const val KEY_ALLOW_EXECUTE = "allow_execute"
        private const val KEY_COMMAND_TIMEOUT = "command_timeout"

        fun getToolchain(env: DesktopEnvironment): DesktopToolchain = when (env) {
            DesktopEnvironment.HYPRLAND -> DesktopToolchain(
                windowManager = "hyprctl",
                inputTool = "ydotool",
                waylandType = "wayland",
                screenshotTool = "grim",
                mediaTool = "playerctl",
                notificationTool = "notify-send",
                terminalEmulator = "kitty",
                envVars = mapOf(
                    "WAYLAND_DISPLAY" to "wayland-1",
                    "DISPLAY" to ":1",
                    "XDG_RUNTIME_DIR" to "/run/user/1000",
                ),
            )
            DesktopEnvironment.KDE -> DesktopToolchain(
                windowManager = "qdbus org.kde.KWin",
                inputTool = "ydotool",
                waylandType = "wayland",
                screenshotTool = "spectacle",
                mediaTool = "playerctl",
                notificationTool = "notify-send",
                terminalEmulator = "konsole",
                envVars = mapOf("XDG_RUNTIME_DIR" to "/run/user/1000"),
            )
            DesktopEnvironment.GNOME -> DesktopToolchain(
                windowManager = "gsettings",
                inputTool = "ydotool",
                waylandType = "wayland",
                screenshotTool = "gnome-screenshot",
                mediaTool = "playerctl",
                notificationTool = "notify-send",
                terminalEmulator = "gnome-terminal",
                envVars = mapOf("XDG_RUNTIME_DIR" to "/run/user/1000"),
            )
            DesktopEnvironment.XFCE -> DesktopToolchain(
                windowManager = "xfwm4",
                inputTool = "xdotool",
                waylandType = "x11",
                screenshotTool = "xfce4-screenshooter",
                mediaTool = "playerctl",
                notificationTool = "notify-send",
                terminalEmulator = "xfce4-terminal",
                envVars = mapOf("DISPLAY" to ":0"),
            )
            DesktopEnvironment.I3 -> DesktopToolchain(
                windowManager = "i3-msg",
                inputTool = "xdotool",
                waylandType = "x11",
                screenshotTool = "scrot",
                mediaTool = "playerctl",
                notificationTool = "notify-send",
                terminalEmulator = "i3-sensible-terminal",
                envVars = mapOf("DISPLAY" to ":0"),
            )
            DesktopEnvironment.GENERIC -> DesktopToolchain(
                windowManager = null,
                inputTool = "xdotool",
                waylandType = "x11",
                screenshotTool = null,
                mediaTool = "playerctl",
                notificationTool = "notify-send",
                terminalEmulator = "xterm",
                envVars = mapOf("DISPLAY" to ":0"),
            )
        }
    }
}
