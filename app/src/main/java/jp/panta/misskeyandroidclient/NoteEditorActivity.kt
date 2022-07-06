package jp.panta.misskeyandroidclient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import jp.panta.misskeyandroidclient.databinding.ActivityNoteEditorBinding
import jp.panta.misskeyandroidclient.databinding.ViewNoteEditorToolbarBinding
import jp.panta.misskeyandroidclient.ui.account.AccountSwitchingDialog
import jp.panta.misskeyandroidclient.ui.account.viewmodel.AccountViewModel
import jp.panta.misskeyandroidclient.ui.confirm.ConfirmDialog
import jp.panta.misskeyandroidclient.ui.emojis.CustomEmojiPickerDialog
import jp.panta.misskeyandroidclient.ui.emojis.viewmodel.EmojiSelection
import jp.panta.misskeyandroidclient.ui.notes.view.editor.*
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.editor.NoteEditorViewModel
import jp.panta.misskeyandroidclient.ui.text.CustomEmojiCompleteAdapter
import jp.panta.misskeyandroidclient.ui.text.CustomEmojiTokenizer
import jp.panta.misskeyandroidclient.ui.users.UserChipListAdapter
import jp.panta.misskeyandroidclient.ui.users.viewmodel.selectable.SelectedUserViewModel
import jp.panta.misskeyandroidclient.util.listview.applyFlexBoxLayout
import jp.panta.misskeyandroidclient.viewmodel.confirm.ConfirmViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import net.pantasystem.milktea.common_compose.FilePreviewTarget
import net.pantasystem.milktea.common_navigation.AuthorizationNavigation
import net.pantasystem.milktea.common_navigation.EXTRA_INT_SELECTABLE_FILE_MAX_SIZE
import net.pantasystem.milktea.common_navigation.EXTRA_SELECTED_FILE_PROPERTY_IDS
import net.pantasystem.milktea.data.infrastructure.confirm.ConfirmCommand
import net.pantasystem.milktea.data.infrastructure.confirm.ResultType
import net.pantasystem.milktea.data.infrastructure.settings.SettingStore
import net.pantasystem.milktea.drive.DriveActivity
import net.pantasystem.milktea.drive.toAppFile
import net.pantasystem.milktea.model.account.AccountStore
import net.pantasystem.milktea.model.channel.Channel
import net.pantasystem.milktea.model.drive.DriveFileRepository
import net.pantasystem.milktea.model.drive.FileProperty
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.emoji.Emoji
import net.pantasystem.milktea.model.file.toFile
import net.pantasystem.milktea.model.instance.MetaRepository
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.user.User
import javax.inject.Inject


@AndroidEntryPoint
class NoteEditorActivity : AppCompatActivity(), EmojiSelection {

    companion object {
        private const val EXTRA_REPLY_TO_NOTE_ID =
            "jp.panta.misskeyandroidclient.EXTRA_REPLY_TO_NOTE_ID"
        private const val EXTRA_QUOTE_TO_NOTE_ID =
            "jp.panta.misskeyandroidclient.EXTRA_QUOTE_TO_NOTE_ID"
        private const val EXTRA_DRAFT_NOTE_ID = "jp.panta.misskeyandroidclient.EXTRA_DRAFT_NOTE"
        private const val EXTRA_ACCOUNT_ID = "jp.panta.misskeyandroidclient.EXTRA_ACCOUNT_ID"

        private const val CONFIRM_SAVE_AS_DRAFT_OR_DELETE = "confirm_save_as_draft_or_delete"
        private const val EXTRA_MENTIONS = "EXTRA_MENTIONS"
        private const val EXTRA_CHANNEL_ID = "EXTRA_CHANNEL_ID"

        fun newBundle(
            context: Context,
            replyTo: Note.Id? = null,
            quoteTo: Note.Id? = null,
            draftNoteId: Long? = null,
            mentions: List<String>? = null,
            channelId: Channel.Id? = null,
        ): Intent {
            return Intent(context, NoteEditorActivity::class.java).apply {
                replyTo?.let {
                    putExtra(EXTRA_REPLY_TO_NOTE_ID, replyTo.noteId)
                    putExtra(EXTRA_ACCOUNT_ID, replyTo.accountId)
                }

                quoteTo?.let {
                    putExtra(EXTRA_QUOTE_TO_NOTE_ID, quoteTo.noteId)
                    putExtra(EXTRA_ACCOUNT_ID, quoteTo.accountId)
                }


                draftNoteId?.let {
                    putExtra(EXTRA_DRAFT_NOTE_ID, it)
                }

                mentions?.let {
                    putExtra(EXTRA_MENTIONS, it.toTypedArray())
                }

                channelId?.let {
                    putExtra(EXTRA_CHANNEL_ID, it.channelId)
                    putExtra(EXTRA_ACCOUNT_ID, it.accountId)
                }

            }
        }
    }

