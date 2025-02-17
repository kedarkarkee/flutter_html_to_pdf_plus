package android.print

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
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

                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    try {

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
