package com.cnapcapture.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cnapcapture.app.R
import com.cnapcapture.app.data.CnapEntry
import com.cnapcapture.app.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Main screen of the app – displays the history of captured CNAP names and lets the user:
 *  - Search entries by name or phone number
 *  - Add an unknown number to Contacts with the captured CNAP name pre-filled
 *  - Delete individual entries (long-press) or clear the whole log
 *  - Navigate to the Accessibility settings to enable the capture service
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: CnapEntryAdapter

    // -----------------------------------------------------------------------
    // Permission launcher (runtime permissions)
    // -----------------------------------------------------------------------

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        binding.cardPermissionWarning.visibility = if (allGranted) View.GONE else View.VISIBLE
    }

    // -----------------------------------------------------------------------
    // Activity lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        observeEntries()
        checkPermissions()
        setupFab()

        binding.btnGrantPermissions.setOnClickListener {
            openAppSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate permission state and FAB visibility every time the user comes back
        // (they may have granted permissions or toggled the accessibility service).
        checkPermissions()
        updateFabVisibility()
    }

    // -----------------------------------------------------------------------
    // Menu
    // -----------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setSearchQuery("")
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_enable_accessibility -> {
            openAccessibilitySettings()
            true
        }
        R.id.action_clear_all -> {
            confirmAndClearAll()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // -----------------------------------------------------------------------
    // RecyclerView setup
    // -----------------------------------------------------------------------

    private fun setupRecyclerView() {
        adapter = CnapEntryAdapter(
            onAddToContacts = { entry -> launchAddToContacts(entry) },
            onDelete = { entry -> deleteEntryWithUndo(entry) }
        )
        binding.rvCallLog.layoutManager = LinearLayoutManager(this)
        binding.rvCallLog.adapter = adapter
    }

    private fun observeEntries() {
        viewModel.entries.observe(this) { entries ->
            adapter.submitList(entries)
            binding.tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            binding.rvCallLog.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // -----------------------------------------------------------------------
    // Contact suggestion
    // -----------------------------------------------------------------------

    /**
     * Opens the system's "Create Contact" screen with the CNAP name and phone number
     * pre-filled, so the user can save the contact with a single tap.
     */
    private fun launchAddToContacts(entry: CnapEntry) {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS))
            return
        }
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, entry.cnapName)
            putExtra(ContactsContract.Intents.Insert.PHONE, entry.phoneNumber)
            putExtra(
                ContactsContract.Intents.Insert.PHONE_TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            )
        }
        startActivity(intent)
    }

    // -----------------------------------------------------------------------
    // Delete with undo
    // -----------------------------------------------------------------------

    private fun deleteEntryWithUndo(entry: CnapEntry) {
        viewModel.deleteEntry(entry)
        Snackbar.make(binding.root, R.string.snack_entry_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.snack_undo) {
                viewModel.restoreEntry(entry)
            }
            .show()
    }

    // -----------------------------------------------------------------------
    // Clear all
    // -----------------------------------------------------------------------

    private fun confirmAndClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(R.string.confirm_clear_message)
            .setPositiveButton(R.string.action_confirm) { _, _ -> viewModel.deleteAll() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // -----------------------------------------------------------------------
    // FAB: enable accessibility service
    // -----------------------------------------------------------------------

    private fun setupFab() {
        binding.fabEnableService.setOnClickListener { openAccessibilitySettings() }
        updateFabVisibility()
    }

    private fun updateFabVisibility() {
        // Hide the FAB once the accessibility service is enabled – no need to remind the user.
        binding.fabEnableService.visibility =
            if (isAccessibilityServiceEnabled()) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val serviceName = "${packageName}/${com.cnapcapture.app.service.CnapAccessibilityService::class.java.name}"
        return enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // -----------------------------------------------------------------------
    // Permissions
    // -----------------------------------------------------------------------

    private fun checkPermissions() {
        val required = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        val missing = required.filter { !hasPermission(it) }
        if (missing.isEmpty()) {
            binding.cardPermissionWarning.visibility = View.GONE
        } else {
            binding.cardPermissionWarning.visibility = View.VISIBLE
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }
}
