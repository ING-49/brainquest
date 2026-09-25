package com.brainquest.game.ui.meta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.ui.PageHeader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PaperItem(val q: String, val a: String, val analysis: String = "")

@Serializable
private data class Paper(
    val id: String,
    val title: String,
    val subject: String = "",
    val year: String = "",
    val intro: String = "",
    val items: List<PaperItem> = emptyList(),
)

@Serializable
private data class PapersFile(val version: Int = 0, val papers: List<Paper> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

/**
 * 热更优先：内容包（filesDir/content/packs/ 递归，UpdateManager 把包解到 <包id>/ 子目录）
 * 里的 papers.json，version 最高者胜；没有热更包回落 assets 内置。
 */
private fun loadPapers(context: android.content.Context): List<Paper> {
    var best: PapersFile? = null
    val packsDir = java.io.File(context.filesDir, "content/packs")
    if (packsDir.isDirectory) {
        packsDir.walkTopDown().filter { it.isFile && it.name == "papers.json" }.forEach { f ->
            runCatching {
                val parsed = json.decodeFromString<PapersFile>(f.readText())
                if (best == null || parsed.version > best!!.version) best = parsed
            }
        }
    }
    val file = best ?: runCatching {
        json.decodeFromString<PapersFile>(
            context.assets.open("papers/papers.json").bufferedReader().use { it.readText() },
        )
    }.getOrNull()
    return file?.papers ?: emptyList()
}

/** 真题试卷：按套浏览历年真题精选卷（题干 + 答案 + 解析的阅读模式） */
@Composable
fun PapersScreen(vm: com.brainquest.game.AppViewModel, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val papers = remember {
        runCatching { loadPapers(context) }.getOrDefault(emptyList())
    }
    var openPaper by remember { mutableStateOf<Paper?>(null) }

    if (openPaper != null) {
        val paper = openPaper!!
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeader(paper.title, onBack = { openPaper = null }, subtitle = "${paper.subject} · ${paper.year} · 共 ${paper.items.size} 题")
            Text(
                paper.intro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                items(paper.items.size) { i ->
                    val item = paper.items[i]
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${i + 1}. ${item.q}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "答案：${item.a}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "解析：${item.analysis}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            "📄 真题试卷",
            onBack = { nav.popBackStack() },
            subtitle = "历年真题精选卷 · 题干 + 答案 + 解析",
        )
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(papers.size) { i ->
                val p = papers[i]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { openPaper = p },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${p.subject} · ${p.year} · ${p.items.size} 题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            p.intro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Text(
            "💡 试卷库会随内容包持续增补，可在考研刷题之外系统性研读。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