    val mViewModel: NoteEditorViewModel by viewModels()

    private lateinit var mBinding: ActivityNoteEditorBinding

    private lateinit var mConfirmViewModel: ConfirmViewModel

    @Inject
    lateinit var accountStore: AccountStore

    val accountViewModel: AccountViewModel by viewModels()

    @Inject
    lateinit var driveFileRepository: DriveFileRepository

    @Inject
    lateinit var filePropertyDataSource: FilePropertyDataSource

    @Inject
    lateinit var metaRepository: MetaRepository

    @Inject
    lateinit var settingStore: SettingStore

    @Inject
    lateinit var authorizationNavigation: AuthorizationNavigation

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme()
        setContentView(R.layout.activity_note_editor)
        val binding = DataBindingUtil.setContentView<ActivityNoteEditorBinding>(
            this,
            R.layout.activity_note_editor
        )
        mBinding = binding

        binding.lifecycleOwner = this
        binding.viewModel = mViewModel

        setSupportActionBar(mBinding.noteEditorToolbar)


        var text: String? = null
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            text = intent.getStringExtra(Intent.EXTRA_TEXT)
        }


        val toolbarBase = getToolbarBase()
        val noteEditorToolbar = DataBindingUtil.inflate<ViewNoteEditorToolbarBinding>(
            LayoutInflater.from(this),
            R.layout.view_note_editor_toolbar,
            toolbarBase,
            true
        )


        val accountId: Long? =
            if (intent.getLongExtra(EXTRA_ACCOUNT_ID, -1) == -1L) null else intent.getLongExtra(
                EXTRA_ACCOUNT_ID,
                -1
            )
        val replyToNoteId = intent.getStringExtra(EXTRA_REPLY_TO_NOTE_ID)?.let {
            requireNotNull(accountId)
            Note.Id(accountId, it)
        }
        val quoteToNoteId = intent.getStringExtra(EXTRA_QUOTE_TO_NOTE_ID)?.let {
            requireNotNull(accountId)
            Note.Id(accountId, it)
        }

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)?.let {
            requireNotNull(accountId)
            Channel.Id(accountId, it)
        }

        val draftNoteId = intent.getLongExtra(EXTRA_DRAFT_NOTE_ID, - 1).let {
            if (it == -1L) null else it
        }


        noteEditorToolbar.actionUpButton.setOnClickListener {
            finishOrConfirmSaveAsDraftOrDelete()
        }

        mConfirmViewModel = ViewModelProvider(this)[ConfirmViewModel::class.java]

        val userChipAdapter = UserChipListAdapter(this)
        binding.addressUsersView.adapter = userChipAdapter
        binding.addressUsersView.applyFlexBoxLayout(this)


        binding.accountViewModel = accountViewModel
        noteEditorToolbar.accountViewModel = accountViewModel
        accountViewModel.switchAccount.observe(this) {
            AccountSwitchingDialog().show(supportFragmentManager, "tag")
        }
        accountViewModel.showProfile.observe(this) {
            val intent =
                UserDetailActivity.newInstance(this, userId = User.Id(it.accountId, it.remoteId))

            intent.putActivity(Activities.ACTIVITY_IN_APP)


            startActivity(intent)
        }

        accountStore.observeCurrentAccount.filterNotNull().flatMapLatest {
            metaRepository.observe(it.instanceDomain)
        }.mapNotNull {
            it?.emojis
        }.distinctUntilChanged().onEach { emojis ->
            binding.inputMain.setAdapter(
                CustomEmojiCompleteAdapter(
                    emojis,
                    this
                )
            )
            binding.inputMain.setTokenizer(CustomEmojiTokenizer())

            binding.cw.setAdapter(
                CustomEmojiCompleteAdapter(
                    emojis,
                    this
                )
            )
            binding.cw.setTokenizer(CustomEmojiTokenizer())
        }.launchIn(lifecycleScope)

        if (!text.isNullOrBlank()) {
            mViewModel.changeText(text)
        }
        mViewModel.setReplyTo(replyToNoteId)
        mViewModel.setRenoteTo(quoteToNoteId)
        mViewModel.setChannelId(channelId)
        if (draftNoteId != null) {
            mViewModel.setDraftNoteId(draftNoteId)
        }
        binding.viewModel = mViewModel
        noteEditorToolbar.viewModel = mViewModel
        noteEditorToolbar.lifecycleOwner = this

        binding.filePreview.apply {
            setContent {
                MdcTheme {
                    NoteFilePreview(
                        noteEditorViewModel = mViewModel,
                        fileRepository = driveFileRepository,
                        dataSource = filePropertyDataSource,
                        onShow = {
                            val file = when (it) {
                                is FilePreviewTarget.Remote -> {
                                    it.fileProperty.toFile()
                                }
                                is FilePreviewTarget.Local -> {
                                    it.file.toFile()
                                }
                            }
                            val intent = net.pantasystem.milktea.media.MediaActivity.newInstance(
                                this@NoteEditorActivity,
                                listOf(file),
                                0
                            )
                            this@NoteEditorActivity.startActivity(intent)
                        }
                    )
                }

            }
        }

        lifecycleScope.launchWhenResumed {
            mViewModel.poll.distinctUntilChangedBy {
                it == null
            }.collect { poll ->
                if (poll == null) {
                    removePollFragment()
                } else {
                    setPollFragment()
                }
            }
        }

        mBinding.cw.addTextChangedListener { e ->
            mViewModel.setCw(e?.toString())
        }

        mBinding.inputMain.addTextChangedListener { e ->
            Log.d("NoteEditorActivity", "text changed:$e")
            mViewModel.setText((e?.toString() ?: ""))
        }

        mViewModel.state.onEach {
            if (it.textCursorPos != null && it.text != null) {
                mBinding.inputMain.setText(it.text ?: "")
                mBinding.inputMain.setSelection(it.textCursorPos ?: 0)
            }
        }.launchIn(lifecycleScope)

        mViewModel.isPost.observe(this) {
            if (it) {
                noteEditorToolbar.postButton.isEnabled = false
                finish()
            }
        }

        mViewModel.showVisibilitySelectionEvent.observe(this) {
            Log.d("NoteEditorActivity", "公開範囲を設定しようとしています")
            val dialog = VisibilitySelectionDialog()
            dialog.show(supportFragmentManager, "NoteEditor")
        }

        lifecycleScope.launchWhenResumed {
            mViewModel.address.collect {
                userChipAdapter.submitList(it)
            }
        }

        mViewModel.showPollTimePicker.observe(this) {
            PollTimePickerDialog().show(supportFragmentManager, "TimePicker")
        }

        mViewModel.showPollDatePicker.observe(this) {
            PollDatePickerDialog().show(supportFragmentManager, "DatePicker")
        }



        mBinding.selectFileFromDrive.setOnClickListener {
            showDriveFileSelector()
        }

        mBinding.selectFileFromLocal.setOnClickListener {
            showFileManager()
        }

        binding.addAddress.setOnClickListener {
            startSearchAndSelectUser()
        }

        binding.mentionButton.setOnClickListener {
            startMentionToSearchAndSelectUser()
        }

        binding.showEmojisButton.setOnClickListener {
            CustomEmojiPickerDialog().show(supportFragmentManager, "Editor")
        }

        binding.reservationAtPickDateButton.setOnClickListener {
            ReservationPostDatePickerDialog().show(supportFragmentManager, "Pick date")
        }

        binding.reservationAtPickTimeButton.setOnClickListener {
            ReservationPostTimePickerDialog().show(supportFragmentManager, "Pick time")
        }


        lifecycleScope.launchWhenStarted {
            accountStore.state.collect {
                if (it.isUnauthorized) {
                    finish()
                    startActivity(
                        authorizationNavigation.newIntent(Unit)
                    )
                }
            }
        }


        mConfirmViewModel.confirmedEvent.observe(this) {
            when (it.eventType) {
                CONFIRM_SAVE_AS_DRAFT_OR_DELETE -> {
                    if (it.resultType == ResultType.POSITIVE) {
                        mViewModel.saveDraft()
                    } else {
                        finish()
                    }
                }
            }
        }

        mConfirmViewModel.confirmEvent.observe(this) {
            ConfirmDialog().show(supportFragmentManager, "confirm")
        }

        mViewModel.isSaveNoteAsDraft.observe(this) {
            runOnUiThread {
                if (it == null) {
                    Toast.makeText(this, "下書きに失敗しました", Toast.LENGTH_LONG).show()
                } else {
                    upTo()
                }
            }

        }

        intent.getStringArrayExtra(EXTRA_MENTIONS)?.let {
            Log.d("NoteEditorActivity", "mentions:${it.toList()}")
            addMentionUserNames(it.toList())
        }

        binding.inputMain.requestFocus()
    }

    override fun onSelect(emoji: Emoji) {
        val pos = mBinding.inputMain.selectionEnd
        mViewModel.addEmoji(emoji, pos).let { newPos ->
            mBinding.inputMain.setText(mViewModel.text.value ?: "")
            mBinding.inputMain.setSelection(newPos)
            Log.d("NoteEditorActivity", "入力されたデータ:${mBinding.inputMain.text}")
        }
    }

    override fun onSelect(emoji: String) {
        val pos = mBinding.inputMain.selectionEnd
        mViewModel.addEmoji(emoji, pos).let { newPos ->
            mBinding.inputMain.setText(mViewModel.text.value ?: "")
            mBinding.inputMain.setSelection(newPos)
        }
    }

    private fun setPollFragment() {
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.edit_poll, PollEditorFragment(), "pollFragment")
        ft.commit()
    }

    private fun removePollFragment() {
        val fragment = supportFragmentManager.findFragmentByTag("pollFragment")
        if (fragment != null) {
            val ft = supportFragmentManager.beginTransaction()
            ft.remove(fragment)
            ft.commit()
        }
    }

    /**
     * 設定をもとにToolbarを表示するベースとなるViewGroupを非表示・表示＆取得をしている
     */
    private fun getToolbarBase(): ViewGroup {
        return if (settingStore.isPostButtonAtTheBottom) {
            mBinding.noteEditorToolbar.visibility = View.GONE
            mBinding.bottomToolbarBase.visibility = View.VISIBLE
            mBinding.bottomToolbarBase
        } else {
            mBinding.bottomToolbarBase.visibility = View.GONE
            mBinding.bottomToolbarBase.visibility = View.VISIBLE
            mBinding.noteEditorToolbar
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
        val selectedSize = mViewModel.state.value.totalFilesCount
        //Directoryは既に選択済みのファイルの数も含めてしまうので選択済みの数も合わせる
        val selectableMaxSize = mViewModel.maxFileCount.value - selectedSize
        val intent = Intent(this, DriveActivity::class.java)
            .putExtra(EXTRA_INT_SELECTABLE_FILE_MAX_SIZE, selectableMaxSize)
            .putExtra(EXTRA_ACCOUNT_ID, accountStore.currentAccount?.accountId)
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        openDriveActivityResult.launch(intent)
    }

    private fun checkPermission(): Boolean {
        val permissionCheck =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (!checkPermission()) {
            requestReadStoragePermissionResult.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    private fun startSearchAndSelectUser() {
        val selectedUserIds = mViewModel.address.value.mapNotNull {
            it.userId
        }

        val intent = SearchAndSelectUserActivity.newIntent(this, selectedUserIds = selectedUserIds)

        selectUserResult.launch(intent)
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    private fun startMentionToSearchAndSelectUser() {
        val intent = Intent(this, SearchAndSelectUserActivity::class.java)
        selectMentionToUserResult.launch(intent)
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    private fun finishOrConfirmSaveAsDraftOrDelete() {
        if (mViewModel.canSaveDraft()) {
            mConfirmViewModel.confirmEvent.event = ConfirmCommand(
                getString(R.string.save_draft),
                getString(R.string.save_the_note_as_a_draft),
                eventType = CONFIRM_SAVE_AS_DRAFT_OR_DELETE,
                args = "",
                positiveButtonText = getString(R.string.save),
                negativeButtonText = getString(R.string.delete)

            )
        } else {
            upTo()
        }
    }

    private fun upTo() {
        if (intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrEmpty()) {
            finish()
        } else {
            val upIntent = Intent(this, MainActivity::class.java)
            upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (shouldUpRecreateTask(upIntent)) {
                TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(upIntent)
                    .startActivities()
                finish()
            } else {
                navigateUpTo(upIntent)
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onBackPressed() {

        finishOrConfirmSaveAsDraftOrDelete()
    }

    private val openDriveActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val ids =
                (result?.data?.getSerializableExtra(EXTRA_SELECTED_FILE_PROPERTY_IDS) as List<*>?)?.mapNotNull {
                    it as? FileProperty.Id
                }
            Log.d("NoteEditorActivity", "result:${ids}")
            val size = mViewModel.fileTotal()

            if (ids != null && ids.isNotEmpty() && size + ids.size <= mViewModel.maxFileCount.value) {
                mViewModel.addFilePropertyFromIds(ids)
            }
        }

    private val openLocalStorageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            val uri = result?.data?.data
            if (uri != null) {
                val size = mViewModel.fileTotal()

                if (size > mViewModel.maxFileCount.value) {
                    Log.d("NoteEditorActivity", "失敗しました")
                } else {
                    mViewModel.add(uri.toAppFile(this))
                    Log.d("NoteEditorActivity", "成功しました")
                }

            }
        }

    private val requestReadStoragePermissionResult =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                showFileManager()
            } else {
                Toast.makeText(this, "ストレージへのアクセスを許可しないとファイルを読み込めないぽよ", Toast.LENGTH_LONG).show()
            }
        }

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val selectUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val changed =
                    result.data?.getSerializableExtra(SearchAndSelectUserActivity.EXTRA_SELECTED_USER_CHANGED_DIFF) as? SelectedUserViewModel.ChangedDiffResult
                if (changed != null) {
                    mViewModel.setAddress(changed.added, changed.removed)
                }
            }
        }

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val selectMentionToUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val changed =
                    result.data?.getSerializableExtra(SearchAndSelectUserActivity.EXTRA_SELECTED_USER_CHANGED_DIFF) as? SelectedUserViewModel.ChangedDiffResult

                if (changed != null) {
                    addMentionUserNames(changed.selectedUserNames)
                }

            }
        }

    private fun addMentionUserNames(userNames: List<String>) {
        val pos = mBinding.inputMain.selectionEnd
        mViewModel.addMentionUserNames(userNames, pos).let { newPos ->
            Log.d(
                "NoteEditorActivity",
                "text:${mViewModel.state.value.text}, stateText:${mViewModel.state.value.text}"
            )
            mBinding.inputMain.setText(mViewModel.state.value.text ?: "")
            mBinding.inputMain.setSelection(newPos)
        }
    }


}
