package com.hasyame.marvelchampions.ui.randomizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.repository.RandomizerNames
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.DrawField
import com.hasyame.marvelchampions.domain.randomizer.RandomizerDraw
import com.hasyame.marvelchampions.domain.randomizer.RandomizerFilters
import com.hasyame.marvelchampions.domain.randomizer.RandomizerPools
import com.hasyame.marvelchampions.domain.randomizer.ScenarioRandomizer
import com.hasyame.marvelchampions.domain.randomizer.ScenarioRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RandomizerUiState(
    val draw: RandomizerDraw = RandomizerDraw(),
    val names: RandomizerNames = RandomizerNames(),
    val pools: RandomizerPools = RandomizerPools(),
    val locked: Set<DrawField> = emptySet(),
    val filters: RandomizerFilters = RandomizerFilters(),
    val excludeBeaten: Boolean = false,
    val history: List<RandomizerHistoryEntity> = emptyList(),
    val isLoading: Boolean = true,
    /** True when the collection is empty, which makes a draw impossible. */
    val hasNoOwnedPacks: Boolean = false,
    /** The drawn scenario's rules could not be parsed with confidence. */
    val scenarioNeedsReview: Boolean = false,
)

@HiltViewModel
class RandomizerViewModel @Inject constructor(
    private val repository: RandomizerRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val draw = MutableStateFlow(RandomizerDraw())
    private val locked = MutableStateFlow<Set<DrawField>>(emptySet())
    private val filters = MutableStateFlow(RandomizerFilters())
    private val excludeBeaten = MutableStateFlow(false)
    private val pools = MutableStateFlow(RandomizerPools())
    private val names = MutableStateFlow(RandomizerNames())
    private val loading = MutableStateFlow(true)

    private var rules: Map<String, ScenarioRule> = emptyMap()
    private var beatenScenarios: Set<String> = emptySet()

    val uiState: StateFlow<RandomizerUiState> = combine(
        combine(draw, locked, filters, excludeBeaten, ::Quad),
        combine(pools, names, loading, ::Triple),
        repository.observeHistory(),
    ) { (currentDraw, currentLocked, currentFilters, skipBeaten),
        (currentPools, currentNames, isLoading),
        history ->
        RandomizerUiState(
            draw = currentDraw,
            names = currentNames,
            pools = currentPools,
            locked = currentLocked,
            filters = currentFilters,
            excludeBeaten = skipBeaten,
            history = history,
            isLoading = isLoading,
            hasNoOwnedPacks = !isLoading && currentPools.scenarios.isEmpty(),
            scenarioNeedsReview = currentDraw.scenarioCode
                ?.let { rules[it]?.needsReview } == true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = RandomizerUiState(),
    )

    init {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            rules = repository.loadRules()
            pools.value = repository.loadPools(locale)
            names.value = repository.loadNames(locale)
            loading.value = false
            if (pools.value.scenarios.isNotEmpty()) {
                rollAll()
            }
        }
        viewModelScope.launch {
            repository.observeBeatenScenarios().collect { beatenScenarios = it.toSet() }
        }
    }

    /** Rerolls everything that is not locked. */
    fun rollAll() {
        draw.value = ScenarioRandomizer.draw(
            pools = pools.value,
            rules = rules,
            filters = effectiveFilters(),
            previous = draw.value,
            locked = locked.value,
        )
    }

    /**
     * Rerolls a single field, leaving every other value alone regardless of
     * whether it is locked.
     */
    fun reroll(field: DrawField) {
        draw.value = ScenarioRandomizer.draw(
            pools = pools.value,
            rules = rules,
            filters = effectiveFilters(),
            previous = draw.value,
            locked = DrawField.entries.toSet() - field,
        )
    }

    fun toggleLock(field: DrawField) {
        locked.value = if (field in locked.value) locked.value - field else locked.value + field
    }

    fun setExcludeBeaten(exclude: Boolean) {
        excludeBeaten.value = exclude
    }

    fun setAllowedDifficulties(allowed: Set<Difficulty>) {
        if (allowed.isEmpty()) {
            return
        }
        filters.value = filters.value.copy(allowedDifficulties = allowed)
    }

    fun setPlayerRange(min: Int, max: Int) {
        filters.value = filters.value.copy(minPlayers = min, maxPlayers = max)
    }

    fun toggleExcludedHero(heroCode: String) {
        val current = filters.value.excludedHeroes
        filters.value = filters.value.copy(
            excludedHeroes = if (heroCode in current) current - heroCode else current + heroCode,
        )
    }

    fun toggleExcludedAspect(aspect: String) {
        val current = filters.value.excludedAspects
        filters.value = filters.value.copy(
            excludedAspects = if (aspect in current) current - aspect else current + aspect,
        )
    }

    fun saveDraw() {
        viewModelScope.launch { repository.save(draw.value) }
    }

    fun setBeaten(id: String, beaten: Boolean) {
        viewModelScope.launch { repository.setBeaten(id, beaten) }
    }

    fun deleteHistoryEntry(id: String) {
        viewModelScope.launch { repository.deleteHistoryEntry(id) }
    }

    /** Folds the "exclude beaten" toggle into the scenario exclusion set. */
    private fun effectiveFilters(): RandomizerFilters {
        val base = filters.value
        return if (excludeBeaten.value) {
            base.copy(excludedScenarios = base.excludedScenarios + beatenScenarios)
        } else {
            base
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** combine() has no four-argument destructuring overload, so this carries one. */
private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
