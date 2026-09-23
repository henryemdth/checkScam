package com.checkscam.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ModelImporterTest {

    @Test
    fun `isComplete true only when both models imported and nothing missing or rejected`() {
        val both = listOf(ModelImporter.LLAMA_MODEL_ASSET, ModelImporter.WHISPER_MODEL_ASSET)
        assertTrue(ModelImportResult(both, emptyList(), emptyList()).isComplete)

        assertFalse(ModelImportResult(listOf(ModelImporter.LLAMA_MODEL_ASSET), emptyList(), emptyList()).isComplete)
        assertFalse(ModelImportResult(emptyList(), both, emptyList()).isComplete)
        assertFalse(ModelImportResult(both, emptyList(), listOf(ModelImporter.LLAMA_MODEL_ASSET)).isComplete)
    }

    @Test
    fun `required model filenames match expected assets`() {
        assertEquals("Llama-3.2-1B-Instruct-Q4_K_M.gguf", ModelImporter.LLAMA_MODEL_ASSET)
        assertEquals("ggml-base.bin", ModelImporter.WHISPER_MODEL_ASSET)
    }

    @Test
    fun `isValidMagic accepts GGUF for llama model`() {
        assertTrue(
            ModelImporter.isValidMagic(
                ModelImporter.LLAMA_MODEL_ASSET,
                "GGUF".toByteArray(Charsets.US_ASCII)
            )
        )
    }

    @Test
    fun `isValidMagic accepts little-endian lmgg for whisper model`() {
        // On-disk representation of GGML_FILE_MAGIC 0x67676d6c (LE uint32).
        assertTrue(
            ModelImporter.isValidMagic(
                ModelImporter.WHISPER_MODEL_ASSET,
                "lmgg".toByteArray(Charsets.US_ASCII)
            )
        )
    }

    @Test
    fun `isValidMagic accepts textual ggml for whisper model`() {
        assertTrue(
            ModelImporter.isValidMagic(
                ModelImporter.WHISPER_MODEL_ASSET,
                "ggml".toByteArray(Charsets.US_ASCII)
            )
        )
    }

    @Test
    fun `isValidMagic rejects wrong or truncated headers`() {
        assertFalse(
            ModelImporter.isValidMagic(
                ModelImporter.WHISPER_MODEL_ASSET,
                "zzzz".toByteArray(Charsets.US_ASCII)
            )
        )
        assertFalse(
            ModelImporter.isValidMagic(
                ModelImporter.LLAMA_MODEL_ASSET,
                "GGUF".toByteArray(Charsets.US_ASCII).copyOf(3)
            )
        )
        assertFalse(ModelImporter.isValidMagic("unknown.bin", "GGUF".toByteArray()))
    }

    @Test
    fun `summary reports complete import as OK`() {
        val both = listOf(ModelImporter.LLAMA_MODEL_ASSET, ModelImporter.WHISPER_MODEL_ASSET)
        val summary = ModelImportResult(both, emptyList(), emptyList()).summary
        assertTrue(summary.startsWith("Model import OK"))
        assertTrue(summary.contains("imported"))
    }

    @Test
    fun `summary lists missing and rejected files`() {
        val summary = ModelImportResult(
            imported = listOf(ModelImporter.LLAMA_MODEL_ASSET),
            missing = emptyList(),
            rejected = listOf(ModelImporter.WHISPER_MODEL_ASSET)
        ).summary
        assertTrue(summary.startsWith("Model import FAILED"))
        assertTrue(summary.contains("rejected: ${ModelImporter.WHISPER_MODEL_ASSET}"))
    }

    @Test
    fun `copyModelBody writes consumed magic header before rest of stream`() {
        // Regression: header read for validation was never written back,
        // producing 4-byte-truncated models on import.
        val full = "GGUF".toByteArray(Charsets.US_ASCII) + byteArrayOf(3, 0, 0, 0, 0x93.toByte())
        val header = full.copyOfRange(0, 4)
        val rest = full.copyOfRange(4, full.size)

        val out = ByteArrayOutputStream()
        ModelImporter.copyModelBody(out, header, header.size, ByteArrayInputStream(rest))

        val result = out.toByteArray()
        assertEquals("full length (header + rest)", full.size, result.size)
        assertArrayEquals(full, result)
        assertTrue("result must start with GGUF magic", result.copyOfRange(0, 4).contentEquals("GGUF".toByteArray()))
    }

    @Test
    fun `copyModelBody handles empty rest stream`() {
        val header = "lmgg".toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        ModelImporter.copyModelBody(out, header, header.size, ByteArrayInputStream(ByteArray(0)))
        assertArrayEquals(header, out.toByteArray())
    }
}