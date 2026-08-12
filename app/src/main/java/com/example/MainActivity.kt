package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.SplitCalculationResult
import com.example.model.BillState
import com.example.model.SplitMode
import com.example.ui.components.AdBannerPlaceholder
import com.example.ui.components.HeroResultCard
import com.example.ui.components.HistoryDialog
import com.example.ui.components.ItemizedSplitSection
import com.example.ui.components.MainCalculatorInputs
import com.example.ui.components.ManagePeopleDialog
import com.example.ui.components.WhoPaysFirstDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val state by viewModel.state.collectAsState()
                val calcResult by viewModel.calculationResult.collectAsState()

                TabSplitApp(
                    state = state,
                    calcResult = calcResult,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSplitApp(
    state: BillState,
    calcResult: SplitCalculationResult,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showManagePeopleDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showWhoPaysDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Text(
                            text = "  TabSplit",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.resetAll()
                            Toast.makeText(context, "Calculator reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset all",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AdBannerPlaceholder()
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. HERO RESULTS CARD
            HeroResultCard(
                currencySymbol = state.currencySymbol,
                result = calcResult,
                peopleCount = state.people.size,
                isRoundUp = state.isRoundUp,
                onToggleRoundUp = { viewModel.toggleRoundUp() },
                onOpenWhoPays = {
                    viewModel.pickRandomWhoPays()
                    showWhoPaysDialog = true
                },
                onShare = {
                    if (calcResult.isValidInput) {
                        viewModel.saveCurrentToHistory()
                        shareSplitResults(context, state, calcResult)
                    }
                },
                onOpenHistory = { showHistoryDialog = true }
            )

            // 2. MAIN CALCULATOR INPUTS
            MainCalculatorInputs(
                billInput = state.billInput,
                onBillInputChange = { viewModel.updateBillInput(it) },
                currencySymbol = state.currencySymbol,
                onCurrencySymbolChange = { viewModel.selectCurrency(it) },
                selectedTipPct = state.tipPercentage,
                isCustomTip = state.isCustomTip,
                customTipInput = state.customTipInput,
                onSelectPresetTip = { viewModel.selectPresetTip(it) },
                onUpdateCustomTip = { viewModel.updateCustomTip(it) },
                people = state.people,
                onPeopleCountChange = { viewModel.setPeopleCount(it) },
                onOpenManageNames = { showManagePeopleDialog = true },
                taxInput = state.taxInput,
                onTaxInputChange = { viewModel.updateTaxInput(it) },
                isTaxIncludedInTipBase = state.isTaxIncludedInTipBase,
                onToggleTaxInTipBase = { viewModel.toggleTaxInTipBase() }
            )

            // 3. UNEVEN / ITEMIZED SPLIT SECTION
            ItemizedSplitSection(
                splitMode = state.splitMode,
                onSplitModeChange = { viewModel.setSplitMode(it) },
                currencySymbol = state.currencySymbol,
                items = state.items,
                people = state.people,
                onAddItem = { name, price, assignedIds -> viewModel.addItem(name, price, assignedIds) },
                onUpdateItem = { id, name, price, assignedIds -> viewModel.updateItem(id, name, price, assignedIds) },
                onRemoveItem = { id -> viewModel.removeItem(id) },
                calculationResult = calcResult
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- MODALS & DIALOGS ---
    if (showManagePeopleDialog) {
        ManagePeopleDialog(
            people = state.people,
            onAddPerson = { viewModel.addPerson() },
            onRemovePerson = { viewModel.removePerson(it) },
            onUpdateName = { id, name -> viewModel.updatePersonName(id, name) },
            onDismiss = { showManagePeopleDialog = false }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            history = state.history,
            onLoadEntry = { entry -> viewModel.loadHistoryEntry(entry) },
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showWhoPaysDialog) {
        WhoPaysFirstDialog(
            selectedPersonId = state.whoPaysFirstPersonId,
            people = state.people,
            onPickRandom = { viewModel.pickRandomWhoPays() },
            onDismiss = {
                viewModel.clearWhoPaysFirst()
                showWhoPaysDialog = false
            }
        )
    }
}

private fun shareSplitResults(context: Context, state: BillState, result: SplitCalculationResult) {
    val currency = state.currencySymbol
    val sb = StringBuilder()
    sb.appendLine("🧾 TabSplit Summary")
    sb.appendLine("Bill: $currency${String.format(Locale.US, "%.2f", result.rawBill)}")
    if (result.rawTax > 0) {
        sb.appendLine("Tax: $currency${String.format(Locale.US, "%.2f", result.rawTax)}")
    }
    sb.appendLine("Tip (${result.tipPct.toInt()}%): $currency${String.format(Locale.US, "%.2f", result.tipAmount)}")
    sb.appendLine("Grand Total: $currency${String.format(Locale.US, "%.2f", result.finalGrandTotal)}")
    sb.appendLine("-------------------------")

    if (state.splitMode == SplitMode.EVEN) {
        sb.appendLine("Split ${state.people.size} ways: $currency${String.format(Locale.US, "%.2f", result.totalPerPersonEven)} each")
    } else {
        sb.appendLine("Itemized Breakdown:")
        result.personShares.forEach { share ->
            sb.appendLine("• ${share.personName}: $currency${String.format(Locale.US, "%.2f", share.finalTotalRounded)}")
        }
    }

    sb.appendLine("\nCalculated with TabSplit")

    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, "Share Bill Breakdown")
    context.startActivity(shareIntent)
}
