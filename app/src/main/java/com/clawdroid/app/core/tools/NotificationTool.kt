package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.notifications.NotificationHelper
import org.json.JSONObject

object NotificationTool {
    fun execute(context: Context, title: String, body: String): JSONObject {
        NotificationHelper.sendAgentNotification(context, title, body)
        return JSONObject()
            .put("sent", true)
            .put("title", title)
            .put("body", body)
    }
}
