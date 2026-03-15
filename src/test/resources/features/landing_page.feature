@ui
Feature: EcoHaven Boots Landing Page

  As a user
  I want to visit the EcoHaven Boots website
  So that I can see the landing page with navigation options

  Scenario: User lands on the landing page and validates navigation
    Given the user navigates to the EcoHaven Boots website
    Then the user should be on the landing page
    And the navigation should contain "Home"
    And the navigation should contain "Products"
    And the navigation should contain "About"
