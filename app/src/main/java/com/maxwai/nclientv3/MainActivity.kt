package com.maxwai.nclientv3

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.maxwai.nclientv3.adapters.ListAdapter
import com.maxwai.nclientv3.api.InspectorV3
import com.maxwai.nclientv3.api.InspectorV3.DefaultInspectorResponse
import com.maxwai.nclientv3.api.InspectorV3.InspectorResponse
import com.maxwai.nclientv3.api.components.Gallery
import com.maxwai.nclientv3.api.components.GenericGallery
import com.maxwai.nclientv3.api.components.Ranges
import com.maxwai.nclientv3.api.components.Tag
import com.maxwai.nclientv3.api.enums.ApiRequestType
import com.maxwai.nclientv3.api.enums.Language
import com.maxwai.nclientv3.api.enums.Language.*
import com.maxwai.nclientv3.api.enums.SortType
import com.maxwai.nclientv3.api.enums.SpecialTagIds
import com.maxwai.nclientv3.api.enums.TagStatus
import com.maxwai.nclientv3.api.enums.TagType
import com.maxwai.nclientv3.async.ScrapeTags
import com.maxwai.nclientv3.async.VersionChecker
import com.maxwai.nclientv3.async.database.Queries
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2
import com.maxwai.nclientv3.components.CookieInterceptor
import com.maxwai.nclientv3.components.GlideX
import com.maxwai.nclientv3.components.activities.BaseActivity
import com.maxwai.nclientv3.components.views.PageSwitcher
import com.maxwai.nclientv3.components.views.PageSwitcher.DefaultPageChanger
import com.maxwai.nclientv3.components.widgets.CustomGridLayoutManager
import com.maxwai.nclientv3.settings.Global
import com.maxwai.nclientv3.settings.Global.ThemeScheme
import com.maxwai.nclientv3.settings.Login
import com.maxwai.nclientv3.settings.TagV2
import com.maxwai.nclientv3.utility.ImageDownloadUtility
import com.maxwai.nclientv3.utility.LogUtility
import com.maxwai.nclientv3.utility.Utility
import okhttp3.Cookie
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import androidx.core.net.toUri

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val startGallery: InspectorResponse = object : MainInspectorResponse() {
        override fun onSuccess(galleries: MutableList<GenericGallery?>) {
            val g = if (galleries.size == 1) galleries[0] as Gallery else Gallery.emptyGallery()
            val intent = Intent(this@MainActivity, GalleryActivity::class.java)
            LogUtility.d(g.toString())
            intent.putExtra("$packageName.GALLERY", g)
            runOnUiThread {
                startActivity(intent)
                finish()
            }
            LogUtility.d("STARTED")
        }
    }
    private val MANAGER = object : CookieInterceptor.Manager {
        private var tokenFound = false

        override fun applyCookie(key: String, value: String) {
            // 使用字符串模板，更清晰
            val cookieString = "$key=$value; Max-Age=31449600; Path=/; SameSite=Lax"
            val cookie = Cookie.parse(Login.BASE_HTTP_URL, cookieString)

            // 使用 Kotlin 的 listOf()，更简洁
            Global.client.cookieJar.saveFromResponse(Login.BASE_HTTP_URL, listOf(cookie!!))

            // 如果 token 还没找到，就检查当前 key 是否是 "csrftoken"
            if (!tokenFound) {
                tokenFound = (key == "csrftoken")
            }
        }

        override fun endInterceptor(): Boolean {
            // 如果在 applyCookie 阶段已经找到 token，直接返回 true
            if (tokenFound) return true

            // 使用 let 和 ?.contains 优雅地处理 null
            val cookies = CookieManager.getInstance().getCookie(Utility.getBaseUrl())
            return cookies?.contains("csrftoken") ?: false
        }

        override fun onFinish() {
            // 假设这段代码在 MainActivity 中，使用 this@MainActivity 来明确引用外部类实例
            inspector = inspector?.cloneInspector(this@MainActivity, resetDataset)
            inspector?.start()
        }
    }
    private val changeLanguageTimeHandler = Handler(Looper.myLooper()!!)
    var adapter: ListAdapter? = null
    private val addDataset: InspectorResponse = object : MainInspectorResponse() {
        override fun onSuccess(galleries: MutableList<GenericGallery?>) {
            adapter!!.addGalleries(galleries)
        }
    }

    //views
    @JvmField
    var loginItem: MenuItem? = null
    var onlineFavoriteManager: MenuItem? = null
    private var inspector: InspectorV3? = null
    private var navigationView: NavigationView? = null
    private var modeType = ModeType.UNKNOWN
    private var idOpenedGallery = -1 //Position in the recycler of the opened gallery
    private var inspecting = false
    private var filteringTag = false
    private var temporaryType: SortType? = null
    private var snackbar: Snackbar? = null
    private var pageSwitcher: PageSwitcher? = null
    private val resetDataset: InspectorResponse = object : MainInspectorResponse() {
        override fun onSuccess(galleries: MutableList<GenericGallery?>) {
            super.onSuccess(galleries)
            adapter!!.restartDataset(galleries)
            showPageSwitcher(inspector!!.page, inspector!!.pageCount)
            runOnUiThread { recycler.smoothScrollToPosition(0) }
        }
    }
    val changeLanguageRunnable: Runnable = Runnable {
        useNormalMode()
        inspector!!.start()
    }
    private var drawerLayout: DrawerLayout? = null
    private var toolbar: Toolbar? = null
    private var setting: Setting? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        handleInsetsInBaseActivity = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //load inspector
        selectStartMode(intent, packageName)
        LogUtility.d("Main started with mode $modeType")
        //init views and actions
        findUsefulViews()
        initializeToolbar()
        initializeNavigationView()
        initializeRecyclerView()
        initializePageSwitcherActions()
        loadStringLogin()
        refresher.setOnRefreshListener {
            inspector = inspector!!.cloneInspector(this@MainActivity, resetDataset)
            if (Global.isInfiniteScrollMain()) inspector!!.setPage(1)
            inspector!!.start()
        }

        manageDrawer()
        setActivityTitle()
        if (firstTime) checkUpdate()
        if (inspector != null) {
            inspector!!.start()
        } else {
            LogUtility.e(intent.extras)
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main_content)
        ) { view: View?, windowInsets: WindowInsetsCompat? ->
            val insets = windowInsets!!.getInsets(WindowInsetsCompat.Type.systemBars())

            view!!.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
    }

    private fun manageDrawer() {
        if (modeType != ModeType.NORMAL) {
            drawerLayout!!.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else {
            val toggle = ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            )
            drawerLayout!!.addDrawerListener(toggle)
            toggle.syncState()
        }
    }

    private fun setActivityTitle() {
        when (modeType) {
            ModeType.FAVORITE -> supportActionBar?.setTitle(R.string.favorite_online_manga)
            ModeType.SEARCH -> supportActionBar?.title = inspector!!.getSearchTitle()
            ModeType.TAG -> supportActionBar?.title = inspector!!.getTag().name
            ModeType.NORMAL -> supportActionBar?.setTitle(R.string.app_name)
            else -> supportActionBar?.title = "WTF"
        }
    }

    private fun initializeToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setTitle(R.string.app_name)
        }
    }

    private fun initializePageSwitcherActions() {
        pageSwitcher!!.setChanger(object : DefaultPageChanger() {
            override fun pageChanged() {
                inspector = inspector!!.cloneInspector(this@MainActivity, resetDataset)
                inspector!!.setPage(pageSwitcher!!.actualPage)
                inspector!!.start()
            }
        })
    }

    private fun initializeRecyclerView() {
        adapter = ListAdapter(this)
        recycler.setAdapter(adapter)
        recycler.setHasFixedSize(true)
        //recycler.setItemViewCacheSize(24);
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (inspecting) return
                if (!Global.isInfiniteScrollMain()) return
                if (refresher.isRefreshing) return

                val manager: CustomGridLayoutManager? =
                    checkNotNull(recycler.layoutManager as CustomGridLayoutManager?)
                if (!pageSwitcher!!.lastPageReached() && lastGalleryReached(manager!!)) {
                    inspecting = true
                    inspector = inspector!!.cloneInspector(this@MainActivity, addDataset)
                    inspector!!.setPage(inspector!!.page + 1)
                    inspector!!.start()
                }
            }
        })
        changeLayout(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    /**
     * Check if the last gallery has been shown
     */
    private fun lastGalleryReached(manager: CustomGridLayoutManager): Boolean {
        return manager.findLastVisibleItemPosition() >= (recycler.adapter!!
            .itemCount - 1 - manager.spanCount)
    }

    private fun initializeNavigationView() {
        changeNavigationImage(navigationView!!)
        toolbar!!.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar!!.setNavigationOnClickListener { v: View? -> finish() }
        navigationView!!.setNavigationItemSelectedListener(this)
        onlineFavoriteManager!!.isVisible = Login.isLogged()
    }

    fun setIdOpenedGallery(idOpenedGallery: Int) {
        this.idOpenedGallery = idOpenedGallery
    }

    private fun findUsefulViews() {
        masterLayout = findViewById(R.id.master_layout)
        toolbar = findViewById(R.id.toolbar)
        navigationView = findViewById(R.id.nav_view)
        recycler = findViewById(R.id.recycler)
        refresher = findViewById(R.id.refresher)
        pageSwitcher = findViewById(R.id.page_switcher)
        drawerLayout = findViewById(R.id.drawer_layout)
        loginItem = navigationView!!.menu.findItem(R.id.action_login)
        onlineFavoriteManager = navigationView!!.menu.findItem(R.id.online_favorite_manager)
    }

    private fun loadStringLogin() {
        if (loginItem == null) return
        if (Login.getUser() != null) loginItem!!.title = getString(
            R.string.login_formatted,
            Login.getUser().username
        )
        else loginItem!!.setTitle(if (Login.isLogged()) R.string.logout else R.string.login)
    }

    private fun hideError() {
        //errorText.setVisibility(View.GONE);
        runOnUiThread {
            if (snackbar != null && snackbar!!.isShown) {
                snackbar!!.dismiss()
                snackbar = null
            }
        }
    }

    private fun showError(text: String?, listener: View.OnClickListener?) {
        if (text == null) {
            hideError()
            return
        }
        if (listener == null) {
            snackbar = Snackbar.make(masterLayout, text, Snackbar.LENGTH_SHORT)
        } else {
            snackbar = Snackbar.make(masterLayout, text, Snackbar.LENGTH_INDEFINITE)
            snackbar!!.setAction(R.string.retry, listener)
        }
        snackbar!!.show()
    }

    private fun showError(@StringRes text: Int, listener: View.OnClickListener?) {
        showError(getString(text), listener)
    }

    private fun checkUpdate() {
        if (Global.shouldCheckForUpdates(this)) VersionChecker(this, true)
        ScrapeTags.startWork(this)
        firstTime = false
    }

    private fun selectStartMode(intent: Intent, packageName: String?) {
        val data = intent.data
        if (intent.getBooleanExtra("$packageName.ISBYTAG", false)) useTagMode(intent, packageName)
        else if (intent.getBooleanExtra("$packageName.SEARCHMODE", false)) useSearchMode(
            intent,
            packageName
        )
        else if (intent.getBooleanExtra("$packageName.FAVORITE", false)) useFavoriteMode(1)
        else if (intent.getBooleanExtra("$packageName.BYBOOKMARK", false)) useBookmarkMode(
            intent,
            packageName
        )
        else if (data != null) manageDataStart(data)
        else useNormalMode()
    }

    private fun useNormalMode() {
        inspector = InspectorV3.basicInspector(this, 1, resetDataset)
        modeType = ModeType.NORMAL
    }

    private fun useBookmarkMode(intent: Intent, packageName: String?) {
        inspector = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                "$packageName.INSPECTOR",
                InspectorV3::class.java
            )
        } else {
            intent.getParcelableExtra("$packageName.INSPECTOR")
        }
        checkNotNull(inspector)
        inspector!!.initialize(this, resetDataset)
        modeType = ModeType.BOOKMARK
        val type = inspector!!.requestType
        if (type === ApiRequestType.BYTAG) modeType = ModeType.TAG
        else if (type === ApiRequestType.BYALL) modeType = ModeType.NORMAL
        else if (type === ApiRequestType.BYSEARCH) modeType = ModeType.SEARCH
        else if (type === ApiRequestType.FAVORITE) modeType = ModeType.FAVORITE
    }

    private fun useFavoriteMode(page: Int) {
        //instantiateWebView();
        inspector = InspectorV3.favoriteInspector(this, null, page, resetDataset)
        modeType = ModeType.FAVORITE
    }

    private fun useSearchMode(intent: Intent, packageName: String?) {
        val query = intent.getStringExtra("$packageName.QUERY")
        val ok = tryOpenId(query!!)
        if (!ok) createSearchInspector(intent, packageName, query)
    }

    private fun createSearchInspector(intent: Intent, packageName: String?, query: String) {
        var query = query
        val advanced = intent.getBooleanExtra("$packageName.ADVANCED", false)
        val tagArrayList = intent.getParcelableArrayListExtra<Tag?>("$packageName.TAGS")
        val ranges: Ranges? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(getPackageName() + ".RANGES", Ranges::class.java)
        } else {
            intent.getParcelableExtra(getPackageName() + ".RANGES")
        }
        var tags: HashSet<Tag?>? = null
        query = query.trim { it <= ' ' }
        if (advanced) {
            checkNotNull(tagArrayList) //tags is always not null when advanced is set
            tags = HashSet(tagArrayList)
        }
        inspector = InspectorV3.searchInspector(
            this,
            query,
            tags,
            1,
            Global.getSortType(),
            ranges,
            resetDataset
        )
        modeType = ModeType.SEARCH
    }

    private fun tryOpenId(query: String): Boolean {
        try {
            val id = query.toInt()
            inspector = InspectorV3.galleryInspector(this, id, startGallery)
            modeType = ModeType.ID
            return true
        } catch (ignore: NumberFormatException) {
        }
        return false
    }

    private fun useTagMode(intent: Intent, packageName: String?) {
        val t: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("$packageName.TAG", Tag::class.java)
        } else {
            intent.getParcelableExtra("$packageName.TAG")
        }
        inspector = InspectorV3.tagInspector(this, t, 1, Global.getSortType(), resetDataset)
        modeType = ModeType.TAG
    }

    /**
     * Load inspector from an URL, it can be either a tag or a search
     */
    private fun manageDataStart(data: Uri) {
        val datas = data.pathSegments

        LogUtility.d("Datas: $datas")
        if (datas.isEmpty()) {
            useNormalMode()
            return
        }
        val dataType = TagType.typeByName(datas[0])
        if (dataType !== TagType.UNKNOWN) useDataTagMode(datas, dataType)
        else useDataSearchMode(data, datas)
    }

    private fun useDataSearchMode(data: Uri, datas: MutableList<String?>) {
        val query = data.getQueryParameter("q")
        val pageParam = data.getQueryParameter("page")
        val favorite = "favorites" == datas[0]
        val type = SortType.findFromAddition(data.getQueryParameter("sort"))
        var page = 1

        if (pageParam != null) page = pageParam.toInt()

        if (favorite) {
            if (Login.isLogged()) useFavoriteMode(page)
            else {
                val intent = Intent(this, FavoriteActivity::class.java)
                startActivity(intent)
                finish()
            }
            return
        }

        inspector = InspectorV3.searchInspector(this, query, null, page, type, null, resetDataset)
        modeType = ModeType.SEARCH
    }

    private fun useDataTagMode(datas: MutableList<String?>, type: TagType?) {
        val query = datas[1]
        var tag = Queries.TagTable.getTagFromTagName(query)
        if (tag == null) tag =
            Tag(query, -1, SpecialTagIds.INVALID_ID.toInt(), type, TagStatus.DEFAULT)
        var sortType: SortType? = SortType.RECENT_ALL_TIME
        if (datas.size == 3) {
            sortType = SortType.findFromAddition(datas[2])
        }
        inspector = InspectorV3.tagInspector(this, tag, 1, sortType, resetDataset)
        modeType = ModeType.TAG
    }

    private fun changeNavigationImage(navigationView: NavigationView) {
        val light = Global.getTheme() == ThemeScheme.LIGHT
        val view = navigationView.getHeaderView(0)
        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val layoutHeader = view.findViewById<View>(R.id.layout_header)
        ImageDownloadUtility.loadImage(
            if (light) R.drawable.ic_logo_dark else R.drawable.ic_logo,
            imageView
        )
        layoutHeader.setBackgroundResource(if (light) R.drawable.side_nav_bar_light else R.drawable.side_nav_bar_dark)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (drawerLayout!!.isDrawerOpen(GravityCompat.START)) drawerLayout!!.closeDrawer(
            GravityCompat.START
        )
        else onBackPressedDispatcher.onBackPressed()
    }

    fun hidePageSwitcher() {
        runOnUiThread { pageSwitcher!!.visibility = View.GONE }
    }

    fun showPageSwitcher(actualPage: Int, totalPage: Int) {
        pageSwitcher!!.setPages(totalPage, actualPage)


        if (Global.isInfiniteScrollMain()) {
            hidePageSwitcher()
        }
    }


    private fun showLogoutForm() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setIcon(R.drawable.ic_exit_to_app).setTitle(R.string.logout)
            .setMessage(R.string.are_you_sure)
        builder.setPositiveButton(
            R.string.yes
        ) { dialogInterface: DialogInterface?, i: Int ->
            Login.logout(this)
            onlineFavoriteManager!!.isVisible = false
            loginItem!!.setTitle(R.string.login)
        }.setNegativeButton(R.string.no, null).show()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        Global.updateACRAReportStatus(this)
        Login.initLogin(this)
        if (idOpenedGallery != -1) {
            adapter!!.updateColor(idOpenedGallery)
            idOpenedGallery = -1
        }
        loadStringLogin()
        onlineFavoriteManager!!.isVisible = Login.isLogged()
        if (setting != null) {
            Global.initFromShared(this) //restart all settings
            inspector = inspector!!.cloneInspector(this, resetDataset)
            inspector!!.start() //restart inspector
            if (setting!!.theme != Global.getTheme() || setting!!.locale != Global.initLanguage(this)) {
                val manager = GlideX.with(applicationContext)
                manager?.pauseAllRequestsRecursive()
                recreate()
            }
            adapter!!.notifyDataSetChanged() //restart adapter
            adapter!!.resetStatuses()
            showPageSwitcher(
                inspector!!.page,
                inspector!!.pageCount
            ) //restart page switcher
            changeLayout(getResources().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            setting = null
        } else if (filteringTag) {
            inspector = InspectorV3.basicInspector(this, 1, resetDataset)
            inspector!!.start()
            filteringTag = false
        }
        invalidateOptionsMenu()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        popularItemDispay(menu.findItem(R.id.by_popular))

        showLanguageIcon(menu.findItem(R.id.only_language))

        menu.findItem(R.id.only_language).isVisible = modeType == ModeType.NORMAL
        menu.findItem(R.id.random_favorite).isVisible = modeType == ModeType.FAVORITE

        initializeSearchItem(menu.findItem(R.id.search))


        if (modeType == ModeType.TAG) {
            val item = menu.findItem(R.id.tag_manager)
            item.isVisible = inspector!!.getTag().id > 0
            val ts = inspector!!.getTag().status
            updateTagStatus(item, ts)
        }
        Utility.tintMenu(menu)
        return true
    }

    private fun initializeSearchItem(item: MenuItem) {
        if (modeType != ModeType.FAVORITE) item.actionView = null
        else {
            (item.actionView as SearchView).setOnQueryTextListener(object :
                SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    inspector =
                        InspectorV3.favoriteInspector(this@MainActivity, query, 1, resetDataset)
                    inspector!!.start()
                    supportActionBar?.title = query
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return false
                }
            })
        }
    }

    private fun popularItemDispay(item: MenuItem) {
        item.title = getString(
            R.string.sort_type_title_format,
            getString(Global.getSortType().nameId)
        )
        Global.setTint(item.icon)
    }

    private fun showLanguageIcon(item: MenuItem) {
        when (Global.getOnlyLanguage()) {
            JAPANESE -> {
                item.setTitle(R.string.only_japanese)
                item.setIcon(R.drawable.ic_jpbw)
            }

            CHINESE -> {
                item.setTitle(R.string.only_chinese)
                item.setIcon(R.drawable.ic_cnbw)
            }

            ENGLISH -> {
                item.setTitle(R.string.only_english)
                item.setIcon(R.drawable.ic_gbbw)
            }

            ALL, UNKNOWN -> {
                item.setTitle(R.string.all_languages)
                item.setIcon(R.drawable.ic_world)
            }
        }
        Global.setTint(item.icon)
    }

    override fun getPortraitColumnCount(): Int {
        return Global.getColPortMain()
    }

    override fun getLandscapeColumnCount(): Int {
        return Global.getColLandMain()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i: Intent?
        LogUtility.d("Pressed item: " + item.itemId)
        if (item.itemId == R.id.by_popular) {
            updateSortType(item)
        } else if (item.itemId == R.id.only_language) {
            updateLanguageAndIcon(item)
        } else if (item.itemId == R.id.search) {
            if (modeType != ModeType.FAVORITE) { //show textbox or start search activity
                i = Intent(this, SearchActivity::class.java)
                startActivity(i)
            }
        } else if (item.itemId == R.id.open_browser) {
            if (inspector != null) {
                i = Intent(Intent.ACTION_VIEW, inspector!!.url.toUri())
                startActivity(i)
            }
        } else if (item.itemId == R.id.random_favorite) {
            inspector = InspectorV3.randomInspector(this, startGallery, true)
            inspector!!.start()
        } else if (item.itemId == R.id.download_page) {
            if (inspector!!.galleries != null) showDialogDownloadAll()
        } else if (item.itemId == R.id.add_bookmark) {
            Queries.BookmarkTable.addBookmark(inspector)
        } else if (item.itemId == R.id.tag_manager) {
            val ts = TagV2.updateStatus(inspector!!.getTag())
            updateTagStatus(item, ts)
        } else if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateSortType(item: MenuItem) {
        val adapter = ArrayAdapter<String?>(this, android.R.layout.select_dialog_singlechoice)
        val builder = MaterialAlertDialogBuilder(this)
        for (type in SortType.entries) adapter.add(getString(type.nameId))
        temporaryType = Global.getSortType()
        builder.setIcon(R.drawable.ic_sort).setTitle(R.string.sort_select_type)
        builder.setSingleChoiceItems(
            adapter,
            temporaryType!!.ordinal
        ) { dialog: DialogInterface?, which: Int ->
            temporaryType = SortType.entries[which]
        }
        builder.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                temporaryType = SortType.entries[position]
                parent.setSelection(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        })
        builder.setPositiveButton(
            R.string.ok
        ) { dialog: DialogInterface?, which: Int ->
            Global.updateSortType(this@MainActivity, temporaryType!!)
            popularItemDispay(item)
            inspector = inspector!!.cloneInspector(this@MainActivity, resetDataset)
            inspector!!.setSortType(temporaryType)
            inspector!!.start()
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun updateLanguageAndIcon(item: MenuItem) {
        val adapter = ArrayAdapter<String?>(this, android.R.layout.select_dialog_singlechoice)
        val builder = MaterialAlertDialogBuilder(this)

        adapter.addAll(
            Arrays.stream(Language.getFilteredValuesArray())
                .map { lang: Language? -> getString(lang!!.nameResId) }
                .collect(Collectors.toList())
        )

        val selectedLanguage = AtomicReference(Global.getOnlyLanguage())
        builder.setIcon(R.drawable.ic_world)
            .setTitle(R.string.change_language)
            .setSingleChoiceItems(
                adapter, selectedLanguage.get()!!.ordinal
            ) { dialog: DialogInterface?, which: Int ->
                selectedLanguage.set(
                    Language.getFilteredValuesArray()[which]
                )
            }
            .setPositiveButton(
                R.string.ok
            ) { dialog: DialogInterface?, which: Int ->
                Global.updateOnlyLanguage(
                    this@MainActivity, Language.valueOf(
                        selectedLanguage.get()!!.name
                    )
                )
                // wait 250ms to reduce the requests
                changeLanguageTimeHandler.removeCallbacks(changeLanguageRunnable)
                changeLanguageTimeHandler.postDelayed(
                    changeLanguageRunnable,
                    CHANGE_LANGUAGE_DELAY.toLong()
                )
                showLanguageIcon(item)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDialogDownloadAll() {
        val builder = MaterialAlertDialogBuilder(this)
        builder
            .setTitle(R.string.download_all_galleries_in_this_page)
            .setIcon(R.drawable.ic_file)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                R.string.ok
            ) { dialog: DialogInterface?, which: Int ->
                for (g in inspector!!.galleries) DownloadGalleryV2.downloadGallery(
                    this@MainActivity,
                    g
                )
            }
        builder.show()
    }

    private fun updateTagStatus(item: MenuItem, ts: TagStatus) {
        when (ts) {
            TagStatus.DEFAULT -> item.setIcon(R.drawable.ic_help)
            TagStatus.AVOIDED -> item.setIcon(R.drawable.ic_close)
            TagStatus.ACCEPTED -> item.setIcon(R.drawable.ic_check)
        }
        Global.setTint(item.icon)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val intent: Intent?
        if (item.itemId == R.id.downloaded) {
            if (Global.hasStoragePermission(this)) startLocalActivity()
            else requestStorage()
        } else if (item.itemId == R.id.bookmarks) {
            intent = Intent(this, BookmarkActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.history) {
            intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.favorite_manager) {
            intent = Intent(this, FavoriteActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.action_settings) {
            setting = Setting(this)
            intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.online_favorite_manager) {
            intent = Intent(this, MainActivity::class.java)
            intent.putExtra("$packageName.FAVORITE", true)
            startActivity(intent)
        } else if (item.itemId == R.id.action_login) {
            if (Login.isLogged()) showLogoutForm()
            else {
                intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        } else if (item.itemId == R.id.random) {
            intent = Intent(this, RandomActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.tag_manager) {
            intent = Intent(this, TagFilterActivity::class.java)
            filteringTag = true
            startActivity(intent)
        } else if (item.itemId == R.id.status_manager) {
            intent = Intent(this, StatusViewerActivity::class.java)
            startActivity(intent)
        }
        //drawerLayout.closeDrawer(GravityCompat.START);
        return true
    }

    private fun requestStorage() {
        requestPermissions(
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), 1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Global.initStorage(this)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocalActivity()
        if (requestCode == 2 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) VersionChecker(
            this,
            true
        )
    }

    private fun startLocalActivity() {
        val i = Intent(this, LocalActivity::class.java)
        startActivity(i)
    }

    /**
     * UNKNOWN in case of error
     * NORMAL when in main page
     * TAG when searching for a specific tag
     * FAVORITE when using online favorite button
     * SEARCH when used SearchActivity
     * BOOKMARK when loaded a bookmark
     * ID when searched for an ID
     */
    private enum class ModeType {
        UNKNOWN, NORMAL, TAG, FAVORITE, SEARCH, BOOKMARK, ID
    }

    internal abstract inner class MainInspectorResponse : DefaultInspectorResponse() {
        override fun onSuccess(galleries: MutableList<GenericGallery?>) {
            super.onSuccess(galleries)
            if (adapter != null) adapter!!.resetStatuses()
            if (galleries.isEmpty()) showError(R.string.no_entry_found, null)
        }

        override fun onStart() {
            runOnUiThread { refresher.isRefreshing = true }
            hideError()
        }

        override fun onEnd() {
            runOnUiThread { refresher.isRefreshing = false }
            inspecting = false
        }

        override fun onFailure(e: Exception?) {
            super.onFailure(e)
            showError(R.string.unable_to_connect_to_the_site) { v: View? ->
                inspector = inspector!!.cloneInspector(this@MainActivity, inspector!!.response)
                inspector!!.start()
            }
        }

        override fun shouldStart(inspector: InspectorV3?): Boolean {
            return true
            //loadWebVewUrl(inspector.getUrl());
            //return inspector.canParseDocument();
        }
    }

    private class Setting(context: MainActivity) {
        val theme: ThemeScheme? = Global.getTheme()
        val locale: Locale = Global.initLanguage(context)
    }

    companion object {
        private const val CHANGE_LANGUAGE_DELAY = 1000
        private var firstTime = true //true only when app starting
    }
}
