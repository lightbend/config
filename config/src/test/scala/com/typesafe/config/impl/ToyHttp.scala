package com.typesafe.config.impl

import java.net.ServerSocket
import java.net.InetSocketAddress
import scala.annotation.tailrec
import scala.util.control.NonFatal
import java.net.Socket
import java.io.BufferedReader
import java.io.IOException
import java.io.EOFException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Date

// terrible http server that's good enough for our test suite
final class ToyHttp(handler: ToyHttp.Request => ToyHttp.Response) {

    import ToyHttp.{ Request, Response }

    private final val serverSocket = new ServerSocket()
    serverSocket.bind(new InetSocketAddress("127.0.0.1", 0))
    final val port = serverSocket.getLocalPort
    private final val thread = new Thread(() => mainLoop())

    thread.setDaemon(true)
    thread.setName("ToyHttp")
    thread.start()

    def stop(): Unit = {
        try serverSocket.close() catch { case e: IOException => }
        try thread.interrupt() catch { case NonFatal(e) => }
        thread.join()
    }

    @tailrec
    private def mainLoop(): Unit = {
        val done = try {
            val socket = serverSocket.accept()

            try handleRequest(socket)
            catch {
                case _: EOFException =>
                case e: IOException =>
                    System.err.println(s"error handling http request: ${e.getClass.getName}: ${e.getMessage} ${e.getStackTrace.mkString("\n")}")
            }
            false
        } catch {
            case e: java.net.SocketException =>
                true
        }
        if (!done)
            mainLoop()
    }

    private def handleRequest(socket: Socket): Unit = {
        val in = socket.getInputStream
        val out = socket.getOutputStream
        try {
            // HTTP requests look like this:
            // GET /path/here HTTP/1.0
            // SomeHeader: bar
            // OtherHeader: foo
            // \r\n
            val reader = new BufferedReader(new java.io.InputStreamReader(in))
            val path = parsePath(reader)
            val header = parseHeader(reader)
            //System.err.println(s"request path '$path' headers $header")
            val response = handler(Request(path, header))
            //System.err.println(s"response $response")
            sendResponse(out, response)
        } finally {
            in.close()
            out.close()
        }
    }

    private def parseHeader(reader: BufferedReader): Map[String, String] = {

        def readHeaders(sofar: Map[String, String]): Map[String, String] = {
            val line = reader.readLine()
            val colon = line.indexOf(':')
            if (colon > 0) {
                val name = line.substring(0, colon).toLowerCase()
                val value = line.substring(colon + 1).replaceAll("^[ \t]+", "")
                readHeaders(sofar + (name -> value))
            } else {
                sofar
            }
        }

        readHeaders(Map.empty)
    }

    private def parsePath(reader: BufferedReader): String = {
        val methodPathProto = reader.readLine().split(" +")
        val method = methodPathProto(0)
        val path = methodPathProto(1)

        path
    }

    private def codeText(code: Int) = code match {
        case 200 => "OK"
        case 404 => "Not Found"
        case 500 => "Internal Server Error"
        case _ => throw new RuntimeException(s"add text for $code")
    }

    private def sendResponse(out: OutputStream, response: Response): Unit = {
        //val stuff = new java.io.ByteArrayOutputStream
        //val writer = new PrintWriter(new OutputStreamWriter(stuff, StandardCharsets.UTF_8))
        val writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))
        val dateFormat = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

        writer.append(s"HTTP/1.1 ${response.code} ${codeText(response.code)}\r\n")
        writer.append(s"Date: ${dateFormat.format(new Date)}\r\n")
        writer.append(s"Content-Type: ${response.contentType}; charset=utf-8\r\n")
        val bytes = response.body.getBytes("UTF-8")
        writer.append(s"Content-Length: $bytes\r\n")
        writer.append("\r\n")
        writer.append(response.body)
        writer.flush()
    }
}

object ToyHttp {
    def apply(handler: Request => Response): ToyHttp =
        new ToyHttp(handler)

    final case class Request(path: String, headers: Map[String, String])
    final case class Response(code: Int, contentType: String, body: String)
}
