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
import android.widget.TextView
import androidx.core.graphics.createBitmap
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import org.opencv.android.Utils
import java.io.ByteArrayOutputStream
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Clase modular que implementa CameraFrameListener para procesar video.
 * Sigue el flujo: addFrameListener() -> process -> removeFrameListener()
 */
class VisionProcessor(private val targetImageViewRaw: ImageView,
                      private val targetImageViewProc: ImageView,
                      private val fpsTextRaw: TextView,
                      private val fpsTextProc: TextView
                    ) : ICameraStreamManager.CameraFrameListener {

    // Handler para asegurar que la UI se actualice en el hilo principal
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var frameCount = 0
    private var lastFps = 0
    private var lastTime = System.currentTimeMillis()

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
        // 1. CONVERSIÓN NATIVA CON OPENCV (Arregla colores y rendimiento)
        // Creamos una matriz para los bytes crudos (YUV requiere altura + altura/2)
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, frameData)
        val rgbaMat = Mat()
        // Convertimos de NV12 (Estándar DJI) a RGBA.
        // Si los colores siguen invertidos, cambia NV12 por NV21 en esta línea.
        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_I420)

        // 2. CREAR BITMAP RAW (Con los colores ya corregidos)
        val rawBitmap = createBitmap(rgbaMat.cols(), rgbaMat.rows())
        Utils.matToBitmap(rgbaMat, rawBitmap)

        // 2. Aplicar tu lógica de filtrado (Visión artificial)
        val processedBitmap = applyAlgorithm(rawBitmap)
        yuvMat.release()
        rgbaMat.release()


        // 3. Empujar el resultado de vuelta a la interfaz gráfica
        mainThreadHandler.post {
            targetImageViewRaw.setImageBitmap(rawBitmap)
            targetImageViewProc.setImageBitmap(processedBitmap.bitmap)
            fpsTextRaw.text = "FPS: ${processedBitmap.currentFps}"
            fpsTextProc.text = "FPS: ${processedBitmap.currentFps} T: ${processedBitmap.filterTimeMs}ms"
        }
    }

    private fun applyAlgorithm(originalBitmap: Bitmap): ResultImage {
        // AHORA: Retorna la imagen intacta.
        // FUTURO: Aquí es donde insertarás OpenCV, cálculos de matrices,
        // transformadas o filtros para el procesamiento del dron.
        val originalMat = Mat()
        Utils.bitmapToMat(originalBitmap, originalMat)

        // Inicio del procesamiento de OpenCV
        val filterStartTime = System.currentTimeMillis() // Inicia cronómetro del filtro
        val processedMat = Mat(originalMat.size(), originalMat.type(), Scalar(0.0, 0.0, 0.0, 255.0))

        // Para filtrar azul convertimos de RGB a HSV
        Imgproc.cvtColor(originalMat, processedMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_RGB2HSV)

        // 2) Rango para azul (ajusta H,S,V según tu escena)
        // H: 100..130 (en OpenCV 0..180), S: >=50, V: >=50
        val lower = Scalar(100.0, 50.0, 50.0)
        val upper = Scalar(130.0, 255.0, 255.0)
        // 3) Crear máscara
        val mask = Mat()
        Core.inRange(processedMat, lower, upper, mask) // mask: 0 o 255

        // 4. Aplicar máscara: Copia los píxeles de originalMat a processedMat SOLO donde hay azul
        originalMat.copyTo(processedMat, mask)

        //Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_RGB2RGBA)

        //Imgproc.cvtColor(originalMat, processedMat, Imgproc.COLOR_RGBA2GRAY)

        val filterTimeMs = System.currentTimeMillis() - filterStartTime // Termina cronómetro

        // 3. Convertir de vuelta a Bitmap para mostrar en pantalla
        val processedBitmap = createBitmap(processedMat.cols(), processedMat.rows())
        Utils.matToBitmap(processedMat, processedBitmap)


        // Lógica de FPS
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime >= 1000) {
            lastFps = frameCount
            frameCount = 0
            lastTime = currentTime
        }

        // Liberar memoria RAM en C++ inmediatamente para evitar crasheos
        originalMat.release()
        processedMat.release()
        mask.release()

        // 4. Actualizar el ImageView en el hilo principal de la interfaz
        return ResultImage(processedBitmap, lastFps, filterTimeMs)
    }
}
