package com.cyberos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun CyberScriptPanel(theme: CyberTheme, onCommand: (String) -> Unit) {
    val context = LocalContext.current
    val scriptsDirectory = remember { File(context.filesDir, "cyberscripts").apply { mkdirs() } }
    var fileName by remember { mutableStateOf("boot-check.cy") }
    var source by remember { mutableStateOf("status\ndiagnostics") }
    var status by remember { mutableStateOf("APP-PRIVATE SCRIPT WORKSPACE") }
    var refresh by remember { mutableStateOf(0) }
    val scripts = remember(refresh) { scriptsDirectory.listFiles()?.filter { it.isFile && it.extension == "cy" }.orEmpty() }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("CYBERSCRIPT // LOCAL EDITOR", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value = fileName,
            onValueChange = { fileName = it.filter { char -> char.isLetterOrDigit() || char in "-_." }.take(48) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("SCRIPT NAME") },
            colors = editorColors(theme)
        )
        OutlinedTextField(
            value = source,
            onValueChange = { source = it.take(12000) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = { Text("COMMANDS") },
            minLines = 4,
            colors = editorColors(theme),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFD8E9DF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            WindowTool("SAVE", theme) {
                val safeName = fileName.removeSuffix(".cy").takeIf { it.isNotBlank() }
                if (safeName == null) status = "ENTER A SCRIPT NAME"
                else runCatching { File(scriptsDirectory, "$safeName.cy").writeText(source) }
                    .onSuccess { status = "SAVED ${safeName}.cy"; refresh++ }
                    .onFailure { status = "SAVE FAILED: ${it.message ?: "ERROR"}" }
            }
            WindowTool("RUN", theme) {
                val commands = source.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.take(40).toList()
                if (commands.isEmpty()) status = "NO COMMANDS TO RUN"
                else {
                    commands.forEach(onCommand)
                    status = "EXECUTED ${commands.size} ALLOWED COMMANDS"
                }
            }
        }
        Text(status, color = Color(0xFFA8C5B3), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        if (scripts.isNotEmpty()) {
            Text("SAVED FILES", color = theme.accent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                scripts.take(4).forEach { file ->
                    WindowTool(file.name.take(14), theme) {
                        runCatching { source = file.readText().take(12000); fileName = file.name }
                            .onFailure { status = "LOAD FAILED" }
                    }
                }
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun editorColors(theme: CyberTheme) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = theme.accent,
    unfocusedBorderColor = theme.accent.copy(alpha = 0.34f),
    focusedLabelColor = theme.accent,
    unfocusedLabelColor = Color(0xFF81998B),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = theme.accent
)