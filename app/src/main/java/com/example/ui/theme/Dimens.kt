package com.example.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing scale (4-pt grid). Use these instead of raw dp values in screens. */
object Spacing {
    val none = 0.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal screen margin: compact phones / tablets & landscape. */
    val screenCompact = 16.dp
    val screenMedium = 24.dp

    /** Minimum interactive size (WCAG 2.5.8, Material). */
    val minTouchTarget = 48.dp
    val iconSm = 20.dp
    val icon = 24.dp
    val iconLg = 32.dp
    val illustration = 96.dp
    val contentMaxWidth = 640.dp

    /** Single-column forms (sign-in, dialogs on tablets) stay readable on wide screens. */
    val formMaxWidth = 440.dp

    /** Full-width primary buttons. */
    val buttonHeight = 52.dp

    /** Small progress indicator inside a button. */
    val progressStroke = 2.dp

    /** Initial-letter avatar in lists. */
    val avatar = 40.dp

    /** Community archipelago card (also a generic tall-content cap) and the island thumbnail in a friend's sheet. */
    val sceneHeight = 260.dp
    val islandThumb = 64.dp
    /** Community archipelago card (taller: it is explored by dragging). */
    val archipelagoHeight = 320.dp
    /** Wallpaper preview in Settings (phone-shaped). */
    val wallpaperPreviewHeight = 168.dp

    /** Main record / stop control for voice cards. */
    val recordButton = 72.dp

    /** Camera shutter button and the smaller round buttons beside it. */
    val shutter = 76.dp
    val cameraSideButton = 56.dp

    /** Crop handles: drawn radius, and the finger hit radius (≥ 48 dp target). */
    val cropHandle = 10.dp
    val cropHandleHit = 28.dp
    val cropStroke = 2.dp

    /** Crop preview in the "Create card" sheet. */
    val cropPreviewHeight = 180.dp

    /** Card editor: minimum height of the text area; table cells in card content. */
    val editorMinHeight = 280.dp
    val tableColumnWidth = 140.dp
    /** Narrowest column before a card table scrolls sideways instead of sharing the width. */
    val tableMinColumnWidth = 88.dp
    val hairline = 1.dp

    /** Accent bar beside note headings and quotes. */
    val noteAccentBar = 4.dp
    val noteBullet = 6.dp

    /** Focus mode: progress ring (portrait / landscape), ring stroke, brightness touch strip. */
    val focusRing = 288.dp
    val focusRingCompact = 220.dp
    val focusRingStroke = 10.dp
    /** 24-hour dial around the Focus timer: track width and the gap left for the timer ring inside. */
    val dayDialStroke = 6.dp
    val dayDialInset = 40.dp
    /** Focus tab: the dial grows up to [focusDialMax]; below [focusDialMin] only the timer ring is drawn. */
    val focusDialMax = 360.dp
    val focusDialMin = 220.dp
    /** Height kept for the Sound / Challenge / Today panel so switching doesn't move the timer. */
    val focusPanelHeight = 84.dp
    /** Focus mode: brightness slider track width and the longest sound name shown. */
    val brightnessTrack = 6.dp
    val focusModeSoundMax = 220.dp
    /** Station artwork / sound icon on the Focus tab's sound card. */
    val soundArt = 56.dp
    /** Outline of an upcoming cycle dot under the Focus timer. */
    val dotBorder = 1.5.dp
    val brightnessStrip = 40.dp
}
