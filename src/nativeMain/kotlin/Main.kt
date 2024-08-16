@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.windows.*


//const val MEDIA_INTENT_BUTTON = 0x4D // 'M' key

private var hook: HHOOK? = null

private fun eventHook(nCode: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
    return if (processHook(nCode, wParam, lParam))
        CallNextHookEx(hook!!, nCode, wParam, lParam)
    else 1
}

@OptIn(ExperimentalForeignApi::class)
fun processHook(nCode: Int, wParam: WPARAM, lParam: LPARAM): Boolean {
    if (nCode < 0)
        return false

    val info = lParam.toCPointer<KBDLLHOOKSTRUCT>()!!.pointed
    val keyCode = info.vkCode.toInt()

    when (wParam.toInt()) {
        WM_KEYDOWN, WM_SYSKEYDOWN -> {
            val shift = keyPressed(VK_SHIFT) xor keyToggled(VK_CAPITAL)
            val alt = keyPressed(VK_MENU)
            val rControl = keyPressed(VK_RCONTROL)
            val lControl = keyPressed(VK_LCONTROL)
            val win = keyPressed(VK_LWIN) or keyPressed(VK_RWIN)

            val isMediaIntentActive = true // keyPressed(MEDIA_INTENT_BUTTON)

            if (isMediaIntentActive && rControl && !(shift || alt || win || lControl)) {
                val mediaInputKey = when (keyCode) {
                    VK_SPACE -> VK_MEDIA_PLAY_PAUSE
                    VK_RIGHT -> VK_MEDIA_NEXT_TRACK
                    VK_LEFT -> VK_MEDIA_PREV_TRACK
                    else -> null
                }

                if (mediaInputKey == null) return true

                memScoped {
                    val inputs = allocArrayOf(
                        cValue<INPUT> {
                            type = 1u
                            ki.wVk = mediaInputKey.toUShort()
                            ki.wScan = 0u
                            ki.dwFlags = 0u
                            ki.time = 0u
                            ki.dwExtraInfo = NULL.toLong().toULong()
                        }.getBytes()
                        + cValue<INPUT> {
                            type = 1u
                            ki.wVk = mediaInputKey.toUShort()
                            ki.wScan = 0u
                            ki.dwFlags = KEYEVENTF_KEYUP.toUInt()
                            ki.time = 0u
                            ki.dwExtraInfo = NULL.toLong().toULong()
                        }.getBytes()
                    )
                    SendInput(
                        2u,
                        inputs.reinterpret(),
                        sizeOf<INPUT>().toInt()
                    )
                }
                return false
            }
        }
    }

    return true
}

fun keyPressed(keyCode: Int) =
    (GetKeyState(keyCode).toInt() and 0x8000) == 0x8000

fun keyToggled(keyCode: Int) =
    (GetKeyState(keyCode).toInt() and 1) == 1

fun initHook() {
    val hMod = GetModuleHandleA(null)
    hook = SetWindowsHookExA(WH_KEYBOARD_LL, staticCFunction(::eventHook), hMod, 0u)!!
}

fun disposeHook() {
    if (hook != null) {
        UnhookWindowsHookEx(hook!!)
        hook = null
    }
}

fun main() {
    ShowWindow(GetConsoleWindow(), SW_HIDE)
    initHook()

    memScoped {
        val msg = alloc<MSG>()
        while (GetMessage!!(msg.ptr, null, 0u, 0u) == 1) {
            TranslateMessage(msg.ptr)
            DispatchMessage!!(msg.ptr)
        }
    }
    disposeHook()
}