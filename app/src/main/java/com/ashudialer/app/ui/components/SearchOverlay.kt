/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.ashudialer.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------------------------------------------------
// Recent searches
// ------------------------------------------------------------------------------------------------------------

/**
 * The searches a person made before, newest first (at most [MAX]). Recents and Contacts each keep their own list.
 * It lives in a tiny private preferences file on the phone, so nothing here ever leaves the device.
 */
class SearchHistoryState internal constructor(
    private val prefs: android.content.SharedPreferences,
    private val key: String
) {
    var items: List<String> by mutableStateOf(load())
        private set

    private fun load(): List<String> = try {
        val arr = org.json.JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<String>) {
        items = list
        prefs.edit().putString(key, org.json.JSONArray(list).toString()).apply()
    }

    /** Remembers [term] as the newest search; the same words typed again just move to the top. */
    fun add(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        save((listOf(t) + items.filterNot { it.equals(t, ignoreCase = true) }).take(MAX))
    }

    fun remove(term: String) = save(items.filterNot { it == term })

    fun clear() = save(emptyList())

    companion object {
        const val MAX = 12
    }
}

@Composable
fun rememberSearchHistory(scope: String): SearchHistoryState {
    val context = LocalContext.current
    return remember(scope) {
        SearchHistoryState(
            context.applicationContext.getSharedPreferences("ashu_search_history", Context.MODE_PRIVATE),
            "terms_$scope"
        )
    }
}

// ------------------------------------------------------------------------------------------------------------
// Voice search - the phone's own speech screen, so it needs no extra permission
// ------------------------------------------------------------------------------------------------------------

/**
 * Returns a function that opens the system voice-input screen. What was said comes back through [onResult].
 * Needs no microphone permission (the speech service asks for it itself). When a phone has no speech service
 * a short message says so instead of crashing.
 */
@Composable
fun rememberVoiceSearch(prompt: String, onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.trimEnd('.', '?', '!', ',')
            if (!spoken.isNullOrBlank()) latest.value.invoke(spoken)
        }
    }
    val start: () -> Unit = remember(launcher, prompt) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Indian English keeps names like "Suraj" in Latin letters, which is how they are saved.
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                launcher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Voice search is not available on this phone", Toast.LENGTH_SHORT).show()
            }
        }
    }
    return start
}

// ------------------------------------------------------------------------------------------------------------
// Matching helpers shared by the Recents and Contacts search
// ------------------------------------------------------------------------------------------------------------

/**
 * How well [name] / [number] fit what was typed. Lower is better, -1 = no match.
 *  0 name starts with it   1 a word of the name starts with it   2 name contains it
 *  3 every typed word is somewhere in the name (any order)       4 the NUMBER contains the typed digits
 */
fun searchMatchRank(name: String, number: String, query: String): Int {
    val q = query.trim()
    if (q.isEmpty()) return -1
    val n = name.trim()
    if (n.startsWith(q, ignoreCase = true)) return 0
    if (n.split(' ', '-', '.', '_').any { it.isNotEmpty() && it.startsWith(q, ignoreCase = true) }) return 1
    if (n.contains(q, ignoreCase = true)) return 2
    val words = q.split(' ').filter { it.isNotBlank() }
    if (words.size > 1 && words.all { n.contains(it, ignoreCase = true) }) return 3
    val digits = q.filter { it.isDigit() }
    val numberLike = digits.length >= 2 && q.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
    if (numberLike && number.filter { it.isDigit() }.contains(digits)) return 4
    return -1
}

/** The number that best explains why a person matched: the one containing the typed digits, else the first. */
fun searchBestNumber(numbers: List<String>, query: String): String {
    val digits = query.filter { it.isDigit() }
    if (digits.length >= 2) {
        numbers.firstOrNull { it.filter { ch -> ch.isDigit() }.contains(digits) }?.let { return it }
    }
    return numbers.firstOrNull().orEmpty()
}

