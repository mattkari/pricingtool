package com.pricingtool.test.hooks;

import com.pricingtool.test.config.PlaywrightContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

public class PlaywrightHooks {

    @Autowired
    private PlaywrightContext playwrightContext;

    @Before("@ui")
    public void setUp() {
        playwrightContext.init();
    }

    @After("@ui")
    public void tearDown() {
        playwrightContext.close();
    }
}
