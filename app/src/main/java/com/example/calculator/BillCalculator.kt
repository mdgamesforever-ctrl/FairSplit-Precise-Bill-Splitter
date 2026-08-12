package com.example.calculator

import com.example.model.BillState
import com.example.model.SplitMode
import kotlin.math.ceil
import kotlin.math.round

data class PersonShareResult(
    val personId: String,
    val personName: String,
    val itemsSubtotal: Double,
    val taxShare: Double,
    val tipShare: Double,
    val rawTotal: Double,
    val finalTotalRounded: Double,
    val orderedItemNames: List<String>,
    val roundingAdjustment: Double = 0.0 // non-zero if 1-2 cent reconciliation was applied
)

data class SplitCalculationResult(
    val isValidInput: Boolean,
    val validationError: String? = null,
    val rawBill: Double = 0.0,
    val rawTax: Double = 0.0,
    val tipBase: Double = 0.0,
    val tipPct: Double = 0.0,
    val tipAmount: Double = 0.0,
    val rawGrandTotal: Double = 0.0,
    val isRoundedUp: Boolean = false,
    val generosityBonus: Double = 0.0,
    val finalGrandTotal: Double = 0.0,
    val tipPerPersonEven: Double = 0.0,
    val totalPerPersonEven: Double = 0.0,
    val personShares: List<PersonShareResult> = emptyList(),
    val sumOfShares: Double = 0.0,
    val roundingNote: String? = null
)

object BillCalculator {

