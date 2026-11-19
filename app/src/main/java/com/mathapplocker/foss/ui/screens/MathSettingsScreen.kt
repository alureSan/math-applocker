package com.mathapplocker.foss.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mathapplocker.foss.R
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.data.Settings
import com.mathapplocker.foss.math.Difficulty
import com.mathapplocker.foss.math.Operation
import com.mathapplocker.foss.ui.common.BackTopBar
import com.mathapplocker.foss.ui.common.SectionLabel

@Composable
fun MathSettingsScreen(
    settings: Settings,
    prefs: Prefs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var intervalText by rememberSaveable(settings.intervalMinutes) {
        mutableStateOf(settings.intervalMinutes.toString())
    }
    var countText by rememberSaveable(settings.numQuestions) {
        mutableStateOf(settings.numQuestions.toString())
    }
    var difficulty by remember(settings.difficulty) { mutableStateOf(settings.difficulty) }
    var categories by remember(settings.categories) { mutableStateOf(settings.categories) }
    var intervalError by remember { mutableStateOf(false) }
    var countError by remember { mutableStateOf(false) }
    var categoriesError by remember { mutableStateOf(false) }

    fun save() {
        val interval = intervalText.toIntOrNull()
        val count = countText.toIntOrNull()
        intervalError = interval == null || interval !in Prefs.MIN_INTERVAL..Prefs.MAX_INTERVAL
        countError = count == null || count !in Prefs.MIN_QUESTIONS..Prefs.MAX_QUESTIONS
        categoriesError = categories.isEmpty()
        if (intervalError || countError || categoriesError) return

        prefs.setIntervalMinutes(interval!!)
        prefs.setNumQuestions(count!!)
        prefs.setDifficulty(difficulty)
        prefs.setCategories(categories)
        Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        onBack()
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
                    intervalError = false
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
                    countError = false
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
                                .clickable { difficulty = option }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = difficulty == option,
                                onClick = { difficulty = option },
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
                                    categories = if (operation in categories) {
                                        categories - operation
                                    } else {
                                        categories + operation
                                    }
                                    categoriesError = false
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = operation in categories,
                                onCheckedChange = { checked ->
                                    categories = if (checked) {
                                        categories + operation
                                    } else {
                                        categories - operation
                                    }
                                    categoriesError = false
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
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
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
