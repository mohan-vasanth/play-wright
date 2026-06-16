package com.automation.playwright_framework;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class JMeterWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path reportDirectory = ArtifactPaths.HTML_REPORT_DIR;
        registry.addResourceHandler("/jmeter-reports/**")
                .addResourceLocations(reportDirectory.toUri().toString());
        registry.addResourceHandler("/dashboard-screenshots/**")
                .addResourceLocations(ArtifactPaths.SCREENSHOTS_DIR.toUri().toString());
    }
}
