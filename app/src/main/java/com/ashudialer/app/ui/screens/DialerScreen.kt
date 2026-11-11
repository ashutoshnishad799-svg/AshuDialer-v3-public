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
package com.ashudialer.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.Contact
import com.ashudialer.app.telecom.DtmfPlayer
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.rememberButtonHaptic
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.util.normalizePhoneNumberForMatch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle

private data class KeyDef(val digit: String, val letters: String)

private val keys = listOf(
    KeyDef("1", ""), KeyDef("2", "ABC"), KeyDef("3", "DEF"),
    KeyDef("4", "GHI"), KeyDef("5", "JKL"), KeyDef("6", "MNO"),
    KeyDef("7", "PQRS"), KeyDef("8", "TUV"), KeyDef("9", "WXYZ"),
    KeyDef("*", ""), KeyDef("0", "+"), KeyDef("#", "")
)

/**
 * Whether clipboard text looks enough like a real phone number to show the
 * "Paste" chip for it - previously this fired for *any* text containing
 * even a single digit ("Meeting at 5pm", "Room 204", any random paragraph
 * with a number in it), which meant the chip showed up almost constantly
 * and stopped meaning anything. Real phone numbers are, once formatting
 * characters (spaces, dashes, parens, a leading +) are stripped away,
 * mostly-to-entirely digits within a fairly narrow, well-known length
 * range - this checks for exactly that shape instead of "contains a digit
 * somewhere."
 */
private fun looksLikePhoneNumber(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 25) return false

    val digitsOnly = trimmed.filter { it.isDigit() }
    // Accepted shapes only (per product decision - the paste chip should be
    // rare and meaningful, not fire on any 7-digit string):
    //   * 3-digit service codes (100, 112, 198 ...)
    //   * 4-6 digit short codes / premium-rate numbers (e.g. 5xxxx SMS codes)
    //     ONLY when written with a leading "*" or "#" (USSD) or a leading
    //     "1"/"5"/"9" short-code style - kept conservative
    //   * exactly 10 digits (Indian mobile / national format)
    //   * exactly 12 digits (91 + 10-digit mobile), with or without "+"
    //   * 11 digits starting with 0 (trunk-prefixed national) or 13 with "+91"-like
    // Anything else (a 7-digit id, a 9-digit order number, a 16-digit card)
    // no longer triggers the chip.
    val len = digitsOnly.length
    val startsWithUssd = trimmed.startsWith("*") || trimmed.startsWith("#")
    val accepted = when {
        len == 3 -> true
        len in 4..6 -> startsWithUssd || trimmed.startsWith("1") || trimmed.startsWith("5") || trimmed.startsWith("9")
        len == 10 -> true
        len == 11 -> digitsOnly.startsWith("0")
        len == 12 -> true
        len == 13 -> trimmed.startsWith("+") || digitsOnly.startsWith("091")
        else -> false
    }
    if (!accepted) return false

    // Every character besides the digits should be a formatting character a
    // real phone number would plausibly contain - not letters or punctuation
    // that would suggest this is prose with a number embedded in it.
    val allowedFormatting = setOf('+', '-', '(', ')', ' ', '.', '*', '#')
    val nonDigitsAreFormatting = trimmed.all { it.isDigit() || it in allowedFormatting }
    if (!nonDigitsAreFormatting) return false

    // Guards against something like "1.2.3.4.5.6.7" (7+ digits but almost
    // entirely separators) slipping through as "mostly digits" - a real
    // phone number's formatting characters are a small minority of its
    // length, not comparable to or exceeding the digit count.
    val formattingCharCount = trimmed.length - digitsOnly.length
    return formattingCharCount <= digitsOnly.length / 2
}

/** Tallest the contact-match list area ever gets (about 3.3 rows at ~70dp each). */
private const val MAX_MATCH_ZONE_DP = 236f

