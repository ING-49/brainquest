package com.brainquest.game.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.brainquest.game.MainActivity
import com.brainquest.game.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 每日提醒：未签到 / 有到期错题时发一条本地通知。
 * 仅在用户已授予通知权限时生效（不主动弹权限框）；每 24h 周期执行。
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val player = com.brainquest.game.data.SaveStore(ctx).load()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val dueReviews = player.wrongBook.count { !it.mastered && (it.nextReviewAt <= System.currentTimeMillis() || it.stage == 0) }
        if (player.lastCheckIn == today && dueReviews == 0) return Result.success()

        val lines = mutableListOf<String>()
        if (player.lastCheckIn != today) lines.add("📅 今日还没签到领金币")
        if (dueReviews > 0) lines.add("📖 有 $dueReviews 道错题到期该复习了")
        if (lines.isEmpty()) return Result.success()

        ensureChannel(ctx)
        val intent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("脑力大冒险 · 今日待办")
            .setContentText(lines.joinToString("；"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        ContextCompat.getSystemService(ctx, NotificationManager::class.java)
            ?.notify(1001, n)
        return Result.success()
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "每日提醒", NotificationManager.IMPORTANCE_DEFAULT)
            ContextCompat.getSystemService(ctx, NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "reminder"

        /** App 启动时调度（幂等：KEEP 策略） */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
