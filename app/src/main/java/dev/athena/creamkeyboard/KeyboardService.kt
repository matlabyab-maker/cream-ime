package dev.athena.creamkeyboard

import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import android.content.ClipDescription
import android.text.InputType
import java.util.ArrayDeque

class KeyboardService : InputMethodService() {
    private var keyboard: KeyboardView? = null
    private var english = false
    private var symbols = false
    private var caps = 0
    private var secureField = false
    private val undoStack = ArrayDeque<EditOp>()
    private val redoStack = ArrayDeque<EditOp>()

    private data class EditOp(val inserted: String, val removed: String)

    override fun onCreate() {
        super.onCreate()
        active = this
    }

    override fun onCreateInputView(): android.view.View {
        keyboard = KeyboardView(this, ::handleAction)
        return keyboard!!
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val inputType = attribute?.inputType ?: InputType.TYPE_NULL
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        secureField = when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        undoStack.clear()
        redoStack.clear()
        caps = 0
        refresh()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        active = this
        refresh()
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    private fun refresh() = keyboard?.update(english, symbols, secureField, caps)

    private fun handleAction(action: KAction) {
        val ic = currentInputConnection ?: return
        when (action.name) {
            "INSERT" -> insertTracked(action.value)
            "LETTER" -> {
                val value = if (english && caps > 0) action.value.uppercase() else action.value
                insertTracked(value)
                if (caps == 1) { caps = 0; refresh() }
            }
            "SPACE" -> insertTracked(" ")
            "DELETE" -> deleteOne()
            "ENTER" -> sendEnter()
            "SEND" -> sendAction()
            "PASTE" -> ic.performContextMenuAction(android.R.id.paste)
            "COPY" -> ic.performContextMenuAction(android.R.id.copy)
            "SELECT" -> ic.performContextMenuAction(android.R.id.selectAll)
            "UNDO" -> undo()
            "REDO" -> redo()
            "ARROW" -> moveCursor(action.value)
            "LANG" -> { english = !english; refresh() }
            "SYMBOLS" -> { symbols = !symbols; refresh() }
            "TOOL" -> toast("ابزارها: ویرایش متن، حرکت مکان‌نما و انتخاب")
            "EMOJI" -> insertTracked("🙂")
            "SUGGEST" -> replaceCurrentWord(action.value)
            "FILE" -> openFilePicker()
            "CAPS_SINGLE" -> { caps = 1; refresh() }
            "CAPS_DOUBLE" -> { caps = if (caps == 2) 0 else 2; refresh() }
            "CAPS_LONG" -> { caps = 0; refresh() }
        }
    }

    private fun insertTracked(text: String) {
        val ic = currentInputConnection ?: return
        val selected = if (!secureField) ic.getSelectedText(0)?.toString().orEmpty() else ""
        ic.commitText(text, 1)
        if (!secureField) {
            undoStack.addLast(EditOp(text, selected))
            redoStack.clear()
        }
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val before = if (!secureField) ic.getTextBeforeCursor(2, 0)?.toString().orEmpty() else ""
        val last = if (before.isEmpty()) "" else {
            val cp = before.codePointBefore(before.length)
            String(Character.toChars(cp))
        }
        if (Build.VERSION.SDK_INT >= 24) ic.deleteSurroundingTextInCodePoints(1, 0)
        else ic.deleteSurroundingText(if (last.isEmpty()) 1 else last.length, 0)
        if (!secureField && last.isNotEmpty()) {
            undoStack.addLast(EditOp("", last))
            redoStack.clear()
        }
    }

    private fun undo() {
        val ic = currentInputConnection ?: return
        if (secureField || undoStack.isEmpty()) { toast("Undo برای این فیلد در دسترس نیست"); return }
        val op = undoStack.removeLast()
        if (op.inserted.isNotEmpty()) deletePrevious(op.inserted)
        if (op.removed.isNotEmpty()) ic.commitText(op.removed, 1)
        redoStack.addLast(op)
    }

    private fun redo() {
        val ic = currentInputConnection ?: return
        if (secureField || redoStack.isEmpty()) { toast("ReDo در دسترس نیست"); return }
        val op = redoStack.removeLast()
        if (op.removed.isNotEmpty()) deletePrevious(op.removed)
        if (op.inserted.isNotEmpty()) ic.commitText(op.inserted, 1)
        undoStack.addLast(op)
    }

    private fun deletePrevious(text: String) {
        val ic = currentInputConnection ?: return
        val count = text.codePointCount(0, text.length)
        if (Build.VERSION.SDK_INT >= 24) ic.deleteSurroundingTextInCodePoints(count, 0)
        else ic.deleteSurroundingText(text.length, 0)
    }

    private fun replaceCurrentWord(word: String) {
        val ic = currentInputConnection ?: return
        if (secureField) return
        val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        val current = before.takeLastWhile { !it.isWhitespace() }
        if (current.isNotEmpty()) deletePrevious(current)
        insertTracked(word + " ")
    }

    private fun sendAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val options = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val action = options and EditorInfo.IME_MASK_ACTION
        val editorAction = when (action) {
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS -> action
            else -> if (info?.actionId != null && info.actionId != 0) info.actionId else 0
        }
        if (editorAction != 0 && ic.performEditorAction(editorAction)) return
        sendEnter()
    }

    private fun sendEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            ic.performEditorAction(action)) return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun moveCursor(direction: String) {
        val code = when (direction) {
            "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "UP" -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
        currentInputConnection?.let {
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun openFilePicker() {
        if (secureField) { toast("انتخاب فایل در فیلد گذرواژه خاموش است"); return }
        try {
            startActivity(android.content.Intent(this, FilePickerActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast("بازکردن انتخاب‌گر فایل ممکن نشد")
        }
    }

    private fun acceptFile(uri: Uri) {
        if (secureField) return
        if (Build.VERSION.SDK_INT < 25) {
            toast("برای ارسال محتوای فایل از کیبورد، Android 7 یا بالاتر لازم است")
            return
        }
        val ic = currentInputConnection ?: run { toast("فیلد ویرایش فعالی پیدا نشد"); return }
        val info = currentInputEditorInfo
        val accepted = info?.contentMimeTypes ?: emptyArray()
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        if (accepted.none { mimeMatches(it, mime) }) {
            toast("این فیلد فایل را نمی‌پذیرد؛ از دکمهٔ پیوست خود سایت استفاده کن")
            return
        }
        val name = FilePickerActivity.displayName(contentResolver, uri) ?: "file"
        val content = InputContentInfo(uri, ClipDescription(name, arrayOf(mime)), null)
        val ok = try {
            ic.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, Bundle())
        } catch (_: Exception) { false }
        toast(if (ok) "فایل به فیلدِ پشتیبانِ محتوای غنی تحویل شد" else "برنامهٔ مقصد این نوع فایل را نپذیرفت")
    }

    private fun mimeMatches(accepted: String, actual: String): Boolean =
        accepted == actual || (accepted.endsWith("/*") && actual.startsWith(accepted.substringBefore('*')))

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        @Volatile private var active: KeyboardService? = null
        internal fun fileChosen(uri: Uri) { active?.acceptFile(uri) }
    }
}
