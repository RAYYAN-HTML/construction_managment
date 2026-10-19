package com.siteledger.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import java.util.UUID

@Composable
fun PhotoAttachment(path: String?, onPathChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile
        if (saved && file?.exists() == true) {
            scope.launch {
                val compressed = withContext(Dispatchers.IO) { compressPhoto(file) }
                if (compressed) onPathChange(file.absolutePath)
            }
        }
        else file?.delete()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.filesDir, "ledger_photos").apply { mkdirs() }
                    val target = File(directory, "${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                        ?: error("Could not read the selected photo.")
                    compressPhoto(target)
                    target.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) onPathChange(savedPath)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picker.launch("image/*") }) {
            Icon(Icons.Default.Image, contentDescription = null)
            Text(" Choose photo")
        }
        TextButton(onClick = {
            runCatching {
                val directory = File(context.filesDir, "ledger_photos").apply { mkdirs() }
                val file = File(directory, "${UUID.randomUUID()}.jpg")
                cameraFile = file
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            }
        }) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Text(" Take photo")
        }
    }
    if (path != null) {
        TextButton(onClick = { onPathChange(null) }) { Text("Remove attached photo") }
        val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
            value = withContext(Dispatchers.IO) { loadProofImage(path) }
        }
        bitmap?.let { Image(it, contentDescription = "Attached proof", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit) }
    }
}

private fun compressPhoto(file: File): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sample = maxOf(1, maxOf(bounds.outWidth / 1600, bounds.outHeight / 1600))
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    bitmap.recycle()
    true
}.getOrDefault(false)

private fun loadProofImage(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = maxOf(1, maxOf(bounds.outWidth / 1200, bounds.outHeight / 1200))
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
