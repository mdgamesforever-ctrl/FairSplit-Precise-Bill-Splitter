package com.example.model

enum class SplitMode {
    EVEN,
    ITEMIZED
}

data class Person(
    val id: String,
    val name: String
)

data class BillItem(
    val id: String,
    val name: String,
    val price: Double,
    val assignedPersonIds: Set<String> = emptySet()
)

data class HistoryEntry(
    val id: String,
    val timestampFormatted: String,
    val billAmount: Double,
    val tipPercentage: Double,
    val totalAmount: Double,
    val peopleCount: Int,
    val perPersonAmount: Double,
    val currencySymbol: String
)

data class BillState(
    val billInput: String = "100.00",
    val tipPercentage: Double = 18.0,
    val isCustomTip: Boolean = false,
    val customTipInput: String = "18",
    val taxInput: String = "8.875",
    val isTaxIncludedInTipBase: Boolean = false, // false = pre-tax tip, true = post-tax tip
    val people: List<Person> = listOf(
        Person("1", "Person 1"),
        Person("2", "Person 2")
    ),
    val currencySymbol: String = "$",
    val splitMode: SplitMode = SplitMode.EVEN,
    val items: List<BillItem> = emptyList(),
    val isRoundUp: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val whoPaysFirstPersonId: String? = null
)
