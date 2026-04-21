package interview.guide.modules.auth.model;

public record AuthResponse(
    String token,
    String tokenType,
    AuthUserDTO user
) {}
