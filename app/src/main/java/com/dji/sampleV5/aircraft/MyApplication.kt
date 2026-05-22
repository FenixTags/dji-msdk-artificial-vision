package com.dji.sampleV5.aircraft

/**
 * Project: sampleV5aircraft
 * From: dji.sampleV5.aircraft
 * Created by: FenixTags
 * On 5/21/2026
 * All Rights Reserved 2026
 */

import android.app.Application
import android.content.Context
import com.cySdkyc.clx.Helper

class MyApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Esto es obligatorio mantenerlo aquí porque desempaqueta
        // la seguridad nativa del MSDK V5 antes de que arranque la app.
        Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Hemos eliminado la inicialización del SDK de aquí.
    }
}