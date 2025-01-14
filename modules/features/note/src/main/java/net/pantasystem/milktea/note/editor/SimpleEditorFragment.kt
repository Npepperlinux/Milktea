package net.pantasystem.milktea.note.editor

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import com.wada811.databinding.dataBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.common_android.ui.listview.applyFlexBoxLayout
import net.pantasystem.milktea.common_android.ui.text.CustomEmojiTokenizer
import net.pantasystem.milktea.common_android_ui.account.viewmodel.AccountViewModel
import net.pantasystem.milktea.common_compose.MilkteaStyleConfigApplyAndTheme
import net.pantasystem.milktea.common_navigation.*
import net.pantasystem.milktea.common_viewmodel.CurrentPageType
import net.pantasystem.milktea.common_viewmodel.CurrentPageableTimelineViewModel
import net.pantasystem.milktea.model.drive.DriveFileRepository
import net.pantasystem.milktea.model.drive.FileProperty
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.emoji.CustomEmojiRepository
import net.pantasystem.milktea.model.note.draft.DraftNoteService
import net.pantasystem.milktea.model.setting.LocalConfigRepository
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.note.NoteEditorActivity
import net.pantasystem.milktea.note.R
import net.pantasystem.milktea.note.databinding.FragmentSimpleEditorBinding
import net.pantasystem.milktea.note.editor.file.EditFileCaptionDialog
import net.pantasystem.milktea.note.editor.file.EditFileNameDialog
import net.pantasystem.milktea.note.editor.poll.PollDatePickerDialog
import net.pantasystem.milktea.note.editor.poll.PollEditorFragment
import net.pantasystem.milktea.note.editor.poll.PollTimePickerDialog
import net.pantasystem.milktea.note.editor.viewmodel.NoteEditorViewModel
import net.pantasystem.milktea.note.editor.viewmodel.toCreateNote
import net.pantasystem.milktea.note.editor.visibility.VisibilitySelectionDialogV2
import net.pantasystem.milktea.note.emojis.CustomEmojiPickerDialog
import javax.inject.Inject


interface SimpleEditor {

    val isShowEditorMenu: MutableLiveData<Boolean>
    fun goToNormalEditor()

    fun closeMenu()
    fun openMenu()
}

@AndroidEntryPoint
class SimpleEditorFragment : Fragment(R.layout.fragment_simple_editor), SimpleEditor {


    val accountViewModel: AccountViewModel by activityViewModels()

    val mViewModel: NoteEditorViewModel by activityViewModels()
    private val currentPageableTimelineViewModel: CurrentPageableTimelineViewModel by activityViewModels()

    private val mBinding: FragmentSimpleEditorBinding by dataBinding()

    override val isShowEditorMenu: MutableLiveData<Boolean> = MutableLiveData(false)

    @Inject
    lateinit var accountStore: AccountStore

    @Inject
    internal lateinit var customEmojiRepository: CustomEmojiRepository


    @Inject
    internal lateinit var filePropertyDataSource: FilePropertyDataSource

    @Inject
    internal lateinit var fileRepository: DriveFileRepository

    @Inject
    internal lateinit var draftNoteService: DraftNoteService

    @Inject
    internal lateinit var driveNavigation: DriveNavigation

    @Inject
    internal lateinit var mediaNavigation: MediaNavigation

    @Inject
    internal lateinit var userDetailNavigation: UserDetailNavigation

    @Inject
    internal lateinit var searchAndSelectUserNavigation: SearchAndSelectUserNavigation

    @Inject
    internal lateinit var configRepository: LocalConfigRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        mBinding.simpleEditor = this

        mBinding.lifecycleOwner = viewLifecycleOwner
        mBinding.noteEditorViewModel = mViewModel

        val userChipAdapter =
            net.pantasystem.milktea.common_android_ui.user.UserChipListAdapter(viewLifecycleOwner)
        mBinding.addressUsersView.adapter = userChipAdapter
        mBinding.addressUsersView.applyFlexBoxLayout(requireContext())


        mBinding.accountViewModel = accountViewModel

