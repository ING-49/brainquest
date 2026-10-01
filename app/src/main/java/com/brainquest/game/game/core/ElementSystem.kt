package com.brainquest.game.game.core

import kotlin.random.Random

/**
 * 元素系统：元素是"标签"，子弹/近战只携带 [Element]，命中时交给本系统挂状态；
 * 敌人只知道自己身上有状态（灼烧/减速/冻结/感电），渲染器读状态画特效。
 * 扩展元素（毒/物理细化）与元素反应都在这里加，不碰子弹与敌人代码。
 */
enum class Element(val label: String) {
    FIRE("火"), ICE("冰"), THUNDER("雷"), PHYSICAL("物理");
}

object ElementSystem {

    private const val BURN_DPS_PER_STACK = 4f
    private const val BURN_DURATION = 2.5f
    private const val BURN_MAX_STACKS = 4
    private const val SLOW_PER_STACK = 0.22f
    private const val SLOW_DURATION = 2.0f
    private const val SLOW_FREEZE_AT = 3      // 冰叠满 3 层冻结
    private const val FREEZE_DURATION = 1.2f
    private const val SHOCK_CHAIN_RADIUS = 200f
    private const val SHOCK_CHAINS = 2
    private const val SHOCK_CHAIN_DMG_RATIO = 0.5f

    /** 命中时挂状态（engine 提供伤害与范围查询） */
    fun onHit(e: CombatEngine.Enemy, element: Element, engine: CombatEngine) {
        when (element) {
            Element.FIRE -> {
                e.burnStacks = (e.burnStacks + 1).coerceAtMost(BURN_MAX_STACKS)
                e.burnTimer = BURN_DURATION
            }
            Element.ICE -> {
                e.slowStacks = (e.slowStacks + 1).coerceAtMost(SLOW_FREEZE_AT)
                e.slowTimer = SLOW_DURATION
                if (e.slowStacks >= SLOW_FREEZE_AT) e.frozen = FREEZE_DURATION
            }
            Element.THUNDER -> {
                // 感电：连锁跳跃到附近敌人（一次性，防无限递归）
                var source = e
                val hit = mutableSetOf(e)
                var dmg = engine.attack.toFloat() * SHOCK_CHAIN_DMG_RATIO
                repeat(SHOCK_CHAINS) {
                    val next = engine.queryNeighbors(source, SHOCK_CHAIN_RADIUS, hit).firstOrNull() ?: return
                    hit.add(next)
                    next.hitFlash = 0.12f
                    next.hp -= dmg
                    engine.events.add(CombatEngine.FxEvent(source.x, source.y, "", false, element, 3, next.x, next.y))
                    if (next.hp <= 0f && next.alive) engine.killEnemy(next)
                    source = next
                    dmg *= 0.7f
                }
            }
            Element.PHYSICAL -> {}   // 物理无状态（暴击在引擎伤害里）
        }
    }

    /** 每帧状态结算（灼烧 DoT、减速/冻结计时） */
    fun tickStatus(e: CombatEngine.Enemy, dt: Float, engine: CombatEngine) {
        if (e.frozen > 0f) e.frozen -= dt
        if (e.slowTimer > 0f) {
            e.slowTimer -= dt
            if (e.slowTimer <= 0f) e.slowStacks = 0
        }
        if (e.burnTimer > 0f) {
            e.burnTimer -= dt
            // 灼烧按"每 0.5s 一跳"结算，叠层越高越痛
            e.burnTick += dt
            if (e.burnTick >= 0.5f) {
                e.burnTick -= 0.5f
                e.hp -= BURN_DPS_PER_STACK * 0.5f * e.burnStacks
                if (e.hp <= 0f && e.alive) engine.killEnemy(e)
            }
            if (e.burnTimer <= 0f) e.burnStacks = 0
        }
    }
}
