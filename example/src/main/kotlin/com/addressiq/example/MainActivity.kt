@file:Suppress("MissingPackageDeclaration")

package com.addressiq.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.addressiq.android.AddressIQ
import com.addressiq.android.AddressIQConfig
import com.addressiq.android.AddressIQEnvironment
import com.addressiq.android.SdkUser
import com.addressiq.android.VerificationLifecycleState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val API_URL = "https://api.addressiq.com"
private const val API_KEY = "aiq_test_demo_bank_seed01"
private const val APP_USER_ID = "cust_sample_001"
private const val LOCATION_CODE = "loc_sample_demo"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SampleScreen()
            }
        }
    }
}

class SampleViewModel : ViewModel() {
    private val _log = mutableStateListOf<String>()
    val log: List<String> get() = _log

    private val _providers = mutableStateListOf<Map<String, Any?>>()
    val providers: List<Map<String, Any?>> get() = _providers

    val state: StateFlow<VerificationLifecycleState> = AddressIQ.stateFlow

    var verificationCode by mutableStateOf<String?>(null)
        private set

    private fun append(line: String) {
        _log += "${System.currentTimeMillis()}  $line"
    }

    fun initialize() {
        viewModelScope.launch {
            runCatching {
                AddressIQ.initialize(
                    AddressIQConfig(
                        apiKey = API_KEY,
                        apiUrl = API_URL,
                        environment = AddressIQEnvironment.SANDBOX,
                    ),
                )
                append("initialize: ok")
                val providers = AddressIQ.listProviders(type = "physical")
                _providers.clear()
                _providers.addAll(providers)
            }.onFailure { append("initialize: error ${it.message}") }
        }
    }

    fun setUser() = viewModelScope.launch {
        runCatching {
            AddressIQ.setUser(SdkUser(appUserId = APP_USER_ID, firstName = "Sample"))
            append("setUser: bound $APP_USER_ID")
        }.onFailure { append("setUser: error ${it.message}") }
    }

    fun startPhysical(slug: String) = viewModelScope.launch {
        runCatching {
            val res = AddressIQ.startPhysical(locationCode = LOCATION_CODE, provider = slug)
            verificationCode = res["verificationCode"] as? String
            append("startPhysical($slug): $verificationCode")
        }.onFailure { append("startPhysical: error ${it.message}") }
    }

    fun pause() = viewModelScope.launch { AddressIQ.pauseVerification(); append("pause: ok") }
    fun resume() = viewModelScope.launch {
        runCatching { AddressIQ.resumeVerification(); append("resume: ok") }
            .onFailure { append("resume: error ${it.message}") }
    }
    fun sync() = viewModelScope.launch { append("sync: flushed ${AddressIQ.sync()}") }
    fun cancel() = viewModelScope.launch {
        val code = verificationCode ?: return@launch
        runCatching {
            val out = AddressIQ.cancelVerification(code)
            append("cancel: ${out["status"]}")
        }.onFailure { append("cancel: error ${it.message}") }
    }
    fun logout() = viewModelScope.launch { AddressIQ.logout(); append("logout: ok") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(vm: SampleViewModel = viewModel()) {
    val state by vm.state.collectAsState(initial = AddressIQ.getVerificationState())
    LaunchedEffect(Unit) { vm.initialize() }

    Scaffold(topBar = { TopAppBar(title = { Text("AddressIQ — Android Sample") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Lifecycle state", style = MaterialTheme.typography.labelMedium)
                    Text(state.state.name, style = MaterialTheme.typography.titleLarge)
                    vm.verificationCode?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }

            SectionLabel("Lifecycle")
            Row(label = "initialize", onClick = vm::initialize)
            Row(label = "setUser", onClick = vm::setUser)
            Row(label = "pause", onClick = vm::pause)
            Row(label = "resume", onClick = vm::resume)
            Row(label = "sync", onClick = vm::sync)
            Row(label = "cancel", enabled = vm.verificationCode != null, onClick = vm::cancel)
            Row(label = "logout", onClick = vm::logout)

            SectionLabel("Start physical via…")
            if (vm.providers.isEmpty()) {
                Text("(no providers — call initialize)", style = MaterialTheme.typography.bodySmall)
            }
            vm.providers.forEach { provider ->
                val slug = provider["slug"] as? String ?: return@forEach
                val display = provider["displayName"] as? String ?: slug
                Row(label = display) { vm.startPhysical(slug) }
            }

            SectionLabel("Activity log")
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(vm.log) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun Row(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
    }
}
