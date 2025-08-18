package com.example.emt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.emt.ui.MainScreen
import com.example.emt.ui.theme.EMTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EMTTheme {
                MainScreen(application as EMTApplication)
            }
        }
    }
}
