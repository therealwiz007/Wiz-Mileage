package com.wizlabs.mileage.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.wizlabs.mileage.data.ShareCodec
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.Vehicle
import com.wizlabs.mileage.ui.components.IconBadge
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.SelectChip
import com.wizlabs.mileage.ui.components.WizCard
import com.wizlabs.mileage.ui.components.icon
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

// ------------------------------------------------------------------ QR drawing

/** Draws a QR code with rounded modules and rounded finder eyes, dark on white for reliable scanning. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
        )
    }
    val ink = Color(0xFF111114)
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val n = matrix.width
            val m = size.width / n
            fun eye(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
            for (y in 0 until n) for (x in 0 until n) {
                if (!matrix[x, y] || eye(x, y)) continue
                drawRoundRect(ink, Offset(x * m + m * 0.06f, y * m + m * 0.06f), Size(m * 0.88f, m * 0.88f), CornerRadius(m * 0.32f))
            }
            listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEach { (ex, ey) ->
                val o = Offset(ex * m, ey * m)
                drawRoundRect(ink, o + Offset(m / 2, m / 2), Size(m * 6, m * 6), CornerRadius(m * 2f), style = Stroke(m))
                drawRoundRect(ink, o + Offset(m * 2, m * 2), Size(m * 3, m * 3), CornerRadius(m * 1.1f))
            }
        }
    }
}

private fun Context.copy(text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Wiz Mileage share code", text))
}

// ------------------------------------------------------------------ Send

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(data: AppData, current: Vehicle, onClose: () -> Unit, onCopied: () -> Unit) {
    val ctx = LocalContext.current
    var all by rememberSaveable { mutableStateOf(false) }
    val vehicles = if (all) data.vehicles else listOf(current)
    val fills = data.fills.filter { f -> vehicles.any { it.id == f.vehicleId } }
    val code = remember(vehicles, fills) { ShareCodec.encode(vehicles, fills) }
    val frames = remember(code) { ShareCodec.qrFrames(code) }
    var frame by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) { while (frames.size > 1) { delay(1100); frame = (frame + 1) % frames.size } }

    // Full brightness while the QR is on screen makes scanning faster.
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        val before = w?.attributes?.screenBrightness
        w?.attributes = w?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }

    val title = if (all) "${vehicles.size} vehicles" else current.name
    Scaffold(
        containerColor = Wiz.c.bg,
        topBar = {
            TopAppBar(
                title = { Text("Share logs") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Wiz.c.bg),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (data.vehicles.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip(current.name, !all, { all = false }, leading = { Icon(current.type.icon(), null, Modifier.size(18.dp), tint = if (!all) Wiz.c.onAccent else Wiz.c.text) })
                SelectChip("All ${data.vehicles.size} vehicles", all, { all = true })
            }

            WizCard(Modifier.fillMaxWidth(), padding = 18) {
                AnimatedContent(frame, label = "qr") { f -> QrImage(frames.getOrElse(f) { frames[0] }, Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(14.dp))
                if (frames.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        frames.indices.forEach { i ->
                            Box(Modifier.padding(horizontal = 3.dp).size(if (i == frame) 10.dp else 7.dp).clip(CircleShape)
                                .background(if (i == frame) Wiz.c.accentInk else Wiz.c.line))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Code ${frame + 1} of ${frames.size} · keep the other phone pointed until all are caught",
                        style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Scan with Wiz Mileage on the other phone", style = MaterialTheme.typography.bodySmall,
                        color = Wiz.c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            WizCard(Modifier.fillMaxWidth(), padding = 16) {
                Overline("What's inside")
                Spacer(Modifier.height(4.dp))
                Text("$title · ${fills.size} fill-up${if (fills.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                fills.minOfOrNull { it.date }?.let { a ->
                    Text("${Fmt.date(a)} – ${Fmt.date(fills.maxOf { it.date })} · ${Fmt.inr(fills.sumOf { it.amount })}",
                        style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Wiz.c.muted)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    code.take(64) + "…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Wiz.c.muted, maxLines = 2,
                )
                Text("Share code · ${Fmt.group(code.length.toLong())} characters", style = MaterialTheme.typography.labelSmall, color = Wiz.c.muted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val msg = "Wiz Mileage · $title · ${fills.size} fill-ups\n" +
                            "Open Wiz Mileage → Receive logs → Paste code (or just share this message to the app).\n\n$code"
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Send share code"))
                    },
                    modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent),
                ) { Icon(Icons.Rounded.Send, null); Spacer(Modifier.width(8.dp)); Text("Send code", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { ctx.copy(code); onCopied() },
                    modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Rounded.ContentCopy, null, tint = Wiz.c.text); Spacer(Modifier.width(8.dp)); Text("Copy", color = Wiz.c.text) }
            }
            Text(
                "Works without internet. The code holds a copy of the log; nothing is uploaded anywhere. " +
                    "Anyone who gets the code can read these fill-ups.",
                style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
            )
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

// ------------------------------------------------------------------ Receive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    data: AppData,
    initialText: String?,
    onImport: (ShareCodec.Bundle) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(if (initialText != null) 1 else 0) }
    var pasted by rememberSaveable { mutableStateOf(initialText ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var bundle by remember { mutableStateOf<ShareCodec.Bundle?>(null) }

    fun tryDecode(text: String) {
        try { bundle = ShareCodec.decode(text); error = null } catch (e: ShareCodec.InvalidCode) { error = e.message; bundle = null }
    }
    LaunchedEffect(initialText) { initialText?.let { tryDecode(it) } }

    Scaffold(
        containerColor = Wiz.c.bg,
        topBar = {
            TopAppBar(
                title = { Text("Receive logs") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Wiz.c.bg),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val b = bundle
            if (b != null) {
                ImportPreview(data, b, onImport = { onImport(b) }, onCancel = { bundle = null })
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Scan QR", mode == 0, { mode = 0 }, leading = { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp), tint = if (mode == 0) Wiz.c.onAccent else Wiz.c.text) })
                SelectChip("Paste code", mode == 1, { mode = 1 }, leading = { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp), tint = if (mode == 1) Wiz.c.onAccent else Wiz.c.text) })
            }
            if (mode == 0) {
                Scanner(onCode = { tryDecode(it) })
                Text("On the other phone open Wiz Mileage → Share logs, then point this camera at the QR code.",
                    style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted)
            } else {
                OutlinedTextField(
                    value = pasted, onValueChange = { pasted = it; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    label = { Text("Share code") }, placeholder = { Text("WZM1:…") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                            val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                            if (t != null) { pasted = t; tryDecode(t) } else error = "The clipboard is empty."
                        },
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                    ) { Icon(Icons.Rounded.ContentPaste, null, tint = Wiz.c.text); Spacer(Modifier.width(8.dp)); Text("Paste", color = Wiz.c.text) }
                    Button(
                        onClick = { tryDecode(pasted) }, enabled = pasted.isNotBlank(),
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent),
                    ) { Text("Read code", fontWeight = FontWeight.Bold) }
                }
            }
            error?.takeIf { mode == 0 }?.let { Text(it, color = Wiz.c.bad, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ImportPreview(data: AppData, b: ShareCodec.Bundle, onImport: () -> Unit, onCancel: () -> Unit) {
    val plan = remember(data, b) { ShareCodec.merge(data, b).second }
    WizCard(Modifier.fillMaxWidth(), padding = 20) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Wiz.c.good, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Code received", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        b.vehicles.forEach { v ->
            val f = b.fills.filter { it.vehicleId == v.id }
            val existing = data.vehicles.firstOrNull { it.name.equals(v.name, ignoreCase = true) }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(v.type.icon(), size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${f.size} fill-ups" + (f.minOfOrNull { it.date }?.let { " · ${Fmt.dateShort(it)} – ${Fmt.dateShort(f.maxOf { x -> x.date })}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                    )
                }
                Text(if (existing != null) "Merge" else "New", style = MaterialTheme.typography.labelLarge,
                    color = if (existing != null) Wiz.c.accentInk else Wiz.c.good, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            buildString {
                append("${plan.newFills} new fill-up${if (plan.newFills == 1) "" else "s"} will be added")
                if (plan.duplicates > 0) append(", ${plan.duplicates} already in your log will be skipped")
                append(".")
                if (plan.mergedInto.isNotEmpty()) append(" Vehicles with the same name are merged.")
            },
            style = MaterialTheme.typography.bodyMedium, color = Wiz.c.muted,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onImport, enabled = plan.newFills > 0 || plan.newVehicles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent),
        ) { Text(if (plan.newFills > 0 || plan.newVehicles.isNotEmpty()) "Import" else "Nothing new to import", fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Scan or paste another", color = Wiz.c.text)
        }
    }
}

@Composable
private fun Scanner(onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val collector = remember { ShareCodec.FrameCollector() }
    var have by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (!granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Camera access is needed to scan", color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("It's only used for the QR code and nothing is saved. You can also paste the code instead.",
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { ask.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent)) { Text("Allow camera") }
            }
            return@Box
        }
        val owner = LocalLifecycleOwner.current
        val executor = remember { Executors.newSingleThreadExecutor() }
        DisposableEffect(Unit) { onDispose { executor.shutdown() } }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val view = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val reader = MultiFormatReader().apply {
                        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
                    }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { img ->
                        try {
                            val plane = img.planes[0]
                            val buf = plane.buffer
                            val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                            val src = PlanarYUVLuminanceSource(bytes, plane.rowStride, img.height, 0, 0, img.width, img.height, false)
                            val text = try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text } catch (_: Exception) { null } finally { reader.reset() }
                            if (text != null) view.post {
                                if (!done && collector.offer(text)) {
                                    have = collector.have; total = collector.total
                                    collector.code?.let { done = true; onCode(it) }
                                } else if (!done && !text.startsWith("WZM")) {
                                    total = -1
                                }
                            }
                        } finally { img.close() }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(c))
                view
            },
        )
        // Viewfinder corners
        val accent = Wiz.c.accent
        Canvas(Modifier.fillMaxSize().padding(40.dp)) {
            val l = size.width * 0.16f; val s = 5.dp.toPx()
            listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f)).forEach { (p, d) ->
                drawLine(accent, p, p + Offset(l * d.x, 0f), s, StrokeCap.Round)
                drawLine(accent, p, p + Offset(0f, l * d.y), s, StrokeCap.Round)
            }
        }
        val status = when {
            total == -1 -> "That QR code isn't a Wiz share code"
            total > 1 -> "Caught $have of $total codes · keep steady"
            else -> "Point at the QR code"
        }
        Text(
            status, color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
