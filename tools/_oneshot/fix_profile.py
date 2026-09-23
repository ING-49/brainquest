"""修复 ProfileScreen imports 与 composable ctx"""
src = open('app/src/main/java/com/brainquest/game/ui/meta/ProfileScreen.kt', encoding='utf-8').read()
old = 'import androidx.compose.foundation.layout.Column'
new = '''import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column'''
assert old in src
src = src.replace(old, new, 1)
# ctx 捕获（composable 顶部），回调里去掉 LocalContext.current
old2 = '''        var showAvatarPicker by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val name = "avatar_${System.currentTimeMillis()}.jpg"'''
new2 = '''        var showAvatarPicker by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(false) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val name = "avatar_${System.currentTimeMillis()}.jpg"'''
assert old2 in src
src = src.replace(old2, new2, 1)
open('app/src/main/java/com/brainquest/game/ui/meta/ProfileScreen.kt', 'w', encoding='utf-8').write(src)
print('fixed')
