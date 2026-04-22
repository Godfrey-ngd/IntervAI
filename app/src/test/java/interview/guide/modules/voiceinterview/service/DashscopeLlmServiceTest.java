package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashscope LLM 服务测试")
class DashscopeLlmServiceTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;

  @Mock
  private VoiceInterviewPromptService promptService;

  @Mock
  private ResumeRepository resumeRepository;

  private DashscopeLlmService dashscopeLlmService;

  @BeforeEach
  void setUp() {
    VoiceInterviewProperties properties = new VoiceInterviewProperties();
    dashscopeLlmService = new DashscopeLlmService(
      llmProviderRegistry,
      promptService,
      resumeRepository,
      properties
    );

    lenient().when(promptService.generateSystemPromptWithContext(anyString(), any(), anyString())).thenReturn("system-prompt");
  }

  @Test
  @DisplayName("认证错误映射为用户可读消息")
  void chat_whenAuthenticationError_shouldReturnFriendlyMessage() {
    VoiceInterviewSessionEntity session = baseSession();
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("403 ACCESS_DENIED"));

    String result = dashscopeLlmService.chat("你好", session, Collections.emptyList());

    assertEquals("AI 服务认证失败，请检查 API Key 配置", result);
    verify(promptService).generateSystemPromptWithContext(eq("java-backend"), eq(null), eq("STRICT"));
  }

  @Test
  @DisplayName("超时错误映射为超时提示")
  void chat_whenTimeoutError_shouldReturnTimeoutMessage() {
    VoiceInterviewSessionEntity session = baseSession();
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("Request timeout after 30s"));

    String result = dashscopeLlmService.chat("你好", session, Collections.emptyList());

    assertEquals("AI 服务响应超时，请稍后重试", result);
  }

  @Test
  @DisplayName("限流错误映射为频率超限提示")
  void chat_whenRateLimitError_shouldReturnRateLimitMessage() {
    VoiceInterviewSessionEntity session = baseSession();
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("429 rate limit exceeded"));

    String result = dashscopeLlmService.chat("你好", session, Collections.emptyList());

    assertEquals("AI 服务调用频率超限，请稍后重试", result);
  }

  @Test
  @DisplayName("网络错误映射为网络提示")
  void chat_whenNetworkError_shouldReturnNetworkMessage() {
    VoiceInterviewSessionEntity session = baseSession();
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("connection refused"));

    String result = dashscopeLlmService.chat("你好", session, Collections.emptyList());

    assertEquals("AI 服务网络连接失败，请检查网络", result);
  }

  @Test
  @DisplayName("未知错误映射为兜底提示")
  void chat_whenUnknownError_shouldReturnDefaultMessage() {
    VoiceInterviewSessionEntity session = baseSession();
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("boom"));

    String result = dashscopeLlmService.chat("你好", session, Collections.emptyList());

    assertEquals("抱歉，AI 服务暂时不可用，请稍后重试", result);
  }

  @Test
  @DisplayName("带简历 ID 时会读取简历文本并传入提示词服务")
  void chat_whenResumeExists_shouldPassResumeTextToPromptService() {
    VoiceInterviewSessionEntity session = baseSession();
    session.setResumeId(100L);

    ResumeEntity resume = new ResumeEntity();
    resume.setResumeText("候选人具备三年后端开发经验");

    when(resumeRepository.findById(100L)).thenReturn(Optional.of(resume));
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("boom"));

    String result = dashscopeLlmService.chat("请开始", session, Collections.emptyList());

    assertNotNull(result);
    verify(promptService).generateSystemPromptWithContext(
      eq("java-backend"),
      eq("候选人具备三年后端开发经验"),
      eq("STRICT")
    );
  }

  @Test
  @DisplayName("chatStream 复用同一套错误映射")
  void chatStream_whenError_shouldReturnMappedMessage() {
    VoiceInterviewSessionEntity session = baseSession();
    when(llmProviderRegistry.getChatClientOrDefault("dashscope"))
      .thenThrow(new RuntimeException("403 Authentication failed"));

    String result = dashscopeLlmService.chatStream("hi", token -> {}, session, Collections.emptyList());

    assertEquals("AI 服务认证失败，请检查 API Key 配置", result);
  }

  @Test
  @DisplayName("空 session 会抛出空指针异常")
  void chat_whenSessionIsNull_shouldThrowNpe() {
    assertThrows(NullPointerException.class,
      () -> dashscopeLlmService.chat("hello", null, Collections.emptyList()));
  }

  private VoiceInterviewSessionEntity baseSession() {
    return VoiceInterviewSessionEntity.builder()
      .id(1L)
      .skillId("java-backend")
      .llmProvider("dashscope")
      .currentPhase(VoiceInterviewSessionEntity.InterviewPhase.INTRO)
      .build();
  }
}
