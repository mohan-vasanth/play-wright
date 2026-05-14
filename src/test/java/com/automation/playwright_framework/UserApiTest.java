package com.automation.playwright_framework;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

public class UserApiTest {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String LOGIN_API_URL = System.getProperty(
            "tradenix.login.api.url",
            "http://ec2-52-74-80-143.ap-southeast-1.compute.amazonaws.com:9000/api/auth/login");
    private static final String USER_TENANTS_API_URL = System.getProperty(
            "tradenix.user.tenants.api.url",
            "http://ec2-52-74-80-143.ap-southeast-1.compute.amazonaws.com:9000/api/auth/users/tenants");
    private static final String ADMIN_USERNAME = System.getProperty("tradenix.admin.username", "prasanna");
    private static final String ADMIN_PASSWORD = System.getProperty("tradenix.admin.password", "123456");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "heisenberg");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "123456789");

    @Test
    public void adminLoginApiReturnsSuccess() {
        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", ADMIN_USERNAME,
                        "password", ADMIN_PASSWORD))
                .when()
                .post(LOGIN_API_URL)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.accessToken", notNullValue())
                .body("data.user.username", equalTo(ADMIN_USERNAME))
                .body("data.user.roles", hasItem("ADMIN"));
    }

    @Test
    public void userLoginApiReturnsSuccessForFirstTenantAndDepartment() {
        Map<String, Object> tenantResponse = getJson(USER_TENANTS_API_URL + "/" + USER_USERNAME);

        Map<String, Object> tenantData = (Map<String, Object>) tenantResponse.get("data");
        Number userId = (Number) tenantData.get("userId");
        List<Map<String, Object>> tenants = (List<Map<String, Object>>) tenantData.get("tenants");
        Number tenantId = (Number) tenants.get(0).get("id");

        Map<String, Object> departmentResponse = getJson(
                USER_TENANTS_API_URL + "?userId=" + userId.intValue() + "&tenantId=" + tenantId.intValue());

        Map<String, Object> departmentData = (Map<String, Object>) departmentResponse.get("data");
        List<Map<String, Object>> departments = (List<Map<String, Object>>) departmentData.get("departments");
        Number departmentId = (Number) departments.get(0).get("id");

        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", USER_USERNAME,
                        "password", USER_PASSWORD,
                        "userId", userId.intValue(),
                        "tenantId", tenantId.intValue(),
                        "departmentId", departmentId.intValue()))
                .when()
                .post(LOGIN_API_URL)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.accessToken", notNullValue())
                .body("data.user.username", equalTo("Heisenberg"));
    }

    private static Map<String, Object> getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Unexpected status " + response.statusCode() + " for " + url);
            }
            return GSON.fromJson(response.body(), Map.class);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request failed for " + url, e);
        }
    }
}
