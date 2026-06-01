package com.dji.sampleV5.aircraft

/**
 * Project: sampleV5aircraft
 * From: com.dji.sampleV5.aircraft
 * Created by: FenixTags
 * On 6/1/2026
 * All Rights Reserved 2026
 */
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import java.io.ByteArrayOutputStream

/**
 * Clase modular que implementa CameraFrameListener para procesar video.
 * Sigue el flujo: addFrameListener() -> process -> removeFrameListener()
 */
class VisionProcessor(private val targetImageView: ImageView) : ICameraStreamManager.CameraFrameListener {

    // Handler para asegurar que la UI se actualice en el hilo principal
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    fun startListening() {
        if (isListening) return

        // Conexión oficial del SDK (Camino derecho del diagrama)
        MediaDataCenter.getInstance().cameraStreamManager.addFrameListener(
            ComponentIndexType.LEFT_OR_MAIN,
            ICameraStreamManager.FrameFormat.YUV420_888, // Formato estándar de extracción
            this
        )
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return

        // Desconexión oficial del SDK
        MediaDataCenter.getInstance().cameraStreamManager.removeFrameListener(this)
        isListening = false
    }

    /**
     * Este método se dispara automáticamente cientos de veces por segundo.
     * PRECAUCIÓN: Se ejecuta en un hilo de trabajo del SDK, no en el principal.
     */
    override fun onFrame(
        frameData: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int,
        format: ICameraStreamManager.FrameFormat
    ) {
        // 1. Decodificar la matriz de bytes crudos a un formato visualizable
        val rawBitmap = decodeYuvToBitmap(frameData, width, height)

        // 2. Aplicar tu lógica de filtrado (Visión artificial)
        val processedBitmap = applyAlgorithm(rawBitmap)

        // 3. Empujar el resultado de vuelta a la interfaz gráfica
        mainThreadHandler.post {
            targetImageView.setImageBitmap(processedBitmap)
        }
    }

    private fun decodeYuvToBitmap(frameData: ByteArray, width: Int, height: Int): Bitmap {
        val yuvImage = YuvImage(frameData, ImageFormat.NV21, width, height, null)
        val outputStream = ByteArrayOutputStream()
        // Comprime el cuadro a JPEG en memoria
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, outputStream)
        val imageBytes = outputStream.toByteArray()
        // Retorna el objeto Bitmap
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun applyAlgorithm(source: Bitmap): Bitmap {
        // AHORA: Retorna la imagen intacta.
        // FUTURO: Aquí es donde insertarás OpenCV, cálculos de matrices,
        // transformadas o filtros para el procesamiento del dron.
        return source
    }
}