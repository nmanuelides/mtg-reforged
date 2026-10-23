package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.R
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.platform.copyToClipboard
import io.github.nmanuelides.mtgreforged.platform.imageRes
import io.github.nmanuelides.mtgreforged.platform.openUrl
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.delay

private const val ALIAS = "magatsu.mp"

/** Same content as the website's Support section: who makes the app and the Mercado Pago alias. */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalAppContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 600.dp) {
        SectionLabel("Support")
        Spacer(Modifier.height(8.dp))
        Text(
            "Buy the developer a coffee", fontFamily = Cinzel, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, color = Palette.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "MTG Reforged is made by one developer, in his spare time. It's free and has no ads. If it makes your games better, a small donation helps keep it growing.",
            style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("From Argentina", fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Palette.Terracotta)
                Text(
                    "Transfer to the Mercado Pago alias from any bank or wallet app. Transfers are free, so all of it arrives.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                )
                Text(
                    ALIAS, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Palette.Ink,
                    modifier = Modifier.paper(Palette.Paper).border(1.5.dp, Palette.LineStrong).padding(horizontal = 14.dp, vertical = 8.dp),
                )
                OrnateButton(
                    if (copied) "Copied" else "Copy alias",
                    { context.copyToClipboard(ALIAS); copied = true },
                    icon = Icons.Default.ContentCopy, style = OrnateStyle.Primary,
                )
            }
            // The Mercado Pago logo opens their site.
            Image(
                imageRes(R.drawable.mercadopago_logo), contentDescription = "Mercado Pago",
                modifier = Modifier.width(96.dp).clickable { context.openUrl("https://www.mercadopago.com.ar") },
            )
        }
    }
}
