package jp.panta.misskeyandroidclient.ui.tags

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.flexbox.*
import com.wada811.databinding.dataBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.databinding.FragmentSortedHashTagBinding
import jp.panta.misskeyandroidclient.viewmodel.tags.SortedHashTagListViewModel
import jp.panta.misskeyandroidclient.viewmodel.tags.provideViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SortedHashTagFragment : Fragment(R.layout.fragment_sorted_hash_tag) {

    companion object {
        /*const val EXTRA_SORT = "jp.panta.misskeyandroidclient.ui.tags.EXTRA_SORT"
        const val EXTRA_IS_ATTACHED_TO_USER_ONLY = "jp.panta.misskeyandroidclient.ui.tags.EXTRA_IS_ATTACHED_TO_USER_ONLY "
        const val EXTRA_IS_ATTACHED_TO_LOCAL_USER_ONLY = "jp.panta.misskeyandroidclient.ui.tags.EXTRA_IS_ATTACHED_TO_LOCAL_USER_ONLY"
        const val EXTRA_IS_ATTACHED_TO_REMOTE_USER_ONLY = "jp.panta.misskeyandroidclient.ui.tags.EXTRA_IS_ATTACHED_TO_REMOTE_USER_ONLY"*/
        const val EXTRA_HASH_TAG_CONDITION =
            "jp.panta.misskeyandroidclient.ui.tags.EXTRA_HASH_TAG_CONDITION"

        fun newInstance(
            sort: String,
            isAttachedToUserOnly: Boolean? = null,
            isAttachedToLocalUserOnly: Boolean? = null,
            isAttachedToRemoteUserOnly: Boolean? = null
        ): SortedHashTagFragment {

            return newInstance(
                SortedHashTagListViewModel.Conditions(
                    sort = sort,
                    isAttachedToUserOnly = isAttachedToUserOnly,
                    isAttachedToLocalUserOnly = isAttachedToLocalUserOnly,
                    isAttachedToRemoteUserOnly = isAttachedToRemoteUserOnly
                )
            )
        }

        fun newInstance(conditions: SortedHashTagListViewModel.Conditions): SortedHashTagFragment {
            return SortedHashTagFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_HASH_TAG_CONDITION, conditions)
                }
            }
        }
    }

    private val mBinding: FragmentSortedHashTagBinding by dataBinding()


    @Inject
    lateinit var assistedFactory: SortedHashTagListViewModel.AssistedViewModelFactory

    val viewModel: SortedHashTagListViewModel by viewModels {
        val conditions = arguments
            ?.getSerializable(EXTRA_HASH_TAG_CONDITION) as SortedHashTagListViewModel.Conditions
        SortedHashTagListViewModel.provideViewModel(assistedFactory, conditions)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = HashTagListAdapter()
        mBinding.hashTagListView.adapter = adapter

        val flexBoxLayoutManager = FlexboxLayoutManager(view.context)
        flexBoxLayoutManager.flexDirection = FlexDirection.ROW
        flexBoxLayoutManager.flexWrap = FlexWrap.WRAP
        flexBoxLayoutManager.justifyContent = JustifyContent.FLEX_START
        flexBoxLayoutManager.alignItems = AlignItems.STRETCH
        mBinding.hashTagListView.layoutManager = flexBoxLayoutManager
        viewModel.hashTags.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            mBinding.hashTagListSwipeRefresh.isRefreshing = it
        }

        mBinding.hashTagListSwipeRefresh.setOnRefreshListener {
            viewModel.load()
        }

    }
}