package dev.athena.creamkeyboard

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns

class FilePickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(pick, REQUEST_FILE)
        } catch (_: Exception) {
            finish()
        }
    }

    @Deprecated("Uses the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: Exception) { /* Some document providers do not offer persistable grants. */ }
                KeyboardService.fileChosen(uri)
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_FILE = 4701
        internal fun displayName(resolver: ContentResolver, uri: Uri): String? {
            var cursor: Cursor? = null
            return try {
                cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else null
            } catch (_: Exception) { null }
            finally { cursor?.close() }
        }
    }
}
