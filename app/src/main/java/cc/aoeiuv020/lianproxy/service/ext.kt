package cc.aoeiuv020.lianproxy.service

/**
 *
 * Created by AoEiuV020 on 2018.03.20-07:57:50.
 */
/**
 * short端口号转成int,
 */
val Short.unsignedInt get() = this.toInt() and 0xffff