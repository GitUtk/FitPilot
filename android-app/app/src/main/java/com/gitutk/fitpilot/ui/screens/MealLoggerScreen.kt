package com.gitutk.fitpilot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitutk.fitpilot.ui.FitPilotViewModel
import com.gitutk.fitpilot.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealLoggerScreen(viewModel: FitPilotViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val chatMessages by viewModel.chatMessages.collectAsState()

    var showLogForm by remember { mutableStateOf(false) }
    var logDesc by remember { mutableStateOf("") }
    var logCalories by remember { mutableStateOf("") }
    var logProtein by remember { mutableStateOf("") }
    var logCarbs by remember { mutableStateOf("") }
    var logFat by remember { mutableStateOf("") }
    
    var localError by remember { mutableStateOf<String?>(null) }
    var localSuccess by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }

    // Scroll to bottom when messages list size changes
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Slate200)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertizontally
            ) {
                Spacer(modifier = Modifier.width(36.dp))
                Text(
                    text = "Food Logging AI",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                IconButton(
                    onClick = { viewModel.clearChat() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Slate200, CircleShape)
                ) {
                    Text("🗑", fontSize = 16.sp, color = Slate900)
                }
            }

            // Quick Log Form Accordion
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate50)
                    .border(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogForm = !showLogForm }
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertizontally
                ) {
                    Text(
                        text = if (showLogForm) "▲" else "▼",
                        fontSize = 12.sp,
                        color = Slate900,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Quick Log Meal Record",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                AnimatedVisibility(visible = showLogForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val errorToShow = localError ?: viewModel.mealError
                        if (errorToShow != null) {
                            Text(text = errorToShow, color = Red500, fontSize = 12.sp)
                        }
                        if (localSuccess) {
                            Text(text = "Meal logged successfully!", color = Emerald500, fontSize = 12.sp)
                        }

                        OutlinedTextField(
                            value = logDesc,
                            onValueChange = { logDesc = it; localError = null },
                            placeholder = { Text("Meal Description (e.g. 2 Rotis + Dal)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = logCalories,
                                onValueChange = { logCalories = it; localError = null },
                                placeholder = { Text("Calories (kcal)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            OutlinedTextField(
                                value = logProtein,
                                onValueChange = { logProtein = it; localError = null },
                                placeholder = { Text("Protein (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = logCarbs,
                                onValueChange = { logCarbs = it; localError = null },
                                placeholder = { Text("Carbs (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            OutlinedTextField(
                                value = logFat,
                                onValueChange = { logFat = it; localError = null },
                                placeholder = { Text("Fat (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val cal = logCalories.toFloatOrNull()
                                val prot = logProtein.toFloatOrNull() ?: 0f
                                val carb = logCarbs.toFloatOrNull() ?: 0f
                                val fat = logFat.toFloatOrNull() ?: 0f

                                if (logDesc.trim().isEmpty() || cal == null || cal < 0) {
                                    localError = "Please enter description and valid calories"
                                    return@Button
                                }
                                viewModel.logMeal(logDesc.trim(), cal, prot, carb, fat) {
                                    logDesc = ""
                                    logCalories = ""
                                    logProtein = ""
                                    logCarbs = ""
                                    logFat = ""
                                    localSuccess = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        localSuccess = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                        ) {
                            Text("Log to Database", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Chat Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                items(chatMessages) { message ->
                    val isUser = message.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(
                                    color = if (isUser) Slate900 else Slate50,
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomEnd = if (isUser) 2.dp else 12.dp,
                                        bottomStart = if (isUser) 12.dp else 2.dp
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isUser) Slate900 else Slate200,
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomEnd = if (isUser) 2.dp else 12.dp,
                                        bottomStart = if (isUser) 12.dp else 2.dp
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            val parsedText = parseMarkdown(message.text)
                            Text(
                                text = parsedText,
                                color = if (isUser) Color.White else Slate900,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                if (viewModel.isChatSending) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertizontally,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(size = 14.dp, color = Slate900)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Nutritionist is thinking...",
                                fontSize = 12.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Input Bar at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Slate200)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertizontally
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type what you ate today...", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.trim().isNotEmpty()) {
                                viewModel.sendChatMessage(inputText.trim())
                                inputText = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(19.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Slate200,
                        unfocusedBorderColor = Slate200,
                        containerColor = Slate100
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.trim().isNotEmpty()) {
                            viewModel.sendChatMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.trim().isNotEmpty() && !viewModel.isChatSending,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "➔",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (inputText.trim().isNotEmpty() && !viewModel.isChatSending) Slate900 else Slate500
                    )
                }
            }
        }
    }
}

/**
 * A basic markdown helper to render bold text matching **word** syntax.
 */
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 != 0) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}
