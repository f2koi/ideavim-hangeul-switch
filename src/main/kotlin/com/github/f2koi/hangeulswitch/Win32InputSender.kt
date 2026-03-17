package com.github.f2koi.hangeulswitch

import com.intellij.openapi.diagnostic.Logger
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * INPUT 구조체 레이아웃 (x64, 40 bytes):
 *   offset  0: type        (DWORD, 4 bytes)
 *   offset  4: padding     (4 bytes)
 *   offset  8: wVk         (WORD, 2 bytes)
 *   offset 10: wScan       (WORD, 2 bytes)
 *   offset 12: dwFlags     (DWORD, 4 bytes)
 *   offset 16: time        (DWORD, 4 bytes)
 *   offset 20: padding     (4 bytes)
 *   offset 24: dwExtraInfo (ULONG_PTR, 8 bytes)
 *   offset 32: union pad   (8 bytes, 가장 큰 구성원인 MOUSEINPUT 크기에 맞춤)
 */
object Win32InputSender {

    private val LOG = Logger.getInstance(Win32InputSender::class.java)

    // https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-keybdinput#members
    private const val INPUT_KEYBOARD = 1
    private const val F_EXTENDEDKEY = 0x1
    private const val F_KEYUP = 0x2
    private const val F_SCANCODE = 0x8
    private const val INPUT_SIZE = 40
    private const val OFF_TYPE = 0L
    private const val OFF_WSCAN = 10L    // 8(union start) + 2(wVk)
    private const val OFF_DWFLAGS = 12L  // 8(union start) + 2(wVk) + 2(wScan)

    // https://learn.microsoft.com/en-us/windows/win32/inputdev/about-keyboard-input#scan-codes
    private const val SCANCODE_RIGHT_ALT: Short = 0x38

    private interface NativeUser32 : Library {
        fun SendInput(nInputs: Int, pInputs: Pointer, cbSize: Int): Int
    }

    private val user32: NativeUser32? by lazy {
        try {
            Native.load("user32", NativeUser32::class.java)
        } catch (e: UnsatisfiedLinkError) {
            LOG.warn("Failed to load user32.dll", e)
            null
        }
    }

    fun sendHangeulToggle(): Boolean {
        if (user32 == null) {
            return false
        }

        val buffer = Memory((INPUT_SIZE * 2).toLong()).apply { clear() }
        prepareKeyEvent(buffer, SCANCODE_RIGHT_ALT, F_SCANCODE or F_EXTENDEDKEY)

        val sent = user32!!.SendInput(2, buffer, INPUT_SIZE)

        if (sent != 2) {
            LOG.warn("SendInput: $sent/2 event(s) sent")
            return false
        }

        return true
    }

    private fun prepareKeyEvent(buf: Memory, scanCode: Short, flags: Int) {
        buf.setInt(OFF_TYPE, INPUT_KEYBOARD)
        buf.setShort(OFF_WSCAN, scanCode)
        buf.setInt(OFF_DWFLAGS, flags)

        val inputSize = INPUT_SIZE.toLong()
        buf.setInt(inputSize + OFF_TYPE, INPUT_KEYBOARD)
        buf.setShort(inputSize + OFF_WSCAN, scanCode)
        buf.setInt(inputSize + OFF_DWFLAGS, flags or F_KEYUP)
    }
}
