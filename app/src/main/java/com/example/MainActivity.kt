package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.ClientFlowApp
import com.example.ui.ClientFlowViewModel

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val viewModel = ViewModelProvider(this)[ClientFlowViewModel::class.java]
    
    setContent {
      ClientFlowApp(viewModel = viewModel)
    }
  }
}
