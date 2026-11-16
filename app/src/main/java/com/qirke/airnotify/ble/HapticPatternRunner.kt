package com.qirke.airnotify.ble

import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

interface HapticSettingsClient {
    fun read(): Single<Int>
    fun write(intensity: Int): Single<Unit>
}

data class AirHapticExecutionResult(val initialIntensity: Int, val finalIntensity: Int, val groupCount: Int)
class HapticsRestoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class HapticPatternRunner(
    private val client: HapticSettingsClient,
    private val scheduler: Scheduler = Schedulers.computation(),
    private val checkpoint: (Int) -> Unit = {},
    private val verified: () -> Unit = {}
) {
    fun checkConnection(): Single<AirHapticExecutionResult> = client.read().map { intensity ->
        require(intensity in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) {
            "Unsupported haptics intensity: $intensity"
        }
        AirHapticExecutionResult(intensity, intensity, 0)
    }

    fun execute(pattern: AirHapticPattern): Single<AirHapticExecutionResult> = client.read().flatMap { original ->
        require(original in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) {
            "Unsupported starting haptics intensity: $original"
        }
        checkpoint(original)

        // Clean single-pulse mechanism:
        // When band is HIGH or LOW, writing OFF is completely silent (no motor pulse).
        // Then restoring to original triggers exactly one physical vibration confirmation!
        // If band was originally OFF, pulse HIGH (vibrates) and restore to OFF (silent).
        val prepValue = if (original == AirDirectSettingsProtocol.HAPTICS_OFF) {
            AirDirectSettingsProtocol.HAPTICS_HIGH
        } else {
            AirDirectSettingsProtocol.HAPTICS_OFF
        }

        var chain = Single.just(Unit)
        repeat(pattern.groupCount) { group ->
            chain = chain.flatMap {
                val before = if (group == 0) 0L else pattern.betweenGroupDelaysMillis[group - 1]
                Single.timer(before, TimeUnit.MILLISECONDS, scheduler).flatMap {
                    client.write(prepValue).flatMap {
                        Single.timer(pattern.restoreDelayMillis, TimeUnit.MILLISECONDS, scheduler)
                            .flatMap { restoreWrite(original) }
                    }.onErrorResumeNext { error ->
                        if (error is HapticsRestoreException) Single.error(error)
                        else restoreWrite(original).flatMap { Single.error<Unit>(error) }
                    }
                }
            }
        }
        chain.flatMap { verify(original) }.map {
            verified()
            AirHapticExecutionResult(original, original, pattern.groupCount)
        }
    }

    fun executeRinging(
        pattern: AirHapticPattern,
        shouldContinue: () -> Boolean,
        maxDurationMillis: Long = 60_000L
    ): Single<AirHapticExecutionResult> = Single.defer {
        require(maxDurationMillis > 0) { "Call duration must be bounded" }
        val started = scheduler.now(TimeUnit.MILLISECONDS)
        fun allowed() = shouldContinue() && (scheduler.now(TimeUnit.MILLISECONDS) - started < maxDurationMillis)

        fun waitOrStop(delay: Long): Single<Long> = Single.timer(delay, TimeUnit.MILLISECONDS, scheduler)
            .ambWith(io.reactivex.Observable.interval(0, 50, TimeUnit.MILLISECONDS, scheduler)
                .filter { !allowed() }.firstOrError())

        client.read().flatMap { original ->
            require(original in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) {
                "Unsupported starting haptics intensity"
            }
            if (!allowed()) return@flatMap Single.just(AirHapticExecutionResult(original, original, 0))
            checkpoint(original)

            val prepValue = if (original == AirDirectSettingsProtocol.HAPTICS_OFF) {
                AirDirectSettingsProtocol.HAPTICS_HIGH
            } else {
                AirDirectSettingsProtocol.HAPTICS_OFF
            }

            fun pulse(): Single<Unit> = client.write(prepValue).flatMap {
                waitOrStop(pattern.restoreDelayMillis).flatMap { restoreWrite(original) }
            }.onErrorResumeNext { error ->
                if (error is HapticsRestoreException) Single.error(error)
                else restoreWrite(original).flatMap { Single.error<Unit>(error) }
            }

            fun next(count: Int): Single<Int> = Single.defer {
                val isAllowed = allowed()
                Timber.d("Air executeRinging pulse: count=%d allowed=%b", count, isAllowed)
                if (!isAllowed) Single.just(count)
                else pulse().flatMap {
                    val gap = if (pattern == AirHapticPattern.CONTINUOUS) 800L else 1_200L
                    waitOrStop(gap).flatMap { next(count + 1) }
                }
            }

            next(0).flatMap { count ->
                verify(original).map {
                    verified()
                    AirHapticExecutionResult(original, original, count)
                }
            }
        }
    }

    fun recover(original: Int): Single<Unit> {
        require(original in AirDirectSettingsProtocol.HAPTICS_OFF..AirDirectSettingsProtocol.HAPTICS_HIGH) {
            "Invalid recovery intensity: $original"
        }
        return restoreWrite(original).flatMap { verify(original) }.doOnSuccess { verified() }
    }

    private fun restoreWrite(original: Int): Single<Unit> = client.write(original).retry(2)
        .onErrorResumeNext { Single.error(HapticsRestoreException("Band vibration setting could not be restored.", it)) }

    private fun verify(original: Int): Single<Unit> = client.read().flatMap { final ->
        if (final == original) Single.just(Unit)
        else Single.error(HapticsRestoreException("Band vibration setting was not restored (expected $original but got $final)."))
    }.onErrorResumeNext {
        Single.error(HapticsRestoreException("Unable to verify the restored band vibration setting.", it))
    }
}
