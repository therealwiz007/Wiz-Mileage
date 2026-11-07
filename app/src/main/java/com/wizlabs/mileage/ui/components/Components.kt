package com.wizlabs.mileage.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirportShuttle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Moped
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wizlabs.mileage.insights.Tip
import com.wizlabs.mileage.insights.TipKind
import com.wizlabs.mileage.model.Brand
import com.wizlabs.mileage.model.VehicleType
import com.wizlabs.mileage.ui.theme.Numeric
import com.wizlabs.mileage.ui.theme.Wiz

fun VehicleType.icon(): ImageVector = when (this) {
    VehicleType.BIKE -> Icons.Rounded.TwoWheeler
    VehicleType.SCOOTER -> Icons.Rounded.Moped
    VehicleType.CAR -> Icons.Rounded.DirectionsCar
    VehicleType.SUV -> Icons.Rounded.AirportShuttle
    VehicleType.OTHER -> Icons.Rounded.Commute
}

val Brand.colour: Color get() = Color(color)

@Composable
fun WizCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = Wiz.c.surface,
        border = BorderStroke(1.dp, Wiz.c.line),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

@Composable
fun Overline(text: String, color: Color = Wiz.c.muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) {
            Text(action, color = Wiz.c.accentInk, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, subColor: Color? = null) {
    WizCard(modifier, padding = 16) {
        Overline(label)
        Spacer(Modifier.height(6.dp))
        Text(
            value, style = MaterialTheme.typography.titleLarge.merge(Numeric), maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = subColor ?: Wiz.c.muted, maxLines = 2)
        }
    }
}

@Composable
fun BrandDot(brand: Brand?, size: Int = 10) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(brand?.colour ?: Wiz.c.muted.copy(alpha = 0.4f)),
    )
}

@Composable
fun TipCard(tip: Tip, modifier: Modifier = Modifier) {
    val (icon, tint) = when (tip.kind) {
        TipKind.WARN -> Icons.Rounded.WarningAmber to Wiz.c.warn
        TipKind.GOOD -> Icons.Rounded.AutoAwesome to Wiz.c.good
        TipKind.INFO -> Icons.Rounded.Info to Wiz.c.accentInk
    }
    WizCard(modifier.fillMaxWidth(), padding = 16) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tip.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = Wiz.c.muted)
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val bg = if (selected) Wiz.c.accent else Wiz.c.surface2
    val fg = if (selected) Wiz.c.onAccent else Wiz.c.text
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Wiz.c.onAccent, bg: Color = Wiz.c.accent, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32).dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.55).dp)) }
}
