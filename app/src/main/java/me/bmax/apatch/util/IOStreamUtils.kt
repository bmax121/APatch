package me.bmax.apatch.util

import android.content.ContentResolver
import android.net.Uri
import me.bmax.apatch.apApp
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream


val cr: ContentResolver get() = apApp.contentResolver

fun Uri.inputStream() = cr.openInputStream(this) ?: throw FileNotFoundException()

fun Uri.outputStream() = cr.openOutputStream(this, "rwt") ?: throw FileNotFoundException()

fun Uri.fileDescriptor(mode: String) =
    cr.openFileDescriptor(this, mode) ?: throw FileNotFoundException()

inline fun <In : InputStream, Out : OutputStream> withStreams(
    inStream: In,
    outStream: Out,
    withBoth: (In, Out) -> Unit
) {
    inStream.use { reader ->
        outStream.use { writer ->
            withBoth(reader, writer)
        }
    }
}

fun InputStream.copyAndClose(out: OutputStream) = withStreams(this, out) { i, o -> i.copyTo(o) }
fun InputStream.writeTo(file: File) = copyAndClose(file.outputStream())

fun InputStream.copyAndCloseOut(out: OutputStream) = out.use { copyTo(it) }
