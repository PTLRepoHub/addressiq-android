@file:Suppress("MissingPackageDeclaration")

package com.addressiq.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.addressiq.android.AddressIQ
import com.addressiq.android.AddressIQConfig
import com.addressiq.android.AddressIQEnvironment
import com.addressiq.android.SdkUser
import com.addressiq.android.VerificationLifecycleState
import com.addressiq.android.ui.AddressIQVerifyContract
import com.addressiq.android.ui.AddressIQVerifyInput
import com.addressiq.android.ui.AddressIQVerifyResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val API_URL = "https://api.addressiqpro.com"
private const val API_KEY = "aiq_test_demo_bank_seed01"

/**
 * OkHi-style §6 screen-canon sample (Kotlin / Compose):
 *   Login → Verification hub → Helpers → Addresses → Developer → Settings.
 *
 * Human labels live on the hub; raw SDK method names appear only on the
 * Developer screen. Track A (Collect UI via [AddressIQVerifyContract]) and
 * Track B (imperative `AddressIQ.*`) are both exercised.
 */
class MainActivity : ComponentActivity() {

    private lateinit var verifyLauncher: ActivityResultLauncher<AddressIQVerifyInput>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: SampleViewModel = viewModel()
                verifyLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    AddressIQVerifyContract(),
                ) { result ->
                    when (result) {
                        is AddressIQVerifyResult.Completed ->
                            vm.onCollectComplete(this@MainActivity, result.locationCode)
                        is AddressIQVerifyResult.Failed ->
                            vm.present("Collect failed", "[${result.code}] ${result.message}")
                        is AddressIQVerifyResult.Cancelled -> { /* no-op */ }
                    }
                }
                AppRoot(
                    vm = vm,
                    onLaunchCollect = {
                        verifyLauncher.launch(
                            AddressIQVerifyInput(
                                apiKey = API_KEY,
                                appUserId = vm.appUserId,
                                environment = vm.environment,
                                // Fallback name; the widget fetches the real
                                // business identity from the backend.
                                businessName = vm.businessName.ifBlank { null },
                                // Point at a local backend for development.
                                apiUrlOverride = vm.localApiUrl.ifBlank { null },
                            ),
                        )
                    },
                )
            }
        }
    }
}

class SampleViewModel : ViewModel() {
    // Login inputs.
    var apiKey by mutableStateOf(API_KEY)
    var appUserId by mutableStateOf("cust_sample_001")
    var environment by mutableStateOf(AddressIQEnvironment.SANDBOX)

    // Demo / local-dev options.
    /** Fallback business name; the widget normally gets it from the backend. */
    var businessName by mutableStateOf("Kuda Business")
    /** Point the widget's API at a local backend (e.g. http://localhost:3355). */
    var localApiUrl by mutableStateOf("")

    // Session-derived state.
    var loggedIn by mutableStateOf(false)
        private set
    var tab by mutableStateOf(0)
    var lifecycle by mutableStateOf("uninitialized")
        private set
    var verificationCode by mutableStateOf<String?>(null)
        private set
    val locationCodes = mutableStateListOf<String>()
    var permissions by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Result/error modal payload (P1-4): a title + a preformatted body + the
     *  resulting verification type (DIGITAL / PHYSICAL / COMBINED) when relevant. */
    var dialogTitle by mutableStateOf<String?>(null)
        private set
    var dialogBody by mutableStateOf("")
        private set
    var dialogType by mutableStateOf<String?>(null)
        private set

    val state: StateFlow<VerificationLifecycleState> = AddressIQ.stateFlow

    fun present(title: String, body: String, type: String? = null) {
        dialogTitle = title
        dialogBody = body
        dialogType = type
    }

    fun dismissDialog() {
        dialogTitle = null
    }

    private fun refreshLifecycle() {
        lifecycle = AddressIQ.getVerificationState().state.name
    }

    private fun remember(locationCode: String?) {
        if (locationCode.isNullOrEmpty()) return
        if (!locationCodes.contains(locationCode)) locationCodes.add(locationCode)
    }

    fun workingLocationCode(): String = locationCodes.lastOrNull() ?: "loc_sample_demo"

    fun onCollectComplete(context: android.content.Context, locationCode: String) {
        remember(locationCode)
        // The Collect UI collects only — the host starts verification here.
        startDigital(context, locationCode)
    }

    // ─── Track B — SDK API ────────────────────────────────────────────────

