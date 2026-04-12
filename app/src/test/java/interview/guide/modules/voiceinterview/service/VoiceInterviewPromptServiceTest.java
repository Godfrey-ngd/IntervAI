package interview.guide.modules.voiceinterview.service;

import interview.guide.modules.interview.skill.InterviewSkillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("语音面试提示词服务测试")
class VoiceInterviewPromptServiceTest {

  @Mock
  private InterviewSkillService skillService;

  @InjectMocks
  private VoiceInterviewPromptService promptService;

  @Test
  @DisplayName("存在 persona 时，返回 persona 并追加语音约束")
  void generateSystemPromptWithContext_withPersona_shouldContainPersonaAndConstraints() {
    InterviewSkillService.SkillDTO skill = new InterviewSkillService.SkillDTO(
      "java-backend",
      "Java后端",
      "desc",
      List.of(new InterviewSkillService.SkillCategoryDTO("backend", "后端基础", "CORE", "ref", true)),
      true,
      null,
      "你是资深Java后端面试官",
      null
    );
    when(skillService.getSkill("java-backend")).thenReturn(skill);

    String prompt = promptService.generateSystemPromptWithContext("java-backend", null);

    assertNotNull(prompt);
    assertTrue(prompt.contains("你是资深Java后端面试官"));
    assertTrue(prompt.contains("语音面试输出约束"));
  }

  @Test
  @DisplayName("skill 查询失败时，回退默认提示词")
  void generateSystemPromptWithContext_whenSkillThrows_shouldFallbackToDefault() {
    when(skillService.getSkill("unknown-skill"))
      .thenThrow(new RuntimeException("not found"));

    String prompt = promptService.generateSystemPromptWithContext("unknown-skill", null);

    assertNotNull(prompt);
    assertTrue(prompt.contains("你是一位专业的面试官"));
    assertTrue(prompt.contains("语音面试输出约束"));
  }

  @Test
  @DisplayName("传入简历时，追加简历上下文")
  void generateSystemPromptWithContext_withResume_shouldContainResumeSection() {
    when(skillService.getSkill("java-backend"))
      .thenThrow(new RuntimeException("fallback"));

    String prompt = promptService.generateSystemPromptWithContext("java-backend", "候选人有三年后端经验");

    assertNotNull(prompt);
    assertTrue(prompt.contains("候选人简历内容"));
    assertTrue(prompt.contains("候选人有三年后端经验"));
  }
}
