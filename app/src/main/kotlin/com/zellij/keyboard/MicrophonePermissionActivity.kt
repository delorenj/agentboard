package com.zellij.keyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** Minimal permission bridge because an InputMethodService cannot show a runtime permission dialog. */
class MicrophonePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            publishPermissionResult(granted = true)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_REQUEST,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            publishPermissionResult(
                granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    private fun publishPermissionResult(granted: Boolean) {
        val callback = MicrophonePermissionContract.onPermissionResult
        finish()
        callback?.invoke(granted)
    }

    private companion object {
        const val MICROPHONE_PERMISSION_REQUEST = 1001
    }
}

internal object MicrophonePermissionContract {
    var onPermissionResult: ((Boolean) -> Unit)? = null
}