/** "Just now", "5m ago", "3h ago", "2d ago", or the date for anything older than a week. */
fun searchTimeLabel(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}

fun searchDirectionLabel(direction: CallDirection): String = when (direction) {
    CallDirection.INCOMING -> "Incoming"
    CallDirection.OUTGOING -> "Outgoing"
    CallDirection.MISSED -> "Missed"
    CallDirection.REJECTED -> "Declined"
}

// ------------------------------------------------------------------------------------------------------------
// The bar on the page: tapping it opens the search screen
// ------------------------------------------------------------------------------------------------------------

/**
 * The resting search bar. It is only a button: the typing happens on the search screen it opens ([SearchOverlay]).
 * [onMic] opens voice search straight away. [trailing] is for an extra control at the end (Recents puts its menu there).
 */
@Composable
fun SearchPill(
    hint: String,
    onOpen: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val palette = LocalDialerPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, shape, tintAlpha = 0.62f)
            .clip(shape)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            hint,
            color = palette.textSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp)
        )
        IconButton(onClick = onMic, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        trailing()
    }
}

// ------------------------------------------------------------------------------------------------------------
// The search screen
// ------------------------------------------------------------------------------------------------------------

/**
 * The full search screen. It opens with one smooth motion (it grows down from the top while fading in), shows the
 * person's recent searches until something is typed, and then hands the typed text to [results] to draw the matches.
 *
 * [results] receives the trimmed query and a function to call when one result was used ([picked] stores that name as a
 * recent search). Back closes the screen. [seedQuery] pre-fills the field (used by voice search from the bar).
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    hint: String,
    seedQuery: String,
    history: SearchHistoryState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    results: @Composable (query: String, picked: (String) -> Unit) -> Unit
) {
    val style = LocalAshuStyle.current
    val ease = FastOutSlowInEasing
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(style.ms(220), easing = ease)) +
            slideInVertically(tween(style.ms(340), easing = ease)) { full -> -full / 12 } +
            scaleIn(tween(style.ms(340), easing = ease), initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0f)),
        exit = fadeOut(tween(style.ms(170))) +
            slideOutVertically(tween(style.ms(230), easing = ease)) { full -> -full / 16 } +
            scaleOut(tween(style.ms(230), easing = ease), targetScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 0f))
    ) {
        SearchOverlayBody(
            hint = hint,
            seedQuery = seedQuery,
            history = history,
            onClose = onClose,
            results = results
        )
    }
}

@Composable
private fun SearchOverlayBody(
    hint: String,
    seedQuery: String,
    history: SearchHistoryState,
    onClose: () -> Unit,
    results: @Composable (query: String, picked: (String) -> Unit) -> Unit
) {
    val palette = LocalDialerPalette.current
    val style = LocalAshuStyle.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(seedQuery, TextRange(seedQuery.length))) }
    val query = field.text.trim()

    val close: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        onClose()
    }
    BackHandler(onBack = close)

    val startVoice = rememberVoiceSearch("Say a name or number") { spoken ->
        field = TextFieldValue(spoken, TextRange(spoken.length))
    }

    // Let the opening motion play first, then bring the keyboard up (not when voice search already filled the field).
    LaunchedEffect(Unit) {
        if (seedQuery.isBlank()) {
            delay(style.ms(140).toLong())
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // the field was not on screen yet; the person can tap it
            }
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.opaqueBackground)
            // Swallow touches so nothing behind the search screen reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = close) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .liquidGlass(palette, RoundedCornerShape(18.dp), tintAlpha = 0.62f)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        Text(
                            hint,
                            color = palette.textSecondary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotEmpty()) history.add(query)
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .focusRequester(focusRequester)
                    )
                }
                AnimatedVisibility(
                    visible = field.text.isNotEmpty(),
                    enter = fadeIn(tween(style.ms(120))) + scaleIn(tween(style.ms(120)), initialScale = 0.6f),
                    exit = fadeOut(tween(style.ms(120))) + scaleOut(tween(style.ms(120)), targetScale = 0.6f)
                ) {
                    IconButton(
                        onClick = {
                            field = TextFieldValue("")
                            try {
                                focusRequester.requestFocus()
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = startVoice, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = palette.accent, modifier = Modifier.size(21.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .searchKeyboardAvoiding()
        ) {
            if (query.isEmpty()) {
                SearchHistoryPanel(
                    history = history,
                    onPick = { term -> field = TextFieldValue(term, TextRange(term.length)) }
                )
            } else {
                results(query) { picked -> history.add(picked) }
            }
        }
    }
}

/**
 * Lifts the bottom of the search results above the keyboard by exactly as much as the keyboard covers. The search screen
 * sits above the bottom bar, so a plain imePadding() would lift it by the bar's height too much; this measures how far
 * the screen's bottom edge is from the window's bottom and subtracts that.
 */
