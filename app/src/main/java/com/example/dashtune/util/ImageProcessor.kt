package com.example.dashtune.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageProcessor {
    // Recommended dimensions for Android Auto artwork
    private const val TARGET_WIDTH = 512
    private const val TARGET_HEIGHT = 512
    private const val MAX_FILE_SIZE_KB = 2000 // 2MB
    private const val COMPRESSION_QUALITY = 90

    /**
     * Processes an image URI to meet Android Auto requirements
     * @return Path to processed image file or null if processing failed
     */
    fun processForAndroidAuto(context: Context, uri: Uri): String? {
        return try {
            // 1. Load bitmap with correct orientation
            val bitmap = loadCorrectlyOrientedBitmap(context, uri) ?: return null
            
            // 2. Scale to target dimensions while maintaining aspect ratio
            val scaledBitmap = scaleBitmap(bitmap)
            
            // 3. Compress to JPEG format
            val outputFile = createTempFile(context)
            compressToFile(scaledBitmap, outputFile)
            
            // 4. Verify final file meets requirements
            if (outputFile.length() > MAX_FILE_SIZE_KB * 1024) {
                null // File too large
            } else {
                outputFile.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadCorrectlyOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val inputStream = context.contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        
        // Calculate sample size to reduce memory usage
        options.inSampleSize = calculateInSampleSize(options, TARGET_WIDTH, TARGET_HEIGHT)
        options.inJustDecodeBounds = false
        
        val newInputStream = context.contentResolver.openInputStream(uri)
        var bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
        newInputStream?.close()
        
        // Fix orientation if needed
        val exif = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it)
        }
        
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        ) ?: ExifInterface.ORIENTATION_UNDEFINED
        
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap!!, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap!!, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap!!, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && 
                   halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        
        if (aspectRatio > 1) {
            // Landscape
            targetWidth = TARGET_WIDTH
            targetHeight = (TARGET_WIDTH / aspectRatio).toInt()
        } else {
            // Portrait or square
            targetHeight = TARGET_HEIGHT
            targetWidth = (TARGET_HEIGHT * aspectRatio).toInt()
        }
        
        return Bitmap.createScaledBitmap(
            bitmap, targetWidth, targetHeight, true
        )
    }

    private fun createTempFile(context: Context): File {
        return File.createTempFile(
            "processed_", ".jpg", context.cacheDir
        )
    }

    private fun compressToFile(bitmap: Bitmap, outputFile: File) {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
        }
    }
}
