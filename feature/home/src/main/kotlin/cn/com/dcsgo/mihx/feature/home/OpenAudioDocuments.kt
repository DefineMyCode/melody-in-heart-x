package cn.com.dcsgo.mihx.feature.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Picks one or more audio files via `ACTION_OPEN_DOCUMENT` with `EXTRA_ALLOW_MULTIPLE`.
 *
 * The built-in [androidx.activity.result.contract.ActivityResultContracts.OpenDocument] only takes
 * MIME types and does not request multiple selection, so we declare a small custom contract to
 * guarantee multi-file import (plan P5-A).
 */
class OpenAudioDocuments : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData
        if (clip != null) return (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        return listOfNotNull(intent.data)
    }
}
