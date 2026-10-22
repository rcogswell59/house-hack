package io.initialcapacity.househack.analyzer

import kotlin.math.max
import kotlin.math.pow

object FinancingCalculator {
    private const val FHA_RENT_HAIRCUT = 0.75

    fun amortizedPayment(principal: Double, annualRate: Double, months: Int = 360): Double {
        if (annualRate == 0.0) return principal / months
        val r = annualRate / 12
        return principal * r / (1 - (1 + r).pow(-months))
    }

    /**
     * Florida reassesses at sale, so taxes are based on price, not the seller's bill.
     * Homestead applies only to the owner-occupied share (1/units); rented units are taxed in full.
     */
    fun annualTaxes(price: Double, units: Int, millage: Double, homesteadExemption: Double): Double {
        val ownerShare = price / units
        val taxable = max(ownerShare - homesteadExemption, 0.0) + (price - ownerShare)
        return taxable * millage
    }

    fun buyerView(i: FinancingInputs): BuyerView {
        val loan = loanAmount(i)
        val pAndI = amortizedPayment(loan, i.mortgageRate)
        val mip = loan * i.annualMipRate / 12
        val taxes = annualTaxes(i.price, i.units, i.millage, i.homesteadExemption) / 12
        val insurance = i.price * i.insuranceRate / 12
        val otherUnitsRent = i.rentPerUnit * (i.units - 1)
        val reserves = otherUnitsRent * (i.vacancyRate + i.maintenanceRate)

        return BuyerView(
            loanAmount = loan,
            principalAndInterest = pAndI,
            monthlyMip = mip,
            monthlyTaxes = taxes,
            monthlyInsurance = insurance,
            reserves = reserves,
            otherUnitsRent = otherUnitsRent,
            netMonthlyCost = pAndI + mip + taxes + insurance + reserves - otherUnitsRent,
            cashToClose = i.price * i.downPaymentRate + i.price * i.closingCostRate - i.assistanceTotal,
        )
    }

    fun lenderView(i: FinancingInputs): LenderView {
        val buyer = buyerView(i)
        val piti = buyer.principalAndInterest + buyer.monthlyMip + buyer.monthlyTaxes + buyer.monthlyInsurance
        val qualifyingRent = FHA_RENT_HAIRCUT * buyer.otherUnitsRent
        val dti = (piti - qualifyingRent + i.otherMonthlyDebts) / i.monthlyIncome
        val dtiPass = dti <= i.maxDti

        val selfSufficiencyRatio = if (i.units >= 3) FHA_RENT_HAIRCUT * i.rentPerUnit * i.units / piti else null
        val selfSufficiencyPass = selfSufficiencyRatio?.let { it >= 1.0 }

        return LenderView(
            piti = piti,
            qualifyingRent = qualifyingRent,
            dti = dti,
            dtiPass = dtiPass,
            selfSufficiencyRatio = selfSufficiencyRatio,
            selfSufficiencyPass = selfSufficiencyPass,
            pass = dtiPass && (selfSufficiencyPass ?: true),
        )
    }

    private fun loanAmount(i: FinancingInputs) = i.price * (1 - i.downPaymentRate) * (1 + i.upfrontMipRate)
}
