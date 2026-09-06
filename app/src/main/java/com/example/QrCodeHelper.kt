package com.example

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

object QrCodeHelper {

    fun generateQrCode(text: String, size: Int = 512): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()

    override fun analyze(image: ImageProxy) {
        try {
            val planes = image.planes
            if (planes.isNotEmpty()) {
                val buffer = planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                val width = image.width
                val height = image.height

                val source = PlanarYUVLuminanceSource(
                    data, width, height, 0, 0, width, height, false
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decode(binaryBitmap)
                val text = result.text
                if (text != null && text.isNotBlank()) {
                    onQrCodeScanned(text)
                }
            }
        } catch (e: Exception) {
            // Failures are expected when no QR code is in sight.
        } finally {
            image.close()
        }
    }
}
