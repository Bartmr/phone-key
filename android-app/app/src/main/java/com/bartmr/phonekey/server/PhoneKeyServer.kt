package com.bartmr.phonekey.server

import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.bartmr.phonekey.keystore.KeyStoreRepository
import com.bartmr.phonekey.keystore.KeystoreSigner
import com.bartmr.phonekey.keystore.SignResult
import com.bartmr.phonekey.settings.AppSettings
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PhoneKeyServer(
    private val activity: FragmentActivity,
    private val repository: KeyStoreRepository,
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
        } catch (e: Exception) {
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
                get("/list-keys") { handleListKeys(call) }
                post("/sign") { handleSign(call) }
            }
        }

    private suspend fun handleListKeys(call: ApplicationCall) {
        if (!commandMutex.tryLock()) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("busy"))
            return
        }

        try {
            val keys = withContext(Dispatchers.IO) {
                repository.listAliases().mapNotNull { alias ->
                    try {
                        val info = repository.getKeyInfo(alias)
                        KeyEntryResponse(
                            alias = info.alias,
                            algorithm = info.algorithm,
                            keySize = info.keySize,
                            purposes = info.purposes,
                            digests = info.digests,
                            signaturePaddings = info.signaturePaddings,
                            encryptionPaddings = info.encryptionPaddings,
                            blockModes = info.blockModes,
                            userAuthenticationRequired = info.userAuthenticationRequired,
                            userAuthenticationValidityDurationSeconds =
                                info.userAuthenticationValidityDurationSeconds,
                            publicKeyBase64 = repository.getPublicKeyDer(alias)?.let {
                                Base64.encodeToString(it, Base64.NO_WRAP)
                            },
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Failed to read key '$alias'", e)
                        null
                    }
                }
            }

            call.respond(ListKeysResponse(keys = keys))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "list-keys failed", e)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad-request"))
        } finally {
            commandMutex.unlock()
        }
    }

    private suspend fun handleSign(call: ApplicationCall) {
        if (!commandMutex.tryLock()) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("busy"))
            return
        }

        try {
            val request = call.receive<SignRequest>()

            val entry = withContext(Dispatchers.IO) {
                repository.getPrivateKeyEntry(request.keyAlias)
            }
            if (entry == null) {
                Log.e(TAG, "Key not found: ${request.keyAlias}")
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("unknown-error"))
                return
            }

            val keyInfo = withContext(Dispatchers.IO) {
                repository.getKeyInfo(request.keyAlias)
            }
            val algorithm = KeystoreSigner.deriveAlgorithm(keyInfo)
            val data = Base64.decode(request.data, Base64.DEFAULT)

            val result = withContext(Dispatchers.Main) {
                signer.sign(entry.privateKey, data, algorithm, keyInfo, activity)
            }

            when (result) {
                is SignResult.Success -> {
                    call.respond(
                        SignResponse(
                            signature = Base64.encodeToString(result.signature, Base64.NO_WRAP),
                        )
                    )
                }

                is SignResult.Error -> {
                    Log.e(TAG, "Signing failed for '${request.keyAlias}': ${result.message}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("unknown-error"),
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "sign failed", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("unknown-error"))
        } finally {
            commandMutex.unlock()
        }
    }

    companion object {
        private const val TAG = "PhoneKeyServer"
        private const val HOST = "127.0.0.1"
    }
}
