package xyz.mufanc.maascreen

import android.content.ComponentName
import android.content.Intent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.system.exitProcess

class Controller : Thread() {
    private val input = BufferedReader(InputStreamReader(System.`in`))
    private val output = BufferedWriter(OutputStreamWriter(System.out))

    override fun run() {
        while (true) {
            val line = input.readLine()
            if (line.isEmpty()) continue
            try {
                parseCommand(line)
            } catch (err: Throwable) {
                err.printStackTrace(System.err)
            }
        }
    }

    private fun parseCommand(line: String) {
        val splits = line.split("\\s+".toRegex())
        val args = splits.drop(1)
        when (splits[0]) {
            "i" -> doInit(args)
            "c" -> doCaptureScreen()
            "s" -> doStartActivity(args)
            "e" -> doExit()
        }
    }

    private fun doInit(args: List<String>) {
        val (width, height, dpi) = args
        val displayId = VScreen.init(width.toInt(), height.toInt(), dpi.toInt())
        response(displayId)
    }

    private fun doCaptureScreen() {
        response(VScreen.capture() ?: "ERR")
    }

    private fun doStartActivity(args: List<String>) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(args[0], args[1]))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        VScreen.startActivity(intent)
    }

    private fun doExit() {
        VScreen.destroy()
        exitProcess(0)
    }

    private fun response(message: Any?) {
        output.write("$message")
        output.newLine()
        output.newLine()
        output.flush()
    }
}