    fun login() = viewModelScope.launch {
        runCatching {
            AddressIQ.initialize(AddressIQConfig(apiKey = apiKey, apiUrl = API_URL, environment = environment))
            AddressIQ.setUser(SdkUser(appUserId = appUserId, firstName = "Sample"))
        }.onSuccess {
            loggedIn = true
            refreshLifecycle()
        }.onFailure { present("Login failed", it.message ?: it.toString()) }
    }

    fun startDigital(context: android.content.Context, locationCode: String) = viewModelScope.launch {
        runCatching { AddressIQ.startVerification(context = context, locationCode = locationCode) }
            .onSuccess {
                verificationCode = it["verificationCode"] as? String
                remember(locationCode)
                refreshLifecycle()
                present("Digital verification started", pretty(it), "DIGITAL")
            }
            .onFailure { present("Digital verification failed", it.message ?: it.toString()) }
    }

    fun startPhysical(context: android.content.Context, locationCode: String, provider: String) = viewModelScope.launch {
        runCatching { AddressIQ.startPhysicalVerification(context = context, locationCode = locationCode, provider = provider) }
            .onSuccess {
                verificationCode = it["verificationCode"] as? String
                remember(locationCode)
                refreshLifecycle()
                present("Physical verification started", pretty(it), "PHYSICAL")
            }
            .onFailure { present("Physical verification failed", it.message ?: it.toString()) }
    }

    fun startCombined(context: android.content.Context, locationCode: String) = viewModelScope.launch {
        runCatching {
            AddressIQ.startDigitalAndPhysicalVerification(
                context = context,
                locationCode = locationCode,
                physicalProvider = "internal_agents",
            )
        }
            .onSuccess {
                verificationCode = it["verificationCode"] as? String
                remember(locationCode)
                refreshLifecycle()
                present("Combined verification started", pretty(it), "COMBINED")
            }
            .onFailure { present("Combined verification failed", it.message ?: it.toString()) }
    }

    fun cancel(context: android.content.Context) = viewModelScope.launch {
        val code = verificationCode ?: run { present("cancelVerification", "No active verification"); return@launch }
        runCatching { AddressIQ.cancelVerification(code) }
            .onSuccess { refreshLifecycle(); present("cancelVerification", pretty(it)) }
            .onFailure { present("cancelVerification error", it.message ?: it.toString()) }
    }

    fun showState() {
        val st = AddressIQ.getVerificationState()
        present(
            "getVerificationState",
            "state: ${st.state.name}\nappUserId: ${st.appUserId}\nverificationId: ${st.verificationId}\nlocationCode: ${st.locationCode}",
        )
    }

    fun refreshPermissions(context: android.content.Context) {
        permissions = AddressIQ.getPermissionState(context)
    }

    fun logout() = viewModelScope.launch {
        AddressIQ.logout()
        loggedIn = false
        refreshLifecycle()
    }

    fun reset() = viewModelScope.launch {
        AddressIQ.reset()
        loggedIn = false
        verificationCode = null
        locationCodes.clear()
        refreshLifecycle()
    }

    private fun pretty(map: Map<String, Any?>): String =
        map.entries.joinToString("\n") { "${it.key}: ${it.value}" }
}

