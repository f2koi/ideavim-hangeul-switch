package com.github.f2koi.hangeulswitch

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.messages.MessageBusConnection
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.state.mode.Mode

/**
 * 기능: Insert -> Normal 전환 시 영문 모드로 전환하고, Normal -> Insert 전환 시 이전 입력 레이아웃 상태를 복원합니다.
 *
 * 입력 상태 감지:
 * - DocumentListener로 마지막 입력 문자의 Unicode 범위를 확인하여 그것이 한글이었다면 한글 모드로 판단
 * - ASCII 문자 입력 → 영문 모드로 판단
 *
 * 제한사항 (v0.1.0):
 * - 한글로 수동 전환 후 아무것도 타이핑하지 않고 ESC를 누르면 전환이 감지되지 않음
 * - 한글로 입력하다가 영문 전환 후 아무것도 타이핑하지 않고 ESC를 누르면 normal mode 전환 시 다시 한글로 바뀌어버림
 */
class HangeulSwitchExtension : VimExtension {

    companion object {
        private val LOG = Logger.getInstance(HangeulSwitchExtension::class.java)
        private val STATE_KEY = Key.create<EditorState>("HangeulSwitch.state")
    }

    private var messageBusConnection: MessageBusConnection? = null

    override fun getName(): String = "hangeul-switch"

    override fun init() {
        LOG.info("Initializing HangeulSwitch")

        val messageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            this.messageBusConnection = it
        }

        // IdeaVIM 모드 변경 시 레이아웃 변경
        messageBusConnection.subscribe(
            CommandListener.TOPIC,
            object : CommandListener {
                override fun commandFinished(event: CommandEvent) {
                    handleCommandFinished()
                }
            }
        )

        // 한/영 레이아웃 상태 추적
        EditorFactory
            .getInstance()
            .eventMulticaster
            .addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        handleDocumentChanged(event)
                    }
                },
                messageBusConnection
            )

        LOG.info("HangeulSwitch loaded")
    }

    override fun dispose() {
        messageBusConnection?.let {
            Disposer.dispose(it)
            messageBusConnection = null
        }
    }

    // ── Mode change handling ────────────────────────────────

    private fun handleCommandFinished() {
        val editor = findFocusedEditor() ?: return

        val currentMode = try {
            editor.vim.mode
        } catch (_: Exception) {
            // IdeaVim이 로드되기 전이거나 disabled 상태
            return
        }

        val state = editor.getOrCreateState()
        val prevMode = state.lastSeenMode
        state.lastSeenMode = currentMode

        if (prevMode == null || prevMode::class == currentMode::class) return

        when {
            isInsertLike(prevMode) && isNormalLike(currentMode) -> onExitInsert(state)
            isNormalLike(prevMode) && isInsertLike(currentMode) -> onEnterInsert(state)
        }
    }

    private fun onExitInsert(state: EditorState) {
        state.wasKoreanOnExitInsert = state.isKorean
        if (state.isKorean) {
            Win32InputSender.sendHangeulToggle()
            state.isKorean = false
        }
    }

    private fun onEnterInsert(state: EditorState) {
        LOG.info("needToRestoreLayout = ${needToRestoreLayout()}")
        if (needToRestoreLayout() && state.wasKoreanOnExitInsert) {
            Win32InputSender.sendHangeulToggle()
            state.isKorean = true
        }
    }

    // ── Korean character detection ──────────────────────────

    private fun handleDocumentChanged(event: DocumentEvent) {
        val editor = findFocusedEditor() ?: return
        if (editor.document !== event.document) return

        val fragment = event.newFragment
        if (fragment.isEmpty()) return

        val lastChar = fragment.last()
        val state = editor.getOrCreateState()

        // Space, punctuation 등 모호한 문자는 상태 변경 안 함.
        // 한글 레이아웃에서도 space나 숫자를 입력할 수 있으므로, 확실한 기준으로만 판단.
        when {
            isKoreanChar(lastChar) -> state.isKorean = true
            lastChar.isLetterOrDigit() -> state.isKorean = false
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun Editor.getOrCreateState(): EditorState {
        return this.getUserData(STATE_KEY) ?: EditorState().also {
            state -> this.putUserData(STATE_KEY, state)
        }
    }

    private fun findFocusedEditor(): Editor? {
        return EditorFactory.getInstance().allEditors.firstOrNull {
            it.contentComponent.isFocusOwner
        }
    }

    private fun needToRestoreLayout(): Boolean {
        val vimVariable = injector
            .variableService
            .getGlobalVariableValue(VimVariable.RESTORE_LAYOUT_STATE_ON_ENTER_INSERT_MODE)
            ?: return false

        return vimVariable.toVimNumber().value == 1
    }

    private fun isInsertLike(mode: Mode): Boolean = mode is Mode.INSERT || mode is Mode.REPLACE
    private fun isNormalLike(mode: Mode): Boolean = mode is Mode.NORMAL

    /**
     * - U+AC00..U+D7A3: Hangul Syllables
     * - U+3131..U+3163: Hangul Compatibility Jamo, 단일 자모 표기용
     * - U+1100..U+11FF: Hangul Jamo, 조합용
     */
    private fun isKoreanChar(c: Char): Boolean =
        c in '\uAC00'..'\uD7A3' || c in '\u3131'..'\u3163' || c in '\u1100'..'\u11FF'
}
