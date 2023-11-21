package com.quantum.barcodescanner

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.quantum.barcodescanner.ScannerActivity.Companion.BARCODE
import com.quantum.barcodescanner.ScannerActivity.Companion.QRCODE
import com.quantum.barcodescanner.ScannerActivity.Companion.TEXTCODE
import com.quantum.barcodescanner.ScannerActivity.Companion.typeCode
import java.io.IOException

@ExperimentalGetImage
class QrCodeAnalyzer(
    private val textFoundListener: (List<String>) -> Unit,
    private val barcodeBoxView: BarcodeBoxView,
    private val previewViewWidth: Float,
    private val previewViewHeight: Float
) : ImageAnalysis.Analyzer {

    /**
     * This parameters will handle preview box scaling
     */
    private var scaleX = 1f
    private var scaleY = 1f

    private fun translateX(x: Float) = x * scaleX
    private fun translateY(y: Float) = y * scaleY

    private fun adjustBoundingRect(rect: Rect) = RectF(
        translateX(rect.left.toFloat()),
        translateY(rect.top.toFloat()),
        translateX(rect.right.toFloat()),
        translateY(rect.bottom.toFloat())
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {

        if (typeCode == BARCODE || typeCode == QRCODE) {

            val img = image.image
            if (img != null) {

                // Update scale factors
                scaleX = previewViewWidth / img.height.toFloat()
                scaleY = previewViewHeight / img.width.toFloat()

                val inputImage = InputImage.fromMediaImage(img, image.imageInfo.rotationDegrees)

                // Process image searching for barcodes
                val options = if (typeCode == BARCODE) {
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_128)
                        .build()
                } else {
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
                        .build()
                }

                val scanner = BarcodeScanning.getClient(options)

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val rectList = mutableListOf<RectF>()
                            val vinList = mutableListOf<String>()
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { code ->
                                    val codeFinal = code
                                        .replace("%", "")
                                        .replace("*", "")
                                        .replace("$", "")
                                        .replace("o", "0")
                                        .replace("O", "0")
                                        .replace("i", "1")
                                        .replace("I", "1")
                                    if (codeFinal.length in 17..19) {
                                        if (codeFinal.matches("^[a-zA-Z0-9]*$".toRegex())) {
                                            if (codeFinal.all { it != "Q"[0] && it != "q"[0] && it != "I"[0] && it != "i"[0] && it != "O"[0] && it != "o"[0] && it != "Ñ"[0] && it != "ñ"[0] }) {
                                                vinList.add(codeFinal)
                                                barcode.boundingBox?.let { rect ->
                                                    rectList.add(adjustBoundingRect(rect))
                                                }
                                            }
                                        }
                                    } else {
                                        if (codeFinal.length == 10) {
                                            if (codeFinal[3] == " "[0]) {
                                                if (codeFinal.substringAfter(" ")
                                                        .matches("-?\\d+(\\.\\d+)?".toRegex()) && codeFinal.substringBefore(
                                                        " "
                                                    ).all { it.isLetter() }
                                                ) {
                                                    vinList.add(codeFinal.substringAfter(" "))
                                                    barcode.boundingBox?.let { rect ->
                                                        rectList.add(adjustBoundingRect(rect))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            textFoundListener(vinList)
                            if (!barcodeBoxView.isDraw || barcodeBoxView.getRectCount() < rectList.size) {
                                barcodeBoxView.setRect(rectList)
                            }
                        } else {
                            // Remove bounding rect
                            if (!barcodeBoxView.isDraw) {
                                barcodeBoxView.setRect(emptyList())
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.println(Log.ERROR, "ERROR", it.message.toString())
                    }
                    .addOnCompleteListener {
                        image.close()
                    }
            }
        } else if (typeCode == TEXTCODE) {
            image.image?.let { process(it, image) }
        }
    }

    private fun process(image: Image, imageProxy: ImageProxy) {
        try {
            readTextFromImage(InputImage.fromMediaImage(image, 90), imageProxy)
        } catch (e: IOException) {
            Log.d("TEXT_READER_ANALYZER", "Failed to load the image")
            e.printStackTrace()
        }
    }

    private fun readTextFromImage(image: InputImage, imageProxy: ImageProxy) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { visionText ->
                processTextFromImage(visionText)
                imageProxy.close()
            }
            .addOnFailureListener { error ->
                Log.d("READ_TEXT_FROM_IMAGE", "Failed to process the image")
                error.printStackTrace()
                imageProxy.close()
            }
    }

    private fun processTextFromImage(visionText: Text) {
        val vinList = mutableListOf<String>()
        val rectList = mutableListOf<RectF>()
        for (block in visionText.textBlocks) {
            // You can access whole block of text using block.text
            for (line in block.lines) {
                val codeFinal = line.text
                    .replace("%", "")
                    .replace("*", "")
                    .replace("$", "")
                    .replace(" ", "")
                    .replace("o", "0")
                    .replace("O", "0")
                    .replace("i", "1")
                    .replace("I", "1")
                if (codeFinal.length in 17..19) {
                    if (codeFinal.matches("^[a-zA-Z0-9]*$".toRegex())) {
                        if (codeFinal.all { it != "Q"[0] && it != "q"[0] && it != "I"[0] && it != "i"[0] && it != "O"[0] && it != "o"[0] && it != "Ñ"[0] && it != "ñ"[0] }) {
                            vinList.add(codeFinal)
                            line.boundingBox?.let { rect ->
                                rectList.add(adjustBoundingRect(rect))
                            }
                        }
                    }
                } else {
                    if (codeFinal.length == 10) {
                        if (codeFinal[3] == " "[0]) {
                            if (codeFinal.substringAfter(" ")
                                    .matches("-?\\d+(\\.\\d+)?".toRegex()) && codeFinal.substringBefore(
                                    " "
                                ).all { it.isLetter() }
                            ) {
                                vinList.add(codeFinal.substringAfter(" "))
                                line.boundingBox?.let { rect ->
                                    rectList.add(adjustBoundingRect(rect))
                                }
                            }
                        }
                    }
                }

            }
        }
        textFoundListener(vinList)
        if (!barcodeBoxView.isDraw || barcodeBoxView.getRectCount() < rectList.size) {
            barcodeBoxView.setRect(rectList)
        }
    }
}