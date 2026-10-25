package com.realme.spamblocker.sms.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.realme.spamblocker.sms.SpamBlockerApp
import com.realme.spamblocker.sms.ui.screens.HomeScreen
import com.realme.spamblocker.sms.ui.theme.RealmeTheme
import com.realme.spamblocker.sms.ui.viewmodel.MessagesViewModel
import com.realme.spamblocker.sms.ui.viewmodel.MessagesViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MessagesViewModel

    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Automatically sync all existing SMS from system when default role is granted
        viewModel.syncExistingSms(this)
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Automatically sync if SMS permission granted
        viewModel.syncExistingSms(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SpamBlockerApp
        val factory = MessagesViewModelFactory(app.repository)
        viewModel = ViewModelProvider(this, factory)[MessagesViewModel::class.java]

        requestDefaultSmsRole()
        requestAppPermissions()

        setContent {
            RealmeTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onComposeClick = {
                        startActivity(Intent(this, ComposeActivity::class.java))
                    }
                )
            }
        }
    }

    private var lastSyncTime: Long = 0L

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (::viewModel.isInitialized && (now - lastSyncTime > 45000L)) {
            lastSyncTime = now
            viewModel.syncExistingSms(this)
        }
    }

    /**
     * Prompts the user to set this app as the default SMS app
     * so it can intercept, classify, and filter spam SMS.
     */
    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    defaultSmsRoleLauncher.launch(intent)
                }
            }
        } else {
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(this)
            if (defaultPackage != packageName) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
                defaultSmsRoleLauncher.launch(intent)
            }
        }
    }

    /**
     * Requests runtime contacts, SMS, and notification permissions
     */
    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
