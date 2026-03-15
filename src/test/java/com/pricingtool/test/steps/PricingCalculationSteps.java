package com.pricingtool.test.steps;

import com.pricingtool.test.context.ScenarioContext;
import com.pricingtool.test.data.LoanApplication;
import com.pricingtool.test.service.LoanPricingService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for pricing calculation scenarios.
 */
public class PricingCalculationSteps {
    
    @Autowired
    private LoanPricingService pricingService;
    
    @Autowired
    private ScenarioContext scenarioContext;

    @Given("a borrower with ID {string}")
    public void aBorrowerWithID(String borrowerId) {
        // Store for later use if needed
    }

    @Given("a loan amount of {bigdecimal}")
    public void aLoanAmountOf(BigDecimal amount) {
        scenarioContext.setLoanAmount(amount);
    }

    @Given("a tenor of {int} months")
    public void aTenorOfMonths(Integer months) {
        scenarioContext.setTenorMonths(months);
    }

    @Given("a risk grade of {string}")
    public void aRiskGradeOf(String riskGrade) {
        scenarioContext.setRiskGrade(riskGrade);
    }

    @When("the pricing engine calculates the interest rate")
    public void thePricingEngineCalculatesTheInterestRate() {
        BigDecimal rate = pricingService.calculateRate(
            scenarioContext.getLoanAmount(),
            scenarioContext.getTenorMonths(),
            scenarioContext.getRiskGrade()
        );
        scenarioContext.setCalculatedRate(rate);
    }

    @When("the loan application is submitted")
    public void theLoanApplicationIsSubmitted() {
        LoanApplication application = pricingService.createLoanApplication(
            "BORROWER-001",
            "STANDARD",
            scenarioContext.getLoanAmount(),
            scenarioContext.getTenorMonths(),
            scenarioContext.getRiskGrade()
        );
        scenarioContext.setLoanApplicationId(application.getId());
        scenarioContext.setCalculatedRate(application.getInterestRate());
    }

    @Then("the interest rate should be {bigdecimal}%")
    public void theInterestRateShouldBe(BigDecimal expectedRate) {
        assertThat(scenarioContext.getCalculatedRate())
            .isNotNull()
            .isEqualByComparingTo(expectedRate);
    }

    @Then("the loan application is persisted in the database")
    public void theLoanApplicationIsPersistedInTheDatabase() {
        Long id = scenarioContext.getLoanApplicationId();
        assertThat(id).isNotNull();
        
        LoanApplication retrieved = pricingService.getLoanApplication(id);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getAmount()).isEqualByComparingTo(scenarioContext.getLoanAmount());
        assertThat(retrieved.getTenorMonths()).isEqualTo(scenarioContext.getTenorMonths());
        assertThat(retrieved.getRiskGrade()).isEqualTo(scenarioContext.getRiskGrade());
    }

    @Then("the interest rate should be greater than {bigdecimal}%")
    public void theInterestRateShouldBeGreaterThan(BigDecimal minRate) {
        assertThat(scenarioContext.getCalculatedRate())
            .isNotNull()
            .isGreaterThan(minRate);
    }
}
