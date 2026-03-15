Feature: Loan pricing calculation
  As a pricing analyst
  I want to calculate interest rates for loan applications
  So that borrowers receive accurate pricing based on risk and tenor

  Scenario: Calculate price for standard loan
    Given a borrower with ID "BORROWER-001"
    And a loan amount of 100000
    And a tenor of 24 months
    And a risk grade of "B"
    When the pricing engine calculates the interest rate
    Then the interest rate should be 6.25%

  Scenario: Calculate and persist loan application
    Given a loan amount of 250000
    And a tenor of 36 months
    And a risk grade of "A"
    When the loan application is submitted
    Then the interest rate should be 6.00%
    And the loan application is persisted in the database

  Scenario Outline: Calculate price for multiple risk grades
    Given a loan amount of <amount>
    And a tenor of <tenorMonths> months
    And a risk grade of "<riskGrade>"
    When the pricing engine calculates the interest rate
    Then the interest rate should be <expectedRate>%

    Examples:
      | amount  | tenorMonths | riskGrade | expectedRate |
      | 50000   | 12          | A         | 5.50         |
      | 100000  | 24          | B         | 6.25         |
      | 150000  | 36          | C         | 7.50         |
      | 200000  | 48          | D         | 9.50         |
      | 75000   | 18          | A         | 5.75         |

  Scenario Outline: High-risk loans have higher rates
    Given a loan amount of 100000
    And a tenor of 24 months
    And a risk grade of "<riskGrade>"
    When the pricing engine calculates the interest rate
    Then the interest rate should be greater than 5.00%

    Examples:
      | riskGrade |
      | B         |
      | C         |
      | D         |
