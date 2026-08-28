package com.example.qoesense.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.qoesense.data.CollectionStatePreferences
import com.example.qoesense.service.upload.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val wasEnabled = CollectionStatePreferences(context).isCollectionEnabled.first()
                if (wasEnabled) {
                    TelemetryForegroundService.start(context.applicationContext)
                    UploadScheduler.schedule(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
