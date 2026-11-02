package io.github.rickpro.stride.ui.components

import android.content.Context
import android.content.Intent
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
