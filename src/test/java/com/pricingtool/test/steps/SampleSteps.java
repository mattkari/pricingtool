package com.pricingtool.test.steps;

import com.pricingtool.test.entity.TestData;
import com.pricingtool.test.repository.TestDataRepository;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

public class SampleSteps {
    
    @Autowired
    private TestDataRepository repository;
    
    private Long testDataId;

    @Given("I have a test value {string}")
    public void iHaveATestValue(String value) {
        // Setup step
    }

    @When("I save test data {string}")
    public void iSaveTestData(String value) {
        TestData data = new TestData();
        data.setValue(value);
        TestData saved = repository.save(data);
        testDataId = saved.getId();
    }

    @Then("the test data should be persisted")
    public void theTestDataShouldBePersisted() {
        assertThat(repository.findById(testDataId)).isPresent();
    }
}
