package interview.guide.modules.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3 到 50 个字符之间")
    String username,

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱长度不能超过 120 个字符")
    String email,

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在 6 到 72 个字符之间")
    String password,

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    String displayName
) {}
