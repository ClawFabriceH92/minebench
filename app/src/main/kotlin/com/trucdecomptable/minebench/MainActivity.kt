package com.trucdecomptable.minebench

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import com.trucdecomptable.minebench.android.AndroidSensors
import com.trucdecomptable.minebench.ui.MineBenchApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val sensors = AndroidSensors(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    MineBenchApp(sensors)
                }
            }
        }
    }
}
