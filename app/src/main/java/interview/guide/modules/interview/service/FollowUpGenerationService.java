package interview.guide.modules.interview.service;

import interview.guide.common.constant.CommonConstants.InterviewDefaults;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.skill.InterviewSkillService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowUpGenerationService {

    private static final int MAX_RECENT_CONVERSATION = 6;

    private final PromptTemplate followUpSystemPromptTemplate;
    private final PromptTemplate followUpUserPromptTemplate;
    private final InterviewSkillService skillService;
    private final InterviewerPersonaService interviewerPersonaService;

    public FollowUpGenerationService(ResourceLoader resourceLoader,
                                     InterviewSkillService skillService,
                                     InterviewerPersonaService interviewerPersonaService) throws IOException {
        this.skillService = skillService;
        this.interviewerPersonaService = interviewerPersonaService;
        this.followUpSystemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/interview-followup-system.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.followUpUserPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/interview-followup-user.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
    }

    /**
     * @param rootMainQuestion 该话题对应的主问题（锚点）
     * @param allQuestions     全量题单（含已答）
     * @param answeredQuestionIndex 候选人刚答完的题在列表中的下标（主问题或任一追问）
     */
    public String generateFollowUp(ChatClient chatClient,
                                   String skillId,
                                   String personaType,
                                   InterviewQuestionDTO rootMainQuestion,
                                   String userAnswer,
                                   List<InterviewQuestionDTO> allQuestions,
                                   int answeredQuestionIndex) {
        String basePersona = loadSkillPersona(skillId);
        String personaSection = interviewerPersonaService.buildPersonaSection(basePersona, personaType);
        InterviewQuestionDTO answeredQ = allQuestions.get(answeredQuestionIndex);
        Map<String, Object> variables = new HashMap<>();
        variables.put("personaSection", personaSection);
        variables.put("rootMainQuestion", rootMainQuestion.question());
        variables.put("lastInterviewerQuestion", answeredQ.question());
        variables.put("mainCategory", rootMainQuestion.category() != null
            ? rootMainQuestion.category() : rootMainQuestion.type());
        variables.put("userAnswer", userAnswer != null ? userAnswer : "");
        variables.put("usedTopicSummaries", buildUsedTopicSummaries(allQuestions));
        variables.put("recentConversation", buildRecentConversation(allQuestions, answeredQuestionIndex));

        Set<String> usedQuestions = allQuestions.stream()
            .map(InterviewQuestionDTO::question)
            .filter(Objects::nonNull)
            .map(this::normalizeForCompare)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        try {
            String systemPrompt = followUpSystemPromptTemplate.render();
            String userPrompt = followUpUserPromptTemplate.render(variables);
            String generated = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();

            String normalized = sanitizeGeneratedFollowUp(generated);
            if (normalized.isBlank() || usedQuestions.contains(normalizeForCompare(normalized))) {
                return buildFallbackFollowUp(answeredQ.question(), userAnswer);
            }
            return normalized;
        } catch (Exception e) {
            return buildFallbackFollowUp(answeredQ.question(), userAnswer);
        }
    }

    private String loadSkillPersona(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        try {
            return skillService.getSkill(skillId).persona();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildUsedTopicSummaries(List<InterviewQuestionDTO> allQuestions) {
        String summaries = allQuestions.stream()
            .map(InterviewQuestionDTO::topicSummary)
            .filter(item -> item != null && !item.isBlank())
            .distinct()
            .map(item -> "- " + item)
            .collect(Collectors.joining("\n"));
        return summaries.isBlank() ? "- 暂无" : summaries;
    }

    private String buildRecentConversation(List<InterviewQuestionDTO> allQuestions, int currentIndex) {
        int start = Math.max(0, currentIndex - MAX_RECENT_CONVERSATION + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= currentIndex && i < allQuestions.size(); i++) {
            InterviewQuestionDTO q = allQuestions.get(i);
            sb.append("Q: ").append(q.question()).append('\n');
            if (q.userAnswer() != null && !q.userAnswer().isBlank()) {
                sb.append("A: ").append(q.userAnswer()).append('\n');
            }
        }
        return sb.isEmpty() ? "暂无" : sb.toString();
    }

    private String sanitizeGeneratedFollowUp(String generated) {
        if (generated == null) {
            return "";
        }
        return generated
            .replaceAll("```", "")
            .replaceAll("`", "")
            .replaceAll("^[\\s\"'“”]+", "")
            .replaceAll("[\\s\"'“”]+$", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeForCompare(String text) {
        return text == null
            ? ""
            : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replaceAll("[，。？！,.!?：:；;]", "");
    }

    private String buildFallbackFollowUp(String mainQuestion, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank() || userAnswer.length() < 40) {
            return "你提到的内容比较简略，请结合一个你亲手负责的真实场景，补充关键决策与结果指标。";
        }
        return "基于你刚才的回答，如果线上出现异常或指标恶化，你会如何定位根因并给出可回滚的修复方案？";
    }
}