        accountViewModel.showProfileEvent.onEach {
            val intent = userDetailNavigation.newIntent(
                UserDetailNavigationArgs.UserId(
                    User.Id(it.accountId, it.remoteId)
                )
            )

            startActivity(intent)
        }.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED).launchIn(lifecycleScope)

        mViewModel.currentAccount.filterNotNull().distinctUntilChanged().onEach { account ->
            mBinding.inputMainText.setAdapter(
                CustomEmojiCompleteAdapter(
                    account,
                    requireContext(),
                    customEmojiRepository,
                )
            )
            mBinding.inputMainText.setTokenizer(CustomEmojiTokenizer())

            mBinding.inputCw.setAdapter(
                CustomEmojiCompleteAdapter(
                    account,
                    requireContext(),
                    customEmojiRepository,
                )
            )
            mBinding.inputCw.setTokenizer(CustomEmojiTokenizer())
        }.launchIn(lifecycleScope)


        val viewModel = mViewModel
        mBinding.noteEditorViewModel = viewModel
        mBinding.filePreview.apply {
            setContent {
                MilkteaStyleConfigApplyAndTheme(configRepository = configRepository) {
                    NoteFilePreview(
                        noteEditorViewModel = viewModel,
                        onShow = {
                            val intent = mediaNavigation.newIntent(MediaNavigationArgs.Files(
                                listOf(it),
                                0
                            ))
                            requireActivity().startActivity(intent)
                        },
                        onEditFileCaptionSelectionClicked = {
                            EditFileCaptionDialog.newInstance(it.file, it.comment).show(childFragmentManager, EditFileCaptionDialog.FRAGMENT_TAG)
                        },
                        onEditFileNameSelectionClicked = {
                            EditFileNameDialog.newInstance(it.file, it.name).show(childFragmentManager, EditFileNameDialog.FRAGMENT_TAG)
                        }
                    )
                }
            }
        }



        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect { uiState ->
                    val poll = uiState.poll
                    if (poll == null) {
                        removePollFragment()
                    } else {
                        setPollFragment()
                    }
                }
            }
        }

        viewModel.isPost.onEach {
            viewModel.clear()
        }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        mBinding.noteVisibility.setOnClickListener {
            val dialog = VisibilitySelectionDialogV2()
            dialog.show(childFragmentManager, VisibilitySelectionDialogV2.FRAGMENT_TAG)
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.address.collect {
                    userChipAdapter.submitList(it)
                }
            }
        }

        viewModel.showPollDatePicker.onEach {
            PollTimePickerDialog().show(childFragmentManager, PollTimePickerDialog.FRAGMENT_TAG)
        }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.showPollDatePicker.onEach {
            PollDatePickerDialog().show(childFragmentManager, PollDatePickerDialog.FRAGMENT_TAG)
        }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
            .launchIn(viewLifecycleOwner.lifecycleScope)


        mBinding.inputCw.addTextChangedListener { e ->
            viewModel.setCw((e?.toString() ?: ""))
        }

        mBinding.inputMainText.addTextChangedListener { e ->
            viewModel.setText((e?.toString() ?: ""))
        }




        mBinding.selectFileFromDrive.setOnClickListener {
            showDriveFileSelector()
        }

        mBinding.selectFileFromLocal.setOnClickListener {
            showFileManager()
        }

        mBinding.addAddress.setOnClickListener {
            startSearchAndSelectUser()
        }

        mBinding.mentionButton.setOnClickListener {
            startMentionToSearchAndSelectUser()
        }

        mBinding.showEmojisButton.setOnClickListener {
            CustomEmojiPickerDialog.newInstance(null).show(childFragmentManager, CustomEmojiPickerDialog.FRAGMENT_TAG)
        }


        mBinding.postButton.setOnClickListener {
            viewModel.post()
        }


        viewLifecycleOwner.lifecycleScope.launch {
            accountViewModel.currentAccount.collect {
                viewModel.setAccountId(it?.accountId)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            currentPageableTimelineViewModel.currentType.collect {
                when(it) {
                    CurrentPageType.Account -> Unit
                    is CurrentPageType.Page -> {
                        viewModel.setAccountId(it.accountId)
                    }
                }
            }
        }
    }


    private fun setPollFragment() {
        val ft = childFragmentManager.beginTransaction()
        ft.replace(R.id.edit_poll, PollEditorFragment(), "pollFragment")
        ft.commit()
    }

    private fun removePollFragment() {
        val fragment = childFragmentManager.findFragmentByTag("pollFragment")
        if (fragment != null) {
            val ft = childFragmentManager.beginTransaction()
            ft.remove(fragment)
            ft.commit()
        }
    }

    private fun showFileManager() {
        if (checkPermission()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            openLocalStorageResult.launch(intent)
        } else {
            requestPermission()
        }

    }

    private fun showDriveFileSelector() {
        val selectedSize = mViewModel.uiState.value.totalFilesCount

        //Directoryは既に選択済みのファイルの数も含めてしまうので選択済みの数も合わせる
        val selectableMaxSize = 4 - selectedSize
        Log.d("", "選択済みのサイズ:$selectedSize")
        val intent = driveNavigation.newIntent(DriveNavigationArgs(
            selectableFileMaxSize = selectableMaxSize
        ))
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        registerForOpenDriveActivityResult.launch(intent)
    }

    private fun checkPermission(): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        //val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (!checkPermission()) {
            requestReadStoragePermissionResult.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun startSearchAndSelectUser() {
        val selectedUserIds = mViewModel.address.value.mapNotNull {
            it.userId ?: it.user.value?.id
        }

        val intent = searchAndSelectUserNavigation.newIntent(
            SearchAndSelectUserNavigationArgs(selectedUserIds = selectedUserIds)
        )
        selectUserResult.launch(intent)
    }

    private fun startMentionToSearchAndSelectUser() {
        val intent = searchAndSelectUserNavigation.newIntent(SearchAndSelectUserNavigationArgs())
        selectMentionToUserResult.launch(intent)
    }

    override fun closeMenu() {
        isShowEditorMenu.value = false
    }

    override fun openMenu() {
        isShowEditorMenu.value = true
    }


    override fun goToNormalEditor() {

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            accountStore.currentAccount?.let {
                draftNoteService.save(mViewModel.uiState.value.toCreateNote(it)).onSuccess {
                    withContext(Dispatchers.Main) {
                        val intent = NoteEditorActivity.newBundle(requireContext(), draftNoteId = it.draftNoteId)
                        startActivity(intent)
                        mViewModel.clear()
                    }
                }
            }
        }

    }

    @Suppress("DEPRECATION")
    private val registerForOpenDriveActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedFilePropertyIds =
                    (result.data?.getSerializableExtra(EXTRA_SELECTED_FILE_PROPERTY_IDS) as List<*>).map {
                        it as FileProperty.Id
                    }
                mViewModel.addFilePropertyFromIds(selectedFilePropertyIds)
            }

        }

    private val openLocalStorageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            val uri = result?.data?.data
            if (uri != null) {
                mViewModel.addFile(uri)
                Log.d("NoteEditorActivity", "成功しました")

            }
        }

    private val requestReadStoragePermissionResult =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                showFileManager()
            } else {
                Toast.makeText(
                    requireContext(),
                    "ストレージへのアクセスを許可しないとファイルを読み込めないぽよ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    @Suppress("DEPRECATION")
    private val selectUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val changed =
                    result.data?.getSerializableExtra(SearchAndSelectUserNavigation.EXTRA_SELECTED_USER_CHANGED_DIFF) as? ChangedDiffResult
                if (changed != null) {
                    mViewModel.setAddress(changed.added, changed.removed)
                }
            }
        }

    @Suppress("DEPRECATION")
    private val selectMentionToUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val changed =
                    result.data?.getSerializableExtra(SearchAndSelectUserNavigation.EXTRA_SELECTED_USER_CHANGED_DIFF) as? ChangedDiffResult

                if (changed != null) {
                    val pos = mBinding.inputMainText.selectionEnd
                    mViewModel.addMentionUserNames(changed.selectedUserNames, pos).let { newPos ->
                        mBinding.inputMainText.setText(mViewModel.text.value ?: "")
                        mBinding.inputMainText.setSelection(newPos)
                    }
                }

            }
        }


}