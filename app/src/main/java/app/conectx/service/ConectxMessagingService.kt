package app.conectx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import app.conectx.MainActivity
import app.conectx.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

/**
 * Handles Firebase Cloud Messaging for squad invites and pre-match
 * coordination. Messages from the web backend arrive here.
 *
 * FCM is used for:
 * - Squad invite notifications (someone shares a join code)
 * - Pre-match reminders (match day is starting, activate your pass)
 * - System announcements (app updates, service status)
 *
 * FCM is NOT used for chat messages — those go through the mesh / RTDB.
 */
@AndroidEntryPoint
class ConectxMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "conectx_notifications"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: ${token.take(10)}…")
        // TODO: send token to Supabase backend for targeting
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message from: ${message.from}")

        val title = message.data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val body = message.data["body"] ?: message.notification?.body ?: return

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        createChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Conectx",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones de squads e invitaciones"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
