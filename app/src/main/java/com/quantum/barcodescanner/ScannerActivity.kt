package com.quantum.barcodescanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quantum.barcodescanner.ScannerActivity.Companion.TYPE_SCAN_KEY
import com.quantum.barcodescanner.databinding.ActivityScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@ExperimentalGetImage
private val Context.dataStore by preferencesDataStore(name = TYPE_SCAN_KEY)

@ExperimentalGetImage
class ScannerActivity : AppCompatActivity() {
    private val requestCodeCameraPermission = 1001
    private lateinit var binding: ActivityScannerBinding
    private lateinit var barcodeBoxView: BarcodeBoxView
    private lateinit var camera: Camera
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var codeSelected = BARCODE
    private var textDetected = false
    private var flashLightStatus: Boolean = false
    private val vinDetectedList = emptyList<String>().toMutableList()
    private var vinDetectedPosition = 0

    companion object {
        const val BARCODE = 0
        const val QRCODE = 1
        const val TEXTCODE = 2
        var typeCode = BARCODE
        const val VIN_SCANNED = 3
        const val TYPE_SCAN_KEY = "com.quantum.barcodescanner.typeScan.Key"
    }

    private lateinit var qrCodeAnalyzer: QrCodeAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        lifecycleScope.launch {
            try {
                val preferences = applicationContext.dataStore.data.first()
                val code = preferences[intPreferencesKey(TYPE_SCAN_KEY)]
                when(code) {
                    BARCODE -> {
                        binding.textBarcode.backgroundTintList =
                            ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
                        codeSelected = BARCODE
                        typeCode = codeSelected
                    }
                    QRCODE -> {
                        binding.textQrcode.backgroundTintList =
                            ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
                        codeSelected = QRCODE
                        typeCode = codeSelected
                    }
                    TEXTCODE -> {
                        binding.textTextcode.backgroundTintList =
                            ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
                        codeSelected = TEXTCODE
                        typeCode = codeSelected
                    }
                    else -> {
                        binding.textBarcode.backgroundTintList =
                            ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
                        codeSelected = BARCODE
                        typeCode = codeSelected
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        barcodeBoxView = BarcodeBoxView(this)
        addContentView(
            barcodeBoxView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        qrCodeAnalyzer = QrCodeAnalyzer(
            ::onTextFound,
            barcodeBoxView,
            binding.cameraPreviewView.width.toFloat(),
            binding.cameraPreviewView.height.toFloat()
        )

        if (ContextCompat.checkSelfPermission(
                this@ScannerActivity, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askForCameraPermission()
        } else {
            startCamera()
        }

        binding.errorMessageCard.setOnClickListener {
            binding.errorMessageCard.visibility = View.GONE
        }

        binding.next.setOnClickListener {
            vinDetectedPosition++
            binding.next.visibility = if(vinDetectedPosition != (vinDetectedList.size-1)) View.VISIBLE else View.INVISIBLE
            binding.previous.visibility = View.VISIBLE
            binding.textScanned.text = vinDetectedList[vinDetectedPosition]
            barcodeBoxView.selectPosition(vinDetectedPosition)
        }

        binding.previous.setOnClickListener {
            vinDetectedPosition--
            binding.previous.visibility = if(vinDetectedPosition > 0) View.VISIBLE else View.INVISIBLE
            binding.next.visibility = View.VISIBLE
            binding.textScanned.text = vinDetectedList[vinDetectedPosition]
            barcodeBoxView.selectPosition(vinDetectedPosition)
        }

        binding.continuar.setOnClickListener {
            val intent = Intent()
            intent.putExtra("VIN", binding.textScanned.text.toString())
            setResult(VIN_SCANNED, intent)
            binding.textScanned.text = null
            binding.cardTextScanned.isVisible = false
            onBackPressedDispatcher.onBackPressed()
        }

        binding.back.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.clear.setOnClickListener {
            binding.textScanned.text = null
            binding.cardTextScanned.isVisible = false
            textDetected = false
            barcodeBoxView.isDraw = false
            barcodeBoxView.deleteRects()
            vinDetectedPosition = 0
            vinDetectedList.clear()
        }

        binding.cardFlash.setOnClickListener {
            openFlashLight()
        }

        binding.cardBarcode.setOnClickListener {
            codeSelected = BARCODE
            binding.textBarcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
            binding.textQrcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.white, null))
            binding.textTextcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.white, null))
            typeCode = codeSelected
            val preferencesKey = intPreferencesKey(TYPE_SCAN_KEY)
            lifecycleScope.launch {
                applicationContext.dataStore.edit { preferences ->
                    preferences[preferencesKey] = codeSelected
                }
            }
        }

        binding.cardQrcode.setOnClickListener {
            codeSelected = QRCODE
            binding.textBarcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.white, null))
            binding.textQrcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
            binding.textTextcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.white, null))
            typeCode = codeSelected
            val preferencesKey = intPreferencesKey(TYPE_SCAN_KEY)
            lifecycleScope.launch {
                applicationContext.dataStore.edit { preferences ->
                    preferences[preferencesKey] = codeSelected
                }
            }
        }

        binding.cardText.setOnClickListener {
            codeSelected = TEXTCODE
            binding.textBarcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.white, null))
            binding.textQrcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.white, null))
            binding.textTextcode.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.yellow_black, null))
            typeCode = codeSelected
            val preferencesKey = intPreferencesKey(TYPE_SCAN_KEY)
            lifecycleScope.launch {
                applicationContext.dataStore.edit { preferences ->
                    preferences[preferencesKey] = codeSelected
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)
                }

            // Image analyzer
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor,
                        QrCodeAnalyzer(
                            ::onTextFound,
                            barcodeBoxView,
                            binding.cameraPreviewView.width.toFloat(),
                            binding.cameraPreviewView.height.toFloat()
                        )
                    )
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                camera.cameraInfo.hasFlashUnit()

            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onTextFound(vinList: List<String>) {
        if(vinList.isEmpty() && !binding.cardTextScanned.isVisible){
            binding.errorMessageCard.visibility = View.VISIBLE
        }else
        if (vinList.isNotEmpty() && (binding.textScanned.text.isNullOrEmpty() || vinDetectedList.size < vinList.size )) {
            binding.cardTextScanned.isVisible = true
            binding.textScanned.text = vinList[0]
            vinDetectedList.clear()
            vinDetectedList.addAll(vinList)
            textDetected = true
            binding.next.isVisible = vinList.size > 1
            binding.errorMessageCard.visibility = View.GONE
        }
    }

    private fun askForCameraPermission() {
        ActivityCompat.requestPermissions(
            this@ScannerActivity,
            arrayOf(Manifest.permission.CAMERA),
            requestCodeCameraPermission
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeCameraPermission && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(applicationContext, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun openFlashLight() {
        camera.cameraControl.enableTorch(!flashLightStatus)
        flashLightStatus = !flashLightStatus
        binding.flashIcon.backgroundTintList =
            ColorStateList.valueOf(
                if (flashLightStatus) resources.getColor(
                    R.color.yellow_black,
                    null
                ) else resources.getColor(R.color.white, null)
            )
    }
}