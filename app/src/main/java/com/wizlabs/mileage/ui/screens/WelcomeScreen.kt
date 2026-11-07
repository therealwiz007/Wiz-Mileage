package com.wizlabs.mileage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.wizlabs.mileage.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.ui.components.IconBadge
import com.wizlabs.mileage.ui.theme.Wiz

@Composable
fun WelcomeScreen(onStart: () -> Unit, onReceive: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.45f))
            }
            Spacer(Modifier.height(24.dp))
            Text("Wiz Mileage", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("Every litre. Every rupee. Every km.", style = MaterialTheme.typography.titleMedium, color = Wiz.c.accentInk)
            Spacer(Modifier.height(36.dp))
            Feature(Icons.Rounded.Speed, "Three numbers per fill-up", "Odometer, amount and RSP. The RSP is remembered, so most days you type just two.")
            Feature(Icons.Rounded.Insights, "Insights that mean something", "Real mileage, cost per km, spending pace, price changes and which fuel brand does best.")
            Feature(Icons.Rounded.Lock, "Yours alone", "Works offline. No account, no ads, no internet permission.")
        }
        Column {
        Button(
            onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent),
        ) { Text("Add your first vehicle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        androidx.compose.material3.TextButton(onClick = onReceive, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("Got a share code? Receive logs", color = Wiz.c.accentInk, fontWeight = FontWeight.SemiBold)
        }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(bottom = 22.dp), verticalAlignment = Alignment.Top) {
        IconBadge(icon, tint = Wiz.c.accentInk, bg = Wiz.c.surface2, size = 42)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Wiz.c.muted)
        }
    }
}
