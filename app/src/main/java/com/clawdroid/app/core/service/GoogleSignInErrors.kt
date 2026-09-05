package com.clawdroid.app.core.service

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

fun googleSignInErrorText(e: Exception): String {
    val code = (e as? ApiException)?.statusCode
    return when (code) {
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google rejected this app build (error 10): it is not registered in the " +
                "Google Cloud project. Add an Android OAuth client for package " +
                "com.clawdroid.app with this build's SHA-1 fingerprint, in the same " +
                "project as the web client, then try again. No app update is needed afterwards."
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
            "Google sign-in was cancelled before completing."
        GoogleSignInStatusCodes.SIGN_IN_FAILED ->
            "Google sign-in failed (error 12500). Check the device network connection and try again."
        CommonStatusCodes.NETWORK_ERROR ->
            "Google sign-in failed: no network connection. Reconnect and try again."
        GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
            "Google sign-in is already in progress. Wait for it to finish."
        else -> {
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "unknown error"
            if (code != null) "Google sign-in failed (error $code): $detail"
            else "Google sign-in failed: $detail"
        }
    }
}
