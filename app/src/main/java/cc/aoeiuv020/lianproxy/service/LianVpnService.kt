package cc.aoeiuv020.lianproxy.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import cc.aoeiuv020.lianproxy.MainActivity
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.intentFor

/**
 *
 * Created by AoEiuV020 on 2018.03.12-22:20:49.
 */


class LianVpnService : VpnService(), AnkoLogger {
    companion object {
        private val ACTION_CONNECT = LianVpnService::class.java.name + ".START"
        private val ACTION_DISCONNECT = LianVpnService::class.java.name + ".STOP"
        fun start(context: Context) {
            val intent = Intent(context, LianVpnService::class.java).apply { action = ACTION_CONNECT }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LianVpnService::class.java).apply { action = ACTION_DISCONNECT }
            context.startService(intent)
        }
    }

    var isRunning: Boolean = false
    lateinit var pfd: ParcelFileDescriptor
    lateinit var mainThread: MainThread
    lateinit var proxyThread: ProxyThread
    private lateinit var configureIntent: PendingIntent

    override fun onCreate() {
        super.onCreate()

        configureIntent = PendingIntent.getActivity(this, 0, intentFor<MainActivity>(), PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent?.action == ACTION_DISCONNECT) {
            debug { "stop" }
            disconnect()
            Service.START_NOT_STICKY
        } else {
            debug { "start" }
            connect()
            Service.START_STICKY
        }
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun connect() {
        isRunning = true
        pfd = Builder().setMtu(1500)
                .addAddress("172.17.1.1", 24)
                .addRoute("0.0.0.0", 0)
//                .addDnsServer("8.8.8.8")
                .setConfigureIntent(configureIntent)
                .setSession(LianVpnService::class.java.simpleName)
//                .setBlocking(true)
                .establish()

        proxyThread = ProxyThread(this)
        proxyThread.start()
        mainThread = MainThread(this)
        mainThread.start()

    }

    private fun disconnect() {
        if (!isRunning) {
            return
        }
        isRunning = false
        try {
            mainThread.quit()
            proxyThread.quit()
            pfd.close()
        } catch (_: Exception) {
        }
        stopSelf()
    }
}
