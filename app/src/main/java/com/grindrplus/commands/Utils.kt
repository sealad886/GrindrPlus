package com.grindrplus.commands

import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import java.io.BufferedReader
import java.io.InputStreamReader

class Utils(
    recipient: String,
    sender: String
) : CommandModule("Utils", recipient, sender) {

    @Command("shell", help = "Run a shell command and display output")
    fun shell(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Please provide a shell command to execute"
            )
            return
        }

        val command = args.joinToString(" ")
        val output = StringBuilder()

        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = reader.readLine()

            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()

            process.waitFor()

        } catch (e: Exception) {
            output.append("Error executing command: ${e.message}")
        }

        CommandDialogs.showTextDialog(
            title = "Output",
            content = output.toString(),
            copyLabel = "Shell Output"
        )
    }

    @Command("prefix", help = "Change the command prefix (default: /)")
    fun prefix(args: List<String>) {
        val prefix = Config.get("command_prefix", "/")
        when {
            args.isEmpty() -> GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "The current command prefix is $prefix"
            )
            args[0].isBlank() -> GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Invalid command prefix"
            )
            args[0] == "reset" || args[0] == "clear" -> {
                Config.put("command_prefix", "/")
                GrindrPlus.showToast(
                    Toast.LENGTH_LONG,
                    "Command prefix reset to /",
                )
            }
            args[0].length > 1 -> GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Command prefix must be a single character"
            )
            !args[0].matches(Regex("[^a-zA-Z0-9]")) -> GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Command prefix must be a special character (no letters or numbers)"
            )
            args[0] == prefix -> GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Command prefix is already set to ${args[0]}"
            )
            else -> {
                Config.put("command_prefix", args[0])
                GrindrPlus.showToast(
                    Toast.LENGTH_LONG,
                    "Command prefix set to ${args[0]}"
                )
            }
        }
    }
}
