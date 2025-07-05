package com.maxwai.nclientv3

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.maxwai.nclientv3.adapters.LocalAdapter
import com.maxwai.nclientv3.api.local.LocalGallery
import com.maxwai.nclientv3.api.local.LocalSortType
import com.maxwai.nclientv3.async.converters.CreatePDF
import com.maxwai.nclientv3.async.downloader.GalleryDownloaderV2
import com.maxwai.nclientv3.components.activities.BaseActivity
import com.maxwai.nclientv3.components.classes.MultichoiceAdapter
import com.maxwai.nclientv3.components.classes.MultichoiceAdapter.DefaultMultichoiceListener
import com.maxwai.nclientv3.components.classes.MultichoiceAdapter.MultichoiceListener
import com.maxwai.nclientv3.settings.Global
import com.maxwai.nclientv3.utility.LogUtility
import com.maxwai.nclientv3.utility.Utility
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ScanResult(
    val galleries: List<LocalGallery>,
    val invalidPaths: List<String>
)

class LocalActivity : BaseActivity() {
    private var optionMenu: Menu? = null
    private var adapter: LocalAdapter? = null
    private val listener: MultichoiceListener = object : DefaultMultichoiceListener() {
        override fun choiceChanged() {
            setMenuVisibility(optionMenu)
        }
    }
    private var toolbar: Toolbar? = null
    var colCount: Int = 0
        private set
    private var idGalleryPosition = -1
    private var folder: File? = Global.MAINFOLDER
    private var searchView: SearchView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Global.initActivity(this);
        setContentView(R.layout.app_bar_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            setTitle(R.string.downloaded_manga)
        }
        findViewById<View>(R.id.page_switcher).visibility = View.GONE
        recycler = findViewById(R.id.recycler)
        refresher = findViewById(R.id.refresher)
        refresher.setOnRefreshListener {
            folder?.let { inspectLocalDownloads(it) }
        }
        changeLayout(getResources().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        folder?.let { inspectLocalDownloads(it) }
    }

    fun setAdapter(adapter: LocalAdapter?) {
        this.adapter = adapter
        this.adapter!!.addListener(listener)
        recycler.setAdapter(adapter)
    }

