package com.example.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val TIME_AND_DATE_ACTIONS =
  setOf(
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
  )

@Composable
fun ObserveTodayTimeAndLifecycle(
  onStarted: () -> Unit,
  onResumed: () -> Unit,
  onStopped: () -> Unit,
  onSystemTimeChanged: () -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val appContext = LocalContext.current.applicationContext

  val currentOnStarted by rememberUpdatedState(onStarted)
  val currentOnResumed by rememberUpdatedState(onResumed)
  val currentOnStopped by rememberUpdatedState(onStopped)
  val currentOnSystemTimeChanged by rememberUpdatedState(onSystemTimeChanged)

  DisposableEffect(lifecycleOwner, appContext) {
    var isReceiverRegistered = false
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if (intent?.action in TIME_AND_DATE_ACTIONS) {
            currentOnSystemTimeChanged()
          }
        }
      }
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
      }

    fun registerReceiverIfNeeded() {
      if (!isReceiverRegistered) {
        ContextCompat.registerReceiver(
          appContext,
          receiver,
          filter,
          ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isReceiverRegistered = true
      }
    }

    fun unregisterReceiverIfNeeded() {
      if (isReceiverRegistered) {
        runCatching { appContext.unregisterReceiver(receiver) }
        isReceiverRegistered = false
      }
    }

    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_START -> {
            registerReceiverIfNeeded()
            currentOnStarted()
          }
          Lifecycle.Event.ON_RESUME -> {
            currentOnResumed()
          }
          Lifecycle.Event.ON_STOP -> {
            unregisterReceiverIfNeeded()
            currentOnStopped()
          }
          Lifecycle.Event.ON_DESTROY -> {
            unregisterReceiverIfNeeded()
          }
          else -> Unit
        }
      }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      unregisterReceiverIfNeeded()
      currentOnStopped()
    }
  }
}
