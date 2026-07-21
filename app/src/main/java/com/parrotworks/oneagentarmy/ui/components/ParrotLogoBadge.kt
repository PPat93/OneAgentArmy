package com.parrotworks.oneagentarmy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.parrotworks.oneagentarmy.R

// The source artwork's content isn't inscribed with enough margin in its square canvas for a
// full-circle clip to avoid cutting into it (measured: as little as ~11% margin, a circle
// needs ~14.6%+ at the corners) - so the image is drawn smaller than the circle (innerSize,
// pick ~82% of size) against a background matching the launcher icon's own backdrop color.
// The source PNG is transparent outside the artwork, so this reads as one solid circular
// badge with zero cropping instead of a square photo floating inside a ring.
@Composable
fun ParrotLogoBadge(size: Dp, innerSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_parrot),
            contentDescription = null,
            modifier = Modifier.size(innerSize),
        )
    }
}
