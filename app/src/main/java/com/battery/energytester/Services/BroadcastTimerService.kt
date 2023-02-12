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
import java.util.*


class BroadcastTimerService() : Service() {

    private val TAG = "BroadcastService"
    val CHANNEL_ID = "BroadcastService_Notifications"

    val COUNTDOWN_BR = "com.battery.energytester.countdown_br"
    var bi = Intent(COUNTDOWN_BR)
    var testDuration : Long = 0
    var testInterval : Int = 30
    var canceled = false

    lateinit var cdt: CountDownTimer

    companion object {
        fun startService(context: Context, interval: Int, duration: Long) {
            val startIntent = Intent(context, BroadcastTimerService::class.java)
            startIntent.putExtra("TestInterval", interval)
            startIntent.putExtra("TestDuration", duration)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, BroadcastTimerService::class.java)
            context.stopService(stopIntent)
        }
    }

    override fun onDestroy() {
        canceled = true
        //cdt.cancel()
        Log.i(TAG, "Timer cancelled")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        canceled = false
        testDuration = intent!!.getLongExtra("TestDuration", 0) * 60
        testInterval = intent.getIntExtra("TestInterval", 30)

        cdt = object : CountDownTimer(testDuration*1000, 1000) {
            var iteration = 0
            var duration_tmp = testDuration
            var secsUntilFinished: Long = 0

            override fun onTick(millisUntilFinished: Long) {
                secsUntilFinished = millisUntilFinished / 1000
                bi.putExtra("countdown_seconds", secsUntilFinished)

                if (((secsUntilFinished).mod(testInterval) == 0 || canceled)) {
                    iteration++
                    val time_period = (duration_tmp - secsUntilFinished).toFloat()
                    duration_tmp -= time_period.toLong()
                    bi.putExtra("countdown_new_period", true)
                    bi.putExtra("countdown_iteration", iteration)
                    bi.putExtra("countdown_time_period", time_period)
                    bi.putExtra("countdown_total_duration", testDuration - duration_tmp)
                }
                //Log.i(TAG, "Sending from onTick")
                sendBroadcast(bi)
                bi.putExtra("countdown_new_period", false)
                bi.putExtra("countdown_finish", false)
                if(canceled) onFinish()
            }

            override fun onFinish() {
                cancel()
                bi.putExtra("countdown_finish", true)
                Log.i(TAG, "Sending from onFinish")
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
