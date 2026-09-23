"""设置页用户化：移除服务器地址/演示提示，长按版本号唤出开发者地址输入"""
src = open('app/src/main/java/com/brainquest/game/ui/meta/SettingsScreen.kt', encoding='utf-8').read()

# 1. 移除服务器地址区块与保存按钮，改为长按版本号触发
old = '''        PageHeader("⚙️ 设置与更新", onBack = { nav.popBackStack() }, subtitle = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        // 通用设置'''
new = '''        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageHeader(
                "⚙️ 设置与更新",
                onBack = { nav.popBackStack() },
                subtitle = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier.weight(1f),
            )
        }

        // 通用设置'''
assert old in src
src = src.replace(old, new, 1)

# 2. 移除 URL 输入区
old2 = '''        Text("更新服务器地址", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
            placeholder = { Text("http://10.0.2.2:8000（模拟器访问本机）") },
        )
        OutlinedButton(onClick = { vm.setSettings(url = url) }, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("保存地址")
        }

'''
new2 = ''
assert old2 in src
src = src.replace(old2, new2, 1)

# 3. 演示流程提示 → 用户文案
old3 = '''        Text(
            "💡 演示流程：电脑上进入 update-server 目录运行 python -m http.server 8000，然后点「检查更新」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )'''
new3 = '''        Text(
            "💡 更新只下载很小的补丁，金币、错题本等进度全部保留，安装完成后会自动回到游戏。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )'''
assert old3 in src
src = src.replace(old3, new3, 1)

# 4. 更新中心描述用户化
old4 = '''                Text(
                    "内容热更新：题库/词对包直接下载生效\\nAPK 更新：支持 bsdiff 增量补丁与全量安装",'''
new4 = '''                Text(
                    "有新版本时在这里更新：只下载很小的补丁，进度不丢失",'''
assert old4 in src
src = src.replace(old4, new4, 1)

# 5. 长按版本号 → 开发者地址对话框（PageHeader 不支持长按，把 subtitle 行为挂在一个组合上）
#    实现：在通用设置上方加一个不可见的开发者手势区不可靠，改为：点击"当前版本"文字行
old5 = '''        PageHeader("⚙️ 设置与更新", onBack = { nav.popBackStack() }, subtitle = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")'''
# PageHeader 已在上方被改（带 Row 包裹），此处处理：找到 Row 包裹后的 PageHeader 调用加 modifier
# ——直接给 PageHeader 增加 modifier 参数不改动公共组件：用 Box 包裹实现长按
old5b = '''        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageHeader(
                "⚙️ 设置与更新",
                onBack = { nav.popBackStack() },
                subtitle = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier.weight(1f),
            )
        }'''
new5b = '''        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showDevUrl = true },
                    ),
            ) {
                PageHeader(
                    "⚙️ 设置与更新",
                    onBack = { nav.popBackStack() },
                    subtitle = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
        }

        if (showDevUrl) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                singleLine = true,
                label = { Text("开发者：更新服务器地址") },
            )
            OutlinedButton(onClick = { vm.setSettings(url = url); showDevUrl = false }, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("保存地址")
            }
        }'''
assert old5b in src
src = src.replace(old5b, new5b, 1)

# 6. 状态与导入
old6 = "    var pendingApk by remember { mutableStateOf<java.io.File?>(null) }"
new6 = "    var pendingApk by remember { mutableStateOf<java.io.File?>(null) }\n    var showDevUrl by remember { mutableStateOf(false) }"
assert src.count(old6) == 1
src = src.replace(old6, new6, 1)

old7 = "import androidx.compose.foundation.layout.Column"
new7 = "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.Box"
assert old7 in src
src = src.replace(old7, new7, 1)
old8 = "import androidx.compose.material3.OutlinedButton"
new8 = "import androidx.compose.material3.OutlinedButton\nimport androidx.compose.foundation.combinedClickable"
assert old8 in src
src = src.replace(old8, new8, 1)
open('app/src/main/java/com/brainquest/game/ui/meta/SettingsScreen.kt', 'w', encoding='utf-8').write(src)
print("SettingsScreen 用户化 OK")