@Composable
fun DialerScreen(
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit = {},
    onAddContact: (String) -> Unit = {},
    onAvatarClick: (Contact) -> Unit = {},
    vibrateOnButtonPress: Boolean = true,
    // The number the green call button dials when the dialpad is empty -
    // previously the button was simply disabled (enabled = number.isNotEmpty())
    // with no fallback at all, so tapping it on a blank dialer did nothing.
    // Stock dialers redial the most recent call in that situation; this is
    // that same number, passed in from Recents (most recent entry) rather
    // than computed here, since MainActivity already holds that list.
    lastCalledNumber: String? = null,
    // long-press 1 on the dial pad
    onVoicemail: () -> Unit = {},
    // v2.0.1 Personalize > Dialer: "classic" | "compact" | "glass", and edge-to-edge contact suggestions
    layoutStyle: String = "classic",
    wideSuggestions: Boolean = true,
    // v5: 0..1 while the floating dialer window is opening (1 = a normal page). The suggestions, the number, every key and
    // the call button fade and rise in one after another, which is what makes the window feel assembled, not just scaled.
    entranceProgress: () -> Float = { 1f },
    // v5: (dp of the top that may stay folded away, whether suggestions are showing) - used only by the floating window.
    onWindowInfo: (Float, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    // TextFieldValue instead of a plain String so the number line can be a
    // real, editable, pasteable text field - not just a Text label. This
    // carries cursor/selection state, which is what lets a long-press bring
    // up the system paste menu and lets someone tap into the middle of a
    // number to fix a mistyped digit instead of only ever being able to
    // backspace from the end.
    var numberField by remember { mutableStateOf(TextFieldValue("")) }
    val number = numberField.text

    val dtmfPlayer = remember { DtmfPlayer() }
    DisposableEffect(Unit) {
        onDispose { dtmfPlayer.release() }
    }

    /**
     * Appends a dialpad digit at the current cursor position rather than
     * always at the end of the string - so typing after tapping into the
     * middle of a pasted or edited number inserts where the cursor actually
     * is, matching how every other text field on the device behaves.
     */
    fun insertAtCursor(text: String) {
        val start = numberField.selection.start.coerceIn(0, numberField.text.length)
        val end = numberField.selection.end.coerceIn(0, numberField.text.length)
        val newText = numberField.text.replaceRange(start, end, text)
        val newCursor = start + text.length
        numberField = TextFieldValue(newText, TextRange(newCursor))
    }

    /** Replaces the character just before the cursor (used when a long-press turns a typed digit into something else). */
    fun replaceLastCharWith(text: String) {
        val pos = numberField.selection.start.coerceIn(0, numberField.text.length)
        if (pos > 0) {
            numberField = TextFieldValue(numberField.text.removeRange(pos - 1, pos), TextRange(pos - 1))
        }
        if (text.isNotEmpty()) insertAtCursor(text)
    }

    // A visible one-tap Paste pill, separate from the system's own
    // long-press paste toolbar on the text field above. The long-press menu
    // still works (BasicTextField provides it for free), but it's discoverable
    // only by long-pressing, which several people won't think to try on a
    // field that looks like a plain number display. This pill surfaces the
    // same action explicitly whenever the dialpad is empty and the clipboard
    // actually holds something number-shaped, mirroring Google Dialer's own
    // "Paste" chip in that same empty-field state.
    var clipboardHasNumber by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        if (number.isEmpty()) {
            // Android 12+ shows its own "<app> pasted from your clipboard"
            // toast every time an app reads the clip *contents* (primaryClip).
            // That toast is drawn by the system and cannot be turned off, so
            // the only fix is to read the contents as rarely as possible:
            //   1. primaryClipDescription is metadata only - reading it never
            //      shows the toast. Bail out unless the clip is plain text.
            //   2. Only read the contents when the clip is NEW since the last
            //      time we looked (its timestamp changed). Opening the dialer
            //      ten times with the same old clip now reads it at most once
            //      instead of ten times.
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val description = clipboard?.primaryClipDescription
            val isText = description?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                description?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML) == true
            if (!isText) {
                clipboardHasNumber = false
            } else {
                val stamp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    description?.timestamp ?: 0L
                } else 0L
                val seenPrefs = context.getSharedPreferences("ashu_clipboard_seen", Context.MODE_PRIVATE)
                val lastStamp = seenPrefs.getLong("last_stamp", -1L)
                val lastWasNumber = seenPrefs.getBoolean("last_was_number", false)
                if (stamp != 0L && stamp == lastStamp) {
                    // Same clip as last time: reuse the previous verdict, no read, no toast.
                    clipboardHasNumber = lastWasNumber
                } else {
                    val clipText = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    val isNumber = looksLikePhoneNumber(clipText)
                    clipboardHasNumber = isNumber
                    seenPrefs.edit().putLong("last_stamp", stamp).putBoolean("last_was_number", isNumber).apply()
                }
            }
        } else {
            clipboardHasNumber = false
        }
    }
    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        // Keep only what a dialer can use (digits, + * #): "Call me on +91 98765-43210." pastes as +919876543210.
        val cleaned = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.isNotEmpty()) insertAtCursor(cleaned)
    }

    fun copyNumber() {
        val text = numberField.text
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Phone number", text))
        // Android 13+ shows its own "copied" confirmation; older versions get a short one from here.
        if (android.os.Build.VERSION.SDK_INT < 33) {
            android.widget.Toast.makeText(context, "Number copied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Long press on the number: Copy / Paste / Clear. A tap on it puts the cursor there (to fix a wrong digit).
    var numberMenuOpen by remember { mutableStateOf(false) }
    var numberLayout by remember { mutableStateOf<TextLayoutResult?>(null) }


    // Every number that currently matches what's been typed so far, not just
    // the first one - a saved contact can have more than one number (Mobile,
    // Work, ...), and each of those is its own Contact row sharing the same
    // contactId (see ContactsRepository.loadAllContacts, which reads
    // Android's own Phone.CONTENT_URI - a row per number, not per person).
    // Previously only firstOrNull() was used, so a second matching number on
    // the same contact - or a second contact entirely - was silently hidden.
    // Every contact's number is normalised ONCE per contact list, not on every keystroke. It used to be redone for the whole
    // list (often thousands of numbers) on the main thread each time a digit was typed, which made fast typing stutter: the
    // key felt late and presses were drawn after the next one. Now a keystroke only compares strings.
    val normalizedNumbers = remember(contacts) { contacts.map { normalizePhoneNumberForMatch(it.phoneNumber) } }
    val matchedContacts = remember(number, contacts, normalizedNumbers) {
        if (number.length < 3) return@remember emptyList()
        val target = normalizePhoneNumberForMatch(number)
        val found = ArrayList<Contact>()
        for (i in contacts.indices) {
            val candidate = normalizedNumbers[i]
            if (candidate.isNotEmpty() && (candidate == target || candidate.contains(target) || target.contains(candidate))) {
                found.add(contacts[i])
            }
        }
        found
    }
    // Grouped by person, preserving the order matches were found in, so one
    // contact with multiple matching numbers renders as a single card
    // listing each number, rather than one card per number.
    val matchedByContactId = remember(matchedContacts) {
        matchedContacts.groupBy { it.contactId }.toList()
    }


    val showAddContactHint = number.length >= 5 && matchedContacts.isEmpty()

    // The suggestion box used to appear and vanish (fade + scale) every time a digit made the matches flip between
    // "some" and "none", which looked like it was stuttering while dialing. Now it stays up for a moment after the
    // last match disappears, so quick typing never makes it flicker.
    var shownMatches by remember { mutableStateOf(matchedByContactId) }
    LaunchedEffect(matchedByContactId) {
        if (matchedByContactId.isNotEmpty()) {
            shownMatches = matchedByContactId
        } else {
            kotlinx.coroutines.delay(260)
            shownMatches = matchedByContactId
        }
    }


    val vibrate = rememberButtonHaptic(vibrateOnButtonPress)

    /**
     * The number that should actually be dialed for the current input.
     * If what's typed matches exactly one saved number, dial that real,
     * complete saved number - not the raw partial digits still on screen.
     * Previously the Call button always dialed the literal typed digits
     * (`number`) even after a contact match was shown, so tapping Call
     * right after seeing a name appear tried to place a call to an
     * incomplete number and failed until the whole number was typed out.
     * With more than one match (ambiguous - could be different numbers for
     * the same person, or different people), the raw typed digits are used
     * so the person isn't sent to a possibly-wrong number; the match list
     * below is there precisely so they can tap the specific number they mean.
     */
    val numberToDial = matchedContacts.singleOrNull()?.phoneNumber ?: number


    // ------------------------------------------------------------------------------
    // RESPONSIVE LAYOUT. The dialer used to be one Column of fixed-dp blocks
    // (72dp keys, 120dp number zone, 140dp match zone ...) stacked from the top.
    // That total (~740dp) fit a small/old phone snugly but left a large empty
    // gap on a tall one, and it could not shrink on a short screen or when the
    // system font/display size was raised - which is why the same app looked
    // different (and worse) from phone to phone and ROM to ROM.
    //
    // Now everything that used a fixed dp is derived from the space this screen
    // is actually given (BoxWithConstraints already excludes the bottom nav and
    // system bars, because the Scaffold hands this composable only what is left):
    //   * dialer keys are sized from BOTH the available width and height, so they
    //     never overflow sideways on a narrow phone nor get cramped on a short one;
    //   * the number zone, match-results zone, hint row and vertical gaps are a
    //     fraction of the available height;
    //   * the whole block is centred in whatever is left over instead of being
    //     glued to the top, so a tall screen gets even breathing room.
    // The zones are still FIXED for a given screen size (they only change when the
    // screen itself changes), so the "dialpad jumps while typing" fixes explained
    // below still hold exactly as before.
    // ------------------------------------------------------------------------------
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // LAYOUT, top to bottom (everything is anchored to the BOTTOM, next to the thumb, like a stock dialer):
        //
        //   [ free space ]  [ contact suggestions (+ Add to Contacts / Paste chips) ]  [ typed number ]  [ keys ]  [ call ]
        //
        // Every zone has a FIXED height that never depends on what has been typed, so nothing shifts while
        // typing. Only the keys are sized from the screen: they are 70dp (the size this dialer has always
        // had) and only SHRINK on a short phone or a large system font. Row spacing is the key plus a 10dp
        // cell margin, with no extra gap - that is what made the keypad look "spread out" when a solver
        // stretched it to fill the height.
        val availW = maxWidth.value
        val availH = maxHeight.value

        val sidePadF = (availW * 0.07f).coerceIn(16f, 32f)
        val keyFromWidth = (availW - sidePadF * 2f - 28f) / 3f

        val topPadF = 6f
        val numberZoneF = 62f          // the typed number sits at the BOTTOM of this zone, right above the keys
        val haloF = 10f                // every key sits in a (key + 10dp) cell
        val callTopGapF = 10f
        val callBottomGapF = 18f

        // v5: the "Add to Contacts" / "Paste" chips now live INSIDE the suggestions zone (they can never be visible at
        // the same time as a suggestion), so the old 42dp hint row between the number and the keys is gone: the number
        // sits lower, closer to the thumb, and that height goes to the suggestions box.
        // One FULL suggestion row is about 70dp. Two rows (140dp) are wanted whenever the keys can stay at 50dp or more;
        // a very short screen settles for one row rather than shrinking the keys.
        val keyBase = availH - topPadF - numberZoneF - callTopGapF - callBottomGapF - 4f * haloF
        val twoRowsF = 140f
        val oneRowF = 76f
        // The key size that exactly fills the height left after [minMatch] of suggestions. The call button follows the
        // keys (0.86x) but never drops below 46dp: under ~53dp keys it stays pinned there and the keys take the rest.
        fun keyFor(minMatch: Float): Float {
            val follow = (keyBase - minMatch) / 4.86f
            return if (follow * 0.86f >= 46f) follow else (keyBase - minMatch - 46f) / 4f
        }
        val minMatchF = if (keyFor(twoRowsF) >= 50f) twoRowsF else oneRowF
        val keyByHeight = keyFor(minMatchF)
        val keyCap = when (layoutStyle) {
            "compact" -> 58f
            "glass" -> 74f
            else -> 70f
        }
        val keySizeF = minOf(keyByHeight, keyFromWidth, keyCap).coerceAtLeast(44f)
        val callSizeF = (keySizeF * 0.86f).coerceIn(46f, 60f)

        val bottomBlockF = 4f * (keySizeF + haloF) + callTopGapF + callSizeF + callBottomGapF
        val leftoverF = availH - topPadF - numberZoneF - bottomBlockF
        val matchZoneF = leftoverF.coerceAtLeast(oneRowF).coerceAtMost(MAX_MATCH_ZONE_DP)
        val topSpacerF = (leftoverF - matchZoneF).coerceAtLeast(0f)
        // Only a very short screen (or landscape) cannot fit everything: then, and only then, the column scrolls.
        val needsScroll = topPadF + numberZoneF + matchZoneF + bottomBlockF > availH + 1f

        // Tells the floating window how much of the top (the suggestions) can stay folded away while there is nothing
        // to suggest: the window rests at about half the screen - just the number, keys and call button, like a stock
        // keypad - and grows upward by itself when matches appear. The dialer's own layout never changes while that
        // happens, so no key moves. (70dp stays visible for the handle and the Add to Contacts / Paste chips.)
        val hasMatches = shownMatches.isNotEmpty()
        LaunchedEffect(topPadF, topSpacerF, matchZoneF, hasMatches) {
            onWindowInfo((topPadF + topSpacerF + matchZoneF - 70f).coerceAtLeast(0f), hasMatches)
        }

        val sidePad = sidePadF.dp
        val keySize = keySizeF.dp
        val numberZoneH = numberZoneF.dp
        val matchZoneH = matchZoneF.dp
        val topPad = topPadF.dp
        val callSize = callSizeF.dp
        val callTopGap = callTopGapF.dp
        val callBottomGap = callBottomGapF.dp
        val numberFontTarget = when {
            number.length <= 11 -> 36f
            number.length <= 15 -> 30f
            else -> 24f
        }
        val numberFont by animateFloatAsState(
            targetValue = numberFontTarget,
            animationSpec = tween(140, easing = FastOutSlowInEasing),
            label = "number-font"
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (needsScroll) Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier)
            .padding(horizontal = sidePad)
            .padding(top = topPad),
        // Keep the number display anchored at the top. The previous Bottom
        // arrangement let the presence/absence of contact-match content
        // change the whole block's vertical position, making the typed
        // number jump between the top and middle of the screen. The extra
        // top padding just nudges the whole anchored block down a bit -
        // there was unused space below the call button - and being a fixed
        // value (not weighted/dynamic) it can't reintroduce any of the
        // jumpiness the fixed-height zones below were built to prevent.
        verticalArrangement = Arrangement.Top
    ) {
        // Leftover height on tall phones: empty space ABOVE the suggestions, so everything below it sits at the bottom
        // within thumb reach. It is 0 when the screen is fully used, and constant for a given screen (it never changes
        // while typing), so the "dialpad jumps" fixes are unaffected.
        if (topSpacerF > 0f) Spacer(Modifier.height(topSpacerF.dp))

        // Contact suggestions + the "Add to Contacts" / "Paste" chips. Bottom-aligned, so everything sits right on top of
        // the number and grows upward; the zone itself is a fixed height, so nothing below it ever moves.
        Box(
            Modifier.fillMaxWidth().height(matchZoneH).staggerEntrance(entranceProgress, 0.08f),
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = shownMatches.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 8 },
                exit = fadeOut(animationSpec = tween(120)),
                modifier = Modifier.fillMaxWidth().heightIn(max = matchZoneH)
            ) {
                MatchedContactsList(
                    wide = wideSuggestions,
                    bleed = sidePad,
                    matchedByContactId = shownMatches,
                    typedDigits = number,
                    palette = palette,
                    onCall = { num -> vibrate(); onCall(num) },
                    onMessage = { num -> vibrate(); onMessage(num) },
                    onAvatarClick = { contact -> vibrate(); onAvatarClick(contact) }
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showAddContactHint && shownMatches.isEmpty(),
                    enter = fadeIn(animationSpec = tween(160)) + scaleIn(animationSpec = tween(180), initialScale = 0.92f),
                    exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100), targetScale = 0.92f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.accentSoft)
                            .clickable { onAddContact(number) }
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Add to Contacts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent, maxLines = 1)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = clipboardHasNumber && shownMatches.isEmpty(),
                    enter = fadeIn(animationSpec = tween(160)) + scaleIn(animationSpec = tween(180), initialScale = 0.92f),
                    exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100), targetScale = 0.92f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.accentSoft)
                            .clickable { pasteFromClipboard() }
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Paste", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent, maxLines = 1)
                    }
                }
            }
        }

        // The typed number: fixed height, centred, directly above the keys. A tap puts the cursor there (to fix a wrong
        // digit in the middle); a long press opens Copy / Paste / Clear. The cursor only shows while it is NOT at the end.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(numberZoneH)
                .staggerEntrance(entranceProgress, 0.12f)
                .then(if (layoutStyle == "glass") Modifier.glassCard(palette, 18.dp) else Modifier)
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { numberMenuOpen = true },
                            onTap = { pos ->
                                val layout = numberLayout
                                val text = numberField.text
                                if (layout != null && text.isNotEmpty()) {
                                    val index = layout
                                        .getOffsetForPosition(Offset(pos.x, layout.size.height / 2f))
                                        .coerceIn(0, text.length)
                                    numberField = numberField.copy(selection = TextRange(index))
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    fontSize = numberFont.sp,
                    fontWeight = FontWeight.Light,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { numberLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            val layout = numberLayout
                            val cursor = numberField.selection.start
                            if (layout != null && cursor in 0 until numberField.text.length) {
                                val rect = layout.getCursorRect(cursor)
                                drawLine(
                                    color = palette.accent,
                                    start = Offset(rect.left, rect.top),
                                    end = Offset(rect.left, rect.bottom),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                )
                DropdownMenu(
                    expanded = numberMenuOpen,
                    onDismissRequest = { numberMenuOpen = false }
                ) {
                    if (number.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { copyNumber(); numberMenuOpen = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                        onClick = { pasteFromClipboard(); numberMenuOpen = false }
                    )
                    if (number.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Clear") },
                            leadingIcon = { Icon(Icons.Filled.Clear, contentDescription = null) },
                            onClick = { numberField = TextFieldValue(""); numberMenuOpen = false }
                        )
                    }
                }
            }
        }

        val keyVerticalPadding = 0.dp

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = keyVerticalPadding)
                ) {
                    row.forEachIndexed { colIndex, key ->
                        DialerKey(
                            key = key,
                            palette = palette,
                            size = keySize,
                            entrance = entranceProgress,
                            entranceDelay = 0.16f + (rowIndex * 3 + colIndex) * 0.025f,
                            // Each key owns an equal third of the row, so there is no dead gap between two keys: a touch
                            // that lands a little to one side of a key still presses THAT key, which is what makes fast
                            // typing forgiving.
                            modifier = Modifier.weight(1f),
                            // v2.0.1: hold 0 for "+", hold 1 for voicemail (the digit typed on touch-down is taken back)
                            onLongPress = when (key.digit) {
                                "0" -> { { replaceLastCharWith("+") } }
                                "1" -> { { replaceLastCharWith(""); onVoicemail() } }
                                else -> null
                            },
                            onPress = {
                                insertAtCursor(key.digit)
                                dtmfPlayer.play(key.digit.first())
                                vibrate()
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = callTopGap, bottom = callBottomGap)
                .staggerEntrance(entranceProgress, 0.46f, rise = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val callInteractionSource = remember { MutableInteractionSource() }
            val isCallPressed by callInteractionSource.collectIsPressedAsState()
            val callScale by animateFloatSpring(if (isCallPressed) 0.9f else 1f)

            IconButton(
                onClick = {
                    if (number.isNotEmpty()) {
                        onCall(numberToDial)
                    } else if (!lastCalledNumber.isNullOrBlank()) {
                        // Blank dialpad: fill in the last call rather than
                        // dialing it immediately outright - the person still
                        // sees the number and gets a normal second tap to
                        // actually place the call, instead of one tap on an
                        // empty screen silently calling someone.
                        numberField = TextFieldValue(lastCalledNumber, TextRange(lastCalledNumber.length))
                    }
                },
                enabled = number.isNotEmpty() || !lastCalledNumber.isNullOrBlank(),
                interactionSource = callInteractionSource,
                modifier = Modifier
                    .size(callSize)
                    .scale(callScale)
                    .clip(CircleShape)
                    .background(palette.callGreen)
            ) {
                Icon(
                    Icons.Filled.Phone,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = number.isNotEmpty(),
                modifier = Modifier.align(Alignment.CenterEnd),
                // Fade only (no scale-in): a key that is still growing when the first digit is typed ignores taps
                // for the first moments, which felt like the backspace "not working".
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(120))
            ) {
                var isBackspacePressed by remember { mutableStateOf(false) }
                val backspaceGlow by animateFloatAsState(
                    targetValue = if (isBackspacePressed) 1f else 0f,
                    animationSpec = tween(if (isBackspacePressed) 45 else 220),
                    label = "backspace-glow"
                )
                val backspaceScale by animateFloatAsState(
                    targetValue = if (isBackspacePressed) 0.88f else 1f,
                    animationSpec = if (isBackspacePressed) tween(50) else spring(dampingRatio = 0.7f, stiffness = 520f),
                    label = "backspace-scale"
                )

                Box(
                    modifier = Modifier
                        // 56dp: the old 44dp target was smaller than a fingertip.
                        .size(56.dp)
                        .graphicsLayer { scaleX = backspaceScale; scaleY = backspaceScale }
                        .clip(CircleShape)
                        .background(palette.textSecondary.copy(alpha = 0.16f * backspaceGlow))
                        .repeatingClickable(
                            enabled = number.isNotEmpty(),
                            onPressChange = { isBackspacePressed = it },
                            onClick = {
                                val start = numberField.selection.start.coerceIn(0, numberField.text.length)
                                val end = numberField.selection.end.coerceIn(0, numberField.text.length)
                                numberField = if (start != end) {
                                    TextFieldValue(numberField.text.removeRange(start, end), TextRange(start))
                                } else if (start > 0) {
                                    TextFieldValue(numberField.text.removeRange(start - 1, start), TextRange(start - 1))
                                } else {
                                    numberField
                                }
                                vibrate()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = palette.textPrimary.copy(alpha = 0.72f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
    }
}

/**
 * Every matched number as one compact row - avatar, name, and number sharing
 * a single line the way a T9 predictive dialer does, so many matches read as
 * a short scrollable list rather than a stack of tall multi-line cards. The
 * list itself scrolls in a height-capped LazyColumn (see DialerScreen's
 * `.weight(1f, fill = false)` + this composable's own heightIn cap) instead
 * of growing without bound, which is what used to push the dialpad down and
 * off-screen as more contacts matched.
 */
@Composable
private fun MatchedContactsList(
    wide: Boolean = false,
    bleed: androidx.compose.ui.unit.Dp = 0.dp,
    matchedByContactId: List<Pair<String, List<Contact>>>,
    typedDigits: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onAvatarClick: (Contact) -> Unit = {}
) {
    // Flatten to one row per number (not per person), preserving which
    // group a row belongs to only for the divider - a contact with two
    // matching numbers (Mobile + Work) still shows as two independently
    // tappable rows, same as the earlier grouped-card version.
    val rows = remember(matchedByContactId) {
        // v4: distinctBy keeps the LazyColumn keys unique even if the same number is listed twice.
        matchedByContactId.flatMap { (_, numbers) -> numbers }
            .distinctBy { it.contactId + "|" + it.phoneNumber }
    }

    // v5: ONE soft panel for all the matches - no separate boxes, no divider lines, no border. With "wide" on it runs edge
    // to edge, and its top edge dissolves into the page, so the list reads as part of the screen rather than a card on it.
    val panel = when {
        palette.glass && palette.isDark -> palette.cardBackground.copy(alpha = 0.45f)
        palette.glass -> palette.cardBackground.copy(alpha = 0.60f)
        else -> palette.cardBackground.copy(alpha = 0.92f)
    }
    LazyColumn(
        modifier = Modifier
            .then(if (wide) Modifier.bleedHorizontally(bleed) else Modifier)
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            // v2.3: THE FIX for "the suggestions at the top look cut off" - the list was as tall as the room it was given, which
            // is rarely a whole number of rows, so the next row showed as a thin hard-cut sliver along the bottom edge. The
            // height now snaps to whole rows (a row is about 59dp), so only complete rows are shown.
            .snapToWholeRows(row = 59.dp, extra = 14.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(panel)
            .verticalEdgeFade(top = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 4.dp)
    ) {
        items(rows, key = { it.contactId + "|" + it.phoneNumber }) { contact ->
            MatchedContactRow(
                contact = contact,
                typedDigits = typedDigits,
                palette = palette,
                onCall = { onCall(contact.phoneNumber) },
                onMessage = { onMessage(contact.phoneNumber) },
                onAvatarClick = { onAvatarClick(contact) }
            )
        }
    }
}

/**
 * A single matched row: avatar, name + number on one line, message/call
 * actions trailing - mirrors the reference Google Dialer row layout instead
 * of the earlier two-line name-above-number card. The digits the person has
 * actually typed so far are bolded and tinted with the accent color inside
 * the number, the same "highlight what you typed" cue Google Dialer uses,
 * so it's visually obvious *why* each row matched.
 */
@Composable
private fun MatchedContactRow(
    contact: Contact,
    typedDigits: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar gets its own tap target (opens the contact/number detail
        // page) instead of inheriting the row's onCall - previously the
        // whole row, avatar included, shared one clickable, so tapping the
        // avatar specifically (the natural "show me who this is" gesture,
        // and the same gesture that opens a detail page from Recents)
        // instead silently placed a call. clip + clickable scoped to just
        // the Avatar's own Box keeps that a distinct, smaller tap target
        // than the rest of the row.
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAvatarClick)
        ) {
            Avatar(name = contact.displayName, photoUri = contact.photoUri, size = 38.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = highlightMatchedDigits(contact.phoneNumber, typedDigits, palette.accent, palette.textSecondary),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        IconButton(
            onClick = onMessage,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Filled.Message, contentDescription = "Message", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onCall,
            modifier = Modifier.size(30.dp).clip(CircleShape).background(palette.accentSoft)
        ) {
            Icon(Icons.Filled.Phone, contentDescription = "Call", tint = palette.accent, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * Bolds and accent-tints whichever digits of `phoneNumber` are the ones the
 * person actually typed, so a matched row visually explains itself the way
 * Google Dialer's predictive list does - e.g. typing "198" against
 * "+91 94151 19835" highlights the embedded "198" run rather than leaving
 * the whole number a flat, unexplained color.
 */
private fun highlightMatchedDigits(
    phoneNumber: String,
    typedDigits: String,
    highlightColor: Color,
    baseColor: Color
): AnnotatedString {
    val target = normalizePhoneNumberForMatch(typedDigits)
    if (target.isEmpty()) {
        return buildAnnotatedString { withStyle(SpanStyle(color = baseColor)) { append(phoneNumber) } }
    }
    // Match against digits-only so formatting characters (spaces, +, -) in
    // the displayed string don't break the digit run we're trying to
    // highlight; matchStartDigitIndex/matchLen are then indices into the
    // digit-only stream, and digitsSeen re-walks phoneNumber to translate
    // those back into positions in the original formatted string.
    val digitsOnlyTarget = target.removePrefix("+")
    val digitsOnlyPhone = phoneNumber.filter { it.isDigit() }
    val matchStartDigitIndex = digitsOnlyPhone.indexOf(digitsOnlyTarget)

    return buildAnnotatedString {
        if (matchStartDigitIndex < 0 || digitsOnlyTarget.isEmpty()) {
            withStyle(SpanStyle(color = baseColor)) { append(phoneNumber) }
            return@buildAnnotatedString
        }
        var digitsSeen = 0
        for (ch in phoneNumber) {
            val isDigit = ch.isDigit()
            val inMatchRange = isDigit &&
                digitsSeen >= matchStartDigitIndex &&
                digitsSeen < matchStartDigitIndex + digitsOnlyTarget.length
            withStyle(
                SpanStyle(
                    color = if (inMatchRange) highlightColor else baseColor,
                    fontWeight = if (inMatchRange) FontWeight.Bold else FontWeight.Normal
                )
            ) { append(ch) }
            if (isDigit) digitsSeen++
        }
    }
}

@Composable
private fun animateFloatSpring(target: Float) = androidx.compose.animation.core.animateFloatAsState(
    targetValue = target,


    animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
    label = "press-scale"
)


private fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 400L,
    minDelayMillis: Long = 45L,
    delayDecayFactor: Float = 0.82f,
    onPressChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)

    this.pointerInput(Unit) {
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (!currentEnabled) return@awaitEachGesture

                onPressChange(true)
                currentOnClick()

                var waitMillis = initialDelayMillis


                while (withTimeoutOrNull(waitMillis) { waitForUpOrCancellation(); false } ?: true) {
                    currentOnClick()
                    waitMillis = (waitMillis * delayDecayFactor).toLong().coerceAtLeast(minDelayMillis)
                }
                onPressChange(false)
            }
        }
    }
}

@Composable
private fun DialerKey(
    key: KeyDef,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    size: androidx.compose.ui.unit.Dp = 78.dp,
    modifier: Modifier = Modifier,
    entrance: () -> Float = { 1f },
    entranceDelay: Float = 0f,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit
) {
    // The digit is entered the moment the finger goes DOWN (and the tone and haptic start with it), exactly like a
    // real keypad. The TOUCH area is the whole grid cell (an equal share of the row wide, the key plus its margin
    // tall); the round key drawn inside it is only the visual. Cells touch each other, so there is nowhere between
    // two keys where a tap is lost.
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = if (pressed) tween(50, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.68f, stiffness = 520f),
        label = "dial-key-scale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 45 else 240),
        label = "dial-key-glow"
    )
    val fire = onPress
    // v4 Personalize > Dial pad keys: glass (default), flat or outline.
    val keypadStyle = com.ashudialer.app.ui.theme.LocalAshuStyle.current.keypadStyle

    Box(
        modifier = modifier
            .height(size + 10.dp)
            .staggerEntrance(entrance, entranceDelay, rise = 14.dp, scaleFrom = 0.82f)
            .pointerInput(key) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() },
                    onPress = {
                        pressed = true
                        fire()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                // State is read inside these lambdas, so a press redraws the key instead of recomposing the screen.
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .let { m ->
                    when (keypadStyle) {
                        com.ashudialer.app.ui.theme.AshuStyle.KEYPAD_FLAT ->
                            m.clip(CircleShape).background(palette.accentSoft.copy(alpha = if (palette.isDark) 0.7f else 1f), CircleShape)
                        com.ashudialer.app.ui.theme.AshuStyle.KEYPAD_OUTLINE ->
                            m.clip(CircleShape).border(1.5.dp, palette.textSecondary.copy(alpha = 0.55f), CircleShape)
                        else -> m.glassCircle(palette)
                    }
                }
                .drawWithContent {
                    // Soft accent glow that lights up on touch-down and fades out on release.
                    drawCircle(color = palette.accent.copy(alpha = 0.20f * glow))
                    drawContent()
                },
            contentAlignment = Alignment.Center
        ) {
            val sizeFraction = size / 78.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(key.digit, fontSize = (30 * sizeFraction).sp, fontWeight = FontWeight.Normal, color = palette.textPrimary)
                if (key.letters.isNotEmpty()) {
                    Text(
                        key.letters, fontSize = (9 * sizeFraction).sp, fontWeight = FontWeight.Bold,
                        color = palette.textSecondary, letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

/**
 * Fades an element in: transparent -> opaque and a small rise (and, optionally, a grow) as [entrance] runs from [delay]
 * to [delay] + 0.5. Read in the draw phase only, so it costs no recomposition. Does nothing once [entrance] reaches 1.
 */
private fun Modifier.staggerEntrance(
    entrance: () -> Float,
    delay: Float,
    rise: androidx.compose.ui.unit.Dp = 12.dp,
    scaleFrom: Float = 1f
): Modifier = this.graphicsLayer {
    val e = entrance()
    if (e < 1f) {
        val k = FastOutSlowInEasing.transform(((e - delay) / 0.5f).coerceIn(0f, 1f))
        alpha = k
        translationY = (1f - k) * rise.toPx()
        val s = scaleFrom + (1f - scaleFrom) * k
        scaleX = s
        scaleY = s
    }
}

/** Soft-fades the top edge of a scrolling panel into whatever is behind it (no hard line, no border). */
private fun Modifier.verticalEdgeFade(top: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadeHeight = top.toPx().coerceAtMost(size.height / 2f)
        if (fadeHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = fadeHeight
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, fadeHeight),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Caps the height at a whole number of rows (plus [extra] for the list's own padding) so a half-visible row is never cut
 * off at the edge. A little [tolerance] lets a row that is only just clipped still count as fitting.
 */
private fun Modifier.snapToWholeRows(
    row: androidx.compose.ui.unit.Dp,
    extra: androidx.compose.ui.unit.Dp,
    tolerance: androidx.compose.ui.unit.Dp = 6.dp
): Modifier = this.layout { measurable, constraints ->
    val rowPx = row.roundToPx().coerceAtLeast(1)
    val extraPx = extra.roundToPx()
    val tolerancePx = tolerance.roundToPx()
    val max = constraints.maxHeight
    val capped = if (max == androidx.compose.ui.unit.Constraints.Infinity) {
        max
    } else {
        val fit = ((max - extraPx + tolerancePx) / rowPx).coerceAtLeast(1)
        (fit * rowPx + extraPx).coerceAtMost(max)
    }
    val placeable = measurable.measure(constraints.copy(maxHeight = capped))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** Lets a child be wider than its parent by [bleed] on each side (used for the edge-to-edge suggestion box). */
private fun Modifier.bleedHorizontally(bleed: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val extra = (bleed * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed.roundToPx(), 0) }
}
