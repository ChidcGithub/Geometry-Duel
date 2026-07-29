package com.geometryduel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** 主 Activity：edge-to-edge + Compose。 */
class MainActivity : ComponentActivity() {

    private lateinit var controller: DuelController

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = DuelController(applicationContext)
        controller.start()
        setContent {
            DuelApp(controller)
        }
    }

    override fun onPause() {
        super.onPause()
        controller.onAppPause()
    }

    override fun onResume() {
        super.onResume()
        controller.onAppResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) controller.shutdown()
    }
}
