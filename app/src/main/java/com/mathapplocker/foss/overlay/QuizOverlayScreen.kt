package com.mathapplocker.foss.overlay

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mathapplocker.foss.R
import com.mathapplocker.foss.math.QuizQuestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuizOverlayScreen(
    questions: List<QuizQuestion>,
    allowNegative: Boolean,
    insets: OverlayInsets,
    onSolved: () -> Unit,
    onExitHome: () -> Unit,
) {
    if (questions.isEmpty()) {
        LaunchedEffect(Unit) { onSolved() }
        return
    }

    var index by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val safeIndex = index.coerceIn(0, questions.lastIndex)
    val question = questions[safeIndex]
    val isLast = safeIndex == questions.lastIndex

    LaunchedEffect(safeIndex, feedback) {
        if (feedback == Feedback.CORRECT) {
            delay(800)
            feedback = null
        }
    }

    fun shakeNow() {
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 360
                    0f at 0
                    -1f at 55
                    1f at 110
                    -0.65f at 170
                    0.65f at 230
                    0f at 300
                },
            )
            shake.snapTo(0f)
        }
    }

    fun submit() {
        val value = input.toIntOrNull()
        if (value == null) {
            feedback = Feedback.INCORRECT
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            shakeNow()
            return
        }
        if (value == question.answer) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (isLast) {
                onSolved()
            } else {
                feedback = Feedback.CORRECT
                index = safeIndex + 1
                input = ""
            }
        } else {
            feedback = Feedback.INCORRECT
            input = ""
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            shakeNow()
        }
    }

    val onDigit: (String) -> Unit = { digit ->
        if (input.length < 5) input += digit
    }
    val onMinus: () -> Unit = {
        input = when {
            input.startsWith("-") -> input.removePrefix("-")
            input.isEmpty() -> "-"
            else -> "-$input"
        }
    }
    val onBackspace: () -> Unit = {
        input = if (input.length <= 1) "" else input.dropLast(1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.left + 20.dp,
                    end = insets.right + 20.dp,
                    top = insets.top + 10.dp,
                    bottom = insets.bottom + 10.dp,
                ),
        ) {
            val landscape = maxWidth > maxHeight

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onExitHome) {
                        Icon(Icons.Filled.Home, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.overlay_exit_home))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.overlay_progress, safeIndex + 1, questions.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (landscape) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Card(
                            modifier = Modifier.weight(1.15f),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                QuizHeader(
                                    progress = (safeIndex + 1f) / questions.size,
                                    questionText = question.text,
                                    shakeOffset = shake.value,
                                )
                                Spacer(Modifier.height(12.dp))
                                AnswerField(
                                    input = input,
                                    shakeOffset = shake.value,
                                    onClear = { input = "" },
                                )
                                Spacer(Modifier.height(6.dp))
                                FeedbackText(feedback)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 18.dp, vertical = 18.dp)
                                    .widthIn(max = 440.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Keypad(
                                    allowNegative = allowNegative,
                                    onDigit = onDigit,
                                    onMinus = onMinus,
                                    onBackspace = onBackspace,
                                )
                                Spacer(Modifier.height(14.dp))
                                SubmitButton(onClick = { submit() })
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                QuizHeader(
                                    progress = (safeIndex + 1f) / questions.size,
                                    questionText = question.text,
                                    shakeOffset = shake.value,
                                )
                                Spacer(Modifier.height(14.dp))
                                AnswerField(
                                    input = input,
                                    shakeOffset = shake.value,
                                    onClear = { input = "" },
                                )
                                Spacer(Modifier.height(6.dp))
                                FeedbackText(feedback)
                                Spacer(Modifier.height(6.dp))
                                Keypad(
                                    allowNegative = allowNegative,
                                    onDigit = onDigit,
                                    onMinus = onMinus,
                                    onBackspace = onBackspace,
                                )
                                Spacer(Modifier.height(14.dp))
                                SubmitButton(onClick = { submit() })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Text(
                    text = stringResource(R.string.overlay_media_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuizHeader(
    progress: Float,
    questionText: String,
    shakeOffset: Float,
) {
    Text(
        text = stringResource(R.string.overlay_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(R.string.overlay_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = questionText,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.offset(x = (shakeOffset * 12).dp),
    )
}

@Composable
private fun AnswerField(
    input: String,
    shakeOffset: Float,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (shakeOffset * 12).dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = input.ifEmpty { stringResource(R.string.overlay_answer_hint) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = if (input.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.overlay_clear))
            }
        }
    }
}

@Composable
private fun FeedbackText(feedback: Feedback?) {
    val color = when (feedback) {
        Feedback.CORRECT -> MaterialTheme.colorScheme.primary
        Feedback.INCORRECT -> MaterialTheme.colorScheme.error
        null -> Color.Transparent
    }
    Text(
        text = when (feedback) {
            Feedback.CORRECT -> stringResource(R.string.overlay_correct)
            Feedback.INCORRECT -> stringResource(R.string.overlay_incorrect)
            null -> " "
        },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier.height(24.dp),
    )
}

@Composable
private fun SubmitButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = stringResource(R.string.overlay_submit),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Keypad(
    allowNegative: Boolean,
    onDigit: (String) -> Unit,
    onMinus: () -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    KeypadKey(
                        label = digit,
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowNegative) {
                KeypadKey(
                    label = "−",
                    modifier = Modifier.weight(1f),
                    onClick = onMinus,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            KeypadKey(
                label = "0",
                modifier = Modifier.weight(1f),
                onClick = { onDigit("0") },
            )
            val backspaceLabel = stringResource(R.string.overlay_backspace)
            KeypadKey(
                label = "⌫",
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = backspaceLabel },
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private enum class Feedback { CORRECT, INCORRECT }
