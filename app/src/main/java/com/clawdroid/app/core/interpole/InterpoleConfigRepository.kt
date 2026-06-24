package com.clawdroid.app.core.interpole

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
        return InterpoleConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            baseUrl = prefs.getString(KEY_BASE_URL, "http://100.x.x.x:8765") ?: "http://100.x.x.x:8765",
            deviceId = prefs.getString(KEY_DEVICE_ID, "") ?: "",
            desktopEnv = runCatching { DesktopEnvironment.valueOf(envName) }.getOrDefault(DesktopEnvironment.HYPRLAND),
            fileTransferPort = prefs.getInt(KEY_FILE_TRANSFER_PORT, 8787),
            downloadPath = prefs.getString(KEY_DOWNLOAD_PATH, "/home/paris") ?: "/home/paris",
            autoStartFileServer = prefs.getBoolean(KEY_AUTO_START_FILE_SERVER, true),
            tailscaleIp = prefs.getString(KEY_TAILSCALE_IP, "") ?: "",
            allowExecute = prefs.getBoolean(KEY_ALLOW_EXECUTE, true),
            commandTimeout = prefs.getInt(KEY_COMMAND_TIMEOUT, 60),
        )
    }

    fun getDeviceToken(): String = securePrefs.getString(KEY_DEVICE_TOKEN, "") ?: ""

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
    }

    fun saveCredentials(deviceId: String, deviceToken: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putBoolean(KEY_ENABLED, deviceId.isNotBlank() && deviceToken.isNotBlank())
            .apply()
        securePrefs.edit().putString(KEY_DEVICE_TOKEN, deviceToken).apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .putBoolean(KEY_ENABLED, false)
            .apply()
        securePrefs.edit().remove(KEY_DEVICE_TOKEN).apply()
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
