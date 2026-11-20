package com.mathapplocker.foss.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathapplocker.foss.R
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.data.db.AppDatabase
import com.mathapplocker.foss.data.db.CustomQuestion
import com.mathapplocker.foss.data.db.QuestionSet
import com.mathapplocker.foss.data.db.QuestionSetSummary
import com.mathapplocker.foss.math.ExpressionEvaluator
import com.mathapplocker.foss.math.ExpressionResult
import com.mathapplocker.foss.ui.common.BackTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QuestionsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val sets by remember { database.questionSets().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by prefs.settings.collectAsStateWithLifecycle()

    var openSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddSet by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<QuestionSetSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<QuestionSetSummary?>(null) }

    val openSet = sets.firstOrNull { it.id == openSetId }

    if (openSet != null) {
        SetDetailScreen(
            set = openSet,
            database = database,
            onBack = { openSetId = null },
        )
    } else {
        SetsScreen(
            sets = sets,
            selectedTables = settings.timesTables,
            intervalMinutes = settings.intervalMinutes,
            numQuestions = settings.numQuestions,
            onSaveTiming = { interval, tasks ->
                prefs.setIntervalMinutes(interval)
                prefs.setNumQuestions(tasks)
            },
            onToggleTable = prefs::toggleTimesTable,
            onBack = onBack,
            onOpenSet = { openSetId = it },
            onToggleSet = { set, enabled ->
                scope.launch(Dispatchers.IO) {
                    database.questionSets().setEnabled(set.id, enabled)
                }
            },
            onRenameSet = { renameTarget = it },
            onDeleteSet = { deleteTarget = it },
            onAddSet = { showAddSet = true },
        )
    }

    if (showAddSet) {
        SetNameDialog(
            title = stringResource(R.string.add_set_title),
            onDismiss = { showAddSet = false },
            onSave = { name ->
                showAddSet = false
                scope.launch(Dispatchers.IO) {
                    val id = database.questionSets().insert(QuestionSet(name = name))
                    withContext(Dispatchers.Main) { openSetId = id }
                }
            },
        )
    }

    renameTarget?.let { set ->
        SetNameDialog(
            title = stringResource(R.string.rename_set_title),
            initialName = set.name,
            onDismiss = { renameTarget = null },
            onSave = { name ->
                renameTarget = null
                scope.launch(Dispatchers.IO) {
                    database.questionSets().rename(set.id, name)
                }
            },
        )
    }

    deleteTarget?.let { set ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_set_title)) },
            text = { Text(stringResource(R.string.confirm_delete_set_msg, set.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch(Dispatchers.IO) {
                            database.questionSets().deleteWithQuestions(
                                QuestionSet(id = set.id, name = set.name),
                            )
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SetsScreen(
    sets: List<QuestionSetSummary>,
    selectedTables: Set<Int>,
    intervalMinutes: Int,
    numQuestions: Int,
    onSaveTiming: (Int, Int) -> Unit,
    onToggleTable: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onToggleSet: (QuestionSetSummary, Boolean) -> Unit,
    onRenameSet: (QuestionSetSummary) -> Unit,
    onDeleteSet: (QuestionSetSummary) -> Unit,
    onAddSet: () -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.manage_questions), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TimingCard(
                    intervalMinutes = intervalMinutes,
                    numQuestions = numQuestions,
                    onSave = onSaveTiming,
                )
            }

            item {
                MultiplicationTablesCard(
                    selectedTables = selectedTables,
                    onToggleTable = onToggleTable,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.custom_sets_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onAddSet) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_set),
                        )
                    }
                }
            }

            if (sets.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_sets),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            items(sets, key = { it.id }) { set ->
                SetRow(
                    set = set,
                    onOpen = { onOpenSet(set.id) },
                    onToggle = { onToggleSet(set, it) },
                    onRename = { onRenameSet(set) },
                    onDelete = { onDeleteSet(set) },
                )
            }
        }
    }
}