// ─── Composables ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: SampleViewModel, onLaunchCollect: () -> Unit) {
    if (vm.dialogTitle != null) {
        AlertDialog(
            onDismissRequest = vm::dismissDialog,
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text("Close") } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.dialogTitle ?: "", modifier = Modifier.weight(1f))
                    vm.dialogType?.let { TypeChip(it) }
                }
            },
            text = { Text(vm.dialogBody, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        )
    }

    if (!vm.loggedIn) {
        LoginScreen(vm)
        return
    }

    val tabs = listOf("Verify", "Helpers", "Addresses", "Developer", "Settings")
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, name ->
                    NavigationBarItem(
                        selected = vm.tab == i,
                        onClick = { vm.tab = i },
                        icon = { Text(name.first().toString()) },
                        label = { Text(name) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (vm.tab) {
                0 -> HubScreen(vm, onLaunchCollect)
                1 -> HelpersScreen(vm)
                2 -> AddressesScreen(vm)
                3 -> DeveloperScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(vm: SampleViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("AddressIQ Sample") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sign in", fontSize = 22.sp)
            OutlinedTextField(value = vm.apiKey, onValueChange = { vm.apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.appUserId, onValueChange = { vm.appUserId = it }, label = { Text("App user ID") }, modifier = Modifier.fillMaxWidth())
            Text("Environment: ${vm.environment.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.environment = AddressIQEnvironment.SANDBOX }) { Text("Sandbox") }
                FilledTonalButton(onClick = { vm.environment = AddressIQEnvironment.PRODUCTION }) { Text("Production") }
            }
            Text("Local development", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            OutlinedTextField(value = vm.localApiUrl, onValueChange = { vm.localApiUrl = it }, label = { Text("Local API URL (optional)") }, placeholder = { Text("http://10.0.2.2:3355") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.businessName, onValueChange = { vm.businessName = it }, label = { Text("Business name (fallback)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { vm.login() }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            Text("Calls initialize() then setUser().", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HubScreen(vm: SampleViewModel, onLaunchCollect: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by vm.state.collectAsState(initial = AddressIQ.getVerificationState())
    val code = vm.workingLocationCode()
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card { Column(Modifier.padding(16.dp)) {
            Text("Lifecycle", fontSize = 12.sp)
            Text(state.state.name, fontSize = 20.sp)
            vm.verificationCode?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        } }
        SectionLabel("Collect")
        FilledTonalButton(onClick = onLaunchCollect, modifier = Modifier.fillMaxWidth()) { Text("Collect Address") }
        SectionLabel("Verify an address ($code)")
        FilledTonalButton(onClick = { vm.startDigital(context, code) }, modifier = Modifier.fillMaxWidth()) { Text("Digital Verification") }
        FilledTonalButton(onClick = { vm.startPhysical(context, code, "internal_agents") }, modifier = Modifier.fillMaxWidth()) { Text("Physical Verification") }
        FilledTonalButton(onClick = { vm.startCombined(context, code) }, modifier = Modifier.fillMaxWidth()) { Text("Digital + Physical") }
    }
}

@Composable
private fun HelpersScreen(vm: SampleViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshPermissions(context) }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Permission state", fontSize = 18.sp)
        for ((key, value) in vm.permissions) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(key)
                Text(value, fontFamily = FontFamily.Monospace)
            }
        }
        FilledTonalButton(onClick = { vm.refreshPermissions(context) }) { Text("Refresh") }
        Text("Values ∈ { GRANTED, DENIED, NOT_DETERMINED, BLOCKED, UNAVAILABLE }.", fontSize = 12.sp)
    }
}

@Composable
private fun AddressesScreen(vm: SampleViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (vm.locationCodes.isEmpty()) {
        Column(Modifier.padding(24.dp)) { Text("No addresses yet. Collect one from the Verify tab.") }
        return
    }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved Addresses", fontSize = 18.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.locationCodes) { code ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text(code, fontFamily = FontFamily.Monospace)
                    TextButton(onClick = { vm.startDigital(context, code) }) { Text("Verify digitally →") }
                } }
            }
        }
    }
}

@Composable
private fun DeveloperScreen(vm: SampleViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("locationCode: ${vm.workingLocationCode()}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        SectionLabel("Raw start* calls")
        FilledTonalButton(onClick = { vm.startDigital(context, vm.workingLocationCode()) }, modifier = Modifier.fillMaxWidth()) { Text("startVerification(...)") }
        FilledTonalButton(onClick = { vm.startPhysical(context, vm.workingLocationCode(), "internal_agents") }, modifier = Modifier.fillMaxWidth()) { Text("startPhysicalVerification(...)") }
        FilledTonalButton(onClick = { vm.startCombined(context, vm.workingLocationCode()) }, modifier = Modifier.fillMaxWidth()) { Text("startDigitalAndPhysicalVerification(...)") }
        SectionLabel("Lifecycle")
        FilledTonalButton(onClick = vm::showState, modifier = Modifier.fillMaxWidth()) { Text("getVerificationState()") }
        FilledTonalButton(onClick = { vm.cancel(context) }, modifier = Modifier.fillMaxWidth()) { Text("cancelVerification(code)") }
    }
}

@Composable
private fun SettingsScreen(vm: SampleViewModel) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Environment"); Text(vm.environment.name) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("App user"); Text(vm.appUserId) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Lifecycle"); Text(vm.lifecycle) }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        FilledTonalButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Reset SDK") }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label.uppercase(), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Coloured chip for the resulting verification type (DIGITAL / PHYSICAL / COMBINED). */
@Composable
private fun TypeChip(type: String) {
    val c = when (type) {
        "DIGITAL" -> Color(0xFF3B82F6)
        "PHYSICAL" -> Color(0xFF8B5CF6)
        "COMBINED" -> Color(0xFF14B8A6)
        else -> Color(0xFF6B7280)
    }
    Text(
        type,
        color = c,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(c.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
