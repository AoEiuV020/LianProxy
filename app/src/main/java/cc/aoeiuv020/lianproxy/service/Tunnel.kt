package cc.aoeiuv020.lianproxy.service

import java.nio.channels.SocketChannel

/**
 *
 * Created by AoEiuV020 on 2018.03.20-07:31:45.
 */
abstract class Tunnel(val channel: SocketChannel) {
    var bindTunnel: Tunnel? = null
}

class RemoteTunnel(channel: SocketChannel) : Tunnel(channel) {
    companion object {
        fun new(tunnel: LocalTunnel): RemoteTunnel {
            val channel = SocketChannel.open()
            channel.configureBlocking(false)
            return RemoteTunnel(channel).apply {
                bind(tunnel)
            }
        }
    }

    private fun bind(tunnel: LocalTunnel) {
        bindTunnel = tunnel
        tunnel.bindTunnel = this
    }
}

class LocalTunnel(channel: SocketChannel) : Tunnel(channel)