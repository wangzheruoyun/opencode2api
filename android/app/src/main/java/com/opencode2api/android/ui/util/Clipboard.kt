package com.opencode2api.android.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Copy helper. Used for the values people actually need to carry out of the
 * app — the server key, the loopback base URL, a model id.
 */
fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}

/** A short line of feedback for actions with no other visible effect. */
fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
