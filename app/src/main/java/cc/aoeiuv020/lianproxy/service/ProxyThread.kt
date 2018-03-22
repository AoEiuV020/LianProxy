package cc.aoeiuv020.lianproxy.service

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

/**
 * 代理服务，负责收到连接时建立隧道，
 * 代理服务收到连接时，准备好隧道，并发起对真正目的的连接请求，
 * finishConnect之后交给Connection类交换数据，
 *
 * Created by AoEiuV020 on 2018.03.20-06:24:09.
 */
class ProxyThread(private val service: LianVpnService) : Thread() {
    private val selector: Selector = Selector.open()
    private val serverSocketChannel: ServerSocketChannel = ServerSocketChannel.open()
    private val TAG = "ProxyThread"
    val port: Short

    init {
        serverSocketChannel.apply {
            configureBlocking(false)
            socket().bind(InetSocketAddress(0))
            register(selector, SelectionKey.OP_ACCEPT)
        }
        port = serverSocketChannel.socket().localPort.toShort()
    }

    override fun run() {
        Log.d(TAG, "start port = ${port.unsignedInt}")

        try {
            while (service.isRunning) {
                selector.select()
                val ite = selector.selectedKeys().iterator()
                while (ite.hasNext()) {
                    val key = ite.next()
                    if (key.isValid) {
                        when {
                            key.isConnectable -> {
                                Log.d(TAG, "connect")
                                val tunnel = key.attachment() as RemoteTunnel
                                assert(tunnel.channel.finishConnect())
                                Connection(tunnel).connected()
                                key.cancel()
                            }
                            key.isAcceptable -> {
                                Log.d(TAG, "accept")
                                onAcceptable()
                            }
                        }
                    }
                    ite.remove()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "stop", e)
        }
        Log.d(TAG, "end")
    }

    /**
     * 根据之前读写tun中的ip包的处理，
     * 这里收到的连接，源端口可以拿到session得到真正的目的ip:port,
     *
     * 这个代理服务器要同时与本地app和远端服务器通信，所以这里两个Tunnel, 各封装一个socket连接，
     * 两个Tunnel绑起来注入selector,
     * connectable时可以拿出来，转交Connection类读写数据，
     */
    private fun onAcceptable() {
        val channel = serverSocketChannel.accept()
        channel.configureBlocking(false)
        Log.d(TAG, "channel ${channel.socket().inetAddress}")
        val session = service.mainThread.sessions[channel.socket().port.toShort()]
                ?: throw IOException("no session")
        val lTunnel = LocalTunnel(channel)
        val rTunnel = RemoteTunnel.new(lTunnel)
        assert(service.protect(channel.socket()))
        assert(service.protect(rTunnel.channel.socket()))
        rTunnel.channel.register(selector, SelectionKey.OP_CONNECT, rTunnel)
        rTunnel.channel.connect(InetSocketAddress(channel.socket().inetAddress, session.port.unsignedInt))
    }

    fun quit() {
        interrupt()
        selector.use { }
        serverSocketChannel.use { }
    }
}