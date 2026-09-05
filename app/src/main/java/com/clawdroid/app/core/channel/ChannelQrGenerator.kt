package com.clawdroid.app.core.channel

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.charset.StandardCharsets

object ChannelQrGenerator {
    private const val QR_CODE_SIZE = 512
    private val hints = mapOf(
        EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
        EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 2
    )

    fun generateWhatsAppQr(): Bitmap {
        val data = "https://clawdroid.io/auth/whatsapp"
        return generateQrCode(data)
    }

    fun generateTelegramQr(): Bitmap {
        val data = "https://clawdroid.io/auth/telegram"
        return generateQrCode(data)
    }

    fun generateDiscordQr(): Bitmap {
        val data = "https://clawdroid.io/auth/discord"
        return generateQrCode(data)
    }

    fun generateSlackQr(): Bitmap {
        val data = "https://clawdroid.io/auth/slack"
        return generateQrCode(data)
    }

    fun generateEmailQr(): Bitmap {
        val data = "https://clawdroid.io/auth/email"
        return generateQrCode(data)
    }

    fun generateWebhookQr(webhookUrl: String): Bitmap {
        return generateQrCode(webhookUrl)
    }

    private fun generateQrCode(data: String): Bitmap {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints)
            
            val bitmap = Bitmap.createBitmap(QR_CODE_SIZE, QR_CODE_SIZE, Bitmap.Config.RGB_565)
            for (x in 0 until QR_CODE_SIZE) {
                for (y in 0 until QR_CODE_SIZE) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bitmap
        } catch (e: Exception) {
            createErrorBitmap()
        }
    }

    private fun createErrorBitmap(): Bitmap {
        return Bitmap.createBitmap(QR_CODE_SIZE, QR_CODE_SIZE, Bitmap.Config.RGB_565).apply {
            eraseColor(0xFFFFFFFF.toInt())
        }
    }
}
