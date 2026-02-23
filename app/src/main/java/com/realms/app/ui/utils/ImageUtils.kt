// File: ui/utils/ImageUtils.kt
package com.realms.app.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun getResizedImageBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            // Logica di ridimensionamento (es: max 1024px)
            val maxDimension = 1024
            val width = originalBitmap.width
            val height = originalBitmap.height

            val (newWidth, newHeight) = if (width > height) {
                maxDimension to (height * maxDimension / width)
            } else {
                (width * maxDimension / height) to maxDimension
            }

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

            val outputStream = ByteArrayOutputStream()
            // Comprimiamo in JPG all'80% per un ottimo bilanciamento qualità/peso
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}