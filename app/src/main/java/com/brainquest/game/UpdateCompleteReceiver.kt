package com.brainquest.game

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.app.PendingIntent
import android.os.Looper

/**
 * 应用内更新安装完成后：
 * 1. 先尝试直接拉起游戏（部分系统允许）
 * 2. 被后台启动限制拦截时，发一条"更新完成"通知，点通知回到游戏
 */
class UpdateCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Handler(Looper.getMainLooper()).postDelayed({
            // 尝试直接拉起
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                )
                return@postDelayed
            } catch (e: Exception) {
                // 后台启动被拦截 → 走通知
            }
            // 通知回游戏
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "更新完成", NotificationManager.IMPORTANCE_HIGH),
                )
            }
            val tapIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notif = androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🎉 更新完成")
                .setContentText("新版本已就绪，点这里继续冒险～")
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, notif)
        }, 800)
    }

    companion object {
        private const val CHANNEL = "update_done"
        private const val NOTIF_ID = 2024
    }
}
