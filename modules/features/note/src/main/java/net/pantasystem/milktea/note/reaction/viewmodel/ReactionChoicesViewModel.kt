package net.pantasystem.milktea.note.reaction.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.common_android.resource.StringSource
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.emoji.Emoji
import net.pantasystem.milktea.model.emoji.UserEmojiConfig
import net.pantasystem.milktea.model.emoji.UserEmojiConfigRepository
import net.pantasystem.milktea.model.instance.Meta
import net.pantasystem.milktea.model.instance.MetaRepository
import net.pantasystem.milktea.model.notes.reaction.LegacyReaction
import net.pantasystem.milktea.model.notes.reaction.history.ReactionHistoryCount
import net.pantasystem.milktea.model.notes.reaction.history.ReactionHistoryRepository
import net.pantasystem.milktea.note.R
import javax.inject.Inject

@HiltViewModel
class ReactionChoicesViewModel @Inject constructor(
    accountStore: AccountStore,
    private val metaRepository: MetaRepository,
    private val reactionHistoryDao: ReactionHistoryRepository,
    private val userEmojiConfigRepository: UserEmojiConfigRepository,
) : ViewModel() {


    @OptIn(ExperimentalCoroutinesApi::class)
    private val meta = accountStore.observeCurrentAccount.filterNotNull().flatMapLatest { ac ->
        metaRepository.observe(ac.normalizedInstanceDomain)
    }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val reactionCount =
        accountStore.observeCurrentAccount.filterNotNull().flatMapLatest { ac ->
            reactionHistoryDao.observeSumReactions(ac.normalizedInstanceDomain)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userSetting =
        accountStore.observeCurrentAccount.filterNotNull().flatMapLatest { ac ->
            userEmojiConfigRepository.observeByInstanceDomain(ac.normalizedInstanceDomain)
        }

    val searchWord = MutableStateFlow("")


    // 検索時の候補
    val uiState =
        combine(
            searchWord,
            meta,
            accountStore.observeCurrentAccount,
            reactionCount,
            userSetting
        ) { word, meta, ac, counts, settings ->
            ReactionSelectionUiState(
                keyword = word,
                account = ac,
                meta = meta,
                reactionHistoryCounts = counts,
                userSettingReactions = settings,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ReactionSelectionUiState("",null, null, emptyList(), emptyList())
        )

    val tabLabels = uiState.map { uiState ->
        uiState.segments.map {
            it.label
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

}

data class ReactionSelectionUiState(
    val keyword: String,
    val account: Account?,
    val meta: Meta?,
    val reactionHistoryCounts: List<ReactionHistoryCount>,
    val userSettingReactions: List<UserEmojiConfig>,
) {

    val isSearchMode = keyword.isNotBlank()

    private val frequencyUsedReactionsV2: List<EmojiType> by lazy {
        reactionHistoryCounts.map {
            it.reaction
        }.mapNotNull { reaction ->
            EmojiType.from(meta?.emojis, reaction)
        }.distinct()
    }

    val all: List<EmojiType> by lazy {
        LegacyReaction.defaultReaction.map {
            EmojiType.Legacy(it)
        } + (meta?.emojis?.map {
            EmojiType.CustomEmoji(it)
        } ?: emptyList())
    }


    private val userSettingEmojis: List<EmojiType> by lazy {
        userSettingReactions.mapNotNull { setting ->
            EmojiType.from(meta?.emojis, setting.reaction)
        }.ifEmpty {
            LegacyReaction.defaultReaction.map {
                EmojiType.Legacy(it)
            }
        }
    }

    private val otherEmojis = meta?.emojis?.filter {
        it.category == null
    }?.map {
        EmojiType.CustomEmoji(it)
    }

    private fun getCategoryBy(category: String): List<EmojiType> {
        return meta?.emojis?.filter {
            it.category == category

        }?.map {
            EmojiType.CustomEmoji(it)
        } ?: emptyList()
    }

    private val categories = meta?.emojis?.filterNot {
        it.category.isNullOrBlank()
    }?.mapNotNull {
        it.category
    }?.distinct() ?: emptyList()

    val segments = listOfNotNull(
        SegmentType.UserCustom(userSettingEmojis),
        SegmentType.OftenUse(frequencyUsedReactionsV2),
        otherEmojis?.let {
            SegmentType.OtherCategory(it)
        }
    ) + categories.map {
        SegmentType.Category(
            it,
            getCategoryBy(it)
        )
    }

    val searchFilteredEmojis = meta?.emojis?.filterEmojiBy(keyword)?.map {
        EmojiType.CustomEmoji(it)
    } ?: emptyList()
}

sealed interface SegmentType {
    val label: StringSource
    val emojis: List<EmojiType>
    data class Category(val name: String, override val emojis: List<EmojiType>) : SegmentType {
        override val label: StringSource
            get() = StringSource.invoke(name)
    }
    data class UserCustom(override val emojis: List<EmojiType>) : SegmentType {
        override val label: StringSource
            get() = StringSource.invoke(R.string.user)
    }
    data class OftenUse(override val emojis: List<EmojiType>) : SegmentType {
        override val label: StringSource
            get() = StringSource.invoke(R.string.often_use)
    }
    data class OtherCategory(override val emojis: List<EmojiType>) : SegmentType {
        override val label: StringSource
            get() = StringSource.invoke(R.string.other)
    }
}

sealed interface EmojiType {
    data class Legacy(val type: String) : EmojiType
    data class CustomEmoji(val emoji: Emoji) : EmojiType
    data class UtfEmoji(val code: String) : EmojiType
    companion object
}

fun EmojiType.toTextReaction(): String {
    return when (val type = this) {
        is EmojiType.CustomEmoji -> {
            ":${type.emoji.name}:"
        }
        is EmojiType.Legacy -> {
            type.type
        }
        is EmojiType.UtfEmoji -> {
            type.code
        }
    }
}


fun EmojiType.Companion.from(emojis: List<Emoji>?, reaction: String): EmojiType? {
    return if (reaction.codePointCount(0, reaction.length) == 1) {
        EmojiType.UtfEmoji(reaction)
    } else if (reaction.startsWith(":") && reaction.endsWith(":") && reaction.contains(
            "@"
        )
    ) {
        val nameOnly = reaction.replace(":", "")
        (emojis?.firstOrNull {
            it.name == nameOnly
        } ?: (nameOnly.split("@")[0]).let { name ->
            emojis?.firstOrNull {
                it.name == name
            }
        })?.let {
            EmojiType.CustomEmoji(it)
        }
    } else if (LegacyReaction.reactionMap[reaction] != null) {
        EmojiType.Legacy(reaction)
    } else {
        emojis?.firstOrNull {
            it.name == reaction.replace(":", "")
        }?.let {
            EmojiType.CustomEmoji(it)
        }
    }
}

fun List<Emoji>.filterEmojiBy(word: String): List<Emoji> {
    val w = word.replace(":", "")
    return filter { emoji ->
        emoji.name.contains(w)
                || emoji.aliases?.any { alias ->
            alias.startsWith(w)
        } ?: false
    }
}