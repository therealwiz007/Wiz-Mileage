package com.wizlabs.mileage.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wizlabs.mileage.ui.theme.Wiz

/** Shows the bundled privacy policy (assets/privacy-policy.html). Works offline; JavaScript and network stay off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onClose: () -> Unit) {
    Scaffold(
        containerColor = Wiz.c.bg,
        topBar = {
            TopAppBar(
                title = { Text("Privacy policy") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Wiz.c.bg),
            )
        },
    ) { pad ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(pad),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    loadUrl("file:///android_asset/privacy-policy.html")
                }
            },
        )
    }
}
