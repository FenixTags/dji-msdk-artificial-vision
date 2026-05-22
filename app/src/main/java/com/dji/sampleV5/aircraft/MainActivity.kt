package com.dji.sampleV5.aircraft

/**
 * Project: sampleV5aircraft
 * From: dji.sampleV5.aircraft
 * Created by: FenixTags
 * On 5/21/2026
 * All Rights Reserved 2026
 */

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.SDKManagerCallback

class MainActivity : AppCompatActivity() {

    private val TAG = "MainControlLogic"
    private val RUNTIME_PERMISSION_REQUEST_CODE = 9999

    private lateinit var surfaceVideoStream: SurfaceView
    private lateinit var textStatusMonitor: TextView

    // Lógica dinámica de permisos para Android 13+ y versiones anteriores
    private val vitalPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private val hardwareCameraListener = object : ICameraStreamManager.AvailableCameraUpdatedListener {
        override fun onAvailableCameraUpdated(availableCameraList: List<ComponentIndexType>) {
            if (availableCameraList.isNotEmpty()) {
                runOnUiThread { textStatusMonitor.text = "Módulo Óptico Detectado. Iniciando video..." }
                routeVideoStreamToGraphicSurface()
            } else {
                runOnUiThread { textStatusMonitor.text = "Esperando señal de video de la cámara..." }
            }
        }
        override fun onCameraStreamEnableUpdate(cameraStreamEnableMap:
                                                MutableMap<ComponentIndexType?, Boolean?>) {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceVideoStream = findViewById(R.id.surface_video_stream)
        textStatusMonitor = findViewById(R.id.text_status_monitor)

        val missingPermissions = analyzeDeficientPermissions()
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), RUNTIME_PERMISSION_REQUEST_CODE)
        } else {
            // Los permisos ya fueron otorgados previamente, iniciamos el SDK de forma segura.
            initDJISDK()
        }
    }

    private fun analyzeDeficientPermissions(): List<String> {
        val deficiencies = mutableListOf<String>()
        for (permission in vitalPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)!= PackageManager.PERMISSION_GRANTED) {
                deficiencies.add(permission)
            }
        }
        return deficiencies
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RUNTIME_PERMISSION_REQUEST_CODE) {
            val pendingDeficiencies = analyzeDeficientPermissions()
            if (pendingDeficiencies.isEmpty()) {
                // El usuario acaba de aceptar los permisos. Ahora sí, arrancamos el motor.
                initDJISDK()
            } else {
                Toast.makeText(this, "Permisos denegados. La app no puede funcionar.", Toast.LENGTH_LONG).show()
                textStatusMonitor.text = "Error: Permisos insuficientes."
            }
        }
    }

    /**
     * Esta función centraliza la inicialización.
     * Garantiza que primero se valide la App Key y, ÚNICAMENTE si es exitoso,
     * se intente jalar el video de la cámara.
     */
    private fun initDJISDK() {
        textStatusMonitor.text = "Iniciando motor DJI SDK..."

        SDKManager.getInstance().init(this.applicationContext, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onRegisterSuccess() {
                runOnUiThread {
                    textStatusMonitor.text = "SDK Registrado. Esperando a que conectes el control remoto..."
                    // Habilitamos la cámara solo cuando el SDK es funcional
                    attachCameraStreamObserver()
                }
            }

            override fun onRegisterFailure(error: IDJIError?) {
                runOnUiThread {
                    textStatusMonitor.text = "Error de registro: ${error?.description()}"
                }
            }

            override fun onProductConnect(productId: Int) {
                runOnUiThread {
                    textStatusMonitor.text = "Hardware conectado. ID: $productId"
                }
            }

            override fun onProductDisconnect(productId: Int) {
                runOnUiThread { textStatusMonitor.text = "Hardware desconectado." }
            }

            override fun onProductChanged(productId: Int) {}
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })
    }

    private fun attachCameraStreamObserver() {
        MediaDataCenter.getInstance().cameraStreamManager.addAvailableCameraUpdatedListener(hardwareCameraListener)
    }

    private fun routeVideoStreamToGraphicSurface() {
        val surfaceHolder: SurfaceHolder = surfaceVideoStream.holder
        val graphicSurface = surfaceHolder.surface

        if (graphicSurface == null ||!graphicSurface.isValid) return

        val targetWidth = surfaceVideoStream.width
        val targetHeight = surfaceVideoStream.height

        if (targetWidth <= 0 || targetHeight <= 0) return

        MediaDataCenter.getInstance().cameraStreamManager.putCameraStreamSurface(
            ComponentIndexType.LEFT_OR_MAIN,
            graphicSurface,
            targetWidth,
            targetHeight,
            ICameraStreamManager.ScaleType.CENTER_CROP
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        val multimediaStreamManager = MediaDataCenter.getInstance().cameraStreamManager
        multimediaStreamManager.removeCameraStreamSurface(surfaceVideoStream.holder.surface)
        multimediaStreamManager.removeAvailableCameraUpdatedListener(hardwareCameraListener)
    }
}
