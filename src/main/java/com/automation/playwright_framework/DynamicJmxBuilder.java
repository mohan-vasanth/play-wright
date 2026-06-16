package com.automation.playwright_framework;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class DynamicJmxBuilder {

    Path writePlan(Path outputPath, String targetUrl, int threads, int rampUpSeconds, int loopCount) throws IOException {
        URI uri = URI.create(targetUrl.trim());
        String protocol = uri.getScheme() != null ? uri.getScheme() : "http";
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        String port = uri.getPort() > 0 ? Integer.toString(uri.getPort()) : "";
        String path = normalizePath(uri);

        String plan = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan"
                              testname="Dynamic Load Testing Dashboard Plan" enabled="true">
                      <boolProp name="TestPlan.functional_mode">false</boolProp>
                      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
                      <elementProp name="TestPlan.user_defined_variables"
                                   elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments">
                        <collectionProp name="Arguments.arguments">
                          <elementProp name="HOST" elementType="Argument">
                            <stringProp name="Argument.name">HOST</stringProp>
                            <stringProp name="Argument.value">%s</stringProp>
                            <stringProp name="Argument.metadata">=</stringProp>
                          </elementProp>
                          <elementProp name="PORT" elementType="Argument">
                            <stringProp name="Argument.name">PORT</stringProp>
                            <stringProp name="Argument.value">%s</stringProp>
                            <stringProp name="Argument.metadata">=</stringProp>
                          </elementProp>
                          <elementProp name="PROTOCOL" elementType="Argument">
                            <stringProp name="Argument.name">PROTOCOL</stringProp>
                            <stringProp name="Argument.value">%s</stringProp>
                            <stringProp name="Argument.metadata">=</stringProp>
                          </elementProp>
                        </collectionProp>
                      </elementProp>
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup"
                                   testname="Dashboard Virtual Users" enabled="true">
                        <intProp name="ThreadGroup.num_threads">%d</intProp>
                        <intProp name="ThreadGroup.ramp_time">%d</intProp>
                        <boolProp name="ThreadGroup.scheduler">false</boolProp>
                        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController"
                                     guiclass="LoopControlPanel" testclass="LoopController">
                          <boolProp name="LoopController.continue_forever">false</boolProp>
                          <intProp name="LoopController.loops">%d</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement"
                                           testname="HTTP Request Defaults" enabled="true">
                          <stringProp name="HTTPSampler.domain">${HOST}</stringProp>
                          <stringProp name="HTTPSampler.port">${PORT}</stringProp>
                          <stringProp name="HTTPSampler.protocol">${PROTOCOL}</stringProp>
                          <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
                          <elementProp name="HTTPsampler.Arguments" elementType="Arguments"
                                       guiclass="HTTPArgumentsPanel" testclass="Arguments">
                            <collectionProp name="Arguments.arguments"/>
                          </elementProp>
                        </ConfigTestElement>
                        <hashTree/>
                        <CookieManager guiclass="CookiePanel" testclass="CookieManager"
                                       testname="HTTP Cookie Manager" enabled="true">
                          <collectionProp name="CookieManager.cookies"/>
                          <boolProp name="CookieManager.clearEachIteration">true</boolProp>
                          <boolProp name="CookieManager.controlledByThreadGroup">false</boolProp>
                        </CookieManager>
                        <hashTree/>
                        <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager"
                                       testname="HTTP Header Manager" enabled="true">
                          <collectionProp name="HeaderManager.headers">
                            <elementProp name="Accept" elementType="Header">
                              <stringProp name="Header.name">Accept</stringProp>
                              <stringProp name="Header.value">application/json,text/html,*/*</stringProp>
                            </elementProp>
                            <elementProp name="Connection" elementType="Header">
                              <stringProp name="Header.name">Connection</stringProp>
                              <stringProp name="Header.value">keep-alive</stringProp>
                            </elementProp>
                          </collectionProp>
                        </HeaderManager>
                        <hashTree/>
                        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy"
                                          testname="GET Target URL" enabled="true">
                          <stringProp name="HTTPSampler.path">%s</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
                          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
                          <elementProp name="HTTPsampler.Arguments" elementType="Arguments"
                                       guiclass="HTTPArgumentsPanel" testclass="Arguments">
                            <collectionProp name="Arguments.arguments"/>
                          </elementProp>
                        </HTTPSamplerProxy>
                        <hashTree>
                          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion"
                                             testname="Assert HTTP 200" enabled="true">
                            <collectionProp name="Asserion.test_strings">
                              <stringProp name="response-code-200">200</stringProp>
                            </collectionProp>
                            <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
                            <boolProp name="Assertion.assume_success">false</boolProp>
                            <intProp name="Assertion.test_type">8</intProp>
                          </ResponseAssertion>
                          <hashTree/>
                        </hashTree>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """.formatted(host, port, protocol, threads, rampUpSeconds, loopCount, path);

        Files.createDirectories(outputPath.toAbsolutePath().normalize().getParent());
        Files.writeString(outputPath, plan, StandardCharsets.UTF_8);
        return outputPath;
    }

    private String normalizePath(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return query == null || query.isBlank() ? path : path + "?" + query;
    }
}
