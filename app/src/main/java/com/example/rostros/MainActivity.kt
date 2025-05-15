package com.example.rostros

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image

import java.io.IOException
import java.io.ByteArrayOutputStream

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject



class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceDetector: FaceDetector
    private var caraDetectada = false
    private val serverUrl = "http://192.168.100.66:5000/procesar_foto"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)

        // Configurar el detector de rostros con clasificación activada
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        faceDetector = FaceDetection.getClient(options)

        if (verificarPermisos()) {
            Toast.makeText(this, "PERMISOS DE CAMARA CONCEDIDOS", Toast.LENGTH_LONG).show()
            iniciarCamara()
        } else {
            solicitudPermisos.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        faceDetector.close()
    }


    private fun verificarPermisos() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    @OptIn(ExperimentalGetImage::class)
    private fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Configurar la vista previa
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Configurar el analizador de imágenes
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                        if (caraDetectada) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                            faceDetector.process(image).addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    val cara = faces[0]
                                    val probSonrisa = cara.smilingProbability

                                    if (probSonrisa != null && probSonrisa > 0.7f) {
                                        caraDetectada = true
                                        val fullBitmap = obtenerImagen(mediaImage, imageProxy.imageInfo.rotationDegrees)

                                        val faceRect = cara.boundingBox
                                        val padding = 20
                                        val x = (faceRect.left - padding).coerceAtLeast(0)
                                        val y = (faceRect.top - padding).coerceAtLeast(0)
                                        val right = (faceRect.right + padding).coerceAtMost(fullBitmap.width)
                                        val bottom = (faceRect.bottom + padding).coerceAtMost(fullBitmap.height)

                                        val width = right - x
                                        val height = bottom - y

                                        val faceBitmap = Bitmap.createBitmap(fullBitmap, x, y, width, height)

                                        runOnUiThread {
                                            Toast.makeText(this, "😊 ¡Estás sonriendo!", Toast.LENGTH_SHORT).show()
                                            enviarFotoAlServidor(faceBitmap)
                                        }

                                        // Pausar análisis 5 segundos
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            caraDetectada = false
                                        }, 5000)
                                    }
                                }
                            }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Error en la detección de rostros", e)
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            // Seleccionar la cámara frontal
            val tipoCamara = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, tipoCamara, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("MainActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    //Obtener la imagen del rostro detectado
    private fun obtenerImagen(image: Image, rotationDegrees: Int): Bitmap {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize) // ← ESTA LÍNEA ES LA CORRECTA

        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val yuv = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(yuv, 0, yuv.size)

        // Rotar la imagen si es necesario
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    private fun convertirImagen(bitmap: Bitmap): String {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }


    private fun enviarFotoAlServidor(bitmap: Bitmap) {
        val imagenBase64 = convertirImagen(bitmap)

        val json = JSONObject().apply {
            put("imagen", imagenBase64)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(serverUrl) // asegúrate que esta sea tu ruta Cloud Run o local correcta
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error al enviar foto: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("FOTO", "Error", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val respuesta = response.body?.string()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Respuesta del servidor: $respuesta", Toast.LENGTH_LONG).show()
                }
            }
        })
    }


    private val solicitudPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            iniciarCamara()
        } else {
            Toast.makeText(this, "Se necesita aceptar los permisos de camara", Toast.LENGTH_LONG).show()
        }
    }

    //Permiso de camara
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
