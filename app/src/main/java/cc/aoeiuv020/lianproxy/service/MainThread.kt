package cc.aoeiuv020.lianproxy.service

import android.os.ParcelFileDescriptor
import android.util.Log
import cc.aoeiuv020.lianproxy.service.tcpip.CommonMethods
import cc.aoeiuv020.lianproxy.service.tcpip.Packet
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 *
 * Created by AoEiuV020 on 2018.03.13-01:51:25.
 */


class MainThread(private val service: LianVpnService) : Thread() {
    private val pfd: ParcelFileDescriptor get() = service.pfd

    private val tunIn: FileInputStream = FileInputStream(pfd.fileDescriptor)

    private val tunOut: FileOutputStream = FileOutputStream(pfd.fileDescriptor)
    val localIp = CommonMethods.ipStringToInt("172.17.1.1")
    val sessions: MutableMap<Short, Session> = mutableMapOf()
    private val TAG = "MainThread"

    override fun run() {
        Log.d(TAG, "start")

        try {
            tunIn.use {
                val packet = Packet()
                var len = it.read(packet.byteArray)
                while (service.isRunning) {
                    if (onPacketReceived(packet, len)) {
                        sleep(100)
                    }

                    len = it.read(packet.byteArray)
                }
            }

        } catch (_: Exception) {

        }

        Log.d(TAG, "end")
    }

    /**
     * 以本地代理服务监听172.17.1.1:44444为例，
     * 有app访问某度web服务器111.13.101.208:80，
     * 需要处理的数据包有两种情况，
     *
     * 对应下面的else,
     * 普通app发起的连接，向外发送的数据包，被转发到tun设备时，源变成 172.17.1.1:55555, 目的不变，
     * 172.17.1.1:55555 -> 111.13.101.208:80
     * 这个源端口是唯一的，所以可以维护一个map，名为sessions，用sourcePort做key, 保存目标ip:port，
     * 做端口映射，目的ip:port改成本app另一个线程开的代理服务serverSocket,
     * 这个serverSocket就会自动处理tcp握手之类的细节，
     * 自己只要处理accept, connect, read, write这些不涉及协议细节的工作，
     * 源端口保留才能从sessions中拿到真正的目的ip:port，
     * 这里的源ip就没用了，下面选择复制一份目的ip，这样代理服务处理accept时可以从源ip得到真正的目的ip，不这样也可以从session里拿，
     * 代理服务器通过普通的被保护的socket与真正的目的服务器连接，交换数据，
     * 最终tcp包是这样的，
     * 111.13.101.208:55555 -> 172.17.1.1:44444
     * 写进tun设备就能被代理服务收到了，
     *
     * 对应下面的if,
     * 代理服务在 收到网站回复后 发送回一开始发出连接的app的数据包，
     * 172.17.1.1:44444 -> 111.13.101.208:55555
     * 和上面的处理刚好相反，要处理成，
     * 111.13.101.208:80 -> 172.17.1.1:55555
     * 显然，重要的key在目的端口，拿到session，几个赋值就搞定了，
     * 写进tun设备就能被原app收到了，
     *
     * @return 没数据读写的话返回true，线程休息一下，
     */
    private fun onPacketReceived(packet: Packet, len: Int): Boolean {
        if (len <= 0 || packet.ip.sourceIP != localIp) {
            return true
        }
        packet.computeHeaderLength()
        if (packet.isTcp) {
            println(packet.toString())
            if (packet.tcp.sourcePort == service.proxyThread.port) {
                val session = sessions[packet.tcp.destinationPort]
                        ?: return true
                packet.ip.sourceIP = packet.ip.destinationIP
                packet.tcp.sourcePort = session.port
                packet.ip.destinationIP = localIp
                packet.computeTCPChecksum()
                tunOut.write(packet.byteArray, 0, len)
            } else {
                val port = packet.tcp.sourcePort
                @Suppress("UNUSED_VARIABLE")
                val session = sessions[port]?.takeIf { it.ip == packet.ip.destinationIP && it.port == packet.tcp.destinationPort }
                        ?: Session(packet.ip.destinationIP, packet.tcp.destinationPort).also { sessions[port] = it }
                packet.ip.sourceIP = packet.ip.destinationIP
                packet.ip.destinationIP = localIp
                packet.tcp.destinationPort = service.proxyThread.port
                packet.computeTCPChecksum()
                tunOut.write(packet.byteArray, 0, len)
            }
        }
        return false
    }

    fun quit() {
        interrupt()
    }
}
