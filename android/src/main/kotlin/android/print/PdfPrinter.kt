package android.print

import android.annotation.TargetApi
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.BufferedOutputStream
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
                        val outputFile = File(path, fileName)
                        if (!outputFile.parentFile?.exists()!!) {
                            outputFile.parentFile?.mkdirs()
                        }

                        FileOutputStream(outputFile).use { outputStream ->
                            // Use PdfRenderer for efficient rendering
                            val parcelFileDescriptor = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_WRITE)
                            val renderer = PdfRenderer(parcelFileDescriptor)

                            for (pageIndex in 0 until info.pageCount) {
                                val page = renderer.openPage(pageIndex)
                                val bitmap = createBitmapForPage(page) // Create bitmap with appropriate size
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                // Write bitmap to PDF (using a PDF library is recommended for more control)
                                // Example (using a simplified approach - consider a proper PDF library):
                                // bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream) // Or PNG

                                // More robust approach using a PDF library (e.g., PdfBox):
                                // (See detailed example below)

                                page.close()
                                bitmap.recycle() // Important: Recycle bitmaps!
                            }
                            renderer.close()
                            parcelFileDescriptor.close()

                            callback.onSuccess(outputFile.absolutePath)
                        }
                    } catch (e: IOException) {
                        Log.d("PDFPLUS", "Failed to generate PDF")
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
