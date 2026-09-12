package com.brainquest.game.data.question

import kotlinx.serialization.Serializable

/** 题库统一 schema：内置题库与热更新下载包都使用此结构 */
@Serializable
data class Question(
    val id: String,
    val subject: String,
    val difficulty: Int = 1,
    val type: String = "single", // single / judge
    val question: String,
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val explanation: String = "",
    val tags: List<String> = emptyList(),
)

/** 科目题库文件（assets 与热更包共用） */
@Serializable
data class SubjectBankFile(
    val subject: String,
    val version: Int = 1,
    val questions: List<Question> = emptyList(),
)

object Subjects {
    const val MATH = "数学口算"
    const val LOGIC = "逻辑推理"
    const val ENGLISH = "英语单词"
    const val SCIENCE = "科学百科"
    const val CODING = "编程基础"
    const val ADV_MATH = "高等数学"
    const val LIN_ALG = "线性代数"
    const val PROBABILITY = "概率论"
    const val RF_CIRCUITS = "高频电子线路"
    const val COMMUNICATION = "通信原理"

    val all = listOf(
        MATH, LOGIC, ENGLISH, SCIENCE, CODING,
        ADV_MATH, LIN_ALG, PROBABILITY, RF_CIRCUITS, COMMUNICATION,
    )

    /** 存放在 JSON 题库文件中的科目（数学生成器动态出题，不需要文件） */
    val curated = listOf(ENGLISH, SCIENCE, CODING, ADV_MATH, LIN_ALG, PROBABILITY, RF_CIRCUITS, COMMUNICATION)

    fun emoji(subject: String): String = when (subject) {
        MATH -> "🔢"
        LOGIC -> "🧩"
        ENGLISH -> "🔤"
        SCIENCE -> "🔬"
        CODING -> "💻"
        ADV_MATH -> "∫"
        LIN_ALG -> "🧮"
        PROBABILITY -> "🎲"
        RF_CIRCUITS -> "📡"
        COMMUNICATION -> "📶"
        else -> "📘"
    }

    fun desc(subject: String): String = when (subject) {
        MATH -> "口算、四则运算与速算技巧"
        LOGIC -> "找规律与逻辑推理"
        ENGLISH -> "单词、词义与例句"
        SCIENCE -> "物理化学生物地理常识"
        CODING -> "计算机与网络编程基础"
        ADV_MATH -> "极限、导数、积分与级数"
        LIN_ALG -> "行列式、矩阵与向量空间"
        PROBABILITY -> "随机事件与统计规律"
        RF_CIRCUITS -> "谐振、调制与高频电路"
        COMMUNICATION -> "调制解调、编码与信道"
        else -> ""
    }
}
