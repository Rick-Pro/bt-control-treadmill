package io.github.rickpro.stride.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Hands plain text to the system share sheet. Nothing leaves the phone unless the user picks a target. */
fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens Android's Bluetooth settings so the user can turn Bluetooth on. */
fun openBluetoothSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Opens a web page in the user's browser. Stride has no internet permission and needs none for this: the browser
 * does the loading. Does nothing if the phone has no app that can open it.
 */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing to open.
    }
}
