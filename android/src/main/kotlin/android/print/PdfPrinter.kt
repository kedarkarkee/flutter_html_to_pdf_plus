package android.print

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfPrinter(private val printAttributes: PrintAttributes) {

    interface Callback {
        fun onSuccess(filePath: String)
        fun onFailure()
    }


    fun print(
        printAdapter: PrintDocumentAdapter,
        path: File,
        fileName: String,
        callback: Callback
    ) {
        // Support for min API 16 is required
        val fileDescriptor = getOutputFile(path, fileName)
        val cancellationSignal = CancellationSignal()
        printAdapter.onLayout(
            null,
            printAttributes,
            null,
            object : PrintDocumentAdapter.LayoutResultCallback() {

                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    try {
                        val outputFileDescriptor = getOutputFile(path, fileName)

                        FileOutputStream(outputFileDescriptor.fileDescriptor).use { outputStream ->
                            PDDocument().use { document ->
                                val renderer = PdfRenderer(outputFileDescriptor)

                                for (pageIndex in 0 until info.pageCount) {
                                    val page = renderer.openPage(pageIndex)
                                    val bitmap = createBitmapForPage(page)
                                    writeBitmapToPdf(bitmap, document)
                                    page.close()
                                    bitmap.recycle()
                                }

                                renderer.close()
                                outputFileDescriptor.close() // Important: Close the ParcelFileDescriptor
                                document.save(outputStream)
                            }
                            callback.onSuccess(File(path, fileName).absolutePath) // Correct path
                        }
                    } catch (e: IOException) {
                        Log.d("PDFPLUS", "Failed to generate PDF")
                        e.message?.let { Log.e("PDFPLUS", it) };
                        callback.onFailure()
                    }
                }
            },
            null
        )
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createBitmapForPage(page: PdfRenderer.Page): android.graphics.Bitmap {
        val pageWidth = page.width
        val pageHeight = page.height

        // Calculate scaling factor to fit print attributes
        val scaleX = printAttributes.getMediaSize()!!.getWidthMils() / 72f / pageWidth.toFloat()
        val scaleY = printAttributes.getMediaSize()!!.getHeightMils() / 72f / pageHeight.toFloat()
        val scale = Math.min(scaleX, scaleY)

        val scaledWidth = (pageWidth * scale).toInt()
        val scaledHeight = (pageHeight * scale).toInt()
        return android.graphics.Bitmap.createBitmap(scaledWidth, scaledHeight, android.graphics.Bitmap.Config.ARGB_8888)
    }
    private fun writeBitmapToPdf(bitmap: Bitmap, document: PDDocument) {
        val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
        document.addPage(page)

        try {
            val pdImage = createPDImageXObject(bitmap, document)

            PDPageContentStream(document, page).use { contentStream ->
                contentStream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            }

        } catch (e: IOException) {
            println("Error writing bitmap to PDF: ${e.message}")
        }
    }
    private fun createPDImageXObject(bitmap: Bitmap, document: PDDocument): PDImageXObject {
        val byteArrayOutputStream = ByteArrayOutputStream()

        // Choose your compression format (PNG is lossless, JPEG is lossy but smaller)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream) // Or JPEG

        val byteArray = byteArrayOutputStream.toByteArray()

        return PDImageXObject.createFromByteArray(document, byteArray, "image.png") // Or "image.jpg"
    }
}


private fun getOutputFile(path: File, fileName: String): ParcelFileDescriptor {
    if (!path.exists()) {
        path.mkdirs()
    }

    File(path, fileName).let {
        it.createNewFile()
        return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_WRITE)
    }
}
