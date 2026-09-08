package com.checkscam.stt

import android.content.Context
import android.util.Log
import java.io.File

object AssetModelCopier {

    private const val TAG = "AssetModelCopier"

    fun copyModelToInternalStorage(context: Context, assetFileName: String): String {
        val targetDir = context.filesDir
        val targetFile = File(targetDir, assetFileName)

        if (targetFile.exists() && targetFile.length() > 0) {
            Log.d(TAG, "Model already copied: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            return targetFile.absolutePath
        }

        targetDir.mkdirs()

        return try {
            context.assets.open(assetFileName).use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var total = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                    }
                    Log.d(TAG, "Copied model to ${targetFile.absolutePath} ($total bytes)")
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            targetFile.delete()
            throw IllegalStateException("Failed to copy model asset '$assetFileName' to internal storage", e)
        }
    }
}
