package com.vorthas.yusu.ui.screen.terminal

import com.vorthas.yusu.ksuApp
import java.io.File

/**
 * JNI bridge to a real pseudo-terminal. The child process is launched through the same
 * ksud `debug su` entry point used by KsuCli, so the session is a genuine root shell with
 * a controlling TTY (interactive programs such as vi/top work).
 */
object Pty {
    init {
        System.loadLibrary("yscore")
    }

    external fun nativeOpen(argv: Array<String>, cols: Int, rows: Int): Long
    external fun nativeRead(handle: Long): ByteArray?
    external fun nativeWrite(handle: Long, data: ByteArray): Int
    external fun nativeResize(handle: Long, cols: Int, rows: Int): Boolean
    external fun nativeClose(handle: Long)

    fun open(args: Array<String>, cols: Int, rows: Int): Long = nativeOpen(args, cols, rows)

    fun open(cols: Int, rows: Int): Long = open(arrayOf("/system/bin/sh"), cols, rows)

    fun send(handle: Long, text: String) {
        if (handle != 0L && text.isNotEmpty()) nativeWrite(handle, text.toByteArray(Charsets.UTF_8))
    }
}
