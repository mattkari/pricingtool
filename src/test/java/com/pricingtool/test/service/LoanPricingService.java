package com.pricingtool.test.service;

import com.pricingtool.test.data.LoanApplication;
import com.pricingtool.test.data.LoanApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for loan pricing calculations.
 * Implements deterministic pricing logic based on risk grade and tenor.
 */
@Service
public class LoanPricingService {
    
    private static final BigDecimal BASE_RATE = new BigDecimal("5.00");
    
    @Autowired
    private LoanApplicationRepository repository;

    /**
     * Calculate interest rate based on loan parameters.
     * Formula: Base Rate + Risk Margin + Tenor Margin
     */
    public BigDecimal calculateRate(BigDecimal principal, int tenorMonths, String riskGrade) {
        BigDecimal riskMargin = getRiskMargin(riskGrade);
        BigDecimal tenorMargin = getTenorMargin(tenorMonths);
        
        return BASE_RATE.add(riskMargin).add(tenorMargin).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Create and persist a loan application with calculated rate.
     */
    @Transactional
    public LoanApplication createLoanApplication(String borrowerId, String productCode, 
                                                  BigDecimal amount, int tenorMonths, 
                                                  String riskGrade) {
        BigDecimal rate = calculateRate(amount, tenorMonths, riskGrade);
        
        LoanApplication application = new LoanApplication();
        application.setBorrowerId(borrowerId);
        application.setProductCode(productCode);
        application.setAmount(amount);
        application.setTenorMonths(tenorMonths);
        application.setRiskGrade(riskGrade);
        application.setInterestRate(rate);
        
        return repository.save(application);
    }

    /**
     * Retrieve loan application by ID.
     */
    @Transactional(readOnly = true)
    public LoanApplication getLoanApplication(Long id) {
        return repository.findById(id).orElse(null);
    }

    private BigDecimal getRiskMargin(String riskGrade) {
        return switch (riskGrade.toUpperCase()) {
            case "A" -> new BigDecimal("0.50");
            case "B" -> new BigDecimal("1.00");
            case "C" -> new BigDecimal("2.00");
            case "D" -> new BigDecimal("3.50");
            default -> new BigDecimal("5.00");
        };
    }

    private BigDecimal getTenorMargin(int tenorMonths) {
        if (tenorMonths <= 12) {
            return new BigDecimal("0.00");
        } else if (tenorMonths <= 24) {
            return new BigDecimal("0.25");
        } else if (tenorMonths <= 36) {
            return new BigDecimal("0.50");
        } else {
            return new BigDecimal("1.00");
        }
    }
}
