"""修复 MathGenerator 两个 bug：x=？=？重复 + 求x逻辑矛盾"""
src = open('app/src/main/java/com/brainquest/game/data/question/MathGenerator.kt', encoding='utf-8').read()

old1 = '            question = "$text = ?"'
new1 = '            question = if (text.endsWith("？") || text.endsWith("?")) text else "$text = ?"'
assert old1 in src, "arithmetic wrapper not found"
src = src.replace(old1, new1, 1)

old4 = '1 -> { val a = rng.nextInt(3, 15); val x = rng.nextInt(3, 15); val b = rng.nextInt(2, 40); "$x × $a + $b = ${x * a + b}，x = ?" to x }'
new4 = '1 -> { val x = rng.nextInt(3, 15); val a = rng.nextInt(3, 15); val b = rng.nextInt(2, 40); "x × $a + $b = ${x * a + b}，x = ?" to x }'
assert old4 in src, "level4 x template not found"
src = src.replace(old4, new4, 1)

open('app/src/main/java/com/brainquest/game/data/question/MathGenerator.kt', 'w', encoding='utf-8').write(src)
print("math bugs fixed")
