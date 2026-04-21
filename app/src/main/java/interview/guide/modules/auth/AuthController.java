package interview.guide.modules.auth;

import interview.guide.common.result.Result;
import interview.guide.modules.auth.model.AuthResponse;
import interview.guide.modules.auth.model.AuthUserDTO;
import interview.guide.modules.auth.model.LoginRequest;
import interview.guide.modules.auth.model.RegisterRequest;
import interview.guide.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received: username={}", request.username());
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received: account={}", request.account());
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<AuthUserDTO> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(authService.getCurrentUser(authorization));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return Result.success(null);
    }
}
