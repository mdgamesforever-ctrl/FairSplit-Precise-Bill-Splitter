package com.example

import com.example.calculator.BillCalculator
import com.example.model.BillItem
import com.example.model.BillState
import com.example.model.Person
import com.example.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testEvenSplit_ExactSumReconciliation() {
    // $100 bill, 15% tip -> $115 total, split 3 ways.
    // 115 / 3 = 38.333333...
    // Rounded per person = $38.33. 38.33 * 3 = 114.99.
    // Reconciled person 1 gets +0.01 -> $38.34.
    // Sum = $115.00 EXACTLY.
    val state = BillState(
      billInput = "100.00",
      tipPercentage = 15.0,
      taxInput = "0.00",
      people = listOf(
        Person("1", "Alice"),
        Person("2", "Bob"),
        Person("3", "Charlie")
      )
    )

    val result = BillCalculator.calculate(state)
    assertTrue(result.isValidInput)
    assertEquals(115.00, result.finalGrandTotal, 0.001)

    val sum = result.personShares.sumOf { it.finalTotalRounded }
    assertEquals(115.00, sum, 0.0001)
    assertNotNull(result.roundingNote)
  }

  @Test
  fun testItemizedSplit_ProportionalTaxAndTip() {
    // Bill = $100, Tax = $10, Tip = 20% ($20) -> Grand Total = $130
    // Alice orders $60 item, Bob orders $40 item.
    // Alice food ratio = 60%, Bob food ratio = 40%
    // Alice total = 60 + 6 (tax) + 12 (tip) = $78
    // Bob total = 40 + 4 (tax) + 8 (tip) = $52
    // Sum = $130.00
    val alice = Person("1", "Alice")
    val bob = Person("2", "Bob")

    val state = BillState(
      billInput = "100.00",
      taxInput = "10.00",
      tipPercentage = 20.0,
      splitMode = SplitMode.ITEMIZED,
      people = listOf(alice, bob),
      items = listOf(
        BillItem("i1", "Steak", 60.0, setOf(alice.id)),
        BillItem("i2", "Pasta", 40.0, setOf(bob.id))
      )
    )

    val result = BillCalculator.calculate(state)
    assertTrue(result.isValidInput)
    assertEquals(130.00, result.finalGrandTotal, 0.001)

    val aliceShare = result.personShares.find { it.personId == alice.id }!!
    val bobShare = result.personShares.find { it.personId == bob.id }!!

    assertEquals(78.00, aliceShare.finalTotalRounded, 0.001)
    assertEquals(52.00, bobShare.finalTotalRounded, 0.001)
    assertEquals(130.00, result.sumOfShares, 0.001)
  }

  @Test
  fun testTaxInTipBaseToggle() {
    // Bill = $100, Tax = $10
    // Pre-tax tip base (default): Tip = 20% of $100 = $20. Grand Total = $130
    // Post-tax tip base: Tip = 20% of $110 = $22. Grand Total = $132
    val statePreTax = BillState(
      billInput = "100.00",
      taxInput = "10.00",
      tipPercentage = 20.0,
      isTaxIncludedInTipBase = false
    )
    val resPreTax = BillCalculator.calculate(statePreTax)
    assertEquals(20.00, resPreTax.tipAmount, 0.001)
    assertEquals(130.00, resPreTax.finalGrandTotal, 0.001)

    val statePostTax = BillState(
      billInput = "100.00",
      taxInput = "10.00",
      tipPercentage = 20.0,
      isTaxIncludedInTipBase = true
    )
    val resPostTax = BillCalculator.calculate(statePostTax)
    assertEquals(22.00, resPostTax.tipAmount, 0.001)
    assertEquals(132.00, resPostTax.finalGrandTotal, 0.001)
  }

  @Test
  fun testRoundUpMode() {
    // Bill = $42.30, Tip = 15% ($6.345) -> Raw Total = $48.645
    // Round Up -> Grand Total = $49.00, Generosity Bonus = $0.355
    val state = BillState(
      billInput = "42.30",
      tipPercentage = 15.0,
      taxInput = "0.00",
      isRoundUp = true
    )
    val result = BillCalculator.calculate(state)
    assertEquals(49.00, result.finalGrandTotal, 0.001)
    assertTrue(result.generosityBonus > 0)
  }

  @Test
  fun testInvalidInputValidation() {
    val stateZero = BillState(billInput = "0.00")
    val resZero = BillCalculator.calculate(stateZero)
    assertFalse(resZero.isValidInput)

    val stateEmptyPeople = BillState(people = emptyList())
    val resEmpty = BillCalculator.calculate(stateEmptyPeople)
    assertFalse(resEmpty.isValidInput)
  }
}

