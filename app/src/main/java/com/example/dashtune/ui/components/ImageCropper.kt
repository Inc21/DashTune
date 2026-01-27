package com.example.dashtune.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.dashtune.util.ImageProcessor

@Composable
fun ImageCropper(
    imageUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onCropCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        // Skip cropping UI and process image directly
        val processedPath = ImageProcessor.processForAndroidAuto(context, imageUri)
        if (processedPath != null) {
            onCropComplete(Uri.parse(processedPath))
        } else {
            // Fallback to original URI if processing fails
            onCropComplete(imageUri)
        }
    }
}
