Feature: Sample Test

  Scenario: Save test data
    Given I have a test value "test1"
    When I save test data "test1"
    Then the test data should be persisted
