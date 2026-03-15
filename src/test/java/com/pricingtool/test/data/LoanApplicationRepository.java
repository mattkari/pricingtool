package com.pricingtool.test.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for LoanApplication entity.
 */
@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    
    List<LoanApplication> findByBorrowerId(String borrowerId);
    
    List<LoanApplication> findByRiskGrade(String riskGrade);
}
