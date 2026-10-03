package com.example.core.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class ProcessedDocument(
    val title: String,
    val fileType: String,
    val extractedText: String,
    val pageCount: Int = 1,
    val summary: String = "",
    val charCount: Int = extractedText.length
)

class DocumentProcessor(private val context: Context) {

    companion object {
        private const val TAG = "DocumentProcessor"
        private const val MAX_PDF_PAGES_OCR = 15
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processFile(file: File, originalName: String): Result<ProcessedDocument> = withContext(Dispatchers.IO) {
        val extension = originalName.substringAfterLast('.', "").lowercase()
        val title = originalName.substringBeforeLast('.')

        try {
            val processed = when (extension) {
                "txt", "md", "csv", "json", "html" -> processPlainText(file, title, extension.uppercase())
                "docx" -> processDocx(file, title)
                "pptx" -> processPptx(file, title)
                "pdf" -> processPdf(file, title)
                "png", "jpg", "jpeg", "webp" -> processImage(file, title)
                else -> processPlainText(file, title, "DOCUMENT")
            }
            Result.success(processed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process document $originalName", e)
            Result.failure(e)
        }
    }

    suspend fun processUri(uri: Uri, fileName: String): Result<ProcessedDocument> = withContext(Dispatchers.IO) {
        try {
            // Copy stream to temp cache file for parsing
            val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            val result = processFile(tempFile, fileName)
            tempFile.delete()
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun processPlainText(file: File, title: String, type: String): ProcessedDocument {
        val text = file.readText(StandardCharsets.UTF_8)
        val summary = generateSummaryPreview(text)
        return ProcessedDocument(
            title = title,
            fileType = type,
            extractedText = text,
            pageCount = 1,
            summary = summary
        )
    }

    private fun processDocx(file: File, title: String): ProcessedDocument {
        val sb = StringBuilder()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                    // Extract text between <w:t> tags
                    val regex = "<w:t[^>]*>(.*?)</w:t>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val matches = regex.findAll(xml)
                    for (match in matches) {
                        sb.append(match.groupValues[1]).append(" ")
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        val cleanText = sb.toString().replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
        val text = if (cleanText.isNotBlank()) cleanText else file.readText(StandardCharsets.ISO_8859_1)
        return ProcessedDocument(
            title = title,
            fileType = "DOCX",
            extractedText = text,
            summary = generateSummaryPreview(text)
        )
    }

    private fun processPptx(file: File, title: String): ProcessedDocument {
        val sb = StringBuilder()
        var slideCount = 0
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    slideCount++
                    val xml = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                    val regex = "<a:t[^>]*>(.*?)</a:t>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    sb.append("\n--- Slide $slideCount ---\n")
                    for (m in regex.findAll(xml)) {
                        sb.append(m.groupValues[1]).append(" ")
                    }
                }
                entry = zis.nextEntry
            }
        }
        val text = sb.toString().trim()
        return ProcessedDocument(
            title = title,
            fileType = "PPTX",
            extractedText = text,
            pageCount = slideCount.coerceAtLeast(1),
            summary = generateSummaryPreview(text)
        )
    }

    private suspend fun processPdf(file: File, title: String): ProcessedDocument {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val totalPages = renderer.pageCount
        val pagesToProcess = totalPages.coerceAtMost(MAX_PDF_PAGES_OCR)

        val fullTextBuilder = StringBuilder()

        for (i in 0 until pagesToProcess) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = textRecognizer.process(inputImage).await()
                if (visionText.text.isNotBlank()) {
                    fullTextBuilder.append("\n[Page ${i + 1}]\n")
                    fullTextBuilder.append(visionText.text).append("\n")
                }
            } catch (e: Exception) {
                Log.w(TAG, "OCR failed for PDF page ${i + 1}: ${e.message}")
            } finally {
                bitmap.recycle()
            }
        }
        renderer.close()
        pfd.close()

        val extracted = fullTextBuilder.toString().trim()
        return ProcessedDocument(
            title = title,
            fileType = "PDF",
            extractedText = extracted.ifBlank { "PDF Document (${totalPages} pages)" },
            pageCount = totalPages,
            summary = generateSummaryPreview(extracted)
        )
    }

    private suspend fun processImage(file: File, title: String): ProcessedDocument {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return ProcessedDocument(title, "IMAGE", "Image asset")
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = textRecognizer.process(inputImage).await()
        bitmap.recycle()

        val text = visionText.text.trim()
        return ProcessedDocument(
            title = title,
            fileType = "IMAGE",
            extractedText = text.ifBlank { "Educational Diagram / Image" },
            summary = generateSummaryPreview(text)
        )
    }

    private fun generateSummaryPreview(text: String): String {
        val clean = text.replace("\\s+".toRegex(), " ").trim()
        return if (clean.length > 200) {
            clean.substring(0, 197) + "..."
        } else {
            clean
        }
    }
}
