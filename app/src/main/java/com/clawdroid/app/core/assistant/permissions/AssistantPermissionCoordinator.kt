package com.clawdroid.app.core.assistant.permissions

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.control.ScreenCaptureManager
import com.clawdroid.app.core.control.ScreenReaderService

enum class AssistantCapability {
    DEFAULT_ASSISTANT,
    SCREEN_CONTEXT,
    SCREEN_CONTROL_ACCESSIBILITY,
    SCREEN_CAPTURE,
    OVERLAY,
    MICROPHONE,
    NOTIFICATIONS,
    BACKGROUND_AGENT,
    FILES_AND_MEDIA,
    CONNECTED_SERVICE,
    BATTERY_UNRESTRICTED
}

enum class PermissionRecoveryAction {
    REQUEST_RUNTIME_PERMISSION,
    REQUEST_ROLE,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_OVERLAY_SETTINGS,
    REQUEST_MEDIA_PROJECTION,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_APP_SETTINGS,
    CONNECT_SERVICE,
    NONE
}

data class PermissionBlockedResult(
    val success: Boolean = false,
    val error: String = "permission_required",
    val capability: AssistantCapability,
    val title: String,
    val message: String,
    val recoveryAction: PermissionRecoveryAction,
    val retryableAfterGrant: Boolean = true,
) {
    fun toJsonObject(): org.json.JSONObject {
        return org.json.JSONObject()
            .put("success", success)
            .put("error", error)
            .put("capability", capability.name)
            .put("title", title)
            .put("message", message)
            .put("recoveryAction", recoveryAction.name)
            .put("retryableAfterGrant", retryableAfterGrant)
    }
}

object AssistantPermissionCoordinator {

    fun isDefaultAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null) {
                return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            }
        }
        val assistantSetting = Settings.Secure.getString(context.contentResolver, "assistant")
        if (assistantSetting != null) {
            val cn = ComponentName.unflattenFromString(assistantSetting)
            return cn?.packageName == context.packageName
        }
        return false
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasAccessibilityService(context: Context): Boolean {
        return ScreenReaderService.instance != null
    }

    fun hasScreenCapturePermission(context: Context): Boolean {
        return ScreenCaptureManager.isActive()
    }

    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    fun checkCapability(context: Context, capability: AssistantCapability): PermissionBlockedResult? {
        return when (capability) {
            AssistantCapability.DEFAULT_ASSISTANT -> {
                if (!isDefaultAssistant(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Default Assistant Required",
                        message = "ClawDroid is not your default assistant yet. Set it as the assistant to invoke it from anywhere.",
                        recoveryAction = PermissionRecoveryAction.REQUEST_ROLE
                    )
                } else null
            }
            AssistantCapability.OVERLAY -> {
                if (!hasOverlayPermission(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Display Over Other Apps Required",
                        message = "ClawDroid needs overlay permission to display the assistant overlay. Without it, I can continue in the main chat.",
                        recoveryAction = PermissionRecoveryAction.OPEN_OVERLAY_SETTINGS
                    )
                } else null
            }
            AssistantCapability.SCREEN_CONTROL_ACCESSIBILITY -> {
                if (!hasAccessibilityService(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Accessibility Service Required",
                        message = "I need ClawDroid Screen Control accessibility service to read and tap the screen for you. Enable it in Settings.",
                        recoveryAction = PermissionRecoveryAction.OPEN_ACCESSIBILITY_SETTINGS
                    )
                } else null
            }
            AssistantCapability.SCREEN_CAPTURE -> {
                if (!hasScreenCapturePermission(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Screen Capture Required",
                        message = "I can read some UI text, but visual screenshot access is off. Enable screen capture for visual questions.",
                        recoveryAction = PermissionRecoveryAction.REQUEST_MEDIA_PROJECTION
                    )
                } else null
            }
            AssistantCapability.MICROPHONE -> {
                if (!hasMicrophonePermission(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Microphone Permission Required",
                        message = "I need microphone access for voice mode. You can still type your request.",
                        recoveryAction = PermissionRecoveryAction.REQUEST_RUNTIME_PERMISSION
                    )
                } else null
            }
            AssistantCapability.NOTIFICATIONS -> {
                if (!hasNotificationPermission(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Notifications Required",
                        message = "Enable notifications so I can alert you when background tasks complete.",
                        recoveryAction = PermissionRecoveryAction.OPEN_NOTIFICATION_SETTINGS
                    )
                } else null
            }
            AssistantCapability.BATTERY_UNRESTRICTED -> {
                if (!isBatteryUnrestricted(context)) {
                    PermissionBlockedResult(
                        capability = capability,
                        title = "Ignore Battery Optimizations Required",
                        message = "Allow ClawDroid to run in the background without battery limits to execute longer tasks.",
                        recoveryAction = PermissionRecoveryAction.OPEN_BATTERY_SETTINGS
                    )
                } else null
            }
            else -> null
        }
    }

    fun getRecoveryIntent(context: Context, action: PermissionRecoveryAction): Intent? {
        return when (action) {
            PermissionRecoveryAction.REQUEST_ROLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                    roleManager?.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                } else {
                    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                }
            }
            PermissionRecoveryAction.OPEN_OVERLAY_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                } else null
            }
            PermissionRecoveryAction.OPEN_ACCESSIBILITY_SETTINGS -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            PermissionRecoveryAction.OPEN_NOTIFICATION_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            PermissionRecoveryAction.OPEN_BATTERY_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else null
            }
            PermissionRecoveryAction.OPEN_APP_SETTINGS -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            else -> null
        }
    }
}
