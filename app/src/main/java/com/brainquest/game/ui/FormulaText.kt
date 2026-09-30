package com.brainquest.game.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.brainquest.game.data.question.Subjects

/**
 * 轻量公式渲染：把题库里混排的 ASCII 记号渲染成真正的上下标，
 * 如 e^x → e 的 x 上标、a_1 → 下标、∫₀^(π/2) → 括号整体上标。
 * Unicode 上下标（x²、∫₀、x→0⁺）字体本身就带格式，原样保留即可。
 * 只解析 ^ / _ 后跟单字符或 (…) 的形式；其余字符原样输出。
 */
private fun formulaAnnotated(text: String, base: TextUnit): androidx.compose.ui.text.AnnotatedString {
    if (text.none { it == '^' || it == '_' }) return androidx.compose.ui.text.AnnotatedString(text)
    val small = base * 0.72f
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if ((ch == '^' || ch == '_') && i + 1 < text.length) {
                val next = text[i + 1]
                var content: String? = null
                var resume = 0
                if (next == '(') {
                    val end = text.indexOf(')', i + 2)
                    if (end > i + 2) {
                        content = text.substring(i + 2, end)
                        resume = end + 1
                    }
                } else if (next.isLetterOrDigit()) {
                    content = next.toString()
                    resume = i + 2
                }
                if (content != null) {
                    pushStyle(
                        SpanStyle(
                            fontSize = small,
                            baselineShift = if (ch == '^') BaselineShift.Superscript else BaselineShift.Subscript,
                        )
                    )
                    append(content)
                    pop()
                    i = resume
                    continue
                }
            }
            append(ch)
            i++
        }
    }
}

/** 数学类科目启用记号渲染；编程/英语里 ^ 与 _ 是普通字符（位运算/蛇形命名），保持纯文本 */
fun isFormulaSubject(subject: String): Boolean = subject != Subjects.CODING && subject != Subjects.ENGLISH

@Composable
fun FormulaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    formula: Boolean = true,
) {
    if (!formula) {
        Text(text, modifier = modifier, style = style, fontWeight = fontWeight, color = color)
        return
    }
    val base = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
    Text(
        formulaAnnotated(text, base),
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
    )
}
