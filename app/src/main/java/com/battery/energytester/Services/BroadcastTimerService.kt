package com.battery.energytester.Services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.battery.energytester.MainActivity


class BroadcastTimerService() : Service() {

    private val TAG = "BroadcastService"
    val CHANNEL_ID = "BroadcastService_Notifications"

    val COUNTDOWN_BR = "com.battery.energytester.countdown_br"
    var bi = Intent(COUNTDOWN_BR)
    var testDuration : Long = 0
    var counting = false

    lateinit var cdt: CountDownTimer

    companion object {
        fun startService(context: Context, message: String, duration: Long) {
            val startIntent = Intent(context, BroadcastTimerService::class.java)
            startIntent.putExtra("inputExtra", message)
            startIntent.putExtra("TestDuration", duration)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, BroadcastTimerService::class.java)
            context.stopService(stopIntent)
        }
    }

    override fun onDestroy() {
        cdt.onFinish()
        Log.i(TAG, "Timer cancelled")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        testDuration = intent!!.getLongExtra("TestDuration", 0) * 60
        cdt = object : CountDownTimer(testDuration*1000, 1000) {
            var iteration = 0
            var duration_tmp = testDuration
            var secsUntilFinished: Long = 0
            override fun onTick(millisUntilFinished: Long) {
                counting = true
                secsUntilFinished = millisUntilFinished / 1000
                bi.putExtra("countdown_seconds", secsUntilFinished);
                if ((secsUntilFinished).mod(30) == 0 && secsUntilFinished > 0) {
                    iteration++
                    val time_period = (duration_tmp - secsUntilFinished).toFloat()
                    duration_tmp -= time_period.toLong()
                    bi.putExtra("countdown_new_period", true)
                    bi.putExtra("countdown_iteration", iteration)
                    bi.putExtra("countdown_time_period", time_period)
                    bi.putExtra("countdown_total_duration", testDuration - duration_tmp)
                    bi.putExtra("countdown_finish", false)
                } else {
                    bi.putExtra("countdown_new_period", false)
                }
                sendBroadcast(bi)
            }

            override fun onFinish() {
                cancel()
                counting = false
                val time_period = (duration_tmp - secsUntilFinished).toFloat()
                duration_tmp -= time_period.toLong()
                iteration++
                bi.putExtra("countdown_new_period", true)
                bi.putExtra("countdown_iteration", iteration)
                bi.putExtra("countdown_time_period", time_period)
                bi.putExtra("countdown_total_duration", testDuration - duration_tmp)
                bi.putExtra("countdown_finish", true)
                sendBroadcast(bi)
            }

        }
        cdt.start()
        val input = intent?.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service Kotlin Example")
            .setContentText(input)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        //stopSelf();
        return START_NOT_STICKY
    }

    override fun onBind(arg0: Intent?): IBinder? {
        return null
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "BroadcastService_Notifications",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }
}