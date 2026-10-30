package io.github.nmanuelides.mtgreforged.engine

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

/**
 * A small blocking WebSocket client (RFC 6455) for the relay, the same on Android and Windows
 * without another library. One thread reads ([read]); any thread may send.
 */
class WebSocketClient private constructor(private val socket: Socket, private val input: DataInputStream, private val output: OutputStream) {

    /** A message: binary [data], or [text] for the relay's own JSON notes. */
    class Message(val text: String?, val data: ByteArray?)

    /** The relay answered without upgrading: its HTTP status and body (a JSON error, usually). */
    class Refused(val status: Int, val body: String) : IOException("HTTP $status: $body")

    private val random = SecureRandom()
    private val writeLock = Any()

    fun sendBinary(data: ByteArray, offset: Int = 0, length: Int = data.size) = send(OP_BINARY, data, offset, length)

    fun sendText(text: String) = text.toByteArray().let { send(OP_TEXT, it, 0, it.size) }

    fun ping() = send(OP_PING, ByteArray(0), 0, 0)

    /** The next text or binary message, or null once the connection closed. */
    fun read(): Message? {
        val buffer = ByteArrayOutputStream()
        var kind = -1
        while (true) {
            val b0 = input.read()
            if (b0 < 0) return null
            val fin = b0 and 0x80 != 0
            val op = b0 and 0x0F
            val b1 = input.readUnsignedByte()
            var length = (b1 and 0x7F).toLong()
            if (length == 126L) length = input.readUnsignedShort().toLong()
            else if (length == 127L) length = input.readLong()
            val mask = if (b1 and 0x80 != 0) ByteArray(4).also { input.readFully(it) } else null
            val payload = ByteArray(length.toInt())
            input.readFully(payload)
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (op) {
                OP_CLOSE -> { close(); return null }
                OP_PING -> send(OP_PONG, payload, 0, payload.size)
                OP_PONG -> Unit
                else -> {
                    if (op != OP_CONTINUATION) kind = op
                    buffer.write(payload)
                    if (fin) {
                        val bytes = buffer.toByteArray()
                        return if (kind == OP_TEXT) Message(String(bytes), null) else Message(null, bytes)
                    }
                }
            }
        }
    }

    fun close() {
        runCatching { synchronized(writeLock) { writeFrame(OP_CLOSE, ByteArray(0), 0, 0) } }
        runCatching { socket.close() }
    }

    private fun send(op: Int, data: ByteArray, offset: Int, length: Int) = synchronized(writeLock) { writeFrame(op, data, offset, length) }

    // Client frames are always masked.
    private fun writeFrame(op: Int, data: ByteArray, offset: Int, length: Int) {
        val header = ByteArrayOutputStream(14)
        header.write(0x80 or op)
        when {
            length < 126 -> header.write(0x80 or length)
            length < 65536 -> { header.write(0x80 or 126); header.write(length ushr 8); header.write(length and 0xFF) }
            else -> { header.write(0x80 or 127); for (shift in 56 downTo 0 step 8) header.write(((length.toLong() ushr shift) and 0xFF).toInt()) }
        }
        val mask = ByteArray(4).also { random.nextBytes(it) }
        header.write(mask)
        val body = ByteArray(length)
        for (i in 0 until length) body[i] = (data[offset + i].toInt() xor mask[i % 4].toInt()).toByte()
        output.write(header.toByteArray())
        output.write(body)
        output.flush()
    }

    companion object {
        private const val OP_CONTINUATION = 0
        private const val OP_TEXT = 1
        private const val OP_BINARY = 2
        private const val OP_CLOSE = 8
        private const val OP_PING = 9
        private const val OP_PONG = 10

        /** Opens wss:// (or ws://) [url]; throws [Refused] if the server answers with an error. */
        fun connect(url: String): WebSocketClient {
            val uri = URI(url)
            val secure = uri.scheme == "wss" || uri.scheme == "https"
            val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
            val socket = if (secure) SSLSocketFactory.getDefault().createSocket(uri.host, port) else Socket(uri.host, port)
            socket.tcpNoDelay = true
            socket.soTimeout = 15_000
            val output = socket.getOutputStream()
            val input = DataInputStream(socket.getInputStream().buffered())
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
            val path = (uri.rawPath ?: "/").ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
            output.write(
                ("GET $path HTTP/1.1\r\nHost: ${uri.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nUser-Agent: MTGReforged\r\n\r\n").toByteArray(),
            )
            output.flush()
            val status = readLine(input).split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            var contentLength = 0
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                if (line.startsWith("content-length:", ignoreCase = true)) contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
            if (status != 101) {
                val body = runCatching { ByteArray(contentLength.coerceIn(0, 4096)).also { input.readFully(it) }.let(::String) }.getOrDefault("")
                socket.close()
                throw Refused(status, body)
            }
            // Reads block for as long as a game is quiet; keep-alives keep the line open.
            socket.soTimeout = 0
            return WebSocketClient(socket, input, output)
        }

        private fun readLine(input: InputStream): String {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) line.append(c.toChar())
            }
            return line.toString()
        }
    }
}