    fun calculate(state: BillState): SplitCalculationResult {
        val billVal = state.billInput.toDoubleOrNull()
        if (billVal == null || billVal <= 0.0) {
            return SplitCalculationResult(
                isValidInput = false,
                validationError = "Enter a valid bill amount greater than 0"
            )
        }

        val people = state.people
        if (people.isEmpty()) {
            return SplitCalculationResult(
                isValidInput = false,
                validationError = "Add at least 1 person to split the bill"
            )
        }

        val taxVal = (state.taxInput.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        val tipPct = (if (state.isCustomTip) state.customTipInput.toDoubleOrNull() else state.tipPercentage)
            ?.coerceAtLeast(0.0) ?: 0.0

        // Tip base calculation: pre-tax or post-tax
        val tipBase = if (state.isTaxIncludedInTipBase) {
            billVal + taxVal
        } else {
            billVal
        }

        val rawTipAmount = tipBase * (tipPct / 100.0)
        val rawGrandTotal = billVal + taxVal + rawTipAmount

        val (finalGrandTotal, generosityBonus) = if (state.isRoundUp) {
            val roundedCeil = ceil(rawGrandTotal)
            val bonus = roundedCeil - rawGrandTotal
            Pair(roundedCeil, bonus)
        } else {
            Pair(rawGrandTotal, 0.0)
        }

        val count = people.size
        val tipPerPersonEven = rawTipAmount / count
        val totalPerPersonEven = finalGrandTotal / count

        val personShares = mutableListOf<PersonShareResult>()

        if (state.splitMode == SplitMode.EVEN || state.items.isEmpty()) {
            // Even Split logic
            val rawSharePerPerson = finalGrandTotal / count
            val roundedShare = roundToCents(rawSharePerPerson)

            people.forEach { person ->
                personShares.add(
                    PersonShareResult(
                        personId = person.id,
                        personName = person.name,
                        itemsSubtotal = billVal / count,
                        taxShare = taxVal / count,
                        tipShare = tipPerPersonEven,
                        rawTotal = rawSharePerPerson,
                        finalTotalRounded = roundedShare,
                        orderedItemNames = emptyList(),
                        roundingAdjustment = 0.0
                    )
                )
            }
        } else {
            // Itemized / Uneven Split logic
            // 1. Calculate each person's food subtotal from assigned items
            val personFoodSubtotals = mutableMapOf<String, Double>()
            val personItemNames = mutableMapOf<String, MutableList<String>>()
            people.forEach {
                personFoodSubtotals[it.id] = 0.0
                personItemNames[it.id] = mutableListOf()
            }

            var unassignedItemsTotal = 0.0

            state.items.forEach { item ->
                if (item.price > 0) {
                    val assigned = item.assignedPersonIds.filter { id -> people.any { p -> p.id == id } }
                    if (assigned.isNotEmpty()) {
                        val sharePrice = item.price / assigned.size
                        assigned.forEach { pId ->
                            personFoodSubtotals[pId] = (personFoodSubtotals[pId] ?: 0.0) + sharePrice
                            personItemNames[pId]?.add(item.name.ifBlank { "Item ($${String.format("%.2f", item.price)})" })
                        }
                    } else {
                        unassignedItemsTotal += item.price
                    }
                }
            }

            // Distribute unassigned items evenly among everyone
            if (unassignedItemsTotal > 0) {
                val perPersonUnassigned = unassignedItemsTotal / count
                people.forEach { p ->
                    personFoodSubtotals[p.id] = (personFoodSubtotals[p.id] ?: 0.0) + perPersonUnassigned
                    personItemNames[p.id]?.add("Shared items ($${String.format("%.2f", unassignedItemsTotal)})")
                }
            }

            val totalCalculatedFood = personFoodSubtotals.values.sum()

            people.forEach { person ->
                val pId = person.id
                val foodSubtotal = personFoodSubtotals[pId] ?: 0.0

                // Proportional ratio of total food bill
                val ratio = if (totalCalculatedFood > 0) {
                    foodSubtotal / totalCalculatedFood
                } else {
                    1.0 / count
                }

                val personBillShare = billVal * ratio
                val personTaxShare = taxVal * ratio
                val personTipShare = rawTipAmount * ratio
                val personBonusShare = generosityBonus * ratio

                val personRawTotal = personBillShare + personTaxShare + personTipShare + personBonusShare
                val roundedTotal = roundToCents(personRawTotal)

                personShares.add(
                    PersonShareResult(
                        personId = person.id,
                        personName = person.name,
                        itemsSubtotal = foodSubtotal,
                        taxShare = personTaxShare,
                        tipShare = personTipShare,
                        rawTotal = personRawTotal,
                        finalTotalRounded = roundedTotal,
                        orderedItemNames = personItemNames[pId] ?: emptyList(),
                        roundingAdjustment = 0.0
                    )
                )
            }
        }

        // --- Exact Sum Reconciliation Step ---
        val sumOfRounded = personShares.sumOf { it.finalTotalRounded }
        val grandTotalTargetRounded = roundToCents(finalGrandTotal)
        val diffCents = round((grandTotalTargetRounded - sumOfRounded) * 100).toInt()

        var roundingNote: String? = null

        if (diffCents != 0 && personShares.isNotEmpty()) {
            // Find person with the largest share (or first person if all equal)
            val largestPersonIndex = personShares.indices.maxByOrNull { personShares[it].finalTotalRounded } ?: 0
            val target = personShares[largestPersonIndex]

            val adjValue = diffCents / 100.0
            val updatedFinal = target.finalTotalRounded + adjValue

            personShares[largestPersonIndex] = target.copy(
                finalTotalRounded = updatedFinal,
                roundingAdjustment = adjValue
            )

            val centSign = if (diffCents > 0) "+${diffCents}¢" else "${diffCents}¢"
            roundingNote = "Rounding adjustment ($centSign) applied to ${target.personName}'s share so totals sum to exactly \$${String.format("%.2f", grandTotalTargetRounded)}"
        }

        val totalSumOfShares = personShares.sumOf { it.finalTotalRounded }

        return SplitCalculationResult(
            isValidInput = true,
            rawBill = billVal,
            rawTax = taxVal,
            tipBase = tipBase,
            tipPct = tipPct,
            tipAmount = rawTipAmount,
            rawGrandTotal = rawGrandTotal,
            isRoundedUp = state.isRoundUp,
            generosityBonus = generosityBonus,
            finalGrandTotal = grandTotalTargetRounded,
            tipPerPersonEven = tipPerPersonEven,
            totalPerPersonEven = totalPerPersonEven,
            personShares = personShares,
            sumOfShares = totalSumOfShares,
            roundingNote = roundingNote
        )
    }

    private fun roundToCents(amount: Double): Double {
        return round(amount * 100.0) / 100.0
    }
}
