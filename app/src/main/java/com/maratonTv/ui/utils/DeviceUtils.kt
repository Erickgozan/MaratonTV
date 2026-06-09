package com.maratonTv.ui.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun isAndroidTv(): Boolean {
    val context = LocalContext.current
    return remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
}
