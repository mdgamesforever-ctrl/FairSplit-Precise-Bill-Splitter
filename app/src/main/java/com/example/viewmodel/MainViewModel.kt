package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.BillCalculator
import com.example.calculator.SplitCalculationResult
import com.example.model.BillItem
import com.example.model.BillState
import com.example.model.HistoryEntry
import com.example.model.Person
import com.example.model.SplitMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(BillState())
    val state: StateFlow<BillState> = _state.asStateFlow()

    val calculationResult: StateFlow<SplitCalculationResult> = _state
        .map { BillCalculator.calculate(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BillCalculator.calculate(_state.value)
        )

    fun updateBillInput(input: String) {
        val sanitized = sanitizeCurrencyInput(input)
        _state.value = _state.value.copy(billInput = sanitized)
    }

    fun selectPresetTip(pct: Double) {
        _state.value = _state.value.copy(
            tipPercentage = pct,
            isCustomTip = false,
            customTipInput = if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()
        )
    }

    fun updateCustomTip(input: String) {
        val filtered = input.filter { it.isDigit() || it == '.' }
        val parts = filtered.split('.')
        val sanitized = if (parts.size > 2) {
            parts[0] + "." + parts.subList(1, parts.size).joinToString("")
        } else filtered

        val pct = sanitized.toDoubleOrNull() ?: 0.0
        _state.value = _state.value.copy(
            isCustomTip = true,
            customTipInput = sanitized,
            tipPercentage = pct
        )
    }

    fun updateTaxInput(input: String) {
        val sanitized = sanitizeCurrencyInput(input)
        _state.value = _state.value.copy(taxInput = sanitized)
    }

    fun toggleTaxInTipBase() {
        _state.value = _state.value.copy(
            isTaxIncludedInTipBase = !_state.value.isTaxIncludedInTipBase
        )
    }

    fun setPeopleCount(count: Int) {
        val targetCount = count.coerceIn(1, 50)
        val currentPeople = _state.value.people.toMutableList()

        if (targetCount > currentPeople.size) {
            for (i in (currentPeople.size + 1)..targetCount) {
                currentPeople.add(Person(UUID.randomUUID().toString(), "Person $i"))
            }
        } else if (targetCount < currentPeople.size) {
            val toRemove = currentPeople.subList(targetCount, currentPeople.size).map { it.id }.toSet()
            currentPeople.subList(targetCount, currentPeople.size).clear()

            // Remove assigned person IDs from items
            val updatedItems = _state.value.items.map { item ->
                item.copy(assignedPersonIds = item.assignedPersonIds - toRemove)
            }
            _state.value = _state.value.copy(items = updatedItems)
        }

        _state.value = _state.value.copy(people = currentPeople)
    }

    fun addPerson(customName: String? = null) {
        val current = _state.value.people.toMutableList()
        val nextIndex = current.size + 1
        val name = customName?.ifBlank { null } ?: "Person $nextIndex"
        current.add(Person(UUID.randomUUID().toString(), name))
        _state.value = _state.value.copy(people = current)
    }

    fun removePerson(personId: String) {
        if (_state.value.people.size <= 1) return
        val updated = _state.value.people.filterNot { it.id == personId }

        val updatedItems = _state.value.items.map { item ->
            item.copy(assignedPersonIds = item.assignedPersonIds - personId)
        }

        _state.value = _state.value.copy(
            people = updated,
            items = updatedItems,
            whoPaysFirstPersonId = if (_state.value.whoPaysFirstPersonId == personId) null else _state.value.whoPaysFirstPersonId
        )
    }

    fun updatePersonName(personId: String, newName: String) {
        val updated = _state.value.people.map {
            if (it.id == personId) it.copy(name = newName) else it
        }
        _state.value = _state.value.copy(people = updated)
    }

    fun setSplitMode(mode: SplitMode) {
        _state.value = _state.value.copy(splitMode = mode)
    }

    fun addItem(name: String, price: Double, assignedPersonIds: Set<String>) {
        val newItem = BillItem(
            id = UUID.randomUUID().toString(),
            name = name,
            price = price,
            assignedPersonIds = assignedPersonIds
        )
        _state.value = _state.value.copy(items = _state.value.items + newItem)
    }

    fun updateItem(itemId: String, name: String, price: Double, assignedPersonIds: Set<String>) {
        val updated = _state.value.items.map {
            if (it.id == itemId) it.copy(name = name, price = price, assignedPersonIds = assignedPersonIds) else it
        }
        _state.value = _state.value.copy(items = updated)
    }

    fun removeItem(itemId: String) {
        val updated = _state.value.items.filterNot { it.id == itemId }
        _state.value = _state.value.copy(items = updated)
    }

    fun toggleRoundUp() {
        _state.value = _state.value.copy(isRoundUp = !_state.value.isRoundUp)
    }

    fun selectCurrency(symbol: String) {
        _state.value = _state.value.copy(currencySymbol = symbol)
    }

    fun pickRandomWhoPays() {
        val people = _state.value.people
        if (people.isNotEmpty()) {
            val randomPerson = people.random()
            _state.value = _state.value.copy(whoPaysFirstPersonId = randomPerson.id)
        }
    }

    fun clearWhoPaysFirst() {
        _state.value = _state.value.copy(whoPaysFirstPersonId = null)
    }

    fun saveCurrentToHistory() {
        val res = calculationResult.value
        if (!res.isValidInput) return

        val currentState = _state.value
        val dateFormat = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val newEntry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            timestampFormatted = dateStr,
            billAmount = res.rawBill,
            tipPercentage = res.tipPct,
            totalAmount = res.finalGrandTotal,
            peopleCount = currentState.people.size,
            perPersonAmount = res.totalPerPersonEven,
            currencySymbol = currentState.currencySymbol
        )

        val updatedHistory = (listOf(newEntry) + currentState.history).take(5)
        _state.value = currentState.copy(history = updatedHistory)
    }

    fun loadHistoryEntry(entry: HistoryEntry) {
        _state.value = _state.value.copy(
            billInput = String.format(Locale.US, "%.2f", entry.billAmount),
            tipPercentage = entry.tipPercentage,
            customTipInput = entry.tipPercentage.toString(),
            currencySymbol = entry.currencySymbol
        )
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = emptyList())
    }

    fun resetAll() {
        _state.value = BillState(history = _state.value.history)
    }

    private fun sanitizeCurrencyInput(input: String): String {
        val filtered = input.filter { it.isDigit() || it == '.' }
        if (filtered.isEmpty()) return ""

        val parts = filtered.split('.')
        return when {
            parts.size == 1 -> parts[0]
            parts.size >= 2 -> {
                val integerPart = parts[0]
                val decimalPart = parts[1].take(2)
                "$integerPart.$decimalPart"
            }
            else -> filtered
        }
    }
}
