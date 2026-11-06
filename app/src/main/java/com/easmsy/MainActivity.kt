package com.easmsy

import android.app.AlertDialog
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.easmsy.core.AdbSecure
import com.easmsy.core.AppEntry
import com.easmsy.core.AppRepository
import com.easmsy.core.Greenify
import com.easmsy.core.Notifications
import com.easmsy.core.Ops
import com.easmsy.core.Prefs
import com.easmsy.core.Users
import com.easmsy.file.ShuttleProvider
import com.easmsy.setup.SetupActivity
import com.easmsy.settings.SettingsActivity
import com.easmsy.ui.AppListAdapter
import com.easmsy.ui.FeaturedAdapter
import com.easmsy.ui.FeaturedItem
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout

/**
 * Main screen: the same three-area structure as Island (toolbar + tabs + app list and a
 * bottom-sheet action panel), implemented without fragments, data binding or view models.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var appList: RecyclerView
    private lateinit var featuredList: RecyclerView
    private lateinit var empty: TextView
    private lateinit var chips: ChipGroup
    private lateinit var chipFilter: Chip
    private lateinit var chipQuery: Chip
    private lateinit var tabs: TabLayout
    private lateinit var bottomSheet: View
    private lateinit var sheetToolbar: Toolbar
    private lateinit var summary: TextView
    private lateinit var packageName: TextView
    private lateinit var debugInfo: TextView
    private lateinit var handler: Handler
    private lateinit var appAdapter: AppListAdapter
    private lateinit var featuredAdapter: FeaturedAdapter
    private lateinit var behavior: BottomSheetBehavior<View>

    private var currentUser = android.os.Process.myUserHandle()
    private var includeSystem = false
    private var query = ""
    private var featuredMode = false
    private var allEntries: List<AppEntry> = emptyList()
    private var selected: AppEntry? = null
    private var loadToken = 0
    private var searchItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Users.isOwner()) {
            // The launcher entry inside the managed profile is only an indicator; settings
            // live in the profile and the main app lives in the owner user.
            startActivity(Intent(this, com.easmsy.settings.IslandSettingsActivity::class.java))
            finish()
            return
        }

        val profile = Users.profile(this)
        if (profile == null || !Users.isProfileManagedByIsland(this, profile)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        repository = AppRepository(this)
        handler = Handler(Looper.getMainLooper())
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.actionbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setTitle(R.string.app_name)

        tabs = findViewById(R.id.tabs)
        appList = findViewById(R.id.app_list)
        featuredList = findViewById(R.id.featured_list)
        empty = findViewById(R.id.empty)
        chips = findViewById(R.id.chips)
        chipFilter = findViewById(R.id.chip_filter)
        chipQuery = findViewById(R.id.chip_query)
        bottomSheet = findViewById(R.id.bottom_sheet)
        sheetToolbar = findViewById(R.id.toolbar)
        summary = findViewById(R.id.summary)
        packageName = findViewById(R.id.package_name)
        debugInfo = findViewById(R.id.debug_info)
        behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.skipCollapsed = true

        appAdapter = AppListAdapter(this, repository, ::selectApp, ::launchApp)
        appList.adapter = appAdapter
        featuredAdapter = FeaturedAdapter(this)
        featuredList.adapter = featuredAdapter

        sheetToolbar.inflateMenu(R.menu.app_actions)
        sheetToolbar.setOnMenuItemClickListener(::onAppAction)

        setupTabs(profile)
        chipFilter.setOnCheckedChangeListener { _, checked ->
            includeSystem = checked
            reloadApps()
        }
        chipFilter.isChecked = false
        chipQuery.setOnCloseIconClickListener { clearQuery() }

        restoreOrSelect(savedInstanceState)
    }

    private fun setupTabs(profile: android.os.UserHandle) {
        tabs.addTab(tabs.newTab().setText(R.string.tab_discovery))
        tabs.addTab(tabs.newTab().setText(R.string.tab_mainland))
        tabs.addTab(tabs.newTab().setText(Prefs.islandName(this, profile)))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                onTabSelected(tab.position, profile)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun restoreOrSelect(savedInstanceState: Bundle?) {
        val savedTab = savedInstanceState?.getInt(STATE_TAB, 1) ?: 1
        val tab = tabs.getTabAt(savedTab.coerceIn(0, tabs.tabCount - 1)) ?: tabs.getTabAt(1)
        tab?.select()
        if (savedTab == 2) onTabSelected(2, Users.profile(this))
        else if (savedTab == 0) onTabSelected(0, Users.profile(this))
        else onTabSelected(1, Users.profile(this))
    }

    private fun onTabSelected(position: Int, profile: android.os.UserHandle?) {
        hideSelection()
        chips.visibility = View.GONE
        when (position) {
            0 -> {
                featuredMode = true
                currentUser = Users.owner()
                showFeatured()
            }
            2 -> {
                if (profile == null || !Users.isProfileManagedByIsland(this, profile)) {
                    Toast.makeText(this, R.string.prompt_island_not_yet_setup, Toast.LENGTH_LONG).show()
                    tabs.getTabAt(1)?.select()
                    return
                }
                featuredMode = false
                currentUser = profile
                showApps()
            }
            else -> {
                featuredMode = false
                currentUser = Users.owner()
                showApps()
            }
        }
        invalidateOptionsMenu()
    }

    private fun showApps() {
        featuredList.visibility = View.GONE
        appList.visibility = View.VISIBLE
        empty.visibility = View.GONE
        reloadApps()
    }

    private fun showFeatured() {
        appList.visibility = View.GONE
        featuredList.visibility = View.VISIBLE
        empty.visibility = View.GONE
        allEntries = emptyList()
        appAdapter.submit(emptyList())
        featuredAdapter.submit(buildFeatured())
    }

    private fun reloadApps() {
        if (featuredMode) return
        val token = ++loadToken
        val user = currentUser
        val system = includeSystem
        Thread {
            val loaded = repository.load(user, system)
            runOnUiThread {
                if (token != loadToken || isFinishing || featuredMode) return@runOnUiThread
                allEntries = loaded
                applyFilter()
            }
        }.apply { name = "app-list-loader"; isDaemon = true }.start()
    }

    private fun applyFilter() {
        val text = query.trim().lowercase()
        val list = if (text.isEmpty()) allEntries else allEntries.filter {
            it.label.lowercase().contains(text) || it.packageName.lowercase().contains(text)
        }
        appAdapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isEmpty()) empty.setText(R.string.tip_empty_app_list)
        selected?.let { entry ->
            if (list.none { it.packageName == entry.packageName && Users.id(it.user) == Users.id(entry.user) }) {
                hideSelection()
            }
        }
    }

    private fun clearQuery() {
        query = ""
        searchItem?.let { item ->
            (item.actionView as? SearchView)?.setQuery("", false)
            item.collapseActionView()
        }
        chipQuery.visibility = View.GONE
        applyFilter()
    }

    private fun selectApp(entry: AppEntry) {
        selected = entry
        appAdapter.selected(entry.packageName)
        sheetToolbar.title = entry.label
        summary.text = entry.summary(this) + if (entry.managed) "" else ", unmanaged"
        packageName.text = getString(R.string.field_app_id, entry.packageName)
        debugInfo.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        debugInfo.text = "user=${Users.id(entry.user)} uid=${entry.info.uid} hidden=${entry.hidden}"
        updateAppActions(entry)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun hideSelection() {
        selected = null
        appAdapter.selected(null)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun updateAppActions(entry: AppEntry) {
        val menu = sheetToolbar.menu
        menu.findItem(R.id.menu_freeze).isVisible =
            entry.installed && entry.managed && !entry.hidden && entry.enabled
        menu.findItem(R.id.menu_unfreeze).isVisible = entry.installed && entry.managed && entry.hidden
        menu.findItem(R.id.menu_clone).isVisible =
            Users.isOwner(entry.user) && Users.hasProfile(this)
        menu.findItem(R.id.menu_suspend).isVisible = false
        menu.findItem(R.id.menu_app_settings).isVisible = entry.installed
        menu.findItem(R.id.menu_greenify).isVisible = entry.installed && entry.managed && entry.enabled
        menu.findItem(R.id.menu_shortcut).isVisible =
            entry.installed && entry.managed && entry.launchable && entry.enabled
        menu.findItem(R.id.menu_remove).isVisible = entry.installed
        menu.findItem(R.id.menu_uninstall).isVisible = entry.installed && !entry.system
        menu.findItem(R.id.menu_reinstall).isVisible = !entry.installed
        menu.findItem(R.id.menu_uninstall).isVisible = entry.installed && !entry.system && !Users.isOwner(entry.user)
    }

    private fun onAppAction(item: MenuItem): Boolean {
        val entry = selected ?: return false
        when (item.itemId) {
            R.id.menu_freeze -> {
                val ok = repository.setHidden(entry, true)
                Toast.makeText(
                    this,
                    if (ok) R.string.action_freeze else R.string.toast_error_freeze_failure,
                    Toast.LENGTH_SHORT
                ).show()
                afterChange()
            }
            R.id.menu_unfreeze -> {
                val ok = repository.setHidden(entry, false)
                Toast.makeText(
                    this,
                    if (ok) R.string.action_unfreeze else R.string.toast_error_unfreeze_failure,
                    Toast.LENGTH_SHORT
                ).show()
                afterChange()
            }
            R.id.menu_clone -> cloneSelected(entry)
            R.id.menu_app_settings -> if (!repository.openAppSettings(entry)) {
                Toast.makeText(this, R.string.toast_app_launch_error, Toast.LENGTH_SHORT).show()
            }
            R.id.menu_shortcut -> repository.requestPinShortcut(entry)
            R.id.menu_greenify -> explainGreenify(entry)
            R.id.menu_remove, R.id.menu_uninstall -> removeSelected(entry)
            R.id.menu_reinstall -> {
                val ok = repository.reinstallSystemApp(entry)
                toast(ok, R.string.action_reinstall)
                afterChange()
            }
            else -> return false
        }
        return true
    }

    private fun cloneSelected(entry: AppEntry) {
        if (!Users.isOwner(entry.user)) {
            Toast.makeText(this, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.prompt_clone_title)
            .setMessage(R.string.prompt_clone_text)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_clone) { _, _ ->
                Thread {
                    val ok = repository.cloneToProfile(entry.packageName)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (ok) getString(R.string.toast_successfully_cloned, entry.label)
                            else getString(R.string.toast_cannot_clone, entry.label),
                            Toast.LENGTH_LONG
                        ).show()
                        reloadApps()
                    }
                }.apply { isDaemon = true }.start()
            }
            .show()
    }

    private fun removeSelected(entry: AppEntry) {
        if (entry.system) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_warning)
                .setMessage(R.string.prompt_disable_sys_app_as_removal)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_continue) { _, _ ->
                    repository.openAppSettings(entry)
                }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_uninstall)
            .setMessage(getString(R.string.field_app_id, entry.packageName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_uninstall) { _, _ ->
                if (!repository.requestUninstall(entry)) {
                    Toast.makeText(this, R.string.toast_app_launch_error, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun explainGreenify(entry: AppEntry) {
        if (!Greenify.isInstalled(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_greenify_title)
                .setMessage(R.string.dialog_greenify_explanation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_install) { _, _ -> Greenify.openInMarket(this) }
                .show()
            return
        }
        if (!Greenify.isSupported(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_greenify_title)
                .setMessage(getString(R.string.dialog_greenify_explanation) + "\n\n" + getString(R.string.dialog_greenify_version_too_low))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_upgrade) { _, _ -> Greenify.openInMarket(this) }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_greenify_title)
            .setMessage(R.string.dialog_greenify_explanation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_activate) { _, _ ->
                val ok = repository.greenify(entry)
                toast(ok, R.string.toast_greenify_failed)
            }
            .show()
    }

    private fun afterChange() {
        handler.postDelayed({ reloadApps() }, 250)
    }

    private fun launchApp(entry: AppEntry) {
        if (!repository.launch(entry)) {
            Toast.makeText(this, getString(R.string.toast_app_launch_failure, entry.label), Toast.LENGTH_LONG).show()
        }
    }

    private fun buildFeatured(): List<FeaturedItem> {
        val items = ArrayList<FeaturedItem>()
        val hasProfile = Users.hasProfile(this)
        val managed = Users.isOwner() && com.easmsy.core.DevicePolicies(this).isProfileOrDeviceOwnerOnCallingUser()

        if (!managed) {
            items += FeaturedItem(
                tag = "managed_mainland",
                title = getString(R.string.featured_managed_mainland_title),
                description = getString(R.string.featured_managed_mainland_description),
                icon = 0,
                button = getString(R.string.featured_button_setup)
            ) { startActivity(Intent(this, SettingsActivity::class.java)) }
        }

        items += FeaturedItem(
            tag = "file_shuttle",
            title = getString(R.string.featured_file_shuttle_title),
            description = getString(R.string.featured_file_shuttle_description),
            icon = 0,
            button = getString(if (Prefs.fileShuttle(this)) R.string.action_disable else R.string.action_activate)
        ) { toggleFileShuttle() }

        val development = try {
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: RuntimeException) {
            false
        }
        if (development || AdbSecure.isEnabled(this)) {
            items += FeaturedItem(
                tag = "adb_secure",
                title = getString(
                    if (managed) R.string.featured_adb_secure_title
                    else R.string.featured_adb_secure_island_title
                ),
                description = getString(R.string.featured_adb_secure_description),
                icon = 0,
                button = getString(if (AdbSecure.isEnabled(this)) R.string.action_disable else R.string.action_enable)
            ) {
                val enable = !AdbSecure.isEnabled(this)
                if (AdbSecure.toggle(this, enable)) {
                    Toast.makeText(
                        this,
                        if (enable) R.string.action_enable else R.string.action_disable,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, R.string.prompt_operation_failure_due_to_incompatibility, Toast.LENGTH_LONG).show()
                }
                showFeatured()
            }
        }

        addInstallCard(items, "com.oasisfeng.greenify", R.string.featured_greenify_title,
            R.string.featured_greenify_description, R.drawable.ic_launcher_greenify)
        addInstallCard(items, "app.gwo.safenhancer.lite", R.string.featured_saf_enhancer_title,
            R.string.featured_saf_enhancer_description, R.drawable.ic_launcher_saf_enhancer)
        addInstallCard(items, "rikka.appops", R.string.featured_appops_title,
            R.string.featured_appops_description, R.drawable.ic_launcher_appops)
        addInstallCard(items, "nextapp.fx", R.string.featured_fx_title,
            R.string.featured_fx_description, R.drawable.ic_launcher_fx)

        if (!hasProfile) {
            // The setup card is still useful if provisioning was cancelled.
            if (items.none { it.tag == "managed_mainland" }) {
                items.add(
                    0,
                    FeaturedItem(
                        tag = "setup",
                        title = getString(R.string.featured_managed_mainland_title),
                        description = getString(R.string.featured_managed_mainland_description),
                        icon = 0,
                        button = getString(R.string.featured_button_setup)
                    ) { startActivity(Intent(this, SetupActivity::class.java)) }
                )
            }
        }
        return items
    }

    private fun addInstallCard(
        items: MutableList<FeaturedItem>,
        pkg: String,
        title: Int,
        description: Int,
        icon: Int
    ) {
        val installed = try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (installed) return
        items += FeaturedItem(
            tag = pkg,
            title = getString(title),
            description = getString(description),
            icon = icon,
            button = getString(R.string.action_install)
        ) { repository.installFromMarket(pkg) }
    }

    private fun toggleFileShuttle() {
        val target = if (Prefs.fileShuttle(this)) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, ShuttleProvider::class.java),
                target,
                PackageManager.DONT_KILL_APP
            )
            Prefs.devicePrefs(this).edit().putBoolean(Prefs.KEY_FILE_SHUTTLE, target == PackageManager.COMPONENT_ENABLED_STATE_ENABLED).apply()
            val profile = Users.profile(this)
            val profileContext = profile?.let { Users.contextAsUser(this, it) }
            if (profileContext != null) {
                profileContext.packageManager.setComponentEnabledSetting(
                    ComponentName(profileContext, ShuttleProvider::class.java),
                    target,
                    PackageManager.DONT_KILL_APP
                )
                Prefs.devicePrefs(profileContext).edit()
                    .putBoolean(Prefs.KEY_FILE_SHUTTLE, target == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    .apply()
            }
            Toast.makeText(
                this,
                if (target == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    R.string.action_activate else R.string.action_disable,
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.prompt_operation_failure_due_to_incompatibility, Toast.LENGTH_LONG).show()
        }
        showFeatured()
    }

    private fun toast(ok: Boolean, successString: Int) {
        Toast.makeText(this, if (ok) successString else R.string.toast_app_launch_error, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_actions, menu)
        val search = menu.findItem(R.id.menu_search)
        searchItem = search
        val view = search.actionView as? SearchView
        if (view != null) {
            view.queryHint = getString(R.string.menu_search)
            view.imeOptions = EditorInfo.IME_ACTION_SEARCH
            view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean {
                    query = text ?: ""
                    applyFilter()
                    return true
                }

                override fun onQueryTextChange(text: String?): Boolean {
                    query = text ?: ""
                    chipQuery.text = query
                    chipQuery.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                    applyFilter()
                    return true
                }
            })
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_tip)?.isVisible = !featuredMode
        menu.findItem(R.id.menu_search)?.isVisible = !featuredMode
        menu.findItem(R.id.menu_filter)?.isVisible = !featuredMode
        menu.findItem(R.id.menu_settings)?.isVisible = true
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_filter -> {
                chips.visibility = if (chips.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_tip -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.menu_tip)
                    .setMessage(R.string.tip_guide)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Intent.ACTION_SEARCH == intent.action) {
            val text = intent.getStringExtra(SearchManager.QUERY) ?: ""
            query = text
            applyFilter()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, tabs.selectedTabPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        loadToken++
        super.onDestroy()
    }

    companion object {
        private const val STATE_TAB = "tab"
    }
}
