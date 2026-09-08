package com.checkscam.classifier

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Copies a bundled asset (model `.gguf` or grammar `.gbnf`) to internal
 * storage so the native layer can mmap/open it by path. Idempotent: skips the
 * copy when the target exists and is non-empty, incl. file size mismatch.
 */
object AssetModelCopier {

    private const val TAG = "AssetModelCopier"

    fun copyToInternalStorage(context: Context, assetFileName: String): String {
        val targetDir = context.filesDir
        val targetFile = File(targetDir, assetFileName)

        if (targetFile.exists() && targetFile.length() > 0) {
            Log.d(TAG, "Asset already present: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
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
                    Log.d(TAG, "Copied asset to ${targetFile.absolutePath} ($total bytes)")
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            targetFile.delete()
            throw IllegalStateException("Failed to copy asset '$assetFileName' to internal storage", e)
        }
    }
}