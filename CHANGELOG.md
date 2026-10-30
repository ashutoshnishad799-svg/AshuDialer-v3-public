# Changelog

## 2.0.0 - 30 October 2026  (the "Liquid Glass" update)

This is a modified edition of the previous release (1.8.0). The GPL asks that changed files are marked
with the date of change - the full list is at the bottom of this file.

### Fixed
- **Crash when searching Recents.** The contact suggestions under the Recents search box used a person's
  contact id as the list key, but the list has one row per phone number. A person with two numbers (or the
  same number saved in two formats) appeared twice with the same key, and Compose crashes on a repeated
  key. Suggestions are now one row per person with a unique key, digits typed with spaces or dashes match,
  and the dial pad's predictive list got the same protection.
- **Call notification timer started before the call was answered.** For outgoing calls the notification
  started its chronometer while the other phone was still ringing. It now says "Calling..." with no timer
  and only starts counting from the moment the call is connected (using Android's own connect time). A call
  on hold shows "On hold" and keeps its start time.

### New - look and feel
- **Defaults are plain White and plain Black** ("Auto" follows the phone). Every other theme is optional.
- **Liquid Glass** (dark) and **Frost Glass** (light): real glass panes with a bright specular edge over a
  slowly moving aurora light. The aurora uses no blur (cheap on mid-range phones), freezes when animation
  speed is Off or Battery Saver is on, and can be switched off.
- Two more optional themes: Obsidian Gold and Emerald Night.
- **Personalize** screen with live preview: theme, accent colour (presets + custom hue), glass frosting,
  shiny edge, aurora, corner roundness, avatar shape, list spacing, text size, animation speed
  (Off/Calm/Normal/Lively), list entrance animation, swipe actions, haptic strength, bottom bar style
  (floating/docked), tab names, dial pad key style (glass/flat/outline), reset.
- New motion toolkit: springy press feedback, staggered list entrance, pulsing halo, shimmer.
- Bottom bar: the glass bubble stretches while it travels between tabs, optional docked style, tab-name
  modes, a light haptic tick on change.
- **Onboarding redesigned**: five animated pages (welcome, dial pad that types itself, live recording
  waveform, shield scan, theme choice) with parallax, a stretching page indicator and a drifting glow.
- **More screen redesigned**: search, featured tiles, grouped sections with a one-line hint on every item.
- **About screen**: what's new, features and the open-source credits (also satisfies the fork notice the
  ShizuCallRecorder licence asks for).
- **Swipe actions** on recent calls: right = call, left = message.

### New - call recording (all optional; the existing recorder is unchanged)
- **Recordings hub** (the screen was rebuilt): status card (ready / recording / setup needed), storage and
  cloud counts, search by name / number / tag / note, quick filters (starred, new, bookmarked, in cloud,
  not backed up, incoming, outgoing), tag filters, source tabs, sort (newest, oldest, longest, largest,
  name), group by person, day headers, multi-select (star, back up, move to Private Space, delete).
- **Player with a real waveform**, scrub by touch, speed 0.75x-2x, +-10 s, and the same playback fixes the
  old player had.
- **Bookmarks**: a flag button on the call screen's recording bar marks a moment while recording; marks
  show as flags on the waveform and as chips you can tap to jump. You can also add them while listening.
- **Star, tags and a note** on every recording (stored beside the audio, never inside it).
- **Optional Google Drive backup, no login**: pick a folder in Android's own file picker (a folder in the
  Drive app). Wi-Fi only / charging only / automatic, retries with back-off, never uploads twice, shows
  per-recording cloud state. **Cloud-only mode**: nothing stays on the phone after a successful upload; until
  then the file waits in private app storage, never in the public Music folder. If an upload cannot finish the
  recording is kept and a notification says so.
- **Storage tools**: clean recordings older than 30 days, shorter than 5 seconds, or already in the cloud.
  Starred recordings are never touched.
- **Discard very short recordings** (optional: under 3 / 5 / 10 seconds).
- Quick quality presets next to the existing format and bitrate choices.

### Version
- versionName 1.8.0 -> 2.0.0, versionCode 15 -> 16.

### New dependencies
- `androidx.work:work-runtime-ktx:2.9.1` and `androidx.documentfile:documentfile:1.0.1` (module `appcalls`).

### Files added
- `app/src/main/java/com/ashudialer/app/ui/components/AshuMotion.kt`
- `app/src/main/java/com/ashudialer/app/ui/components/AuroraBackground.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/PersonalizeScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecordingCloudSection.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecordingPlayerPanel.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecordingsHub.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecordingsHubParts.kt`
- `app/src/main/java/com/ashudialer/app/ui/theme/AshuStyle.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/cloud/CloudCopyWorker.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/cloud/CloudPrefs.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/cloud/CloudSync.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/recording/RecordingMetaStore.kt`

### Files modified
- `app/build.gradle.kts`
- `app/src/main/java/com/ashudialer/app/MainActivity.kt`
- `app/src/main/java/com/ashudialer/app/data/AppSettingsRepository.kt`
- `app/src/main/java/com/ashudialer/app/telecom/CallNotificationHelper.kt`
- `app/src/main/java/com/ashudialer/app/telecom/InCallActivity.kt`
- `app/src/main/java/com/ashudialer/app/telecom/PixelInCallService.kt`
- `app/src/main/java/com/ashudialer/app/telecom/VideoCallActivity.kt`
- `app/src/main/java/com/ashudialer/app/ui/components/AddCallDialog.kt`
- `app/src/main/java/com/ashudialer/app/ui/components/Avatar.kt`
- `app/src/main/java/com/ashudialer/app/ui/components/BottomNav.kt`
- `app/src/main/java/com/ashudialer/app/ui/components/Haptics.kt`
- `app/src/main/java/com/ashudialer/app/ui/components/LiquidGlass.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/AboutScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/CallHistoryScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/CallScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/ContactDetailScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/DialerScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/MoreScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/OnboardingScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecentsScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecordingSettingsScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/RecordingsScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/screens/UnknownNumberDetailScreen.kt`
- `app/src/main/java/com/ashudialer/app/ui/theme/DialerColors.kt`
- `app/src/main/java/com/ashudialer/app/ui/theme/Theme.kt`
- `app/src/main/java/com/ashudialer/app/viewmodel/MainViewModel.kt`
- `appcalls/build.gradle.kts`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/recording/RecordingForegroundService.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/recording/RecordingLibrary.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/recording/RecordingPrefs.kt`
- `appcalls/src/main/java/com/ashudialer/app/appcalls/recording/RecordingStorage.kt`
