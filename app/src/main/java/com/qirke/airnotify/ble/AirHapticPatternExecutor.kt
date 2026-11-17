package com.qirke.airnotify.ble

import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.data.Method
import com.fitbit.goldengate.bindings.coap.data.OutgoingRequestBuilder
import com.fitbit.goldengate.bindings.coap.data.ok
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AirHapticPatternExecutor {

    @Volatile
    var isWriteInFlight: Boolean = false

    fun execute(
        endpoint: CoapEndpoint,
        pattern: AirHapticPattern,
        checkpoint: (Int) -> Unit = {},
        verified: () -> Unit = {}
    ): Single<AirHapticExecutionResult> =
        HapticPatternRunner(CoapSettingsClient(endpoint), checkpoint = checkpoint, verified = verified)
            .execute(pattern)

    fun executeRinging(
        endpoint: CoapEndpoint,
        pattern: AirHapticPattern,
        shouldContinue: () -> Boolean,
        maxDurationMillis: Long = 60_000L,
        checkpoint: (Int) -> Unit = {},
        verified: () -> Unit = {}
    ): Single<AirHapticExecutionResult> =
        HapticPatternRunner(CoapSettingsClient(endpoint), checkpoint = checkpoint, verified = verified)
            .executeRinging(pattern, shouldContinue, maxDurationMillis)

    fun checkConnection(endpoint: CoapEndpoint): Single<AirHapticExecutionResult> =
        HapticPatternRunner(CoapSettingsClient(endpoint)).checkConnection()

    fun recover(endpoint: CoapEndpoint, original: Int, verified: () -> Unit): Single<Unit> =
        HapticPatternRunner(CoapSettingsClient(endpoint), verified = verified).recover(original)

    private class CoapSettingsClient(private val endpoint: CoapEndpoint) : HapticSettingsClient {
        override fun read(): Single<Int> {
            val request = OutgoingRequestBuilder("/settings", Method.PUT)
                .expectSuccess(false)
                .maxResendCount(2)
                .body(AirDirectSettingsProtocol.encodeHapticsSettingsRequest())
                .build()

            return endpoint.responseFor(request)
                .observeOn(Schedulers.io())
                .timeout(8, TimeUnit.SECONDS)
                .flatMap { response ->
                    require(response.responseCode.ok()) { "Settings read returned ${response.responseCode}" }
                    response.body.asData().map {
                        AirDirectSettingsProtocol.parseHapticsIntensity(it)
                            ?: throw IllegalStateException("Haptics intensity missing from settings response")
                    }
                }
        }

        override fun write(intensity: Int): Single<Unit> {
            val request = OutgoingRequestBuilder("/settings", Method.POST)
                .expectSuccess(false)
                .maxResendCount(2)
                .body(AirDirectSettingsProtocol.encodeHapticsSettingsResponse(intensity))
                .build()

            return Single.fromCallable { isWriteInFlight = true }
                .flatMap {
                    endpoint.responseFor(request)
                        .observeOn(Schedulers.io())
                        .timeout(8, TimeUnit.SECONDS)
                }
                .doFinally { isWriteInFlight = false }
                .map { response ->
                    require(response.responseCode.ok()) { "Settings write returned ${response.responseCode}" }
                    Timber.d("Haptic write confirmed: %d", intensity)
                    Unit
                }
        }
    }
}
