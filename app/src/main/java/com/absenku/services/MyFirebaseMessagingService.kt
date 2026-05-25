package com.absenku.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.absenku.R
import com.absenku.dashboard.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 1. Fungsi ini dipanggil jika HP mendapat token baru dari Firebase
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Simpan token ke Firestore agar Admin tahu harus kirim ke mana
        saveTokenToFirestore(token)
    }

    // 2. Fungsi ini dipanggil saat notifikasi masuk
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Ambil Judul dan Isi Pesan
        val title = remoteMessage.notification?.title ?: "Notifikasi Baru"
        val message = remoteMessage.notification?.body ?: ""

        // Tampilkan ke layar HP
        sendNotification(title, message)
    }

    private fun sendNotification(title: String, messageBody: String) {
        // Jika notif diklik, buka DashboardActivity
        val intent = Intent(this, DashboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_notification", true) // Kirim penanda agar langsung buka halaman notif
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "AbsenKu_Channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Desain bentuk notifikasi
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Ganti dengan icon logo aplikasi kamu
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // Hilang setelah diklik
            .setSound(defaultSoundUri) // Suara notif nyala
            .setPriority(NotificationCompat.PRIORITY_HIGH) // PRIORITY_HIGH agar muncul Pop-up (Heads-up)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Untuk Android 8 (Oreo) ke atas wajib pakai Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notifikasi Absensi",
                NotificationManager.IMPORTANCE_HIGH // IMPORTANCE_HIGH agar bisa bunyi dan muncul popup
            ).apply {
                description = "Pemberitahuan izin, jadwal, dan pengumuman HRD"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Munculkan notifikasi (Angka 0 adalah ID notif, bisa di-random agar notif numpuk)
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Simpan/Update FCM Token di dokumen user
        db.collection("users").document(uid)
            .update("fcmToken", token)
    }
}