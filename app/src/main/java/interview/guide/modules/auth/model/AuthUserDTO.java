package interview.guide.modules.auth.model;

import java.time.LocalDateTime;

public record AuthUserDTO(
    Long id,
    String username,
    String email,
    String displayName,
    String role,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt
) {}
