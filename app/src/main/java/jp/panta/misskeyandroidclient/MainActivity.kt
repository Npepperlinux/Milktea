package jp.panta.misskeyandroidclient

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.wada811.databinding.dataBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.panta.misskeyandroidclient.databinding.ActivityMainBinding
import jp.panta.misskeyandroidclient.databinding.NavHeaderMainBinding
import jp.panta.misskeyandroidclient.ui.ScrollableTop
import jp.panta.misskeyandroidclient.ui.account.AccountSwitchingDialog
import jp.panta.misskeyandroidclient.ui.account.viewmodel.AccountViewModel
import net.pantasystem.milktea.messaging.MessagingHistoryFragment
import jp.panta.misskeyandroidclient.ui.notes.view.ActionNoteHandler
import jp.panta.misskeyandroidclient.ui.notes.view.TabFragment
import jp.panta.misskeyandroidclient.ui.notes.view.editor.SimpleEditorFragment
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.NotesViewModel
import jp.panta.misskeyandroidclient.ui.notification.NotificationMentionFragment
import jp.panta.misskeyandroidclient.ui.notification.notificationMessageScope
import jp.panta.misskeyandroidclient.ui.search.SearchTopFragment
import jp.panta.misskeyandroidclient.ui.settings.activities.PageSettingActivity
import jp.panta.misskeyandroidclient.ui.strings_helper.webSocketStateMessageScope
import jp.panta.misskeyandroidclient.ui.users.ReportStateHandler
import jp.panta.misskeyandroidclient.ui.users.viewmodel.ReportViewModel
import jp.panta.misskeyandroidclient.util.BottomNavigationAdapter
import jp.panta.misskeyandroidclient.util.DoubleBackPressedFinishDelegate
import jp.panta.misskeyandroidclient.viewmodel.MainViewModel
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import jp.panta.misskeyandroidclient.viewmodel.confirm.ConfirmViewModel
import jp.panta.misskeyandroidclient.viewmodel.timeline.CurrentPageableTimelineViewModel
import jp.panta.misskeyandroidclient.viewmodel.timeline.SuitableType
import jp.panta.misskeyandroidclient.viewmodel.timeline.suitableType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import net.pantasystem.milktea.api.misskey.MisskeyAPI
import net.pantasystem.milktea.api.misskey.v12.MisskeyAPIV12
import net.pantasystem.milktea.api.misskey.v12_75_0.MisskeyAPIV1275
import net.pantasystem.milktea.channel.ChannelActivity
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.data.infrastructure.settings.SettingStore
import net.pantasystem.milktea.drive.DriveActivity
import net.pantasystem.milktea.messaging.MessagingListActivity
import net.pantasystem.milktea.model.CreateNoteTaskExecutor
import net.pantasystem.milktea.model.TaskState
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.account.AccountStore
import net.pantasystem.milktea.model.channel.Channel
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.model.user.report.ReportState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    val mNotesViewModel: NotesViewModel by viewModels()

    @ExperimentalCoroutinesApi
    private val mAccountViewModel: AccountViewModel by viewModels()

    private lateinit var mBottomNavigationAdapter: MainBottomNavigationAdapter

    private val mBackPressedDelegate = DoubleBackPressedFinishDelegate()

    @Inject
    lateinit var loggerFactory: Logger.Factory
    private val logger: Logger by lazy {
        loggerFactory.create("MainActivity")
    }



    private val binding: ActivityMainBinding by dataBinding()

    @Inject
    lateinit var accountStore: AccountStore

    @Inject
    lateinit var settingStore: SettingStore

    @Inject
    lateinit var noteTaskExecutor: CreateNoteTaskExecutor

    private val mainViewModel: MainViewModel by viewModels()

    private val currentPageableTimelineViewModel: CurrentPageableTimelineViewModel by viewModels()

    private val reportViewModel: ReportViewModel by viewModels()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme()
        setContentView(R.layout.activity_main)

        setSupportActionBar(binding.appBarMain.toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.appBarMain.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { item ->
            showNavDrawersActivityBy(item)
            binding.drawerLayout.closeDrawerWhenOpened()
            false
        }

        binding.appBarMain.fab.setOnClickListener {
            onFabClicked()
        }

        val miApplication = application as MiApplication

        initAccountViewModelListener()
        binding.setupHeaderProfile()

        ActionNoteHandler(
            this,
            mNotesViewModel,
            ViewModelProvider(this)[ConfirmViewModel::class.java],
            settingStore,
        ).initViewModelListener()


        // NOTE: 各ばーしょんに合わせMenuを制御している
        miApplication.getCurrentAccountMisskeyAPI().filterNotNull().onEach { api ->
            changeMenuVisibilityFrom(api)
        }.launchIn(lifecycleScope)


        lifecycleScope.launchWhenStarted {
            accountStore.state.collect {
                if (it.isUnauthorized) {
                    this@MainActivity.startActivity(
                        Intent(
                            this@MainActivity,
                            AuthorizationActivity::class.java
                        )
                    )
                    finish()
                }
            }
        }

        // NOTE: メッセージの既読数をバッジに表示する
        mainViewModel.unreadMessageCount.onEach {
            showUnreadMessageCountBadge(it)
        }.launchIn(lifecycleScope)

        // NOTE: 通知の既読数を表示する
        mainViewModel.unreadNotificationCount.onEach {
            showNotificationCountBadge(it)
        }.launchIn(lifecycleScope)

        // NOTE: 最新の通知をSnackBar等に表示する
        mainViewModel.newNotifications.onEach { notificationRelation ->
            notificationMessageScope {
                notificationRelation.showSnackBarMessage(binding.appBarMain.simpleNotification)
            }
        }.catch { e ->
            logger.error("通知取得エラー", e = e)
        }.launchIn(lifecycleScope + Dispatchers.Main)

        lifecycleScope.launchWhenResumed {
            mainViewModel.currentAccountSocketStateEvent.collect {
                webSocketStateMessageScope {
                    it.showToastMessage()
                }
            }
        }

        // NOTE: ノート作成処理の状態をSnackBarで表示する
        lifecycleScope.launchWhenCreated {
            noteTaskExecutor.tasks.collect { taskState ->
                showCreateNoteTaskStatusSnackBar(taskState)
            }
        }

        lifecycleScope.launchWhenResumed {
            reportViewModel.state.distinctUntilChangedBy {
                it is ReportState.Sending.Success
                        || it is ReportState.Sending.Failed
            }.collect { state ->
                showSendReportStateFrom(state)
            }
        }

        startService(Intent(this, NotificationService::class.java))
        mBottomNavigationAdapter =
            MainBottomNavigationAdapter(savedInstanceState, binding.appBarMain.bottomNavigation)

    }


    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    inner class MainBottomNavigationAdapter(
        savedInstanceState: Bundle?,
        bottomNavigation: BottomNavigationView
    ) : BottomNavigationAdapter(
        bottomNavigation,
        supportFragmentManager,
        R.id.navigation_home,
        R.id.content_main,
        savedInstanceState
    ) {

        var currentMenuItem: MenuItem? = null

        override fun viewChanged(menuItem: MenuItem, fragment: Fragment) {
            super.viewChanged(menuItem, fragment)
            when (menuItem.itemId) {
                R.id.navigation_home -> changeTitle(getString(R.string.menu_home))
                R.id.navigation_search -> changeTitle(getString(R.string.search))
                R.id.navigation_notification -> changeTitle(getString(R.string.notification))
                R.id.navigation_message_list -> changeTitle(getString(R.string.message))
            }
            currentMenuItem = menuItem
        }

        override fun getItem(menuItem: MenuItem): Fragment? {
            return when (menuItem.itemId) {
                R.id.navigation_home -> TabFragment()
                R.id.navigation_search -> SearchTopFragment()
                R.id.navigation_notification -> NotificationMentionFragment()
                R.id.navigation_message_list -> MessagingHistoryFragment()
                else -> null
            }
        }

        override fun menuRetouched(menuItem: MenuItem, fragment: Fragment) {
            if (fragment is ScrollableTop) {
                fragment.showTop()
            }
        }


    }


    /**
     * シンプルエディターの表示・非表示を行う
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun ActivityMainBinding.setSimpleEditor() {
        val miCore = applicationContext as MiCore
        val ft = supportFragmentManager.beginTransaction()

        val editor = supportFragmentManager.findFragmentByTag("simpleEditor")

        if (miCore.getSettingStore().isSimpleEditorEnabled) {
            this.appBarMain.fab.visibility = View.GONE
            if (editor == null) {
                ft.replace(R.id.simpleEditorBase, SimpleEditorFragment(), "simpleEditor")
            }
        } else {
            this.appBarMain.fab.visibility = View.VISIBLE

            editor?.let {
                ft.remove(it)
            }

        }
        ft.commit()
    }


    private fun String.showSnackBar(action: Pair<String, (View) -> Unit>? = null) {
        val snackBar =
            Snackbar.make(binding.appBarMain.simpleNotification, this, Snackbar.LENGTH_LONG)
        if (action != null) {
            snackBar.setAction(action.first, action.second)
        }
        snackBar.show()
    }


    private val switchAccountButtonObserver = Observer<Int> {
        runOnUiThread {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            val dialog = AccountSwitchingDialog()
            dialog.show(supportFragmentManager, "mainActivity")
        }
    }


    private val showFollowingsObserver = Observer<User.Id> {
        binding.drawerLayout.closeDrawerWhenOpened()
        val intent = FollowFollowerActivity.newIntent(this, it, true)
        startActivity(intent)
    }

    private val showFollowersObserver = Observer<User.Id> {
        binding.drawerLayout.closeDrawerWhenOpened()
        val intent = FollowFollowerActivity.newIntent(this, it, false)
        startActivity(intent)
    }

    @ExperimentalCoroutinesApi
    private val showProfileObserver = Observer<Account> {
        binding.drawerLayout.closeDrawerWhenOpened()
        val intent =
            UserDetailActivity.newInstance(this, userId = User.Id(it.accountId, it.remoteId))
        intent.putActivity(Activities.ACTIVITY_IN_APP)
        startActivity(intent)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun initAccountViewModelListener() {
        mAccountViewModel.switchAccount.removeObserver(switchAccountButtonObserver)
        mAccountViewModel.switchAccount.observe(this, switchAccountButtonObserver)

        mAccountViewModel.showFollowings.observe(this, showFollowingsObserver)
        mAccountViewModel.showFollowers.observe(this, showFollowersObserver)
        mAccountViewModel.showProfile.observe(this, showProfileObserver)
    }

    fun changeTitle(title: String?) {
        supportActionBar?.title = title
    }


    @FlowPreview
    @ExperimentalCoroutinesApi
    private fun ActivityMainBinding.setupHeaderProfile() {
        DataBindingUtil.bind<NavHeaderMainBinding>(this.navView.getHeaderView(0))
        val headerBinding =
            DataBindingUtil.getBinding<NavHeaderMainBinding>(this.navView.getHeaderView(0))
        headerBinding?.lifecycleOwner = this@MainActivity
        headerBinding?.accountViewModel = mAccountViewModel
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        when {
            drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            mBottomNavigationAdapter.currentMenuItem?.itemId != R.id.navigation_home -> {
                mBottomNavigationAdapter.setCurrentFragment(R.id.navigation_home)
            }
            else -> {
                if (mBackPressedDelegate.back()) {
                    super.onBackPressed()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.please_again_to_finish),
                        Toast.LENGTH_SHORT
                    ).apply {
                        setGravity(Gravity.CENTER, 0, 0)
                        show()
                    }
                }
            }
        }
    }


    @MainThread
    private fun DrawerLayout.closeDrawerWhenOpened() {
        if (this.isDrawerOpen(GravityCompat.START)) {
            this.closeDrawer(GravityCompat.START)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)

        listOf(
            menu.findItem(R.id.action_messaging),
            menu.findItem(R.id.action_notification),
            menu.findItem(R.id.action_search)
        ).forEach {
            it.isVisible = settingStore.isClassicUI
        }

        //setMenuTint(menu)
        return true
    }


    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val idAndActivityMap = mapOf(
            R.id.action_settings to SettingsActivity::class.java,
            R.id.action_tab_setting to PageSettingActivity::class.java,
            R.id.action_notification to NotificationsActivity::class.java,
            R.id.action_messaging to MessagingListActivity::class.java,
            R.id.action_search to SearchActivity::class.java
        )

        val targetActivity = idAndActivityMap[item.itemId]
            ?: return super.onOptionsItemSelected(item)
        startActivity(Intent(this, targetActivity))
        return true
    }

    override fun onStart() {
        super.onStart()
        setBackgroundImage()
        applyUI()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        Log.d("MainActivity", "#onSaveInstanceStateが呼び出された")

        mBottomNavigationAdapter.saveState(outState)
    }


    private fun setBackgroundImage() {
        val path = settingStore.backgroundImagePath
        Glide.with(this)
            .load(path)
            .into(binding.appBarMain.contentMain.backgroundImage)
    }

    @MainThread
    private fun applyUI() {
        invalidateOptionsMenu()
        binding.setSimpleEditor()

        binding.appBarMain.bottomNavigation.visibility = if (settingStore.isClassicUI) {
            View.GONE
        } else {
            View.VISIBLE
        }
        if (settingStore.isClassicUI) {
            mBottomNavigationAdapter.setCurrentFragment(R.id.navigation_home)
        }
    }

    private fun showCreateNoteTaskStatusSnackBar(taskState: TaskState<Note>) {
        when (taskState) {
            is TaskState.Error -> {
                getString(R.string.note_creation_failure).showSnackBar(
                    getString(R.string.retry) to ({
                        noteTaskExecutor.dispatch(taskState.task)
                    })
                )
            }
            is TaskState.Success -> {
                getString(R.string.successfully_created_note).showSnackBar(
                    getString(R.string.show) to ({
                        startActivity(
                            NoteDetailActivity.newIntent(this@MainActivity, taskState.res.id)
                        )
                    })
                )
            }
            is TaskState.Executing -> {
            }
        }
    }

    private fun showNotificationCountBadge(count: Int) {
        val bottomNav = binding.appBarMain.bottomNavigation
        if (count <= 0) {
            bottomNav.getBadge(R.id.navigation_notification)
                ?.clearNumber()
        }
        bottomNav.getOrCreateBadge(R.id.navigation_notification)
            .apply {
                isVisible = count > 0
                number = count
            }
    }

    private fun showUnreadMessageCountBadge(count: Int) {
        val bottomNav = binding.appBarMain.bottomNavigation
        if (count <= 0) {
            bottomNav.getBadge(R.id.navigation_message_list)?.clearNumber()
        }
        bottomNav.getOrCreateBadge(R.id.navigation_message_list).apply {
            isVisible = count > 0
            number = count
        }
    }

    private fun onFabClicked() {
        when (val type = currentPageableTimelineViewModel.currentType.value.suitableType()) {
            is SuitableType.Other -> {
                startActivity(Intent(this, NoteEditorActivity::class.java))
            }
            is SuitableType.Gallery -> {
                val intent = Intent(this, GalleryPostsActivity::class.java)
                intent.action = Intent.ACTION_EDIT
                startActivity(intent)
            }
            is SuitableType.Channel -> {
                val accountId = accountStore.currentAccountId!!
                startActivity(
                    NoteEditorActivity.newBundle(
                        this,
                        channelId = Channel.Id(accountId, type.channelId)
                    )
                )
            }
        }
    }

    private fun changeMenuVisibilityFrom(api: MisskeyAPI) {
        binding.navView.menu.also { menu ->
            menu.findItem(R.id.nav_antenna).isVisible = api is MisskeyAPIV12
            menu.findItem(R.id.nav_channel).isVisible = api is MisskeyAPIV12
            menu.findItem(R.id.nav_gallery).isVisible = api is MisskeyAPIV1275
        }
    }

    private fun showNavDrawersActivityBy(item: MenuItem) {
        val activity = when (item.itemId) {
            R.id.nav_setting -> SettingsActivity::class.java
            R.id.nav_drive -> DriveActivity::class.java
            R.id.nav_favorite -> FavoriteActivity::class.java
            R.id.nav_list -> ListListActivity::class.java
            R.id.nav_antenna -> AntennaListActivity::class.java
            R.id.nav_draft -> DraftNotesActivity::class.java
            R.id.nav_gallery -> GalleryPostsActivity::class.java
            R.id.nav_channel -> ChannelActivity::class.java
            else -> throw IllegalStateException("未定義なNavigation Itemです")
        }
        startActivity(Intent(this, activity))
    }

    private fun showSendReportStateFrom(state: ReportState) {
        ReportStateHandler().invoke(binding.appBarMain.simpleNotification, state)
    }

    @ExperimentalCoroutinesApi
    private fun MiCore.getCurrentAccountMisskeyAPI(): Flow<MisskeyAPI?> {
        return accountStore.observeCurrentAccount.filterNotNull().flatMapLatest {
            getMetaRepository().observe(it.instanceDomain)
        }.map {
            it?.let {
                this.getMisskeyAPIProvider().get(it.uri, it.getVersion())
            }
        }
    }

}

