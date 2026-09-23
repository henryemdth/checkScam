package com.checkscam.app

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Imports the on-device ML models from a user-selected storage folder (SAF
 * tree URI) into the app's internal storage, using the exact filenames the
 * engines already expect (`AssetModelCopier` skips when the file is present).
 *
 * Model binaries are deliberately NOT bundled in the APK (`ignoreAssetsPattern`
 * excludes `*.gguf`/`*.bin`), so this is the required onboarding step before
 * STT and classification can run. Grammar (`.gbnf`) and the schema `.json` stay
 * bundled. Fully offline: files are only copied, never fetched.
 */
object ModelImporter {

    private const val TAG = "ModelImporter"

    const val LLAMA_MODEL_ASSET = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
    const val WHISPER_MODEL_ASSET = "ggml-base.bin"

    private const val MAGIC_SIZE = 4

    private val REQUIRED_MODEL_FILES = listOf(LLAMA_MODEL_ASSET, WHISPER_MODEL_ASSET)

    /**
     * Validates the first [MAGIC_SIZE] bytes of a model header (JVM-testable).
     *
     * - `.gguf`: ASCII `GGUF` (magic 0x46554747 is byte-order identical LE/ASCII).
     * - `.bin` (whisper): on-disk the GGML magic `0x67676d6c` is written
     *   little-endian → bytes `6c 6d 67 67` (`lmgg`). Some serializers store
     *   the raw ASCII `ggml` instead; accept both spellings.
     */
    internal fun isValidMagic(name: String, header: ByteArray): Boolean {
        if (header.size < MAGIC_SIZE) return false
        return when (name) {
            LLAMA_MODEL_ASSET -> header.contentEquals("GGUF".toByteArray(Charsets.US_ASCII))
            WHISPER_MODEL_ASSET ->
                header.contentEquals("lmgg".toByteArray(Charsets.US_ASCII)) ||
                    header.contentEquals("ggml".toByteArray(Charsets.US_ASCII))
            else -> false
        }
    }

    fun areModelsImported(context: Context): Boolean {
        val dir = context.filesDir
        return REQUIRED_MODEL_FILES.all { name ->
            val f = File(dir, name)
            f.exists() && f.length() > 0L
        }
    }

    /**
     * Copies every required model found in [uri] (an `OpenDocumentTree` result)
     * into [Context.getFilesDir]. Returns what was imported, rejected (bad
     * magic / read error) and missing. Idempotent: existing valid files are
     * left untouched.
     */
    suspend fun importFolder(
        context: Context,
        uri: Uri,
        onProgress: (String) -> Unit = {}
    ): ModelImportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null) {
            Log.e(TAG, "Cannot open document tree for $uri")
            return@withContext ModelImportResult(emptyList(), REQUIRED_MODEL_FILES, emptyList())
        }

        val found = mutableMapOf<String, Uri>()
        root.listFiles().forEach { doc ->
            val name = doc.name ?: return@forEach
            if (doc.isFile && name in REQUIRED_MODEL_FILES) {
                found[name] = doc.uri
            }
        }

        val imported = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (name in REQUIRED_MODEL_FILES) {
            val source = found[name] ?: continue
            onProgress("Importing $name")
            if (copyIfValid(context, source, name)) {
                imported += name
            } else {
                rejected += name
            }
        }

        val missing = REQUIRED_MODEL_FILES.filterNot { it in imported || it in rejected }
        Log.i(TAG, "Import result: imported=$imported missing=$missing rejected=$rejected")
        ModelImportResult(imported, missing, rejected)
    }

    private fun copyIfValid(context: Context, source: Uri, name: String): Boolean {
        val target = File(context.filesDir, name)

        // Already installed and valid: skip the expensive re-copy.
        if (target.exists() && target.length() > 0 && hasMagic(target, name)) {
            Log.i(TAG, "Skipping $name: valid copy already present")
            return true
        }

        // Stale target with bad magic (e.g. a previously truncated import):
        // remove it so renameTo below cannot fail on an existing destination.
        if (target.exists()) {
            Log.w(TAG, "Removing invalid existing copy of $name (${target.length()} bytes)")
            target.delete()
        }

        val expectedLength = DocumentFile.fromSingleUri(context, source)?.length() ?: -1L

        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                val header = ByteArray(MAGIC_SIZE)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < header.size || !isValidMagic(name, header)) {
                    Log.w(
                        TAG,
                        "Rejected $name: bad magic bytes " +
                            "(got=${header.take(read).joinToString(",") { "%02x".format(it) }})"
                    )
                    return false
                }

                val part = File(context.filesDir, "$name.part")
                part.outputStream().use { output ->
                    // The magic header was consumed above; it MUST be written
                    // back or the imported copy is 4 bytes short (regression:
                    // models loaded 4 bytes short, whisper/llama init failed).
                    copyModelBody(output, header, read, input)
                }
                if (expectedLength > 0 && part.length() != expectedLength) {
                    part.delete()
                    Log.w(
                        TAG,
                        "Rejected $name: length mismatch (copied=${part.length()} source=$expectedLength)"
                    )
                    return false
                }
                if (!part.renameTo(target)) {
                    part.delete()
                    throw IOException("Rename failed for $name")
                }
                Log.i(TAG, "Imported $name (${target.length()} bytes)")
                true
            } ?: run {
                Log.w(TAG, "Cannot open $name for reading")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed for $name", e)
            File(context.filesDir, "$name.part").delete()
            false
        }
    }

    /**
     * Writes the already-consumed magic header followed by the remaining
     * stream. Extracted for JVM unit testing — forgetting the header write
     * previously shipped 4-byte-truncated models.
     */
    internal fun copyModelBody(
        output: OutputStream,
        header: ByteArray,
        read: Int,
        input: InputStream
    ) {
        output.write(header, 0, read)
        input.copyTo(output)
    }

    private fun hasMagic(file: File, name: String): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(MAGIC_SIZE)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n <= 0) break
                    read += n
                }
                read == header.size && isValidMagic(name, header)
            }
        } catch (e: Exception) {
            false
        }
    }
}

data class ModelImportResult(
    val imported: List<String>,
    val missing: List<String>,
    val rejected: List<String>
) {
    val isComplete: Boolean
        get() {
            val all = listOf(ModelImporter.LLAMA_MODEL_ASSET, ModelImporter.WHISPER_MODEL_ASSET)
            return all.all { it in imported } && missing.isEmpty() && rejected.isEmpty()
        }

    /** Human-readable status line for the dashboard + diagnostic console. */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (imported.isNotEmpty()) parts += "imported: ${imported.joinToString()}"
            if (missing.isNotEmpty()) parts += "missing: ${missing.joinToString()}"
            if (rejected.isNotEmpty()) parts += "rejected: ${rejected.joinToString()}"
            if (parts.isEmpty()) return "no model files found in selected folder"
            val ok = if (isComplete) "OK" else "FAILED"
            return "Model import $ok — " + parts.joinToString(" | ")
        }
}