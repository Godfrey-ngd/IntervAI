package interview.guide.modules.interview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文本面试「候选人反问」环节：生成面试官对候选人问题的回答（不再追问候选人）。
 */
@Slf4j
@Service
public class InterviewReverseQaService {

    private static final int MAX_RESUME_CONTEXT_CHARS = 1200;

    private final PromptTemplate systemTemplate;
    private final PromptTemplate userTemplate;

    public InterviewReverseQaService(ResourceLoader resourceLoader) throws IOException {
        this.systemTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/interview-reverse-qa-system.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/interview-reverse-qa-user.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
    }

    public String generateInterviewerReply(ChatClient chatClient,
                                           String resumeText,
                                           String promptLine,
                                           String candidateQuestion) {
        String resumeContext = buildResumeContext(resumeText);
        Map<String, Object> vars = new HashMap<>();
        vars.put("resumeContext", resumeContext);
        vars.put("promptLine", promptLine != null ? promptLine : "");
        vars.put("candidateQuestion", candidateQuestion != null ? candidateQuestion : "");

        String systemPrompt = systemTemplate.render();
        String userPrompt = userTemplate.render(vars);
        try {
            String text = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
            return sanitize(text);
        } catch (Exception e) {
            log.warn("生成反问环节面试官回答失败: {}", e.getMessage());
            return "感谢你的提问。关于这一点，不同团队细节会有差异，若后续进入下一轮，HR 或用人经理可以结合你的关注点再具体说明。";
        }
    }

    private static String buildResumeContext(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return "（本次面试未提供简历正文）";
        }
        String t = resumeText.strip();
        if (t.length() > MAX_RESUME_CONTEXT_CHARS) {
            t = t.substring(0, MAX_RESUME_CONTEXT_CHARS) + "…";
        }
        return t;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("```", "").trim();
    }
}
