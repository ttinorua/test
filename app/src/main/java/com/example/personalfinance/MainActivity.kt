package com.example.personalfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.personalfinance.ui.navigation.PersonalFinanceNavHost
import com.example.personalfinance.ui.theme.PersonalFinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PersonalFinanceApp

        setContent {
            PersonalFinanceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PersonalFinanceNavHost(repository = app.repository)
                }
            }
        }
    }
}
