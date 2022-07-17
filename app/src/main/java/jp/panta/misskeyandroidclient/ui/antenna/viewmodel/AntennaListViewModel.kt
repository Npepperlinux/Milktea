package jp.panta.misskeyandroidclient.ui.antenna.viewmodel

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.panta.misskeyandroidclient.util.eventbus.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import net.pantasystem.milktea.api.misskey.v12.MisskeyAPIV12
import net.pantasystem.milktea.api.misskey.v12.antenna.AntennaQuery
import net.pantasystem.milktea.common.Encryption
import net.pantasystem.milktea.common.throwIfHasError
import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.account.AccountStore
import net.pantasystem.milktea.model.account.page.Pageable
import net.pantasystem.milktea.model.account.page.PageableTemplate
import net.pantasystem.milktea.model.antenna.Antenna
import javax.inject.Inject

@HiltViewModel
class AntennaListViewModel @Inject constructor(
    val accountStore: AccountStore,
    val accountRepository: AccountRepository,
    val misskeyAPIProvider: MisskeyAPIProvider,
    val encryption: Encryption
) : ViewModel() {



    val antennas = MediatorLiveData<List<Antenna>>()

    val editAntennaEvent = EventBus<Antenna>()

    val confirmDeletionAntennaEvent = EventBus<Antenna>()

    private val openAntennasTimelineEvent = EventBus<Antenna>()

    val isLoading = MutableLiveData(false)
    private var mIsLoading: Boolean = false
        set(value) {
            field = value
            isLoading.postValue(value)
        }

    private val mPagedAntennaIds = MutableLiveData<Set<Antenna.Id>>()
    val pagedAntennaIds: LiveData<Set<Antenna.Id>> = mPagedAntennaIds


    init {

        accountStore.observeCurrentAccount.onEach {
            loadInit()

            mPagedAntennaIds.postValue(
                it?.pages?.mapNotNull { page ->
                    val pageable = page.pageable()
                    if (pageable is Pageable.Antenna) {
                        it.accountId.let { accountId ->
                            Antenna.Id(accountId, pageable.antennaId)

                        }
                    } else {
                        null
                    }
                }?.toSet() ?: emptySet()
            )
        }.launchIn(viewModelScope + Dispatchers.IO)


    }

    private val deleteResultEvent = EventBus<Boolean>()

    fun loadInit() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val account = accountRepository.getCurrentAccount().getOrThrow()
                val res =
                    (misskeyAPIProvider.get(account) as MisskeyAPIV12).getAntennas(
                        AntennaQuery(
                            i = account.getI(encryption),
                            limit = null,
                            antennaId = null
                        )
                    ).body()
                        ?: throw IllegalStateException("アンテナの取得に失敗しました")
                res.map { dto ->
                    dto.toEntity(account)
                }
            }.onSuccess {
                antennas.postValue(it)
            }
            mIsLoading = false
        }

    }

    fun toggleTab(antenna: Antenna?) {
        antenna ?: return
        val paged = accountStore.currentAccount?.pages?.firstOrNull {

            it.pageParams.antennaId == antenna.id.antennaId
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (paged == null) {
                accountStore.addPage(
                    PageableTemplate(accountStore.currentAccount!!)
                        .antenna(
                        antenna
                    )
                )
            } else {
                accountStore.removePage(paged)
            }
        }

    }

    fun confirmDeletionAntenna(antenna: Antenna?) {
        antenna ?: return
        confirmDeletionAntennaEvent.event = antenna
    }

    fun editAntenna(antenna: Antenna?) {
        antenna ?: return
        editAntennaEvent.event = antenna
    }

    fun openAntennasTimeline(antenna: Antenna?) {
        openAntennasTimelineEvent.event = antenna
    }

    fun deleteAntenna(antenna: Antenna) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val i = accountStore.currentAccount?.getI(encryption)
                    ?: return@launch
                getMisskeyAPI()?.deleteAntenna(
                    AntennaQuery(
                        i = i,
                        antennaId = antenna.id.antennaId,
                        limit = null
                    )
                )?.throwIfHasError()
            }.onSuccess {
                loadInit()
            }.onFailure {
                deleteResultEvent.event = false

            }

        }


    }

    private fun getMisskeyAPI(): MisskeyAPIV12? {
        return misskeyAPIProvider
            .get(accountStore.currentAccount!!) as? MisskeyAPIV12
    }
}