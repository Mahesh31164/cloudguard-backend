
package com.cloudguard.backend;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;
    private final AppUserRepository repository;

    public UserController(
            UserService userService,
            AppUserRepository repository) {
        this.userService = userService;
        this.repository = repository;
    }

    @PostMapping
    public Map<String, Object> createUser(
            @RequestBody CreateUserRequest request,
            Authentication authentication) {

        if (!authentication.getName().equals("admin")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the administrator can create accounts.");
        }

        try {
            AppUser user = userService.createUser(
                    request.username(),
                    request.email(),
                    request.password());

            return Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "role", user.getRole(),
                    "message", "User account created successfully.");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping
    public List<Map<String, Object>> listUsers(
            Authentication authentication) {

        if (!authentication.getName().equals("admin")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the administrator can manage users.");
        }

        return repository.findAll().stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "role", user.getRole(),
                        "enabled", user.isEnabled()))
                .toList();
    }

    public record CreateUserRequest(
            String username,
            String email,
            String password) {}
}
