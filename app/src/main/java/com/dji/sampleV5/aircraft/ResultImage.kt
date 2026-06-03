package com.dji.sampleV5.aircraft

import android.graphics.Bitmap

/**
 * Project: sampleV5aircraft
 * From: com.dji.sampleV5.aircraft
 * Created by: FenixTags
 * On 6/3/2026
 * All Rights Reserved 2026
 */
data class ResultImage(val bitmap: Bitmap, val currentFps:Int, val filterTimeMs: Long)
