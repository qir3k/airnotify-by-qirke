package com.qirke.airnotify.ble

import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers

object NativePeerCleanup {
    fun close(action: () -> Unit): Completable =
        Completable.fromAction { action() }.subscribeOn(Schedulers.io())
}
