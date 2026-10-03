
package com.cloudguard.backend;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser createUser(
            String username, String email, String password) {

        username = username.trim();
        email = email.trim().toLowerCase();

        if (username.isBlank() || email.isBlank()
                || password == null || password.length() < 12) {
            throw new IllegalArgumentException(
                    "Enter a username, email, and password of at least 12 characters.");
        }

        if (username.equalsIgnoreCase("admin")) {
            throw new IllegalArgumentException(
                    "This username is reserved.");
        }

        if (repository.existsByUsername(username)) {
            throw new IllegalArgumentException(
                    "Username already exists.");
        }

        if (repository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email already exists.");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setEnabled(true);

        return repository.save(user);
    }
}
