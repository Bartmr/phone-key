package com.bartmr.phonekey

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.bartmr.phonekey.httpserver.PhoneKeyHttpServer
import com.bartmr.phonekey.keystore.KeystoreSigner
import com.bartmr.phonekey.keystore.KeysRepository
import com.bartmr.phonekey.keystore.UserPresence
import com.bartmr.phonekey.settings.AppSettings
import java.lang.ref.WeakReference
import java.security.KeyStore

class PhoneKeyApplication : Application() {
    lateinit var repository: KeysRepository
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var httpServer: PhoneKeyHttpServer
        private set

    private var currentActivity = WeakReference<FragmentActivity>(null)

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is FragmentActivity) {
                    currentActivity = WeakReference(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity.get() === activity) {
                    currentActivity = WeakReference(null)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                if (currentActivity.get() === activity) {
                    currentActivity = WeakReference(null)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity.get() === activity) {
                    currentActivity = WeakReference(null)
                }
            }
        })

        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        repository = KeysRepository(applicationContext, keyStore)
        settings = AppSettings(applicationContext)
        val userPresence = UserPresence()
        val keystoreSigner = KeystoreSigner(userPresence)
        httpServer = PhoneKeyHttpServer(
            repository = repository,
            settings = settings,
            signer = keystoreSigner,
            getCurrentActivity = { currentActivity.get() },
        )
        httpServer.start()
    }
}
