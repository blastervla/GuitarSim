package com.example.guitarsim.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.*

fun <T> LifecycleOwner.load(loader: () -> T): Deferred<T> {
    val deferred = GlobalScope.async(context = Dispatchers.Default,
        start = CoroutineStart.LAZY) {
        loader()
    }

    lifecycle.addObserver(CoroutineLifecycleListener(deferred))
    return deferred
}

infix fun <T> Deferred<T>.then(block: (T) -> Unit): Job {
    return GlobalScope.launch(Dispatchers.Main) {
        block(this@then.await())
    }
}

/**
 * Created by blastervla on 1/22/18.
 */
class CoroutineLifecycleListener(val deferred: Deferred<*>) : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun cancelCoroutine() {
        if (!deferred.isCancelled) {
            deferred.cancel()
        }
    }
}
