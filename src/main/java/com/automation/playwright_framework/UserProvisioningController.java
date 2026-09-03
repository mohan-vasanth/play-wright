package com.automation.playwright_framework;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/user-provisioning")
public class UserProvisioningController {

    private final UserProvisioningService provisioningService;

    public UserProvisioningController(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("user-provisioning-template.xlsx").build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(UserProvisioningWorkbook.createTemplate());
    }

    @GetMapping("/pool")
    public ResponseEntity<?> pool() {
        return ResponseEntity.ok(provisioningService.availableUsers());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("provisioned-load-test-users.xlsx").build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(provisioningService.exportUsers());
    }

    @PostMapping("/single")
    public ResponseEntity<UserProvisioningService.ProvisioningResponse> single(
            @RequestBody SingleUserRequest request) {
        return ResponseEntity.ok(provisioningService.provision(new UserProvisioningService.ProvisioningRequest(
                request.loginUrl(), request.adminUsername(), request.adminPassword(),
                List.of(new UserProvisioningService.UserInput(
                        request.username(), request.password(), request.email(), request.organizationRole(),
                        request.forwarder(), request.department())))));
    }

    @PostMapping("/generate")
    public ResponseEntity<UserProvisioningService.ProvisioningResponse> generate(
            @RequestBody GenerateUsersRequest request) {
        String prefix = request.usernamePrefix() == null || request.usernamePrefix().isBlank()
                ? "loadtest" : request.usernamePrefix().trim();
        int start = request.startSequence();
        int count = request.count();
        if (start < 1 || count < 1) {
            throw new IllegalArgumentException("Start sequence and user count must be at least 1.");
        }
        if (count > 500 || start > Integer.MAX_VALUE - count) {
            throw new IllegalArgumentException("Generate at most 500 users in a valid sequence range.");
        }
        String domain = request.emailDomain() == null ? "" : request.emailDomain().trim().replaceFirst("^@", "");
        if (domain.isBlank() || !domain.contains(".")) {
            throw new IllegalArgumentException("A valid email domain is required.");
        }
        List<UserProvisioningService.UserInput> users = IntStream.range(start, start + count)
                .mapToObj(number -> {
                    String username = prefix + number;
                    return new UserProvisioningService.UserInput(
                            username, request.password(), username + "@" + domain,
                            request.organizationRole(), request.forwarder(), request.department());
                }).toList();
        return ResponseEntity.ok(provisioningService.provision(new UserProvisioningService.ProvisioningRequest(
                request.loginUrl(), request.adminUsername(), request.adminPassword(), users)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProvisioningService.ProvisioningResponse> upload(
            @RequestParam("loginUrl") String loginUrl,
            @RequestParam("adminUsername") String adminUsername,
            @RequestParam("adminPassword") String adminPassword,
            @RequestParam("file") MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Upload an .xlsx file created from the provided template.");
        }
        return ResponseEntity.ok(provisioningService.provision(new UserProvisioningService.ProvisioningRequest(
                loginUrl, adminUsername, adminPassword, UserProvisioningWorkbook.parse(file.getBytes()))));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
    }

    public record SingleUserRequest(
            String loginUrl,
            String adminUsername,
            String adminPassword,
            String username,
            String password,
            String email,
            String organizationRole,
            String forwarder,
            String department) {
    }

    public record GenerateUsersRequest(
            String loginUrl,
            String adminUsername,
            String adminPassword,
            String usernamePrefix,
            int startSequence,
            int count,
            String password,
            String emailDomain,
            String organizationRole,
            String forwarder,
            String department) {
    }
}
