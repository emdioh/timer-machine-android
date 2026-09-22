package xyz.aprildown.timer.domain.usecases.timer

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import xyz.aprildown.timer.domain.entities.FolderSortBy
import xyz.aprildown.timer.domain.repositories.TimerStampRepository

class TimerSortTest {

    private data class Row(
        val id: Int,
        val name: String,
        val duration: Long,
        val difficulty: Double,
    )

    private val timerStampRepository: TimerStampRepository = mock()

    private val rows = listOf(
        Row(id = 1, name = "a", duration = 100L, difficulty = 0.5),
        Row(id = 2, name = "b", duration = 300L, difficulty = 1.2),
        Row(id = 3, name = "c", duration = 200L, difficulty = 0.9),
    )

    private suspend fun sortedIds(sortBy: FolderSortBy): List<Int> {
        return rows.sort(
            timerStampRepository = dagger.Lazy { timerStampRepository },
            sortBy = sortBy,
            idOf = { it.id },
            nameOf = { it.name },
            durationOf = { it.duration },
            difficultyOf = { it.difficulty },
        ).map { it.id }
    }

    @Test
    fun duration() = runTest {
        assertEquals(listOf(2, 3, 1), sortedIds(FolderSortBy.DurationLongest))
        assertEquals(listOf(1, 3, 2), sortedIds(FolderSortBy.DurationShortest))
    }

    @Test
    fun difficulty() = runTest {
        assertEquals(listOf(2, 3, 1), sortedIds(FolderSortBy.HardnessHardest))
        assertEquals(listOf(1, 3, 2), sortedIds(FolderSortBy.HardnessEasiest))
    }
}