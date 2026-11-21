package com.framex.app.ui.screens.about.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.FrameXTopBar
import com.framex.app.ui.screens.about.AboutHeroCache

private const val GITHUB_REPO_URL = "https://github.com/MaheshSharan/FrameX-Android"
private const val DEVELOPER_EMAIL = "maheshsharan28@gmail.com"
private val DarkBackground = Color(0xFF0C0D12)

/**
 * About & Legal Hero Section.
 * Renders the top app bar with 48dp optical balance back button, centered title,
 * hero character artwork asynchronously cached, app branding, and primary action buttons.
 */
@Composable
fun AboutHeroCard(
    versionName: String,
    versionCode: Long,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heroBitmap by produceState<ImageBitmap?>(
        initialValue = remember { AboutHeroCache.get() },
        key1 = Unit
    ) {
        if (value == null) {
            value = AboutHeroCache.load(context, R.drawable.about_hero)
        }
    }

    val onContactDeveloper: () -> Unit = {
        onOpenUrl("mailto:$DEVELOPER_EMAIL")
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        HeroBackgroundArtwork(
            bitmap = heroBitmap,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Top bar sits outside the 20dp content inset so the back arrow
            // lines up with every other full screen.
            FrameXTopBar(
                title = stringResource(R.string.about_legal_title),
                onNavigateBack = onNavigateBack,
                applyStatusBarsPadding = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                HeroBrandingContent(
                    versionName = versionName,
                    versionCode = versionCode
                )
                Spacer(modifier = Modifier.height(20.dp))
                HeroActionButtons(
                    onViewGithub = { onOpenUrl(GITHUB_REPO_URL) },
                    onContactDeveloper = onContactDeveloper
                )
            }
        }
    }
}

@Composable
private fun HeroBackgroundArtwork(
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        bitmap?.let { loadedBitmap ->
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Left-to-right scrim: keeps text & logo clear on left while artwork shines on right
        val horizontalScrim = remember {
            Brush.horizontalGradient(
                colors = listOf(
                    DarkBackground.copy(alpha = 0.94f),
                    DarkBackground.copy(alpha = 0.72f),
                    DarkBackground.copy(alpha = 0.25f),
                    Color.Transparent
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(horizontalScrim)
        )

        // Top-to-bottom scrim: blends top for status bar and bottom into pitch dark background
        val verticalScrim = remember {
            Brush.verticalGradient(
                colors = listOf(
                    DarkBackground.copy(alpha = 0.70f),
                    Color.Transparent,
                    DarkBackground.copy(alpha = 0.85f),
                    DarkBackground
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(verticalScrim)
        )

        // Subtle core neon bloom
        val primaryColor = MaterialTheme.colorScheme.primary
        val radialBloom = remember(primaryColor) {
            Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                radius = 500f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(radialBloom)
        )
    }
}

@Composable
private fun HeroBrandingContent(
    versionName: String,
    versionCode: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // App icon with neon border - vector logo fills container cleanly with transparent background
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F1015))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.framex_logo),
                contentDescription = "FrameX Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.70f, scaleY = 1.70f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FrameX Title: "Frame" in white, "X" in primary theme
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Frame",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "X",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "v$versionName (Build $versionCode)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFA0A0A0)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Real-time performance\nmonitoring for gamers.",
            fontSize = 13.sp,
            color = Color(0xFFCCCCCC),
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun HeroActionButtons(
    onViewGithub: () -> Unit,
    onContactDeveloper: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeroButton(
            iconRes = R.drawable.ic_github,
            title = stringResource(R.string.view_on_github),
            onClick = onViewGithub,
            modifier = Modifier.weight(1f)
        )

        HeroButton(
            iconRes = R.drawable.ic_gmail,
            title = stringResource(R.string.contact_developer),
            onClick = onContactDeveloper,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HeroButton(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC111218))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.40f),
            modifier = Modifier.size(16.dp)
        )
    }
}
