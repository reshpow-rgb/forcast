package com.forcast.asxvalue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forcast.asxvalue.ui.AsxValueNav
import com.forcast.asxvalue.ui.theme.AsxValueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AsxValueApp
        setContent {
            AsxValueTheme {
                AsxValueNav(app)
            }
        }
    }
}
