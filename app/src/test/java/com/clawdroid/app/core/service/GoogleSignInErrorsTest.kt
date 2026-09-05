package com.clawdroid.app.core.service

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInErrorsTest {

    @Test
    fun developerErrorExplainsAppRegistration() {
        val text = googleSignInErrorText(ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR)))

        assertTrue(text.contains("error 10"))
        assertTrue(text.contains("com.clawdroid.app"))
        assertTrue(text.contains("SHA-1"))
    }

    @Test
    fun cancelledSignInSaysCancelled() {
        val text = googleSignInErrorText(ApiException(Status(12501)))

        assertTrue(text.contains("cancelled"))
    }

    @Test
    fun nonApiExceptionKeepsDetail() {
        val text = googleSignInErrorText(Exception("socket timeout"))

        assertTrue(text.contains("socket timeout"))
    }
}