    fun setIdGalleryPosition(idGalleryPosition: Int) {
        this.idGalleryPosition = idGalleryPosition
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.download, menu)
        menuInflater.inflate(R.menu.local_multichoice, menu)
        this.optionMenu = menu
        setMenuVisibility(menu)
        searchView = menu.findItem(R.id.search).actionView as SearchView?
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (recycler.adapter != null) (recycler.adapter as LocalAdapter).filter
                    .filter(newText)
                return true
            }
        })

        Utility.tintMenu(menu)

        return true
    }

    private fun setMenuVisibility(menu: Menu?) {
        if (menu == null) return
        val mode = if (adapter == null) MultichoiceAdapter.Mode.NORMAL else adapter!!.mode
        var hasGallery = false
        var hasDownloads = false
        if (mode == MultichoiceAdapter.Mode.SELECTING) {
            hasGallery = adapter!!.hasSelectedClass(LocalGallery::class.java)
            hasDownloads = adapter!!.hasSelectedClass(GalleryDownloaderV2::class.java)
        }

        menu.findItem(R.id.search).isVisible = mode == MultichoiceAdapter.Mode.NORMAL
        menu.findItem(R.id.sort_by_name).isVisible = mode == MultichoiceAdapter.Mode.NORMAL
        menu.findItem(R.id.folder_choose).isVisible =
            mode == MultichoiceAdapter.Mode.NORMAL && Global.getUsableFolders(this).size > 1
        menu.findItem(R.id.random_favorite).isVisible = mode == MultichoiceAdapter.Mode.NORMAL

        menu.findItem(R.id.delete_all).isVisible = mode == MultichoiceAdapter.Mode.SELECTING
        menu.findItem(R.id.select_all).isVisible = mode == MultichoiceAdapter.Mode.SELECTING
        menu.findItem(R.id.pause_all).isVisible =
            mode == MultichoiceAdapter.Mode.SELECTING && !hasGallery && hasDownloads
        menu.findItem(R.id.start_all).isVisible =
            mode == MultichoiceAdapter.Mode.SELECTING && !hasGallery && hasDownloads
        menu.findItem(R.id.pdf_all).isVisible =
            mode == MultichoiceAdapter.Mode.SELECTING && hasGallery && !hasDownloads && CreatePDF.hasPDFCapabilities()
        menu.findItem(R.id.zip_all).isVisible =
            mode == MultichoiceAdapter.Mode.SELECTING && hasGallery && !hasDownloads
    }

    override fun onDestroy() {
        if (adapter != null) adapter!!.removeObserver()
        super.onDestroy()
    }

    override fun changeLayout(landscape: Boolean) {
        colCount = (if (landscape) landscapeColumnCount else portraitColumnCount)
        if (adapter != null) adapter!!.setColCount(colCount)
        super.changeLayout(landscape)
    }

    override fun onResume() {
        super.onResume()
        if (idGalleryPosition != -1) {
            adapter!!.updateColor(idGalleryPosition)
            idGalleryPosition = -1
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.pause_all) {
            adapter?.pauseSelected()
        } else if (item.itemId == R.id.start_all) {
            adapter?.startSelected()
        } else if (item.itemId == R.id.delete_all) {
            adapter?.deleteSelected()
        } else if (item.itemId == R.id.pdf_all) {
            adapter?.pdfSelected()
        } else if (item.itemId == R.id.zip_all) {
            adapter?.zipSelected()
        } else if (item.itemId == R.id.select_all) {
            adapter?.selectAll()
        } else if (item.itemId == R.id.folder_choose) {
            showDialogFolderChoose()
        } else if (item.itemId == R.id.random_favorite) {
            if (adapter != null) adapter!!.viewRandom()
        } else if (item.itemId == R.id.sort_by_name) {
            dialogSortType()
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter != null && adapter!!.mode == MultichoiceAdapter.Mode.SELECTING) adapter!!.deselectAll()
        else onBackPressedDispatcher.onBackPressed()
    }

    private fun showDialogFolderChoose() {
        val strings = Global.getUsableFolders(this)
        val adapter =
            ArrayAdapter<File?>(this, android.R.layout.select_dialog_singlechoice, strings)
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.choose_directory).setIcon(R.drawable.ic_folder)
        builder.setAdapter(
            adapter
        ) { dialog: DialogInterface?, which: Int ->
            folder = File(strings[which], "NClientV3")
            folder?.let { inspectLocalDownloads(it) }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun dialogSortType() {
        // 获取当前排序类型
        val currentSortType = Global.getLocalSortType()

        val view = layoutInflater.inflate(R.layout.local_sort_type, null) // root 设置为 null 更安全
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group)
        val materialSwitch = view.findViewById<MaterialSwitch>(R.id.ascending)

        // 初始化对话框的 UI 状态
        // getChildAt 返回 View，需要将其 ID 设置给 check()
        (chipGroup.getChildAt(currentSortType.type.ordinal) as? com.google.android.material.chip.Chip)?.id?.let {
            chipGroup.check(it)
        }
        materialSwitch.isChecked = currentSortType.descending

        MaterialAlertDialogBuilder(this).apply {
            setTitle(R.string.sort_select_type)
            setView(view)
            setPositiveButton(R.string.ok) { _, _ ->
                // 从 ChipGroup 获取选中的 Chip 的索引
                val checkedChipId = chipGroup.checkedChipId
                val selectedChip =
                    chipGroup.findViewById<com.google.android.material.chip.Chip>(checkedChipId)
                val typeSelectedIndex = chipGroup.indexOfChild(selectedChip)

                // 避免在索引为 -1 (未选择) 时崩溃
                if (typeSelectedIndex == -1) return@setPositiveButton

                val typeSelected = LocalSortType.Type.entries[typeSelectedIndex]
                val isDescending = materialSwitch.isChecked
                val newSortType = LocalSortType(typeSelected, isDescending)

                // 只有在排序类型改变时才执行操作
                if (currentSortType != newSortType) {
                    Global.setLocalSortType(this@LocalActivity, newSortType)
                    adapter?.sortChanged() // 使用安全调用 ?.
                }
            }
            setNeutralButton(R.string.cancel, null) // { dialog, which -> ... } 也可以
        }.show()

        /*
        val isSortByName = Global.isLocalSortByName()
        val iconRes = if (isSortByName) R.drawable.ic_sort_by_alpha else R.drawable.ic_access_time
        val titleRes = if (isSortByName) R.string.sort_by_title else R.string.sort_by_latest

        item.setIcon(iconRes)
        item.setTitle(titleRes)
        item.icon?.let { Global.setTint(it) } // 安全调用 icon, 防止 icon 为 null
        */
    }

    override fun getPortraitColumnCount(): Int {
        return Global.getColPortDownload()
    }

    override fun getLandscapeColumnCount(): Int {
        return Global.getColLandDownload()
    }

    val query: String
        get() {
            if (searchView == null) return ""
            val query = searchView!!.query
            return query?.toString() ?: ""
        }

    /**
     * 使用协程扫描本地下载的画廊。
     *
     * @param baseFolder 包含 "Download" 文件夹的父目录 (例如 context.getFilesDir())
     */
    private fun inspectLocalDownloads(baseFolder: File) {
        // lifecycleScope 会自动将协程与 Activity 的生命周期绑定。
        lifecycleScope.launch {
            // ---- 对应于 onPreExecute / onProgressUpdate ----
            // 立即在 UI 线程上显示刷新指示器。
            // 这对应于原版在 doInBackground 开始时调用 publishProgress 的效果。
            refresher.isRefreshing = true

            // ---- 对应于 doInBackground ----
            // withContext 将文件操作切换到 IO 线程，并在完成后返回结果。
            val result = withContext(Dispatchers.IO) {
                val downloadFolder = File(baseFolder, "Download")

                // 如果 "Download" 文件夹不存在或无法列出文件，则直接返回空结果。
                if (!downloadFolder.exists()) {
                    return@withContext ScanResult(emptyList(), emptyList())
                }
                downloadFolder.mkdirs() // 确保目录存在
                val files = downloadFolder.listFiles()
                    ?: return@withContext ScanResult(emptyList(), emptyList())

                val validGalleries = mutableListOf<LocalGallery>()
                val failedPaths = mutableListOf<String>()

                // ---- 对应于 createGallery() 的逻辑 ----
                for (f in files) {
                    if (f.isDirectory) {
                        val lg = LocalGallery(f, true)
                        if (lg.isValid) {
                            validGalleries.add(lg)
                        } else {
                            LogUtility.e(lg)
                            failedPaths.add(f.absolutePath)
                        }
                    }
                }
                // 返回包含两个列表的结果对象
                ScanResult(galleries = validGalleries, invalidPaths = failedPaths)
            }

            // ---- 对应于 onPostExecute ----
            // 当 withContext 块执行完毕后，代码会自动回到 UI 线程。

            // 打印无效路径日志
            result.invalidPaths.forEach { path ->
                LogUtility.d("Invalid path: $path")
            }

            // 更新 RecyclerView 的 Adapter
            // 'this@LocalActivity' 明确指代 Activity 实例
            setAdapter(LocalAdapter(this@LocalActivity, ArrayList(result.galleries)))

            // 隐藏刷新指示器
            refresher.isRefreshing = false
        }
    }
}
