package com.example.qoesense.ui.survey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.qoesense.data.model.MosTriggerType
import com.example.qoesense.service.survey.MosSubmitWorker
import com.example.qoesense.ui.theme.QoESenseTheme

/**
 * Translucent "lightweight activity" survey prompt, per the design spec's alternative to a
 * heads-up notification - a full custom notification layout would need RemoteViews/XML,
 * which doesn't fit this Compose-only codebase. Themed transparent in the manifest so it
 * overlays rather than fully replacing whatever the participant was doing.
 */
class MosSurveyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val triggerType = MosTriggerType.valueOf(
            intent.getStringExtra(EXTRA_TRIGGER_TYPE) ?: MosTriggerType.PERIODIC.name
        )
        setContent {
            QoESenseTheme {
                MosSurveyScreen(
                    onScoreSelected = { score ->
                        MosSubmitWorker.enqueue(applicationContext, score, triggerType)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRIGGER_TYPE = "trigger_type"
    }
}
