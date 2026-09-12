package com.aeibi.avd.feature.projects.bridge

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aeibi.avd.domain.project.ProjectIconUpload
import com.aeibi.avd.feature.projects.ProjectIconPreview
import com.aeibi.avd.feature.projects.R
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
internal fun ProjectIconPickerBridge(
    existingIcon: ProjectIconPreview?,
    selectedIcon: ProjectIconUpload?,
    onIconPrepared: (ProjectIconUpload) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
                ?.let(context::prepareIcon)
                ?.let(onIconPrepared)
                ?: onError()
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            onError()
        }
    }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val outputFile = File(
                File(context.cacheDir, "project-icons"),
                "project-icon-${UUID.randomUUID()}.png"
            )
            outputFile.parentFile?.mkdirs()
            val output = FileProvider.getUriForFile(
                context,
                "${context.packageName}.projects.fileprovider",
                outputFile
            )
            UCrop.of(uri, output)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(ICON_SIZE, ICON_SIZE)
                .withOptions(
                    UCrop.Options().apply {
                        setCompressionFormat(Bitmap.CompressFormat.PNG)
                    }
                )
                .start(context, cropLauncher)
        }
    val bytes = selectedIcon?.copyPngBytes() ?: existingIcon?.copyPngBytes()
    val image = remember(bytes?.contentHashCode()) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
            .clickable {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
    ) {
        if (image == null) {
            Text(stringResource(R.string.projects_pick_icon))
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.projects_selected_icon),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun Context.prepareIcon(uri: Uri): ProjectIconUpload? {
    val original =
        contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return null
    val scaled = if (original.width == ICON_SIZE && original.height == ICON_SIZE) {
        original
    } else {
        Bitmap.createScaledBitmap(original, ICON_SIZE, ICON_SIZE, true).also { original.recycle() }
    }
    return try {
        val bytes = ByteArrayOutputStream().use { output ->
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
            output.toByteArray()
        }
        ProjectIconUpload.fromPng(bytes)
    } finally {
        scaled.recycle()
    }
}

private const val ICON_SIZE = 512
