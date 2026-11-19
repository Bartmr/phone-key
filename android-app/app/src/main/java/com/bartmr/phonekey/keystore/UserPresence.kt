package com.bartmr.phonekey.keystore

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class UserPresence {

    suspend fun requestApproval(activity: FragmentActivity, storedKey: StoredKey): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false

        val approved = withTimeoutOrNull(APPROVAL_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Approve signing")
                    .setMessage("Allow this request to sign with \"${storedKey.name}\"?")
                    .setPositiveButton("Approve") { _, _ ->
                        if (continuation.isActive) continuation.resume(true)
                    }
                    .setNegativeButton("Deny") { _, _ ->
                        if (continuation.isActive) continuation.resume(false)
                    }
                    .setOnCancelListener {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    .create()

                continuation.invokeOnCancellation {
                    activity.runOnUiThread { dialog.dismiss() }
                }

                dialog.show()
            }
        }

        return approved == true
    }

    companion object {
        private const val APPROVAL_TIMEOUT_MILLIS = 120_000L
    }
}
