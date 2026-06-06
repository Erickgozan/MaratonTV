package com.maratonTv.service.extension

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class ExtensionServer(
    private val onStatusUpdate: (String) -> Unit,
    private val onDataReceived: (JSONObject) -> Unit,
    private val onHeartbeat: (Long) -> Unit
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        serverScope.launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(9999)
                while (isActive) {
                    val socket = serverSocket.accept()
                    handleSocketInput(socket)
                }
            } catch (e: Exception) {
                Log.e("ExtensionServer", "Server error: ${e.message}")
                onStatusUpdate("Error: ${e.localizedMessage}")
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handleSocketInput(socket: Socket) {
        serverScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = socket.getOutputStream()
                
                val line = reader.readLine() ?: ""
                if (line.isEmpty()) {
                    socket.close()
                    return@launch
                }
                
                val parts = line.split(" ")
                if (parts.size < 3) {
                    socket.close()
                    return@launch
                }
                val method = parts[0]
                val path = parts[1]
                
                var contentLength = 0
                while (true) {
                    val headerLine = reader.readLine() ?: ""
                    if (headerLine.isEmpty()) break
                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = headerLine.substring(15).trim().toIntOrNull() ?: 0
                    }
                }
                
                if (method.equals("OPTIONS", ignoreCase = true)) {
                    val response = "HTTP/1.1 204 No Content\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.flush()
                    socket.close()
                    return@launch
                }
                
                if (path.startsWith("/ping") || path.startsWith("/status")) {
                    onHeartbeat(System.currentTimeMillis())
                    onStatusUpdate("Activo (Conectado)")
                    val jsonResponse = "{\"status\":\"ok\",\"app\":\"blooderstv\"}"
                    val responseBytes = jsonResponse.toByteArray()
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.write(responseBytes)
                    out.flush()
                } else if (method.equals("POST", ignoreCase = true) && path.startsWith("/scrap")) {
                    onHeartbeat(System.currentTimeMillis())
                    onStatusUpdate("Activo (Datos Recibidos)")
                    
                    val bodyChars = CharArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    val requestBody = String(bodyChars)
                    
                    try {
                        onDataReceived(JSONObject(requestBody))
                    } catch (ex: Exception) {
                        Log.e("ExtensionServer", "Error parsing scrap data: ${ex.message}")
                    }
                    
                    val jsonResponse = "{\"status\":\"ok\",\"message\":\"scraped_received\"}"
                    val responseBytes = jsonResponse.toByteArray()
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.write(responseBytes)
                    out.flush()
                } else {
                    val jsonResponse = "{\"status\":\"error\",\"message\":\"not_found\"}"
                    val responseBytes = jsonResponse.toByteArray()
                    val response = "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.write(responseBytes)
                    out.flush()
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e("ExtensionServer", "Socket error: ${e.message}")
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        serverScope.cancel()
    }
}