@Composable
private fun Modifier.searchKeyboardAvoiding(): Modifier {
    val density = LocalDensity.current
    val view = LocalView.current
    var gapBelowPx by remember { mutableStateOf(0) }
    val imePx = WindowInsets.ime.getBottom(density)
    val padPx = (imePx - gapBelowPx).coerceAtLeast(0)
    return this
        .onGloballyPositioned { coords ->
            val bottom = coords.positionInWindow().y + coords.size.height
            gapBelowPx = (view.rootView.height - bottom).toInt().coerceAtLeast(0)
        }
        .padding(bottom = with(density) { padPx.toDp() })
}

@Composable
private fun SearchHistoryPanel(history: SearchHistoryState, onPick: (String) -> Unit) {
    val palette = LocalDialerPalette.current
    val scope = rememberCoroutineScope()
    val removing = remember { mutableStateListOf<String>() }
    val terms = history.items

    if (terms.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.History, contentDescription = null,
                tint = palette.textSecondary.copy(alpha = 0.45f), modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("No recent searches", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Names and numbers you search for will show up here", fontSize = 12.5.sp, color = palette.textSecondary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "history-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent searches",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Clear search history",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { history.clear() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        itemsIndexed(terms, key = { _, term -> "h_$term" }) { index, term ->
            AnimatedVisibility(
                visible = term !in removing,
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggerIn(index)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(term) }
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        term,
                        color = palette.textPrimary,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 13.dp)
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                removing.add(term)
                                delay(190)
                                history.remove(term)
                                removing.remove(term)
                            }
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "Remove $term from history",
                            tint = palette.textSecondary, modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------
// Pieces the two screens use to draw their results
// ------------------------------------------------------------------------------------------------------------

@Composable
fun SearchSectionTitle(text: String, modifier: Modifier = Modifier) {
    val palette = LocalDialerPalette.current
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
        modifier = modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
    )
}

/**
 * One match. The typed part of the name and of the number is coloured, and the number is on the row from the first letter.
 * [subtitle] is a small third line (the last call, or "No recent call found").
 */
@Composable
fun SearchResultRow(
    name: String,
    number: String,
    photoUri: String?,
    query: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleIsWarning: Boolean = false,
    onClick: () -> Unit,
    onCall: (() -> Unit)? = null
) {
    val palette = LocalDialerPalette.current
    val showNumber = number.isNotBlank() && number != name
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = name, photoUri = photoUri, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = remember(name, query, palette.accent) { highlightMatch(name, query, palette.accent) },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showNumber) {
                Text(
                    text = remember(number, query, palette.accent) { highlightMatch(number, query, palette.accent) },
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = if (subtitleIsWarning) palette.danger else palette.textSecondary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        if (onCall != null) {
            IconButton(onClick = onCall, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.Phone, contentDescription = "Call $name", tint = palette.callGreen, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SearchNoResults(text: String, modifier: Modifier = Modifier) {
    val palette = LocalDialerPalette.current
    Box(modifier = modifier.fillMaxWidth().padding(top = 72.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = palette.textSecondary, fontSize = 14.5.sp)
    }
}
