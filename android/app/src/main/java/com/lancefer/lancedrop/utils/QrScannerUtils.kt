package com.lancefer.lancedrop.utils

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val mlKitScanner = BarcodeScanning.getClient()
    private val zxingReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE
        )
        setHints(hints)
    }

    private var isDispatched = false

    private fun handleFoundCode(raw: String) {
        if (isDispatched) return
        val decrypted = if (raw.startsWith("LANCEDROP:SEC:v1:")) {
            QrSecurityCrypto.decryptQrPayload(raw) ?: raw
        } else {
            raw
        }
        if (decrypted.startsWith("lancedrop://") || decrypted.startsWith("fastdrop://") || 
            decrypted.contains("://") || decrypted.trim().startsWith("{")) {
            isDispatched = true
            onQrCodeScanned(decrypted.trim())
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isDispatched) {
            imageProxy.close()
            return
        }

        // 1. Décodage local instantané et hors-ligne via ZXing
        try {
            val yBuffer = imageProxy.planes[0].buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)
            val source = PlanarYUVLuminanceSource(
                yBytes,
                imageProxy.width,
                imageProxy.height,
                0,
                0,
                imageProxy.width,
                imageProxy.height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = zxingReader.decodeWithState(binaryBitmap)
            zxingReader.reset()
            if (result != null && !result.text.isNullOrEmpty()) {
                handleFoundCode(result.text)
                imageProxy.close()
                return
            }
        } catch (_: Exception) {
            zxingReader.reset()
        }

        // 2. Décodage de secours via ML Kit BarcodeScanning
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            mlKitScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { raw ->
                            handleFoundCode(raw)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
