package com.dji.sampleV5.aircraft

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.SDKManagerCallback
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private val RUNTIME_PERMISSION_REQUEST_CODE = 9999

    // Vistas de la Interfaz
    private lateinit var layoutNormalView: View
    private lateinit var layoutFilteredView: View
    private lateinit var textStatusMonitor: TextView
    private lateinit var textFPSMonitorRaw: TextView
    private lateinit var textFPSMonitorProc: TextView

    // Procesadores Modulares
    private lateinit var rawVideoProcessor: RawVideoProcessor
    private lateinit var visionProcessor: VisionProcessor

    // Estado del sistema
    private var isShowingFilteredView = false
    private var isCameraAvailable = false

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

    // Escucha de disponibilidad de hardware (Cámara del dron)
    private val hardwareCameraListener = object : ICameraStreamManager.AvailableCameraUpdatedListener {
        override fun onAvailableCameraUpdated(availableCameraList: List<ComponentIndexType>) {
            isCameraAvailable = availableCameraList.isNotEmpty()

            runOnUiThread {
                if (isCameraAvailable) {
                    // Arrancamos el procesador que corresponda a la vista activa
                    if (isShowingFilteredView) {
                        visionProcessor.startListening()
                        textStatusMonitor.text = "Módulo: Visión Artificial (Activo)"
                    } else {
                        // El post asegura que la vista ya tiene dimensiones antes de inyectar el video
                        layoutNormalView.post {
                            rawVideoProcessor.startStream()
                        }
                        textStatusMonitor.text = "Módulo: Cámara Raw (Activo)"
                    }
                } else {
                    textStatusMonitor.text = "Esperando señal de video de la cámara..."
                    stopAllProcessors()
                }
            }
        }

        override fun onCameraStreamEnableUpdate(cameraStreamEnableMap: MutableMap<ComponentIndexType?, Boolean?>) {
            // No es necesario implementar lógica aquí para este caso de uso
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // INICIALIZACIÓN DE OPENCV
        if (OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV inicializado correctamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error crítico: OpenCV no se cargó", Toast.LENGTH_LONG).show()
            // Si no carga, no deberías permitir que el usuario cambie al filtro
        }

        // 1. Configurar Toolbar para el menú
        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)

        // 2. Enlazar contenedores principales
        layoutNormalView = findViewById(R.id.layout_normal_view)
        layoutFilteredView = findViewById(R.id.layout_filtered_view)
        textStatusMonitor = findViewById(R.id.text_status_monitor)
        textFPSMonitorRaw = findViewById(R.id.fpsTextView_raw)
        textFPSMonitorProc = findViewById(R.id.fpsTextView_opencv)

        // 3. Enlazar e instanciar los procesadores con sus vistas internas
        val surfaceVideoStream = findViewById<SurfaceView>(R.id.surface_video_stream)
        rawVideoProcessor = RawVideoProcessor(surfaceVideoStream)

        val ivRawVision = findViewById<ImageView>(R.id.iv_raw_vision)
        val ivFilteredVision = findViewById<ImageView>(R.id.iv_filtered_vision)
        visionProcessor = VisionProcessor(ivRawVision,
            ivFilteredVision,
            textFPSMonitorRaw,
            textFPSMonitorProc
        )
        // 4. Validar permisos e iniciar
        val missingPermissions = analyzeDeficientPermissions()
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), RUNTIME_PERMISSION_REQUEST_CODE)
        } else {
            initDJISDK()
        }
    }

    // --- MANEJO DE PERMISOS ---

    private fun analyzeDeficientPermissions(): List<String> {
        val deficiencies = mutableListOf<String>()
        for (permission in vitalPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
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
                initDJISDK()
            } else {
                Toast.makeText(this, "Permisos denegados. La app no puede funcionar.", Toast.LENGTH_LONG).show()
                textStatusMonitor.text = "Error: Permisos insuficientes."
            }
        }
    }

    // --- INICIALIZACIÓN DJI MSDK V5 ---

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
                    textStatusMonitor.text = "SDK Registrado. Esperando conexión del control remoto..."
                    // Conectamos el listener de la cámara solo cuando el registro es exitoso
                    MediaDataCenter.getInstance().cameraStreamManager
                        .addAvailableCameraUpdatedListener(hardwareCameraListener)
                }
            }

            override fun onRegisterFailure(error: IDJIError?) {
                runOnUiThread {
                    textStatusMonitor.text = "Error de registro SDK: ${error?.description()}"
                }
            }

            override fun onProductConnect(productId: Int) {
                runOnUiThread {
                    textStatusMonitor.text = "Hardware conectado. ID: $productId"
                }
            }

            override fun onProductDisconnect(productId: Int) {
                runOnUiThread {
                    textStatusMonitor.text = "Hardware desconectado."
                    stopAllProcessors()
                }
            }

            override fun onProductChanged(productId: Int) {}
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })
    }

    // --- MANEJO DEL MENÚ SUPERIOR ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_vistas, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_camara_normal -> {
                switchToNormalView()
                true
            }
            R.id.nav_camara_filtro -> {
                switchToFilteredView()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- LÓGICA DE INTERCAMBIO DE MÓDULOS (SWAP) ---

    private fun switchToNormalView() {
        if (!isShowingFilteredView) return
        isShowingFilteredView = false

        // Apagar visión artificial
        visionProcessor.stopListening()

        // Cambiar visibilidad de interfaces
        layoutFilteredView.visibility = View.GONE
        layoutNormalView.visibility = View.VISIBLE

        // Encender hardware raw stream si la cámara está activa
        if (isCameraAvailable) {
            layoutNormalView.post { rawVideoProcessor.startStream() }
            textStatusMonitor.text = "Módulo: Cámara Raw (Activo)"
        }
    }

    private fun switchToFilteredView() {
        if (isShowingFilteredView) return
        isShowingFilteredView = true

        // Apagar hardware raw stream
        rawVideoProcessor.stopStream()

        // Cambiar visibilidad de interfaces
        layoutNormalView.visibility = View.GONE
        layoutFilteredView.visibility = View.VISIBLE

        // Encender visión artificial si la cámara está activa
        if (isCameraAvailable) {
            visionProcessor.startListening()
            textStatusMonitor.text = "Módulo: Visión Artificial (Activo)"
        }
    }

    private fun stopAllProcessors() {
        rawVideoProcessor.stopStream()
        visionProcessor.stopListening()
    }

    // --- LIMPIEZA DE MEMORIA (CICLO DE VIDA) ---

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopAllProcessors()
            MediaDataCenter.getInstance().cameraStreamManager
                .removeAvailableCameraUpdatedListener(hardwareCameraListener)
        } catch (e: Exception) {
            // Se ignora de forma segura si el usuario cierra la app
            // antes de que el motor de DJI se haya inicializado.
        }
    }
}