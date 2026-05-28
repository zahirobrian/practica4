package com.ipn.btbrowser.common

/**
 * Protocolo de comunicación Bluetooth entre servidor y cliente.
 *
 * Formato de mensajes (separados por newline):
 *   REQUEST:<url>          cliente → servidor: solicitar URL
 *   RESPONSE_START:<len>   servidor → cliente: inicio de respuesta con tamaño total
 *   RESPONSE_CHUNK:<data>  servidor → cliente: fragmento de datos
 *   RESPONSE_END           servidor → cliente: fin de respuesta
 *   ERROR:<message>        servidor → cliente: error al procesar
 *   PING                   heartbeat
 *   PONG                   respuesta a heartbeat
 */
object BtProtocol {
    const val REQUEST      = "REQUEST:"
    const val RESPONSE_START = "RESPONSE_START:"
    const val RESPONSE_CHUNK = "RESPONSE_CHUNK:"
    const val RESPONSE_END = "RESPONSE_END"
    const val ERROR        = "ERROR:"
    const val PING         = "PING"
    const val PONG         = "PONG"

    fun request(url: String)     = "$REQUEST$url\n"
    fun responseStart(len: Int)  = "$RESPONSE_START$len\n"
    fun responseChunk(data: String) = "$RESPONSE_CHUNK$data\n"
    fun responseEnd()            = "$RESPONSE_END\n"
    fun error(msg: String)       = "$ERROR$msg\n"
}