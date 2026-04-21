package interview.guide.modules.auth.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.model.AuthResponse;
import interview.guide.modules.auth.model.AuthUserDTO;
import interview.guide.modules.auth.model.AuthUserEntity;
import interview.guide.modules.auth.model.LoginRequest;
import interview.guide.modules.auth.model.RegisterRequest;
import interview.guide.modules.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String DEFAULT_ROLE = "USER";

    private final AuthUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        String email = normalize(request.email());
        String displayName = normalize(request.displayName());

        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.AUTH_USER_EXISTS, "用户名或邮箱已存在");
        }

        AuthUserEntity entity = AuthUserEntity.builder()
            .username(username)
            .email(email)
            .displayName(displayName)
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(DEFAULT_ROLE)
            .enabled(true)
            .build();

        AuthUserEntity saved = userRepository.save(entity);
        log.info("User registered: username={}, email={}", saved.getUsername(), saved.getEmail());
        return buildResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String account = normalize(request.account());
        AuthUserEntity user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(account, account)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "账号或密码错误"));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED, "账号已被禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "账号或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        AuthUserEntity saved = userRepository.save(user);
        log.info("User logged in: username={}", saved.getUsername());
        return buildResponse(saved);
    }

    public AuthUserDTO getCurrentUser(String authorizationHeader) {
        return toDTO(resolveUserFromAuthorization(authorizationHeader));
    }

    public void logout(String authorizationHeader) {
        resolveUserFromAuthorization(authorizationHeader);
        log.info("User logged out");
    }

    private AuthResponse buildResponse(AuthUserEntity user) {
        return new AuthResponse(generateToken(user), TOKEN_TYPE, toDTO(user));
    }

    private AuthUserDTO toDTO(AuthUserEntity user) {
        return new AuthUserDTO(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.isEnabled(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }

    private AuthUserEntity resolveUserFromAuthorization(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效");
        }

        String[] parts = decoded.split(":", 4);
        if (parts.length < 4) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效");
        }

        Long userId;
        try {
            userId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效");
        }

        String username = parts[1];
        return userRepository.findById(userId)
            .filter(user -> username.equalsIgnoreCase(user.getUsername()))
            .filter(AuthUserEntity::isEnabled)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效"));
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING, "未携带登录令牌");
        }

        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效");
        }

        String token = trimmed.substring(7).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_MISSING, "未携带登录令牌");
        }
        return token;
    }

    private String generateToken(AuthUserEntity user) {
        String payload = user.getId() + ":" + user.getUsername() + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
