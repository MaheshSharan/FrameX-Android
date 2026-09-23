package com.framex.app.ui.screens.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framex.app.R

@Composable
fun OnboardingPageIndicator(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            val dotWidth by animateDpAsState(
                targetValue = if (isSelected) 32.dp else 8.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth"
            )
            val dotColor by animateColorAsState(
                targetValue = if (isSelected) activeColor else inactiveColor,
                label = "dotColor"
            )
            val pageSemanticLabel = stringResource(
                R.string.onboarding_page_n_of_m,
                index + 1,
                pageCount
            )

            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 48.dp)
                    .semantics {
                        contentDescription = pageSemanticLabel
                    }
                    .clickable(
                        role = Role.Button,
                        onClick = { onPageSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}
