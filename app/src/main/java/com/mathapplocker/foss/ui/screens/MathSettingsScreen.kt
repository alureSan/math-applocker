package com.mathapplocker.foss.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mathapplocker.foss.R
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.data.Settings
import com.mathapplocker.foss.math.Difficulty
import com.mathapplocker.foss.math.Operation
import com.mathapplocker.foss.math.QuestionGenerator
import com.mathapplocker.foss.ui.common.BackTopBar
import com.mathapplocker.foss.ui.common.SectionLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MathSettingsScreen(
    settings: Settings,
    prefs: Prefs,
    onBack: () -> Unit,
) {
    var intervalText by rememberSaveable(settings.intervalMinutes) {
        mutableStateOf(settings.intervalMinutes.toString())
    }
    var countText by rememberSaveable(settings.numQuestions) {
        mutableStateOf(settings.numQuestions.toString())
    }
    var difficulty by remember(settings.difficulty) { mutableStateOf(settings.difficulty) }
    var operandDigits by rememberSaveable(settings.operandDigits) {
        mutableIntStateOf(settings.operandDigits)
    }
    var categories by remember(settings.categories) { mutableStateOf(settings.categories) }
    var intervalError by remember { mutableStateOf(false) }
    var countError by remember { mutableStateOf(false) }
    var categoriesError by remember { mutableStateOf(false) }

    // Settings apply immediately, so leaving without pressing a Save button can
    // never leave an old digit count or difficulty behind.
    fun applyInterval(text: String) {
        val value = text.toIntOrNull()
        intervalError = value == null || value !in Prefs.MIN_INTERVAL..Prefs.MAX_INTERVAL
        if (!intervalError && value != null) prefs.setIntervalMinutes(value)
    }

    fun applyCount(text: String) {
        val value = text.toIntOrNull()
        countError = value == null || value !in Prefs.MIN_QUESTIONS..Prefs.MAX_QUESTIONS
        if (!countError && value != null) prefs.setNumQuestions(value)
    }

    fun applyDifficulty(option: Difficulty) {
        if (difficulty == option) return
        difficulty = option
        // A new age preset should never inherit a larger digit count from an
        // earlier age. Parents can still pick digits again afterwards.
        operandDigits = QuestionGenerator.AUTO_DIGITS
        prefs.setDifficulty(option)
        prefs.setOperandDigits(QuestionGenerator.AUTO_DIGITS)
    }

    fun applyDigits(digits: Int) {
        operandDigits = digits
        prefs.setOperandDigits(digits)
    }

    fun applyCategories(next: Set<Operation>) {
        categories = next
        categoriesError = next.isEmpty()
        if (next.isNotEmpty()) prefs.setCategories(next)
    }

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.configure_math_rules), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = intervalText,
                onValueChange = {
                    intervalText = it.filter(Char::isDigit).take(3)
                    applyInterval(intervalText)
                },
                label = { Text(stringResource(R.string.interval_minutes)) },
                supportingText = {
                    Text(
                        if (intervalError) {
                            stringResource(R.string.invalid_interval)
                        } else {
                            stringResource(R.string.interval_helper)
                        },
                    )
                },
                isError = intervalError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = countText,
                onValueChange = {
                    countText = it.filter(Char::isDigit).take(2)
                    applyCount(countText)
                },
                label = { Text(stringResource(R.string.num_questions)) },
                supportingText = {
                    if (countError) Text(stringResource(R.string.invalid_question_count))
                },
                isError = countError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.difficulty))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Difficulty.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyDifficulty(option) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = difficulty == option,
                                onClick = { applyDifficulty(option) },
                            )
                            Text(
                                text = difficultyLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.operand_digits))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = operandDigits == QuestionGenerator.AUTO_DIGITS,
                    onClick = { applyDigits(QuestionGenerator.AUTO_DIGITS) },
                    label = { Text(stringResource(R.string.digits_auto)) },
                )
                (1..Prefs.MAX_OPERAND_DIGITS).forEach { digits ->
                    FilterChip(
                        selected = operandDigits == digits,
                        onClick = { applyDigits(digits) },
                        label = { Text(digits.toString()) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.operand_digits_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            SectionLabel(stringResource(R.string.categories))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Operation.entries.forEach { operation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    applyCategories(
                                        if (operation in categories) {
                                            categories - operation
                                        } else {
                                            categories + operation
                                        },
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = operation in categories,
                                onCheckedChange = { checked ->
                                    applyCategories(
                                        if (checked) {
                                            categories + operation
                                        } else {
                                            categories - operation
                                        },
                                    )
                                },
                            )
                            Text(
                                text = operationLabel(operation),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
            if (categoriesError) {
                Text(
                    text = stringResource(R.string.select_at_least_one_category),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_auto_save),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.AGE_3_4 -> stringResource(R.string.radio_3_4)
    Difficulty.AGE_5_7 -> stringResource(R.string.radio_5_7)
    Difficulty.AGE_8_10 -> stringResource(R.string.radio_8_10)
    Difficulty.AGE_11_13 -> stringResource(R.string.radio_11_13)
    Difficulty.AGE_14_17 -> stringResource(R.string.radio_14_17)
    Difficulty.ADULT -> stringResource(R.string.radio_adult)
}

@Composable
private fun operationLabel(operation: Operation): String = when (operation) {
    Operation.ADD -> stringResource(R.string.cat_add)
    Operation.SUB -> stringResource(R.string.cat_sub)
    Operation.MUL -> stringResource(R.string.cat_mul)
    Operation.DIV -> stringResource(R.string.cat_div)
}
