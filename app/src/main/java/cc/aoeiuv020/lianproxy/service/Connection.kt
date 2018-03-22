package cc.aoeiuv020.lianproxy.service

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.BlockingDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

/**
 * 就普通的socket通信，两个隧道交换数据，
 *
 * Created by AoEiuV020 on 2018.03.20-08:52:16.
 */
class Connection(private val rTunnel: RemoteTunnel) : Runnable {
    private val TAG: String = "Connection"
    private val selector: Selector = Selector.open()
    private val buf = ByteBuffer.allocate(4096)
    private val remoteQueue: BlockingDeque<ByteBuffer> = LinkedBlockingDeque()
    private val localQueue: BlockingDeque<ByteBuffer> = LinkedBlockingDeque()

    companion object {
        private val executor: Executor = Executors.newCachedThreadPool()
    }

    fun connected() {
        rTunnel.channel.register(selector, SelectionKey.OP_READ, rTunnel)
        rTunnel.bindTunnel!!.channel.register(selector, SelectionKey.OP_READ, rTunnel.bindTunnel)
        executor.execute(this)
    }

    override fun run() {
        Log.d(TAG, "start ${Thread.currentThread().name}")

        try {
            while (true) {
                selector.select()
                val ite = selector.selectedKeys().iterator()
                while (ite.hasNext()) {
                    val key = ite.next()
                    when {
                        key.isReadable -> {
                            onReadable(key)
                        }
                        key.isWritable -> {
                            onWritable(key)
                        }
                    }
                    ite.remove()
                }
            }
        } catch (e: InterruptedException) {
            Log.d(TAG, "quit", e)
        } catch (e: IOException) {
            // 网络连续问题，或者是vpn关掉了之类的，
            Log.e(TAG, "stop", e)
        }
        rTunnel.channel.use { }
        rTunnel.bindTunnel?.channel.use { }
        selector.use { }
        localQueue.clear()
        remoteQueue.clear()
        Log.d(TAG, "end ${Thread.currentThread().name}")
    }

    private fun onWritable(key: SelectionKey) {
        val tunnel = key.attachment() as Tunnel
        Log.d("WRITE", if (tunnel is RemoteTunnel) "remote" else "local")
        // 远端可写就从队列中取出数据，写，
        // 反之亦然，
        if (tunnel is RemoteTunnel) {
            remoteQueue
        } else {
            localQueue
        }.let { queue ->
            if (queue.isEmpty()) {
                // 没有数据可写就不再关注可写事件，
                key.interestOps(SelectionKey.OP_READ)
                return
            }
            val wBuffer = queue.poll()
            Log.d("WRITE", String(buf.array(), buf.position(), buf.limit() - buf.position()))
            val len = tunnel.channel.write(wBuffer)
            if (len <= 0) {
                // 出错了，
                key.cancel()
                throw IOException("write $len bytes,")
            }
            if (wBuffer.hasRemaining()) {
                queue.put(wBuffer.duplicate())
            }
        }
    }

    private fun onReadable(key: SelectionKey) {
        val tunnel = key.attachment() as Tunnel
        Log.d("READ", if (tunnel is RemoteTunnel) "remote" else "local")
        buf.clear()
        val len = tunnel.channel.read(buf)
        if (len > 0) {
            buf.flip()

            // 真正的tcp数据都可以在这里处理，http之类的可以在这里拿到内容，
            Log.d("READ", String(buf.array(), buf.position(), buf.limit() - buf.position()))

            // 本地读的数据准备写到远端，先推进队列中，
            // 反之亦然，
            if (tunnel is LocalTunnel) {
                remoteQueue
            } else {
                localQueue
            }.let { queue ->
                if (queue.isEmpty()) {
                    // 如果队列是空，很可能相应的没有在关注可写事件，要注册下，
                    tunnel.bindTunnel!!.channel.keyFor(selector).interestOps(SelectionKey.OP_READ or SelectionKey.OP_WRITE)
                }
                queue.put(buf.duplicate())
            }
        } else if (len <= 0) {
            key.cancel()
            throw IOException("read $len bytes,")
        }
    }
}