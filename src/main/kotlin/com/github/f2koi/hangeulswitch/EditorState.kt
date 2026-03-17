package com.github.f2koi.hangeulswitch

import com.maddyhome.idea.vim.state.mode.Mode

class EditorState {
    var lastSeenMode: Mode? = null
    var isKorean: Boolean = false
    var wasKoreanOnExitInsert: Boolean = false
}
