package com.davidgcd.backlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidgcd.backlog.ui.backlog.BacklogScreen
import com.davidgcd.backlog.ui.backlog.BacklogViewModelFactory
import com.davidgcd.backlog.ui.theme.BacklogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as BacklogApplication).repository

        setContent {
            BacklogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel = viewModel(factory = BacklogViewModelFactory(repository))
                    BacklogScreen(viewModel = viewModel)
                }
            }
        }
    }
}
