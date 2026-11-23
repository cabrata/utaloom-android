package com.metrolist.music.ui.screens.encore

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.metrolist.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal suspend fun shareEncore(context: Context, bitmap: Bitmap, year: Int) {
    val uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "images")
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not create recap cache directory")
        }
        val image = File.createTempFile("utaloom-encore-$year-", ".png", directory)
        image.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Could not encode recap image")
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", image)
    }

    withContext(Dispatchers.Main) {
        val title = context.getString(R.string.encore_recap_share)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
