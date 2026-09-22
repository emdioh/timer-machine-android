package xyz.aprildown.timer.domain.usecases.timer

import dagger.Lazy
import dagger.Reusable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import xyz.aprildown.timer.domain.di.IoDispatcher
import xyz.aprildown.timer.domain.entities.FolderSortBy
import xyz.aprildown.timer.domain.entities.TimerInfo
import xyz.aprildown.timer.domain.repositories.TimerRepository
import xyz.aprildown.timer.domain.repositories.TimerStampRepository
import xyz.aprildown.timer.domain.usecases.CoroutinesUseCase
import javax.inject.Inject

@Reusable
class GetTimerInfo @Inject constructor(
    @IoDispatcher dispatcher: CoroutineDispatcher,
    private val repository: TimerRepository,
    private val timerStampRepository: Lazy<TimerStampRepository>,
) : CoroutinesUseCase<GetTimerInfo.Params, List<TimerInfo>>(dispatcher) {

    data class Params(val folderId: Long, val sortBy: FolderSortBy)

    override suspend fun create(params: Params): List<TimerInfo> {
        // TimerInfo has no duration or difficulty, so fall back to the added order.
        val sortBy = when (params.sortBy) {
            FolderSortBy.DurationLongest,
            FolderSortBy.DurationShortest,
            FolderSortBy.HardnessHardest,
            FolderSortBy.HardnessEasiest -> FolderSortBy.AddedOldest

            else -> params.sortBy
        }
        return repository.getTimerInfo(params.folderId)
            .sort(timerStampRepository, sortBy, { it.id }, { it.name })
    }
}

internal suspend fun <T> List<T>.sort(
    timerStampRepository: Lazy<TimerStampRepository>,
    sortBy: FolderSortBy,
    idOf: (T) -> Int,
    nameOf: (T) -> String,
    durationOf: (T) -> Long = { 0L },
    difficultyOf: (T) -> Double = { 0.0 },
): List<T> {
    return when (sortBy) {
        FolderSortBy.AddedNewest -> sortedByDescending { idOf(it) }
        FolderSortBy.AddedOldest -> sortedBy { idOf(it) }
        FolderSortBy.AToZ -> sortedBy { nameOf(it) }
        FolderSortBy.ZToA -> sortedByDescending { nameOf(it) }
        FolderSortBy.RunNewest -> {
            sortedByDescending {
                runBlocking {
                    timerStampRepository.get().getRecentOne(idOf(it))?.end ?: 0
                }
            }
        }
        FolderSortBy.RunOldest -> {
            sortedBy {
                runBlocking {
                    timerStampRepository.get().getRecentOne(idOf(it))?.end ?: 0
                }
            }
        }
        FolderSortBy.DurationLongest -> sortedByDescending { durationOf(it) }
        FolderSortBy.DurationShortest -> sortedBy { durationOf(it) }
        FolderSortBy.HardnessHardest -> sortedByDescending { difficultyOf(it) }
        FolderSortBy.HardnessEasiest -> sortedBy { difficultyOf(it) }
    }
}
