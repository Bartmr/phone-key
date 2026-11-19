package com.bartmr.phonekey.keystore

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class UserPresence {

    suspend fun requestApproval(activity: FragmentActivity, storedKey: StoredKey): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false

        return suspendCancellableCoroutine { continuation ->
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
}
