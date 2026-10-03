package com.cloudguard.backend;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/aws-connection")
public class AwsConnectionController {

    private final AwsConnectionService connectionService;

    public AwsConnectionController(AwsConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public Map<String, Object> getStatus() {
        return connectionService.getStatus();
    }

    @PostMapping
    public Map<String, Object> connect(@Valid @RequestBody ConnectRequest request) {
        try {
            return connectionService.connect(
                    request.accountId(), request.roleArn(), request.externalId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping
    public Map<String, Object> disconnect() {
        connectionService.disconnect();
        return connectionService.getStatus();
    }

    public record ConnectRequest(
            @NotBlank
            @Pattern(regexp = "\\d{12}", message = "Account ID must contain 12 digits.")
            String accountId,
            @NotBlank String roleArn,
            @NotBlank String externalId) {
    }
}
