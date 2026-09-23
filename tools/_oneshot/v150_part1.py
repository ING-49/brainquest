"""v1.5.0 批量改造脚本一：lastGoodSource + 联机版本显示移除"""

# ---------- 1. PlayerState.lastGoodSource ----------
src = open('app/src/main/java/com/brainquest/game/data/PlayerState.kt', encoding='utf-8').read()
old = '''    val pkServerUrl: String = "ws://10.0.2.2:8765", // 联机服务器地址（记忆上次填写）'''
new = '''    val pkServerUrl: String = "ws://10.0.2.2:8765", // 联机服务器地址（记忆上次填写）
    val lastGoodSource: String = "", // 最近一次检查更新成功的源（优先复用，利于国内更新）'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/data/PlayerState.kt', 'w', encoding='utf-8').write(src)
print('1 PlayerState OK')

# 2. AppViewModel.setSettings 加 lastGoodSource
src = open('app/src/main/java/com/brainquest/game/AppViewModel.kt', encoding='utf-8').read()
old = '''    fun setSettings(url: String? = null, sound: Boolean? = null, haptics: Boolean? = null, hard: Boolean? = null, pkServer: String? = null) = commit {
        it.copy(
            updateServerUrl = url ?: it.updateServerUrl,
            soundOn = sound ?: it.soundOn,
            hapticsOn = haptics ?: it.hapticsOn,
            hardMode = hard ?: it.hardMode,
            pkServerUrl = pkServer ?: it.pkServerUrl,
        )
    }'''
new = '''    fun setSettings(url: String? = null, sound: Boolean? = null, haptics: Boolean? = null, hard: Boolean? = null, pkServer: String? = null, lastGoodSource: String? = null) = commit {
        it.copy(
            updateServerUrl = url ?: it.updateServerUrl,
            soundOn = sound ?: it.soundOn,
            hapticsOn = haptics ?: it.hapticsOn,
            hardMode = hard ?: it.hardMode,
            pkServerUrl = pkServer ?: it.pkServerUrl,
            lastGoodSource = lastGoodSource ?: it.lastGoodSource,
        )
    }'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/AppViewModel.kt', 'w', encoding='utf-8').write(src)
print('2 viewmodel OK')

# 3. SettingsScreen：成功源优先 + 保存
src = open('app/src/main/java/com/brainquest/game/ui/meta/SettingsScreen.kt', encoding='utf-8').read()
old = '''                                val bases = if (player.updateServerUrl != defaultBase) listOf(player.updateServerUrl)
                                else listOf(
                                    defaultBase,
                                    "$defaultBase".let { "https://gh-proxy.com/$it" },
                                    "$defaultBase".let { "https://ghfast.top/$it" },
                                )
                                val (fetched, base) = updater.fetchManifestMulti(bases)'''
new = '''                                val bases = if (player.updateServerUrl != defaultBase) listOf(player.updateServerUrl)
                                else {
                                    // 上次成功的源排最前（利于国内更新），其余兜底
                                    val all = listOf(
                                        defaultBase,
                                        "$defaultBase".let { "https://gh-proxy.com/$it" },
                                        "$defaultBase".let { "https://ghfast.top/$it" },
                                    )
                                    if (player.lastGoodSource in all)
                                        listOf(player.lastGoodSource) + all.filter { it != player.lastGoodSource }
                                    else all
                                }
                                val (fetched, base) = updater.fetchManifestMulti(bases)
                                vm.setSettings(lastGoodSource = base)'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/ui/meta/SettingsScreen.kt', 'w', encoding='utf-8').write(src)
print('3 settings lastGoodSource OK')

# 4. 联机界面移除版本显示（等待页双方版本行）
src = open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', encoding='utf-8').read()
old = '''                    Text("我的版本 ${BuildConfig.VERSION_NAME} · 对手版本 ${peerVersion.ifBlank { "?" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (peerVersion.isNotBlank() && peerVersion != BuildConfig.VERSION_NAME)
                            Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant)
'''
assert old in src
src = src.replace(old, '', 1)
# peerVersion 状态保留（校验用），仅移除显示
open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', 'w', encoding='utf-8').write(src)
print('4 version display removed')
