package com.parrotworks.oneagentarmy.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Thumbnail for a locally stored attachment image. Files are app-managed and
// already re-encoded small, so a plain BitmapFactory decode on IO (downsampled
// to ~800px) is enough - no image-loading library needed.
@Composable
fun AttachmentImage(
    absolutePath: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = absolutePath) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= TARGET_EDGE_PX) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

private const val TARGET_EDGE_PX = 800
