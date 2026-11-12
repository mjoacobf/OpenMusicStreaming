package me.mjoacobf.openmusicstreaming.core.download.mux

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/** Fuente de lectura con retroceso sobre un archivo temporal. */
internal class FileInputSharpStream(file: File) : SharpStream() {
    private val raf = RandomAccessFile(file, "r")
    private var closed = false

    override fun read(): Int = raf.read()
    override fun read(buffer: ByteArray): Int = raf.read(buffer)
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int = raf.read(buffer, offset, count)

    override fun skip(amount: Long): Long {
        val target = minOf(raf.filePointer + amount, raf.length())
        val skipped = target - raf.filePointer
        raf.seek(target)
        return skipped
    }

    override fun available(): Long = raf.length() - raf.filePointer
    override fun rewind() = raf.seek(0)
    override fun isClosed(): Boolean = closed
    override fun close() {
        if (!closed) {
            closed = true
            raf.close()
        }
    }

    override fun canRewind(): Boolean = true
    override fun canRead(): Boolean = true
    override fun canWrite(): Boolean = false

    override fun write(value: Byte) = throw UnsupportedOperationException()
    override fun write(buffer: ByteArray) = throw UnsupportedOperationException()
    override fun write(buffer: ByteArray, offset: Int, count: Int) = throw UnsupportedOperationException()
}

/** Destino de escritura con búsqueda sobre un archivo temporal (requerido por el muxer MP4). */
internal class FileOutputSharpStream(file: File) : SharpStream() {
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0) }
    private var closed = false

    override fun read(): Int = throw UnsupportedOperationException()
    override fun read(buffer: ByteArray): Int = throw UnsupportedOperationException()
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int = throw UnsupportedOperationException()

    /** En escritura, "saltar" avanza el puntero (rellenando con ceros si hace falta). */
    override fun skip(amount: Long): Long {
        raf.seek(raf.filePointer + amount)
        return amount
    }

    override fun available(): Long = 0
    override fun rewind() = raf.seek(0)
    override fun isClosed(): Boolean = closed
    override fun close() {
        if (!closed) {
            closed = true
            raf.close()
        }
    }

    override fun canRewind(): Boolean = true
    override fun canSeek(): Boolean = true
    override fun seek(offset: Long) = raf.seek(offset)
    override fun length(): Long = raf.length()
    override fun canRead(): Boolean = false
    override fun canWrite(): Boolean = true

    override fun write(value: Byte) = raf.write(value.toInt())
    override fun write(buffer: ByteArray) = raf.write(buffer)
    override fun write(buffer: ByteArray, offset: Int, count: Int) = raf.write(buffer, offset, count)
}

/** Destino de solo escritura (p. ej. el OutputStream de un documento SAF). */
internal class OutputSharpStream(stream: OutputStream) : SharpStream() {
    private val out = BufferedOutputStream(stream, 64 * 1024)
    private var closed = false

    override fun read(): Int = throw UnsupportedOperationException()
    override fun read(buffer: ByteArray): Int = throw UnsupportedOperationException()
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int = throw UnsupportedOperationException()
    override fun skip(amount: Long): Long = throw UnsupportedOperationException()
    override fun available(): Long = 0
    override fun rewind() = throw UnsupportedOperationException()
    override fun isClosed(): Boolean = closed
    override fun close() {
        if (!closed) {
            closed = true
            out.flush()
            out.close()
        }
    }

    override fun canRewind(): Boolean = false
    override fun canRead(): Boolean = false
    override fun canWrite(): Boolean = true

    override fun write(value: Byte) = out.write(value.toInt())
    override fun write(buffer: ByteArray) = out.write(buffer)
    override fun write(buffer: ByteArray, offset: Int, count: Int) = out.write(buffer, offset, count)
    override fun flush() = out.flush()
}
