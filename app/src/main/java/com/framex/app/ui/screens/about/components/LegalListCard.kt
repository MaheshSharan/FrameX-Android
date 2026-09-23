package com.framex.app.ui.screens.about.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val URL_PRIVACY_POLICY = "https://maheshsharan.github.io/FrameX-Android/privacy-policy"
private const val URL_LICENSE = "https://github.com/MaheshSharan/FrameX-Android/blob/main/LICENSE"
private const val URL_LIMITATIONS = "https://github.com/MaheshSharan/FrameX-Android/blob/main/KNOWN_LIMITATIONS.md"

@Composable
fun LegalListCard(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Legal",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
        ) {
            LegalListItem(
                icon = Icons.Default.Policy,
                title = "Privacy Policy",
                onClick = { onOpenUrl(URL_PRIVACY_POLICY) }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            LegalListItem(
                icon = Icons.Default.Code,
                title = "Open-source License",
                onClick = { onOpenUrl(URL_LICENSE) }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            LegalListItem(
                icon = Icons.Default.Gavel,
                title = "Known Limitations",
                onClick = { onOpenUrl(URL_LIMITATIONS) }
            )
        }
    }
}

@Composable
private fun LegalListItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Open $title",
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.DarkGray
        )
    }
}
