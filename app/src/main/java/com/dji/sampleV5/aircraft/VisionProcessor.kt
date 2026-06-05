package com.dji.sampleV5.aircraft

/**
 * Project: sampleV5aircraft
 * From: com.dji.sampleV5.aircraft
 * Created by: FenixTags
 * On 6/1/2026
 * All Rights Reserved 2026
 */
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.createBitmap
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.core.Rect

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
        //val processedBitmap = applyAlgorithm(rawBitmap)
        val processedBitmap = colorDetectionCMSS(rawBitmap,
            Scalar(100.0, 150.0, 0.0),
            Scalar(140.0, 225.0, 225.0),
            Pair(width, height), // width x height, ex: 1920x1080
        )
        yuvMat.release()
        rgbaMat.release()


        // 3. Empujar el resultado de vuelta a la interfaz gráfica
        /*
        mainThreadHandler.post {
            targetImageViewRaw.setImageBitmap(rawBitmap)
            targetImageViewProc.setImageBitmap(processedBitmap.bitmap)
            fpsTextRaw.text = "FPS: ${processedBitmap.currentFps}"
            fpsTextProc.text = "FPS: ${processedBitmap.currentFps} T: ${processedBitmap.filterTimeMs}ms" +
                    "\n ${processedBitmap.bitmap.width}x${processedBitmap.bitmap.height} px"
        }
         */
        mainThreadHandler.post {
            targetImageViewRaw.setImageBitmap(rawBitmap)
            targetImageViewProc.setImageBitmap(processedBitmap.bitmap)
            fpsTextRaw.text = "FPS: ${processedBitmap.currentFps}"
            fpsTextProc.text = "FPS: ${processedBitmap.currentFps} T: ${processedBitmap.filterTimeMs}ms" +
                    "\n ${processedBitmap.bitmap.width}x${processedBitmap.bitmap.height} px"
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

        // 3. Convertir de vuelta a Bitmap para mostrar en pantalla
        val processedBitmap = createBitmap(processedMat.cols(), processedMat.rows())
        Utils.matToBitmap(processedMat, processedBitmap)

        val filterTimeMs = System.currentTimeMillis() - filterStartTime // Termina cronómetro

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

    private fun colorDetectionCMSS(rgbBitmap: Bitmap,
                                   lowerColor: Scalar,
                                   upperColor: Scalar,
                                   sizeImg: Pair<Int, Int>, // width x height, ex: 1920x1080
                                   kernelSize: Int = 5): CoordsCMSS{

        // Umbrales colores
        // azul = Scalar(100.0, 150.0, 0.0), Scalar(140, 225, 225)
        // rojo = Scalar(165.0, 70.0, 25.0), Scalar(215, 255, 255)
        // verde = Scalar(0.0, 0.0, 0.0), Scalar(180, 255, 255)
        // Variables a regresar
        var xCMSS: Int? = null
        var yCMSS: Int? = null
        val maskClean = Mat.zeros(sizeImg.second, sizeImg.first, CvType.CV_8UC1)

        val filterStartTime = System.currentTimeMillis() // Inicia cronómetro del filtro

        val originalMat = Mat()
        val hsvMat = Mat()
        Utils.bitmapToMat(rgbBitmap, originalMat)

        // Paso 1. Convertir frame al espacio HSV y guardar en hsv
        Imgproc.cvtColor(originalMat, originalMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(originalMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        // Paso 2. Crear mascara binaria
        val mask = Mat()
        Core.inRange(hsvMat, lowerColor, upperColor, mask)

        // Paso 3. Creamos kernel morfologico de tamano nxn
        val kernelSizeElement = Size(kernelSize.toDouble(), kernelSize.toDouble())
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,kernelSizeElement)

        // Paso 4. Aplicamos operacion close
        val maskClosed = Mat()
        Imgproc.morphologyEx(mask, maskClosed, Imgproc.MORPH_CLOSE, kernel)

        // Paso 5. Aplicamos operacion open
        Imgproc.morphologyEx(maskClosed, maskClean, Imgproc.MORPH_OPEN, kernel)

        // Recortamos zona superior de interes
        val roi = Rect(0, 0,
            sizeImg.first, (sizeImg.second * (280.0/480.0)).toInt())
        val maskCropped = Mat(maskClean, roi).clone()
        maskClean.setTo(Scalar(0.0))
        maskCropped.copyTo(maskClean.submat(roi))

        // Paso 7. Normalizamos mascara (no necesario)
        //croppedMask.convertTo(croppedMask, -1, 1.0 / 255.0)

        // Paso 8. Comprobamos que la mascara no esta vacia
        val maskCleanColor = Mat()
        Imgproc.cvtColor(maskClean, maskCleanColor, Imgproc.COLOR_GRAY2RGB)
        val moments = Imgproc.moments(maskClean)
        val m00 = moments.m00
        //if (Core.countNonZero(croppedMask) == 0) { // Por countNonZero
        if (m00 != 0.0) { // Por momentos
            // Paso 9. Calculamos centro de masa
            xCMSS = (moments.m10 / m00).toInt()
            yCMSS = (moments.m01 / m00).toInt()

            // Paso 10. Comprobamos que el centro de masa sea valido (comprobado anteriormente)

            // Paso 11. Pintamos centro de masa en la imagen original
            val point = Point(xCMSS.toDouble(), yCMSS.toDouble())

            Imgproc.circle(maskCleanColor, point, 5,
                Scalar(255.0, 0.0, 0.0), 5)

        }

        // Paso 11. Pintamos el centro de la imagen
        val point = Point((sizeImg.first/2).toDouble(), (sizeImg.second/2).toDouble())
        Imgproc.circle(maskCleanColor, point, 3,
            Scalar(0.0, 255.0, 0.0), 2)



        // Paso 11 (No anadido): Convertir Mat a Bitmap para mostrar en pantalla
        val bitmapMaskClean = createBitmap(maskCleanColor.cols(), maskCleanColor.rows())
        Utils.matToBitmap(maskCleanColor, bitmapMaskClean)

        // Limpiamos memoria
        originalMat.release()
        hsvMat.release()
        mask.release()
        maskClosed.release()
        kernel.release()
        maskClosed.release()
        maskCropped.release()
        maskClean.release()
        maskCleanColor.release()


        val filterTimeMs = System.currentTimeMillis() - filterStartTime // Termina cronómetro

        // Lógica de FPS
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime >= 1000) {
            lastFps = frameCount
            frameCount = 0
            lastTime = currentTime
        }

        return CoordsCMSS(xCMSS, yCMSS, bitmapMaskClean, lastFps, filterTimeMs)


    }
}
