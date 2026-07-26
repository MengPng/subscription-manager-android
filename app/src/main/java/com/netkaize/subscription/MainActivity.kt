package com.netkaize.subscription

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.netkaize.subscription.data.LegacyWebDataMigrator
import com.netkaize.subscription.ui.AppViewModel
import com.netkaize.subscription.ui.FileActions
import com.netkaize.subscription.ui.SubscriptionApp
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var pendingBackup: String? = null
    private var pendingImage: ((String) -> Unit)? = null

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingBackup
        pendingBackup = null
        if (uri != null && content != null) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = contentResolver.openOutputStream(uri) ?: error("无法打开目标文件")
                        stream.bufferedWriter().use { it.write(content) }
                    }
                }
                result.onSuccess { viewModel.showMessage("备份已导出") }
                    .onFailure { viewModel.showMessage("备份导出失败：${it.message ?: "无法写入文件"}") }
            }
        }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = contentResolver.openInputStream(uri) ?: error("无法打开备份文件")
                        stream.bufferedReader().use { it.readText() }
                    }
                }
                result.onSuccess(viewModel::importBackup)
                    .onFailure { viewModel.showMessage("备份读取失败：${it.message ?: "文件不可用"}") }
            }
        }
    }

    private val openImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingImage
        pendingImage = null
        if (uri != null && callback != null) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { decodeImage(uri) } }
                result.onSuccess(callback)
                    .onFailure { viewModel.showMessage("图片读取失败：${it.message ?: "请选择其他图片"}") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubscriptionApp(
                viewModel = viewModel,
                fileActions = FileActions(
                    export = { fileName, content -> pendingBackup = content; createBackup.launch(fileName) },
                    import = { openBackup.launch(arrayOf("application/json", "text/plain")) },
                    pickImage = { callback -> pendingImage = callback; openImage.launch(arrayOf("image/*")) },
                ),
            )
        }
        LegacyWebDataMigrator(this, viewModel.repository).run(viewModel::migrateLegacy)
    }

    private fun decodeImage(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 512 || bounds.outHeight / sampleSize > 512) {
            sampleSize *= 2
        }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("无法读取图片")
        return bitmap.toDataImage().also { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    private fun Bitmap.toDataImage(): String {
        val largest = max(width, height).coerceAtLeast(1)
        val scale = minOf(1f, 192f / largest)
        val outputBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
        } else {
            this
        }
        val bytes = ByteArrayOutputStream()
        val hasTransparency = outputBitmap.hasAlpha()
        val format = if (hasTransparency) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        outputBitmap.compress(format, if (hasTransparency) 100 else 86, bytes)
        if (outputBitmap !== this) outputBitmap.recycle()
        val mime = if (hasTransparency) "image/png" else "image/jpeg"
        return "data:$mime;base64,${Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)}"
    }
}
