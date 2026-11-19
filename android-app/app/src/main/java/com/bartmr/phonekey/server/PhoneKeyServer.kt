package com.bartmr.phonekey.server

import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.bartmr.phonekey.keystore.KeysRepository
import com.bartmr.phonekey.keystore.KeystoreSigner
import com.bartmr.phonekey.keystore.SignResult
import com.bartmr.phonekey.settings.AppSettings
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.BindException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PhoneKeyServer(
    private val activity: FragmentActivity,
    private val repository: KeysRepository,
    private val settings: AppSettings,
    private val signer: KeystoreSigner = KeystoreSigner(),
) {
    private val commandMutex = Mutex()
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var runningPort: Int? = null

    fun start() {
        if (engine != null) return

        val port = settings.serverPort
        val server = createServer(port)
        server.start(wait = false)
        engine = server
        runningPort = port

        Log.i(TAG, "Listening on http://$HOST:$port")
    }

    fun stop() {
        val running = engine ?: return
        engine = null
        runningPort = null
        running.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        Log.i(TAG, "Stopped")
    }

    fun restart(): Boolean {
        val port = settings.serverPort
        if (engine != null && port == runningPort) return true

        if (engine != null) {
            Log.i(TAG, "Restarting server on port $port")
            stop()
        }

        return try {
            start()
            true
        } catch (e: BindException) {
            Log.e(TAG, "Failed to start server on port $port", e)
            false
        }
    }

    private fun createServer(
        port: Int,
    ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(
            CIO,
            port = port,
            host = HOST,
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("/keys") { handleListKeys(call) }
                post("/keys/{id}/sign") { handleSign(call) }
            }
        }

    private suspend fun handleListKeys(call: ApplicationCall) {
        if (!commandMutex.tryLock()) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("busy"))
            return
        }

        try {
            val keys = withContext(Dispatchers.IO) {
                repository.getAllStoredKeys().map { storedKey ->
                    KeyEntryResponse(
                        id = storedKey.id,
                        name = storedKey.name,
                        algorithm = storedKey.algorithm,
                        keySize = storedKey.keySize,
                        purposes = storedKey.purposes,
                        digests = storedKey.digests,
                        signaturePaddings = storedKey.signaturePaddings,
                        encryptionPaddings = storedKey.encryptionPaddings,
                        blockModes = storedKey.blockModes,
                        publicKeyBase64 = repository.getPublicKeyDer(storedKey.id)?.let {
                            Base64.encodeToString(it, Base64.NO_WRAP)
                        },
                    )
                }
            }

            call.respond(ListKeysResponse(keys = keys))
        } finally {
            commandMutex.unlock()
        }
    }

    private suspend fun handleSign(call: ApplicationCall) {
        if (!commandMutex.tryLock()) {
            call.respond(HttpStatusCode.Conflict)
            return
        }

        try {
            val keyId = call.parameters["id"]
            if (keyId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return
            }

            val data = call.receive<ByteArray>()

            val entry = withContext(Dispatchers.IO) {
                repository.getPrivateKeyEntry(keyId)
            }
            if (entry == null) {
                Log.e(TAG, "Key not found: $keyId")
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val storedKey = withContext(Dispatchers.IO) {
                repository.getStoredKey(keyId)
            }
            val algorithm = KeystoreSigner.deriveAlgorithm(storedKey)

            val result = withContext(Dispatchers.Main) {
                signer.sign(entry.privateKey, data, algorithm, storedKey, activity)
            }

            when (result) {
                is SignResult.Success -> {
                    call.respondBytes(result.signature, ContentType.Application.OctetStream)
                }

                is SignResult.Denied -> {
                    Log.i(TAG, "Signing denied by user for '$keyId'")
                    call.respond(HttpStatusCode.Forbidden)
                }

                is SignResult.KeyInvalidated -> {
                    Log.e(TAG, "Key '$keyId' is permanently invalidated")
                    call.respond(HttpStatusCode.Gone)
                }

                is SignResult.Error -> {
                    Log.e(TAG, "Signing failed for '$keyId': ${result.message}")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        } finally {
            commandMutex.unlock()
        }
    }

    companion object {
        private const val TAG = "PhoneKeyServer"
        private const val HOST = "127.0.0.1"
    }
}
