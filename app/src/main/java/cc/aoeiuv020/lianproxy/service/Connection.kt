package cc.aoeiuv020.lianproxy.service

import android.util.Log
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 就普通的读写，两个隧道交换数据，
 *
 * Created by AoEiuV020 on 2018.03.20-08:52:16.
 */
class Connection(private val rTunnel: RemoteTunnel) : Runnable {
    private val TAG: String = "Connection"
    private val selector: Selector = Selector.open()
    private val buf = ByteBuffer.allocate(Short.MAX_VALUE.toInt())

    companion object {
        private val executor: Executor = Executors.newCachedThreadPool()
    }

    fun connected() {
        rTunnel.channel.register(selector, SelectionKey.OP_READ, rTunnel)
        rTunnel.bindTunnel?.channel?.register(selector, SelectionKey.OP_READ, rTunnel.bindTunnel)
        executor.execute(this)
    }

    override fun run() {
        Log.d(TAG, "start ${Thread.currentThread().name}")

        try {
            out@ while (true) {
                selector.select()
                val ite = selector.selectedKeys().iterator()
                while (ite.hasNext()) {
                    val key = ite.next()
                    when {
                        key.isReadable -> {
                            val tunnel = key.attachment() as Tunnel
                            buf.clear()
                            val len = tunnel.channel.read(buf)
                            Log.d(TAG, "read $len ${tunnel.javaClass.simpleName}")
                            if (len > 0) {
                                buf.flip()
//                                Log.v(TAG, String(buf.array(), 0, len))
                                // 真正的tcp数据都可以在这里处理，http之类的可以在这里拿到内容，
                                // TODO 要考虑分段写的情况，
                                assert(tunnel.bindTunnel?.channel?.write(buf) == len)
                            } else if (len <= 0) {
                                key.cancel()
                                break@out
                            }
                        }
                    }
                    ite.remove()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "stop", e)
        }
        Log.d(TAG, "end ${Thread.currentThread().name}")
        rTunnel.channel.use { }
        rTunnel.bindTunnel?.channel.use { }
        selector.close()
    }
}