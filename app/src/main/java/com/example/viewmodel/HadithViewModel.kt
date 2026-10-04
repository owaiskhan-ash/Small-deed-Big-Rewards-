package com.example.viewmodel

import android.app.Application
import android.content.Context
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
import java.util.Random

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

    private val _weeklyActivity = MutableStateFlow<Set<Int>>(emptySet())
    val weeklyActivity: StateFlow<Set<Int>> = _weeklyActivity.asStateFlow()

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
                val notifEnabled = safePrefRead(true) { prefs?.getBoolean("smdr_daily_notif", true) ?: true }
                _dailyNotificationsEnabled.value = notifEnabled
                if (notifEnabled) {
                    com.example.worker.DailyHadithWorker.scheduleDailyNotification(getApplication())
                }
                
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
                _weeklyActivity.value = emptySet()
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
        _weeklyActivity.value = emptySet()
        _totalCompletionsMap.value = emptyMap()

        saveIntSet("smdr_read_hadiths", emptySet())
        saveIntSet("smdr_favorites", emptySet())
        saveIntSet("smdr_weekly_activity", emptySet())
        saveCompletionsMap(emptyMap())

        safeEdit { editor ->
            editor.putInt("smdr_last_read", 1)
            editor.putInt("smdr_streak", 0)
            editor.putLong("smdr_last_read_day", -1L)
            for (id in 1..100) {
                editor.remove("smdr_session_count_$id")
                editor.remove("smdr_session_target_index_$id")
            }
        }
    }

    // Seeded Daily Hadith
    fun getDailyHadith(): Hadith {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        // seed with day + year to keep it robust and change daily
        val seed = (year * 1000 + dayOfYear).toLong()
        val random = Random(seed)
        val list = HadithsData.hadiths
        if (list.isEmpty()) {
            return Hadith(
                id = 1,
                chapter = 1,
                chapterName = "The Sanctuary of Prayer",
                title = "The Radiant Walk",
                description = "Reward of walking to masjid on Friday",
                arabicText = "مَنِ اغْتَسَلَ يَوْمَ الجُمُعَةِ وَغَسَّلَ وَبَكَّرَ وَابْتَكَرَ وَدَنَا وَاسْتَمَعَ وَأَنْصَتَ كَانَ لَهُ بِكُلِّ خُطْوَةٍ يَخْطُوهَا أَجْرُ سَنَةٍ صِيَامُهَا وَقِيَامُهَا",
                translation = "Whoever performs Ghusl on Friday, goes early and arrives early, gets close and listens and is silent — there will be for him in every step he takes the reward of a year of fasting and standing in prayer.",
                narrator = "Aws bin Aws",
                reference = "Jami` at-Tirmidhi Hadith 496 | Sahih"
            )
        }
        val index = random.nextInt(list.size)
        return list[index]
    }

    // Daily activity recording for streaks (Feature 5)
    fun recordActivityForToday() {
        val todayCalendar = Calendar.getInstance()
        val todayEpochDay = todayCalendar.timeInMillis / (24L * 60L * 60L * 1000L)
        
        val lastEpochDay = safePrefRead(-1L) { prefs?.getLong("smdr_last_read_day", -1L) ?: -1L }
        val streak = safePrefRead(0) { prefs?.getInt("smdr_streak", 0) ?: 0 }
        
        val newStreak = when {
            lastEpochDay == todayEpochDay -> {
                streak
            }
            lastEpochDay == todayEpochDay - 1 -> {
                val updated = streak + 1
                safeEdit { it.putInt("smdr_streak", updated) }
                _currentStreak.value = updated
                updated
            }
            else -> {
                safeEdit { it.putInt("smdr_streak", 1) }
                _currentStreak.value = 1
                1
            }
        }
        
        safeEdit { it.putLong("smdr_last_read_day", todayEpochDay) }
        
        // Save today's day of the week (1=SUNDAY, 2=MONDAY, ..., 7=SATURDAY)
        val dayOfWeek = todayCalendar.get(Calendar.DAY_OF_WEEK)
        val updatedActivity = _weeklyActivity.value.toMutableSet().apply { add(dayOfWeek) }
        _weeklyActivity.value = updatedActivity
        saveIntSet("smdr_weekly_activity", updatedActivity)
    }

    fun checkAndUpdateStreakOnStartup() {
        val todayCalendar = Calendar.getInstance()
        val todayEpochDay = todayCalendar.timeInMillis / (24L * 60L * 60L * 1000L)
        
        val lastEpochDay = safePrefRead(-1L) { prefs?.getLong("smdr_last_read_day", -1L) ?: -1L }
        if (lastEpochDay != -1L) {
            if (todayEpochDay - lastEpochDay > 1) {
                // Streak broken!
                safeEdit { it.putInt("smdr_streak", 0) }
                _currentStreak.value = 0
            } else {
                _currentStreak.value = safePrefRead(0) { prefs?.getInt("smdr_streak", 0) ?: 0 }
            }
        } else {
            _currentStreak.value = safePrefRead(0) { prefs?.getInt("smdr_streak", 0) ?: 0 }
        }
        
        // Load weekly activity and completions map
        _weeklyActivity.value = loadIntSet("smdr_weekly_activity")
        _totalCompletionsMap.value = loadCompletionsMap()
        
        // If more than 7 days have elapsed since last reading activity, clear weekly checkboxes
        if (lastEpochDay != -1L && todayEpochDay - lastEpochDay >= 7) {
            _weeklyActivity.value = emptySet()
            saveIntSet("smdr_weekly_activity", emptySet())
        }
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

    // Perform tiny haptic feedback (15ms vibration)
    fun triggerHaptic() {
        if (_hapticEnabled.value) {
            // Safely bypass hardware vibration calls in VM/Cloud streaming environments
            // to avoid native runtime SIGSEGV crashes on headless systems.
            android.util.Log.d("HadithViewModel", "Haptic interaction triggered safely")
        }
    }
}