@Composable
private fun TimingCard(
    intervalMinutes: Int,
    numQuestions: Int,
    onSave: (Int, Int) -> Unit,
) {
    var intervalText by rememberSaveable(intervalMinutes) {
        mutableStateOf(intervalMinutes.toString())
    }
    var tasksText by rememberSaveable(numQuestions) {
        mutableStateOf(numQuestions.toString())
    }
    var intervalError by rememberSaveable { mutableStateOf(false) }
    var tasksError by rememberSaveable { mutableStateOf(false) }

    // Timing fields save immediately when both values are valid.
    fun apply(interval: Int?, tasks: Int?) {
        intervalError = interval == null ||
            interval !in Prefs.MIN_INTERVAL..Prefs.MAX_INTERVAL
        tasksError = tasks == null ||
            tasks !in Prefs.MIN_QUESTIONS..Prefs.MAX_QUESTIONS
        if (!intervalError && !tasksError && interval != null && tasks != null) {
            onSave(interval, tasks)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.challenge_timing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = intervalText,
                onValueChange = {
                    intervalText = it.filter(Char::isDigit).take(3)
                    apply(intervalText.toIntOrNull(), tasksText.toIntOrNull())
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tasksText,
                onValueChange = {
                    tasksText = it.filter(Char::isDigit).take(2)
                    apply(intervalText.toIntOrNull(), tasksText.toIntOrNull())
                },
                label = { Text(stringResource(R.string.num_questions)) },
                supportingText = {
                    if (tasksError) Text(stringResource(R.string.invalid_question_count))
                },
                isError = tasksError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.advanced_tasks_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun MultiplicationTablesCard(
    selectedTables: Set<Int>,
    onToggleTable: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.times_tables),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.times_tables_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            for (row in 0 until 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (column in 1..3) {
                        val table = row * 3 + column
                        TableButton(
                            table = table,
                            selected = table in selectedTables,
                            onClick = { onToggleTable(table) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (row < 2) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun TableButton(
    table: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.times_table_n, table),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SetRow(
    set: QuestionSetSummary,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = set.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(6.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onOpen)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = set.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.question_count,
                        set.questionCount,
                        set.questionCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_set),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_set),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetDetailScreen(
    set: QuestionSetSummary,
    database: AppDatabase,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val questions by remember(set.id) { database.customQuestions().observeBySet(set.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var showAdd by rememberSaveable(set.id) { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<CustomQuestion?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomQuestion?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = { BackTopBar(title = set.name, onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_question)) },
            )
        },
    ) { padding ->
        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_questions_in_set),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(questions, key = { it.id }) { question ->
                    QuestionRow(
                        question = question,
                        onToggleEnabled = { enabled ->
                            scope.launch(Dispatchers.IO) {
                                database.customQuestions().setEnabled(question.id, enabled)
                            }
                        },
                        onEdit = { editTarget = question },
                        onDelete = { deleteTarget = question },
                    )
                }
            }
        }
    }

    if (showAdd) {
        QuestionDialog(
            title = stringResource(R.string.add_question_title),
            onDismiss = { showAdd = false },
            onSave = { text, numberAnswer, textAnswer ->
                showAdd = false
                scope.launch(Dispatchers.IO) {
                    database.customQuestions().insert(
                        CustomQuestion(
                            setId = set.id,
                            text = text,
                            answer = numberAnswer ?: 0,
                            textAnswer = textAnswer,
                        ),
                    )
                }
            },
        )
    }

    editTarget?.let { question ->
        QuestionDialog(
            title = stringResource(R.string.edit_question_title),
            initial = question,
            onDismiss = { editTarget = null },
            onSave = { text, numberAnswer, textAnswer ->
                editTarget = null
                scope.launch(Dispatchers.IO) {
                    database.customQuestions().update(
                        question.copy(
                            text = text,
                            answer = numberAnswer ?: 0,
                            textAnswer = textAnswer,
                        ),
                    )
                }
            },
        )
    }

    deleteTarget?.let { question ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_question_title)) },
            text = { Text(stringResource(R.string.confirm_delete_question_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch(Dispatchers.IO) {
                            database.customQuestions().delete(question)
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun QuestionRow(
    question: CustomQuestion,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = question.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.padding(6.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = question.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (question.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "= ${question.textAnswer ?: question.answer}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (question.textAnswer == null) {
                        FontFamily.Monospace
                    } else {
                        FontFamily.Default
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_question),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_question),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(60)
                        showError = false
                    },
                    label = { Text(stringResource(R.string.hint_set_name)) },
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.set_name_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        showError = true
                    } else {
                        onSave(trimmed)
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionDialog(
    title: String,
    initial: CustomQuestion? = null,
    onDismiss: () -> Unit,
    onSave: (question: String, numberAnswer: Int?, textAnswer: String?) -> Unit,
) {
    var kind by rememberSaveable {
        mutableStateOf(if (initial?.textAnswer != null) AnswerKind.TEXT else AnswerKind.NUMBER)
    }
    var questionText by rememberSaveable { mutableStateOf(initial?.text ?: "") }
    var numberText by rememberSaveable {
        mutableStateOf(
            if (initial != null && initial.textAnswer == null) initial.answer.toString() else "",
        )
    }
    var textAnswerText by rememberSaveable { mutableStateOf(initial?.textAnswer ?: "") }
    var error by rememberSaveable { mutableStateOf<QuestionError?>(null) }
    var expectedAnswer by rememberSaveable { mutableStateOf<Int?>(null) }

    val computed = if (kind == AnswerKind.NUMBER) {
        (ExpressionEvaluator.evaluate(questionText) as? ExpressionResult.Value)?.value
    } else {
        null
    }

    val errorMessage = when (error) {
        QuestionError.SYNTAX -> stringResource(R.string.invalid_math_question)
        QuestionError.DIVISION_BY_ZERO -> stringResource(R.string.division_by_zero)
        QuestionError.NOT_WHOLE -> stringResource(R.string.division_not_whole)
        QuestionError.TOO_LARGE -> stringResource(R.string.expression_too_large)
        QuestionError.ANSWER_MISMATCH -> stringResource(
            R.string.answer_mismatch,
            expectedAnswer ?: 0,
        )
        QuestionError.QUESTION_REQUIRED -> stringResource(R.string.question_required)
        QuestionError.TEXT_ANSWER_REQUIRED -> stringResource(R.string.text_answer_required)
        null -> null
    }

    fun save() {
        if (kind == AnswerKind.NUMBER) {
            when (val result = ExpressionEvaluator.evaluate(questionText)) {
                is ExpressionResult.Value -> {
                    val typed = numberText.toIntOrNull()
                    if (typed != null && typed != result.value) {
                        expectedAnswer = result.value
                        error = QuestionError.ANSWER_MISMATCH
                    } else {
                        onSave(
                            ExpressionEvaluator.normalize(questionText),
                            result.value,
                            null,
                        )
                    }
                }

                ExpressionResult.SyntaxError -> error = QuestionError.SYNTAX
                ExpressionResult.DivisionByZero -> error = QuestionError.DIVISION_BY_ZERO
                ExpressionResult.NotAnInteger -> error = QuestionError.NOT_WHOLE
                ExpressionResult.Overflow -> error = QuestionError.TOO_LARGE
            }
        } else {
            val question = questionText.trim()
            val answer = textAnswerText.trim()
            when {
                question.isEmpty() -> error = QuestionError.QUESTION_REQUIRED
                answer.isEmpty() -> error = QuestionError.TEXT_ANSWER_REQUIRED
                else -> onSave(question, null, answer)
            }
        }
    }

    val questionField: @Composable (Modifier, Boolean) -> Unit = { fieldModifier, compact ->
        val hint = stringResource(
            if (kind == AnswerKind.NUMBER) {
                R.string.hint_question
            } else {
                R.string.hint_text_question
            },
        )
        OutlinedTextField(
            value = questionText,
            onValueChange = {
                questionText = if (kind == AnswerKind.NUMBER) {
                    ExpressionEvaluator.sanitize(it).take(60)
                } else {
                    it.take(200)
                }
                error = null
            },
            label = if (compact) null else { { Text(hint) } },
            placeholder = if (compact) { { Text(hint) } } else null,
            singleLine = kind == AnswerKind.NUMBER,
            maxLines = if (kind == AnswerKind.NUMBER) 1 else 3,
            isError = error != null && error != QuestionError.ANSWER_MISMATCH &&
                error != QuestionError.TEXT_ANSWER_REQUIRED,
            supportingText = if (kind == AnswerKind.NUMBER) {
                computed?.let { value ->
                    { Text(stringResource(R.string.question_result, value)) }
                }
            } else {
                null
            },
            modifier = fieldModifier.fillMaxWidth(),
        )
    }

    val answerField: @Composable (Modifier, Boolean) -> Unit = { fieldModifier, compact ->
        if (kind == AnswerKind.NUMBER) {
            val hint = stringResource(R.string.hint_answer)
            OutlinedTextField(
                value = numberText,
                onValueChange = {
                    numberText = it.filterIndexed { index, c ->
                        c.isDigit() || (index == 0 && c == '-')
                    }.take(7)
                    error = null
                },
                label = if (compact) null else { { Text(hint) } },
                placeholder = if (compact) { { Text(hint) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error == QuestionError.ANSWER_MISMATCH,
                supportingText = {
                    Text(stringResource(R.string.answer_optional_hint))
                },
                modifier = fieldModifier.fillMaxWidth(),
            )
        } else {
            val hint = stringResource(R.string.hint_text_answer)
            OutlinedTextField(
                value = textAnswerText,
                onValueChange = {
                    textAnswerText = it.take(120)
                    error = null
                },
                label = if (compact) null else { { Text(hint) } },
                placeholder = if (compact) { { Text(hint) } } else null,
                maxLines = 3,
                isError = error == QuestionError.TEXT_ANSWER_REQUIRED,
                supportingText = {
                    Text(stringResource(R.string.text_answer_helper))
                },
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // In landscape the dialog is limited by the screen height (and the
            // keyboard), so the form scrolls instead of clipping the lower field.
            val landscape = maxWidth > maxHeight
            Surface(
                modifier = Modifier
                    .widthIn(max = if (landscape) 720.dp else 560.dp)
                    .fillMaxWidth()
                    .then(
                        if (landscape) {
                            Modifier.fillMaxHeight()
                        } else {
                            Modifier.heightIn(max = maxHeight)
                        },
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 24.dp,
                        vertical = if (landscape) 14.dp else 20.dp,
                    ),
                ) {
                    if (!landscape) {
                        Text(text = title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = landscape)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (landscape) {
                            Text(text = title, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(2.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = kind == AnswerKind.NUMBER,
                                onClick = {
                                    kind = AnswerKind.NUMBER
                                    error = null
                                },
                                label = { Text(stringResource(R.string.answer_type_number)) },
                            )
                            FilterChip(
                                selected = kind == AnswerKind.TEXT,
                                onClick = {
                                    kind = AnswerKind.TEXT
                                    error = null
                                },
                                label = { Text(stringResource(R.string.answer_type_text)) },
                            )
                        }

                        if (landscape) {
                            // Side-by-side fields keep the whole form visible in
                            // the short landscape height.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                questionField(Modifier.weight(1f), true)
                                answerField(Modifier.weight(1f), true)
                            }
                        } else {
                            questionField(Modifier, false)
                            answerField(Modifier, false)
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { save() }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

private enum class AnswerKind { NUMBER, TEXT }

private enum class QuestionError {
    SYNTAX,
    DIVISION_BY_ZERO,
    NOT_WHOLE,
    TOO_LARGE,
    ANSWER_MISMATCH,
    QUESTION_REQUIRED,
    TEXT_ANSWER_REQUIRED,
}
