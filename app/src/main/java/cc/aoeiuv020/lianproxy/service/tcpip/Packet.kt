package cc.aoeiuv020.lianproxy.service.tcpip

import java.nio.ByteBuffer

/**
 * 封装ip, tcp数据包处理相关细节，
 * TODO: 目前用的几个类是GPL3的，之后换掉，
 * Created by AoEiuV020 on 2018.03.20-01:49:26.
 */
class Packet {
    val byteArray = ByteArray(Short.MAX_VALUE.toInt())
    val byteBuffer: ByteBuffer = ByteBuffer.wrap(byteArray)
    val ip = IPHeader(byteArray, 0)
    val tcp = TCPHeader(byteArray, 20)
    val udp = TCPHeader(byteArray, 20)

    val isTcp: Boolean get() = ip.protocol == IPHeader.TCP

    override fun toString(): String {
        return ip.toString() + "\n" + if (isTcp) tcp.toString() else udp.toString()
    }

    fun computeTCPChecksum() {
        CommonMethods.ComputeTCPChecksum(ip, tcp)
    }
}