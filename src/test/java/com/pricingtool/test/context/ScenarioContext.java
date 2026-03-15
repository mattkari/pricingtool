package com.pricingtool.test.context;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@ScenarioScope
public class ScenarioContext {
    private BigDecimal loanAmount;
    private Integer tenorMonths;
    private String riskGrade;
    private BigDecimal calculatedRate;
    private Long loanApplicationId;

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(BigDecimal loanAmount) {
        this.loanAmount = loanAmount;
    }

    public Integer getTenorMonths() {
        return tenorMonths;
    }

    public void setTenorMonths(Integer tenorMonths) {
        this.tenorMonths = tenorMonths;
    }

    public String getRiskGrade() {
        return riskGrade;
    }

    public void setRiskGrade(String riskGrade) {
        this.riskGrade = riskGrade;
    }

    public BigDecimal getCalculatedRate() {
        return calculatedRate;
    }

    public void setCalculatedRate(BigDecimal calculatedRate) {
        this.calculatedRate = calculatedRate;
    }

    public Long getLoanApplicationId() {
        return loanApplicationId;
    }

    public void setLoanApplicationId(Long loanApplicationId) {
        this.loanApplicationId = loanApplicationId;
    }
}
