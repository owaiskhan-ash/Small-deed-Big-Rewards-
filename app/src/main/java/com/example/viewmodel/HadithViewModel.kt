package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HadithsData
import com.example.model.Hadith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ActiveScreen {
    HOME,
    BROWSE,
    FAVORITES,
    SETTINGS,
    HADITH_DETAIL,
    CHAPTER_VIEW
}

enum class FilterChip {
    ALL,
    UNREAD,
    FAVORITES
}

class HadithViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Duration of the UI tick produced by [triggerHaptic]. */
        private const val HAPTIC_MILLIS = 15L
    }

    private val prefs = try {
        application.getSharedPreferences("smdr_prefs", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        android.util.Log.e("HadithViewModel", "Failed to retrieve SharedPreferences safely", e)
        null
    }

    private fun safeEdit(block: (android.content.SharedPreferences.Editor) -> Unit) {
        try {
            val editor = prefs?.edit()
            if (editor != null) {
                block(editor)
                editor.apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("HadithViewModel", "Prefs transaction failed: ${e.message}", e)
        }
    }

    // Standard safety fallback wrapper to guarantee zero crashes on corrupted preference logs
    private fun <T> safePrefRead(defaultValue: T, readBlock: () -> T): T {
        if (prefs == null) return defaultValue
        return try {
            readBlock()
        } catch (e: Exception) {
            android.util.Log.e("HadithViewModel", "Robust check caught prefs read error: ${e.message}", e)
            defaultValue
        }
    }

    // Screen state
    private val _currentScreen = MutableStateFlow(ActiveScreen.HOME)
    val currentScreen: StateFlow<ActiveScreen> = _currentScreen.asStateFlow()

    private val _selectedChapterId = MutableStateFlow<Int?>(null)
    val selectedChapterId: StateFlow<Int?> = _selectedChapterId.asStateFlow()

    private val _detailHadithId = MutableStateFlow(1)
    val detailHadithId: StateFlow<Int> = _detailHadithId.asStateFlow()

    // Hadith Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilterChip = MutableStateFlow(FilterChip.ALL)
    val selectedFilterChip: StateFlow<FilterChip> = _selectedFilterChip.asStateFlow()

    private val _selectedFilterChapterId = MutableStateFlow<Int?>(null)
    val selectedFilterChapterId: StateFlow<Int?> = _selectedFilterChapterId.asStateFlow()

    // 1. Reading Preferences Preference Keys
    private val _fontSize = MutableStateFlow(16) // 14, 16, 18, 22
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _arabicSize = MutableStateFlow(24) // 18 to 32
    val arabicSize: StateFlow<Int> = _arabicSize.asStateFlow()

    private val _fontStyle = MutableStateFlow("sans") // sans | serif
    val fontStyle: StateFlow<String> = _fontStyle.asStateFlow()

    private val _lineHeight = MutableStateFlow("normal") // compact | normal | relaxed
    val lineHeight: StateFlow<String> = _lineHeight.asStateFlow()

    // 2. Appearance
    private val _theme = MutableStateFlow("system") // light | dark | system
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _accentColor = MutableStateFlow("emerald") // emerald, gold, indigo, rose, teal, slate
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    // 3. Reading Experience
    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _autoReadEnabled = MutableStateFlow(true)
    val autoReadEnabled: StateFlow<Boolean> = _autoReadEnabled.asStateFlow()

    private val _showArabicEnabled = MutableStateFlow(true)
    val showArabicEnabled: StateFlow<Boolean> = _showArabicEnabled.asStateFlow()

    private val _showNarratorEnabled = MutableStateFlow(true)
    val showNarratorEnabled: StateFlow<Boolean> = _showNarratorEnabled.asStateFlow()

    private val _dailyNotificationsEnabled = MutableStateFlow(true)
    val dailyNotificationsEnabled: StateFlow<Boolean> = _dailyNotificationsEnabled.asStateFlow()

    // Progress persistence state
    private val _readHadithIds = MutableStateFlow<Set<Int>>(emptySet())
    val readHadithIds: StateFlow<Set<Int>> = _readHadithIds.asStateFlow()

    private val _favoriteHadithIds = MutableStateFlow<Set<Int>>(emptySet())
    val favoriteHadithIds: StateFlow<Set<Int>> = _favoriteHadithIds.asStateFlow()

    private val _lastReadId = MutableStateFlow(1)
    val lastReadId: StateFlow<Int> = _lastReadId.asStateFlow()

    // Streak and Practice stats for UX features 4 & 5
    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _totalCompletionsMap = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val totalCompletionsMap: StateFlow<Map<Int, Int>> = _totalCompletionsMap.asStateFlow()

    // Stacks for screen history
    private val screenHistory = mutableListOf<ActiveScreen>()

    init {
        // Defer all heavy startup loading off the main UI execution loop (similar to React's useEffect after first render)
        // to prevent UI freezing, ANR, and channel disposal. Fully guarded with fallbacks.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Initialize all user pref values safely on a background thread
                _fontSize.value = safePrefRead(16) { prefs?.getInt("smdr_font_size", 16) ?: 16 }
                _arabicSize.value = safePrefRead(24) { prefs?.getInt("smdr_arabic_size", 24) ?: 24 }
                _fontStyle.value = safePrefRead("sans") { prefs?.getString("smdr_font_style", "sans") ?: "sans" }
                _lineHeight.value = safePrefRead("normal") { prefs?.getString("smdr_line_height", "normal") ?: "normal" }
                
                _theme.value = safePrefRead("system") { prefs?.getString("smdr_theme", "system") ?: "system" }
                _accentColor.value = safePrefRead("emerald") { prefs?.getString("smdr_accent", "emerald") ?: "emerald" }
                
                _hapticEnabled.value = safePrefRead(true) { prefs?.getBoolean("smdr_haptic", true) ?: true }
                _autoReadEnabled.value = safePrefRead(true) { prefs?.getBoolean("smdr_auto_read", true) ?: true }
                _showArabicEnabled.value = safePrefRead(true) { prefs?.getBoolean("smdr_show_arabic", true) ?: true }
                _showNarratorEnabled.value = safePrefRead(true) { prefs?.getBoolean("smdr_show_narrator", true) ?: true }
                _dailyNotificationsEnabled.value =
                    safePrefRead(true) { prefs?.getBoolean("smdr_daily_notif", true) ?: true }
                
                _lastReadId.value = safePrefRead(1) { prefs?.getInt("smdr_last_read", 1) ?: 1 }

                val readIds = loadIntSet("smdr_read_hadiths")
                val favoriteIds = loadIntSet("smdr_favorites")
                _readHadithIds.value = readIds
                _favoriteHadithIds.value = favoriteIds
                checkAndUpdateStreakOnStartup()
            } catch (e: Exception) {
                android.util.Log.e("HadithViewModel", "Crash prevented during deferred startup initialization: ${e.message}", e)
                _readHadithIds.value = emptySet()
                _favoriteHadithIds.value = emptySet()
                _currentStreak.value = 0
                _totalCompletionsMap.value = emptyMap()
            }
        }
    }

    private val mainTabs = setOf(ActiveScreen.HOME, ActiveScreen.BROWSE, ActiveScreen.FAVORITES, ActiveScreen.SETTINGS)

    // Navigation triggers
    fun navigateTo(screen: ActiveScreen, detailId: Int? = null, chapterId: Int? = null) {
        triggerHaptic()
        if (_currentScreen.value != screen) {
            if (screen == ActiveScreen.HOME) {
                screenHistory.clear()
            } else if (mainTabs.contains(screen) && mainTabs.contains(_currentScreen.value)) {
                screenHistory.removeAll { mainTabs.contains(it) }
                screenHistory.add(ActiveScreen.HOME)
            } else {
                screenHistory.add(_currentScreen.value)
            }
            _currentScreen.value = screen
        }
        if (detailId != null) {
            _detailHadithId.value = detailId
            safeEdit { it.putInt("smdr_last_read", detailId) }
            _lastReadId.value = detailId
        }
        if (chapterId != null) {
            _selectedChapterId.value = chapterId
        }
    }

    fun navigateBack() {
        triggerHaptic()
        if (screenHistory.isNotEmpty()) {
            _currentScreen.value = screenHistory.removeAt(screenHistory.size - 1)
        } else {
            _currentScreen.value = ActiveScreen.HOME
        }
    }

    // Setters
    fun updateFontSize(size: Int) {
        _fontSize.value = size
        safeEdit { it.putInt("smdr_font_size", size) }
    }

    fun updateArabicSize(size: Int) {
        _arabicSize.value = size
        safeEdit { it.putInt("smdr_arabic_size", size) }
    }

    fun updateFontStyle(style: String) {
        _fontStyle.value = style
        safeEdit { it.putString("smdr_font_style", style) }
    }

    fun updateLineHeight(height: String) {
        _lineHeight.value = height
        safeEdit { it.putString("smdr_line_height", height) }
    }

    fun updateTheme(themeStr: String) {
        _theme.value = themeStr
        safeEdit { it.putString("smdr_theme", themeStr) }
    }

    fun updateAccentColor(color: String) {
        _accentColor.value = color
        safeEdit { it.putString("smdr_accent", color) }
        triggerHaptic()
    }

    fun updateHaptic(enabled: Boolean) {
        _hapticEnabled.value = enabled
        safeEdit { it.putBoolean("smdr_haptic", enabled) }
        if (enabled) triggerHaptic()
    }

    fun updateAutoRead(enabled: Boolean) {
        _autoReadEnabled.value = enabled
        safeEdit { it.putBoolean("smdr_auto_read", enabled) }
    }

    fun updateShowArabic(enabled: Boolean) {
        _showArabicEnabled.value = enabled
        safeEdit { it.putBoolean("smdr_show_arabic", enabled) }
    }

    fun updateShowNarrator(enabled: Boolean) {
        _showNarratorEnabled.value = enabled
        safeEdit { it.putBoolean("smdr_show_narrator", enabled) }
    }

    fun updateDailyNotifications(enabled: Boolean) {
        _dailyNotificationsEnabled.value = enabled
        safeEdit { it.putBoolean("smdr_daily_notif", enabled) }
        val context = getApplication<Application>()
        if (enabled) {
            com.example.worker.DailyHadithWorker.scheduleDailyNotification(context)
        } else {
            com.example.worker.DailyHadithWorker.cancelDailyNotification(context)
        }
        triggerHaptic()
    }

    fun triggerTestNotification() {
        com.example.worker.DailyHadithWorker.triggerImmediateNotification(getApplication())
        triggerHaptic()
    }

    // Searching / Filtering
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilterChip(chip: FilterChip) {
        triggerHaptic()
        _selectedFilterChip.value = chip
    }

    fun updateFilterChapterId(chapterId: Int?) {
        _selectedFilterChapterId.value = chapterId
    }

    // Read toggles
    fun toggleRead(id: Int) {
        triggerHaptic()
        val currentSet = _readHadithIds.value.toMutableSet()
        if (currentSet.contains(id)) {
            currentSet.remove(id)
        } else {
            currentSet.add(id)
            recordActivityForToday()
        }
        _readHadithIds.value = currentSet
        saveIntSet("smdr_read_hadiths", currentSet)
    }

    fun markAsRead(id: Int) {
        if (!_readHadithIds.value.contains(id)) {
            val currentSet = _readHadithIds.value.toMutableSet()
            currentSet.add(id)
            _readHadithIds.value = currentSet
            saveIntSet("smdr_read_hadiths", currentSet)
            recordActivityForToday()
            triggerHaptic()
        }
    }

    // Favorite toggles
    fun toggleFavorite(id: Int) {
        triggerHaptic()
        val currentSet = _favoriteHadithIds.value.toMutableSet()
        if (currentSet.contains(id)) {
            currentSet.remove(id)
        } else {
            currentSet.add(id)
        }
        _favoriteHadithIds.value = currentSet
        saveIntSet("smdr_favorites", currentSet)
    }

    // Reset Progress
    fun resetProgress() {
        triggerHaptic()
        _readHadithIds.value = emptySet()
        _favoriteHadithIds.value = emptySet()
        _lastReadId.value = 1
        _detailHadithId.value = 1
        _currentStreak.value = 0
        _totalCompletionsMap.value = emptyMap()

        saveIntSet("smdr_read_hadiths", emptySet())
        saveIntSet("smdr_favorites", emptySet())
        saveCompletionsMap(emptyMap())

        safeEdit { editor ->
            editor.putInt("smdr_last_read", 1)
            editor.putInt("smdr_streak", 0)
            editor.putLong("smdr_last_read_day", -1L)
            for (id in 1..HadithsData.totalHadiths) {
                editor.remove("smdr_session_count_$id")
                editor.remove("smdr_session_target_index_$id")
            }
        }
    }

    // Seeded Daily Hadith. Delegates to the single shared selection in
    // HadithsData so the notification and the Home card can never disagree.
    fun getDailyHadith(): Hadith = HadithsData.hadithForDay()

    /**
     * Consecutive day number in the **user's local timezone** (days since
     * 1970-01-01 as observed locally).
     *
     * The previous implementation divided epoch millis by 86,400,000, which is
     * a *UTC* day index: for anyone off UTC the "day" rolled over at the wrong
     * local hour, so streaks broke or mis-credited. Deriving the number from
     * the local calendar date (via the days-from-civil algorithm) is exact,
     * monotonic and consecutive across day, month, year and DST boundaries.
     */
    private fun localDayNumber(): Long {
        val cal = Calendar.getInstance()
        return daysFromCivil(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    /** Howard Hinnant's days-from-civil; exact Gregorian day ordinal. */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365L + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    // Daily activity recording for streaks.
    fun recordActivityForToday() {
        val today = localDayNumber()

        val lastDay = safePrefRead(-1L) { prefs?.getLong("smdr_last_read_day", -1L) ?: -1L }
        val streak = safePrefRead(0) { prefs?.getInt("smdr_streak", 0) ?: 0 }

        when {
            lastDay == today -> {
                // Already counted today; the streak is unchanged.
            }
            lastDay == today - 1 -> {
                val updated = streak + 1
                safeEdit { it.putInt("smdr_streak", updated) }
                _currentStreak.value = updated
            }
            else -> {
                safeEdit { it.putInt("smdr_streak", 1) }
                _currentStreak.value = 1
            }
        }

        safeEdit { it.putLong("smdr_last_read_day", today) }
    }

    fun checkAndUpdateStreakOnStartup() {
        val today = localDayNumber()

        val lastDay = safePrefRead(-1L) { prefs?.getLong("smdr_last_read_day", -1L) ?: -1L }
        if (lastDay != -1L && today - lastDay > 1) {
            // A whole day was skipped: the streak is broken.
            safeEdit { it.putInt("smdr_streak", 0) }
            _currentStreak.value = 0
        } else {
            _currentStreak.value = safePrefRead(0) { prefs?.getInt("smdr_streak", 0) ?: 0 }
        }

        _totalCompletionsMap.value = loadCompletionsMap()
    }

    private fun loadCompletionsMap(): Map<Int, Int> {
        return safePrefRead(emptyMap()) {
            val str = prefs?.getString("smdr_completions", "") ?: ""
            if (str.isEmpty()) return@safePrefRead emptyMap()
            str.split(";").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    val id = parts[0].toIntOrNull()
                    val count = parts[1].toIntOrNull()
                    if (id != null && count != null) id to count else null
                } else null
            }.toMap()
        }
    }

    private fun saveCompletionsMap(map: Map<Int, Int>) {
        safeEdit { editor ->
            val str = map.map { "${it.key}:${it.value}" }.joinToString(";")
            editor.putString("smdr_completions", str)
        }
    }
    
    fun incrementPracticeCount(hadithId: Int) {
        val currentMap = _totalCompletionsMap.value.toMutableMap()
        val newCount = (currentMap[hadithId] ?: 0) + 1
        currentMap[hadithId] = newCount
        _totalCompletionsMap.value = currentMap
        saveCompletionsMap(currentMap)
        triggerHaptic()
        recordActivityForToday()
    }

    // Helper functions for Set serialization
    private fun loadIntSet(key: String): Set<Int> {
        return safePrefRead(emptySet()) {
            val str = prefs?.getString(key, "") ?: ""
            if (str.isEmpty()) return@safePrefRead emptySet()
            str.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        }
    }

    private fun saveIntSet(key: String, set: Set<Int>) {
        safeEdit { editor ->
            val str = set.joinToString(",")
            editor.putString(key, str)
        }
    }

    fun getSessionCount(hadithId: Int): Int {
        return safePrefRead(0) { prefs?.getInt("smdr_session_count_$hadithId", 0) ?: 0 }
    }

    fun saveSessionCount(hadithId: Int, count: Int) {
        safeEdit { it.putInt("smdr_session_count_$hadithId", count) }
    }

    fun getSessionTargetIndex(hadithId: Int): Int {
        return safePrefRead(0) { prefs?.getInt("smdr_session_target_index_$hadithId", 0) ?: 0 }
    }

    fun saveSessionTargetIndex(hadithId: Int, index: Int) {
        safeEdit { it.putInt("smdr_session_target_index_$hadithId", index) }
    }

    /**
     * A single short tick of haptic feedback (15 ms), used by taps, toggles and
     * the practice counter.
     *
     * This was previously a logging stub, which left the "Haptic Feedback
     * (Vibrate)" setting in Settings controlling nothing. It now drives the
     * system vibrator and honours [hapticEnabled]. Every call is guarded so a
     * device without a vibrator (or an emulator/headless host) simply no-ops
     * rather than throwing.
     */
    fun triggerHaptic() {
        if (!_hapticEnabled.value) return
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(HAPTIC_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(HAPTIC_MILLIS)
            }
        } catch (e: Exception) {
            // Vibration is a nicety; never let it break an interaction.
            android.util.Log.w("HadithViewModel", "Haptic feedback unavailable: ${e.message}")
        }
    }
}
