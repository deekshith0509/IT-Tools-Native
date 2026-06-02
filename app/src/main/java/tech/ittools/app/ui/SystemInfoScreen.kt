package tech.ittools.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.ittools.app.web.ConsoleLog
import tech.ittools.app.web.DeviceInfo
import tech.ittools.app.web.InfoSection

private data class ToolLink(val label: String, val path: String)

private val QUICK_TOOLS = listOf(
    ToolLink("Device info", "device-information"),
    ToolLink("Token", "token-generator"),
    ToolLink("Hash", "hash-text"),
    ToolLink("UUID", "uuid-generator"),
    ToolLink("Base64", "base64-string-converter"),
    ToolLink("JWT", "jwt-parser"),
    ToolLink("Crontab", "crontab-generator"),
    ToolLink("IP calc", "ipv4-subnet-calculator"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemInfoScreen(
    onBack: () -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val sections = remember(refreshKey) { DeviceInfo.collect(context) }
    val mono = FontFamily.Monospace

    fun copyAll() {
        val text = DeviceInfo.dump(sections) +
            "\n== JS Console ==\n" + ConsoleLog.dump()
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("System Info") },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { ConsoleLog.clear() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear console")
                    }
                    IconButton(onClick = { copyAll() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { QuickToolsRow(onOpenTool) }

            sections.forEach { sectionItem(it, mono) }

            item {
                SectionHeader("JS Console (${ConsoleLog.entries.size})")
            }
            if (ConsoleLog.entries.isEmpty()) {
                item { Text("No console output yet.", fontFamily = mono, fontSize = 12.sp) }
            } else {
                items(ConsoleLog.entries) { e ->
                    SelectionContainer {
                        Text(
                            text = "[${e.ts}] ${e.level}: ${e.message}",
                            fontFamily = mono,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolsRow(onOpenTool: (String) -> Unit) {
    Column {
        SectionHeader("Open a tool")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_TOOLS.forEach { tool ->
                AssistChip(
                    onClick = { onOpenTool(tool.path) },
                    label = { Text(tool.label) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionItem(
    section: InfoSection,
    mono: FontFamily,
) {
    item { SectionHeader(section.title) }
    items(section.entries) { entry ->
        SelectionContainer {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.key,
                    fontFamily = mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.42f),
                )
                Text(
                    text = entry.value,
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp))
    }
}
