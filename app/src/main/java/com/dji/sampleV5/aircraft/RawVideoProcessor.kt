package com.dji.sampleV5.aircraft

/**
 * Project: sampleV5aircraft
 * From: com.dji.sampleV5.aircraft
 * Created by: FenixTags
 * On 6/1/2026
 * All Rights Reserved 2026
 */

import android.view.SurfaceHolder
import android.view.SurfaceView
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager

class RawVideoProcessor(private val surfaceView: SurfaceView) {

    private var isStreaming = false

    fun startStream() {
        if (isStreaming) return

        val surfaceHolder: SurfaceHolder = surfaceView.holder
        val graphicSurface = surfaceHolder.surface

        // Validamos que la superficie gráfica esté lista para recibir datos
        if (graphicSurface == null || !graphicSurface.isValid) return

        val targetWidth = surfaceView.width
        val targetHeight = surfaceView.height

        // Si la vista aún no se ha dibujado en pantalla, sus dimensiones serán 0
        if (targetWidth <= 0 || targetHeight <= 0) return

        // Inyectamos el video por hardware
        MediaDataCenter.getInstance().cameraStreamManager.putCameraStreamSurface(
            ComponentIndexType.LEFT_OR_MAIN,
            graphicSurface,
            targetWidth,
            targetHeight,
            ICameraStreamManager.ScaleType.CENTER_CROP
        )
        isStreaming = true
    }

    fun stopStream() {
        if (!isStreaming) return

        // Desconectamos el flujo
        MediaDataCenter.getInstance().cameraStreamManager.removeCameraStreamSurface(surfaceView.holder.surface)
        isStreaming = false
    }
}