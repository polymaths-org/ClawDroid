package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.notifications.NotificationHelper
import org.json.JSONObject

object NotificationTool {
    fun execute(context: Context, title: String, body: String, triggerAction: String? = null): JSONObject {
        NotificationHelper.sendAgentNotification(
            context = context,
            title = title,
            body = body,
            triggerAction = triggerAction ?: "open_app",
        )
        return JSONObject()
            .put("sent", true)
            .put("title", title)
            .put("body", body)
            .put("trigger_action", triggerAction ?: "open_app")
    }
}
