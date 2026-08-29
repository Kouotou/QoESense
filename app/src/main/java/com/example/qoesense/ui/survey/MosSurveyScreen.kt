package com.example.qoesense.ui.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.qoesense.R
import kotlinx.coroutines.delay

/**
 * Auto-dismisses if the participant ignores it - a survey prompt must never linger, both to
 * avoid annoyance and because a stale, unanswered popup would keep whatever app they were
 * using out of focus. No penalty for missing one - it just tries again at the next trigger.
 */
private const val AUTO_DISMISS_MS = 8_000L

@Composable
fun MosSurveyScreen(onScoreSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(AUTO_DISMISS_MS)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.mos_survey_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (score in 1..5) {
                        OutlinedButton(
                            onClick = { onScoreSelected(score) },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(score.toString())
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.mos_survey_caption),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
