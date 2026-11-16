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
        val safeIntensity = if (intensity == AirDirectSettingsProtocol.HAPTICS_LOW) {
            AirDirectSettingsProtocol.HAPTICS_LOW
        } else {
            AirDirectSettingsProtocol.HAPTICS_HIGH
        }
        AirHapticExecutionResult(safeIntensity, safeIntensity, 0)
    }

    fun execute(pattern: AirHapticPattern): Single<AirHapticExecutionResult> = client.read().flatMap { original ->
        val safeOriginal = if (original == AirDirectSettingsProtocol.HAPTICS_LOW) {
            AirDirectSettingsProtocol.HAPTICS_LOW
        } else {
            AirDirectSettingsProtocol.HAPTICS_HIGH
        }
        checkpoint(safeOriginal)

        // 1 toggle per pulse between supported intensities HIGH (3) and LOW (2).
        // On Fitbit Air, writing a supported setting change triggers exactly 1 physical confirmation buzz!
        var currentIntensity = safeOriginal
        fun nextIntensity(current: Int): Int = if (current == AirDirectSettingsProtocol.HAPTICS_HIGH) {
            AirDirectSettingsProtocol.HAPTICS_LOW
        } else {
            AirDirectSettingsProtocol.HAPTICS_HIGH
        }

        var chain = Single.just(Unit)
        repeat(pattern.groupCount) { group ->
            chain = chain.flatMap {
                val delay = if (group == 0) 0L else pattern.betweenGroupDelaysMillis[group - 1]
                Single.timer(delay, TimeUnit.MILLISECONDS, scheduler).flatMap {
                    val target = nextIntensity(currentIntensity)
                    currentIntensity = target
                    client.write(target)
                }
            }
        }
        chain.map {
            verified()
            AirHapticExecutionResult(safeOriginal, currentIntensity, pattern.groupCount)
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
            val safeOriginal = if (original == AirDirectSettingsProtocol.HAPTICS_LOW) {
                AirDirectSettingsProtocol.HAPTICS_LOW
            } else {
                AirDirectSettingsProtocol.HAPTICS_HIGH
            }
            if (!allowed()) return@flatMap Single.just(AirHapticExecutionResult(safeOriginal, safeOriginal, 0))
            checkpoint(safeOriginal)

            var currentIntensity = safeOriginal
            fun nextIntensity(): Int {
                currentIntensity = if (currentIntensity == AirDirectSettingsProtocol.HAPTICS_HIGH) {
                    AirDirectSettingsProtocol.HAPTICS_LOW
                } else {
                    AirDirectSettingsProtocol.HAPTICS_HIGH
                }
                return currentIntensity
            }

            fun pulse(): Single<Unit> = client.write(nextIntensity())

            fun next(count: Int): Single<Int> = Single.defer {
                val isAllowed = allowed()
                Timber.d("Air executeRinging pulse: count=%d allowed=%b", count, isAllowed)
                if (!isAllowed) Single.just(count)
                else pulse().flatMap {
                    val gap = if (pattern == AirHapticPattern.CONTINUOUS) 800L else 1_200L
                    waitOrStop(gap).flatMap { next(count + 1) }
                }
            }

            next(0).map { count ->
                verified()
                AirHapticExecutionResult(safeOriginal, currentIntensity, count)
            }
        }
    }

    fun recover(original: Int): Single<Unit> {
        val safeOriginal = if (original == AirDirectSettingsProtocol.HAPTICS_LOW) {
            AirDirectSettingsProtocol.HAPTICS_LOW
        } else {
            AirDirectSettingsProtocol.HAPTICS_HIGH
        }
        return client.write(safeOriginal)
            .onErrorResumeNext { Single.just(Unit) }
            .doOnSuccess { verified() }
    }
}
