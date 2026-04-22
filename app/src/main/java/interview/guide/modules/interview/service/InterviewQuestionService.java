package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.constant.CommonConstants.InterviewDefaults;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.CategoryDTO;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillCategoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import interview.guide.modules.interview.model.HistoricalQuestion;

/**
 * 面试问题生成服务
 * 基于 Skill 动态出题，type 由 Skill category key 驱动
 */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    private static final String DEFAULT_QUESTION_TYPE = "GENERAL";

    private final PromptTemplate skillSystemPromptTemplate;
    private final PromptTemplate skillUserPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final InterviewSkillService skillService;
    private final InterviewerPersonaService interviewerPersonaService;
    private final int followUpCount;

    private static final int MAX_FOLLOW_UP_COUNT = 2;

    private static final Map<String, String> DIFFICULTY_DESCRIPTIONS = Map.of(
        "junior", "校招/0-1年经验。考察基础概念和简单应用。",
        "mid", "1-3年经验。考察原理理解和实战经验。",
        "senior", "3年+经验。考察架构设计和深度调优。"
    );

    // 通用行为/软技能兜底题（与方向无关）
    private static final String[][] GENERIC_FALLBACK_QUESTIONS = {
        {"请描述一个你主导解决的技术难题，你的分析思路是什么？", "GENERAL", "综合能力"},
        {"你在做技术方案选型时，通常考虑哪些因素？请举例说明。", "GENERAL", "综合能力"},
        {"请分享一次你处理线上故障的经历，从发现到修复的完整过程。", "GENERAL", "综合能力"},
        {"你如何保证代码质量？介绍你实践过的有效手段。", "GENERAL", "综合能力"},
        {"描述一个你做过的技术优化案例，优化的动机、方案和效果。", "GENERAL", "综合能力"},
        {"你在团队协作中遇到过最大的分歧是什么？如何解决的？", "GENERAL", "综合能力"},
    };

    private record QuestionListDTO(List<QuestionDTO> questions) {}

    private record QuestionDTO(String question, String type, String category, String topicSummary, List<String> followUps) {}

    public InterviewQuestionService(
            StructuredOutputInvoker structuredOutputInvoker,
            InterviewSkillService skillService,
            InterviewerPersonaService interviewerPersonaService,
            InterviewQuestionProperties properties,
            ResourceLoader resourceLoader) throws IOException {
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.skillService = skillService;
        this.interviewerPersonaService = interviewerPersonaService;
        this.skillSystemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(properties.getQuestionSystemPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.skillUserPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(properties.getQuestionUserPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(properties.getFollowUpCount(), MAX_FOLLOW_UP_COUNT));
    }

    public List<InterviewQuestionDTO> generateQuestionsBySkill(
            ChatClient chatClient,
            String skillId,
            String difficulty,
            String resumeText,
            int questionCount,
            List<HistoricalQuestion> historicalQuestions,
            List<CategoryDTO> customCategories,
            String jdText,
            String personaType) {

        SkillDTO skill;
        if (InterviewSkillService.CUSTOM_SKILL_ID.equals(skillId)
                && customCategories != null && !customCategories.isEmpty()) {
            skill = skillService.buildCustomSkill(customCategories, jdText != null ? jdText : "");
        } else {
            skill = skillService.getSkill(skillId);
        }
        Map<String, Integer> allocation = skillService.calculateAllocation(skill.categories(), questionCount);
        String allocationTable = skillService.buildAllocationDescription(allocation, skill.categories());

        String difficultyDesc = DIFFICULTY_DESCRIPTIONS.getOrDefault(
            difficulty != null ? difficulty : InterviewDefaults.DIFFICULTY,
            DIFFICULTY_DESCRIPTIONS.get(InterviewDefaults.DIFFICULTY));

        log.info("Skill 驱动出题: skill={}, difficulty={}, total={}, historicalCount={}, allocation={}",
            skillId, difficulty, questionCount,
            historicalQuestions != null ? historicalQuestions.size() : 0, allocation);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", followUpCount);
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("skillName", skill.name());
            variables.put("skillToolCommand", skill.id());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("allocationTable", allocationTable);
            variables.put("resumeSection", buildResumeSection(resumeText));
            variables.put("historicalSection", buildHistoricalSection(historicalQuestions));
            variables.put("referenceSection", skillService.buildReferenceSection(skill, allocation));
            variables.put("personaSection", interviewerPersonaService.buildPersonaSection(skill.persona(), personaType));
            variables.put("jdSection", buildJdSection(skill.sourceJd()));

            String systemPrompt = skillSystemPromptTemplate.render();
            String userPrompt = skillUserPromptTemplate.render(variables);
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                chatClient, systemPromptWithFormat, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "面试问题生成失败：", "Skill 驱动出题", log
            );

            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            long mainQuestionCount = questions.stream().filter(q -> !Boolean.TRUE.equals(q.isFollowUp())).count();
            if (mainQuestionCount == 0) {
                log.warn("Skill 驱动出题返回空题单，回退到默认问题: skill={}, difficulty={}, requestedMainQuestions={}",
                    skillId, difficulty, questionCount);
                return generateFallbackQuestions(skill, questionCount, hasResumeText(resumeText));
            }
            if (mainQuestionCount < questionCount) {
                log.warn("Skill 驱动出题主问题数量不足: skill={}, difficulty={}, requestedMainQuestions={}, actualMainQuestions={}",
                    skillId, difficulty, questionCount, mainQuestionCount);
            }
            log.info("Skill 驱动出题成功: {} 个问题", questions.size());
            return questions;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Skill 驱动出题失败，回退到默认问题: {}", e.getMessage(), e);
            return generateFallbackQuestions(skill, questionCount, hasResumeText(resumeText));
        }
    }

    public List<InterviewQuestionDTO> generateQuestions(ChatClient chatClient, String resumeText, int questionCount, List<HistoricalQuestion> historicalQuestions) {
        return generateQuestionsBySkill(
            chatClient,
            InterviewDefaults.SKILL_ID,
            InterviewDefaults.DIFFICULTY,
            resumeText,
            questionCount,
            historicalQuestions,
            null,
            null,
            null
        );
    }

    public List<InterviewQuestionDTO> generateQuestions(ChatClient chatClient, String resumeText, int questionCount) {
        return generateQuestions(chatClient, resumeText, questionCount, null);
    }

    private static boolean hasResumeText(String resumeText) {
        return resumeText != null && !resumeText.isBlank();
    }

    private String buildResumeSection(String resumeText) {
        if (!hasResumeText(resumeText)) {
            return "## 候选人材料（未提供简历正文）\n\n"
                + "**当前为通用面试模式。** 你必须遵守：\n"
                + "- 禁止假定候选人有工作经历或项目履历；禁止在题干或追问中出现「你的项目」「简历中」「你做过」「你负责的系统」等表述。\n"
                + "- 主问题与追问以该方向的**标准知识、原理与开放思考题**为主；需要场景时使用「假设」「如果」「在实现某类功能时」等中性表述。\n"
                + "- followUps 中同样不得默认对方有真实项目可引用；可请对方结合所学或假设场景说明思路。\n";
        }
        return "## 候选人简历\n---简历内容开始---\n" + resumeText + "\n---简历内容结束---";
    }

    private static final int TOPIC_SUMMARY_FALLBACK_LENGTH = 30;

    private String buildHistoricalSection(List<HistoricalQuestion> historicalQuestions) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return "暂无历史提问";
        }

        Map<String, List<String>> grouped = new HashMap<>();
        for (HistoricalQuestion hq : historicalQuestions) {
            String type = hq.type() != null && !hq.type().isBlank() ? hq.type() : "GENERAL";
            String summary = hq.topicSummary();
            if (summary == null || summary.isBlank()) {
                String q = hq.question();
                summary = q.length() > TOPIC_SUMMARY_FALLBACK_LENGTH
                    ? q.substring(0, TOPIC_SUMMARY_FALLBACK_LENGTH) + "…"
                    : q;
            }
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(summary);
        }

        StringBuilder sb = new StringBuilder("已考过的知识点（避免重复出题）：\n");
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            sb.append(String.join(", ", entry.getValue()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String buildJdSection(String sourceJd) {
        if (sourceJd == null || sourceJd.isBlank()) {
            return "";
        }
        return "## 职位描述（JD）\n根据以下 JD 关键要求出题，确保题目与岗位实际需求相关：\n" + sourceJd;
    }

    private List<InterviewQuestionDTO> convertToQuestions(QuestionListDTO dto) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (dto == null || dto.questions() == null) {
            return questions;
        }

        for (QuestionDTO q : dto.questions()) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            String type = (q.type() != null && !q.type().isBlank()) ? q.type().toUpperCase() : DEFAULT_QUESTION_TYPE;
            int mainQuestionIndex = index;
            questions.add(InterviewQuestionDTO.create(index++, q.question(), type, q.category(), q.topicSummary(), false, null));

            List<String> followUps = sanitizeFollowUps(q.followUps());
            for (int i = 0; i < followUps.size(); i++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, followUps.get(i), type,
                    buildFollowUpCategory(q.category(), i + 1), null, true, mainQuestionIndex
                ));
            }
        }

        return questions;
    }

    /**
     * 两层兜底策略：
     * 1. 根据 Skill categories 生成通用占位题
     * 2. 如果 Skill 也拿不到，用通用行为/软技能题
     */
    private List<InterviewQuestionDTO> generateFallbackQuestions(SkillDTO skill, int count, boolean resumeAvailable) {
        List<SkillCategoryDTO> categories = skill != null ? skill.categories() : List.of();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (!categories.isEmpty()) {
            // 第一层：按 Skill categories 轮转生成占位题，保证主问题数量满足请求值
            int generated = 0;
            while (generated < count) {
                SkillCategoryDTO cat = categories.get(generated % categories.size());
                String question = resumeAvailable
                    ? "请谈谈你在\"" + cat.label() + "\"方向的技术理解和实践经验。"
                    : "请说明你对\"" + cat.label() + "\"方向核心概念的理解，并举例说明其典型应用场景（可为课堂或练习中的例子）。";
                questions.add(InterviewQuestionDTO.create(index++, question, cat.key(), cat.label(), null, false, null));
                int mainIndex = index - 1;
                for (int j = 0; j < followUpCount; j++) {
                    questions.add(InterviewQuestionDTO.create(
                        index++, buildDefaultFollowUp(question, j + 1, resumeAvailable),
                        cat.key(), buildFollowUpCategory(cat.label(), j + 1), null, true, mainIndex
                    ));
                }
                generated++;
            }
            return questions;
        }

        // 第二层：通用行为/软技能题
        for (int i = 0; i < Math.min(count, GENERIC_FALLBACK_QUESTIONS.length); i++) {
            String[] q = GENERIC_FALLBACK_QUESTIONS[i];
            String mainQ = resumeAvailable ? q[0] : neutralizeGenericFallbackQuestion(q[0]);
            questions.add(InterviewQuestionDTO.create(index++, mainQ, q[1], q[2], null, false, null));
            int mainIndex = index - 1;
            for (int j = 0; j < followUpCount; j++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, buildDefaultFollowUp(mainQ, j + 1, resumeAvailable),
                    q[1], buildFollowUpCategory(q[2], j + 1), null, true, mainIndex
                ));
            }
        }
        return questions;
    }

    /**
     * 无简历时弱化「你做过/你的经历」类表述，改为假设或通用方法论题。
     */
    private static String neutralizeGenericFallbackQuestion(String question) {
        return switch (question) {
            case "请描述一个你主导解决的技术难题，你的分析思路是什么？" ->
                "若遇到一道较复杂的技术难题，你会如何拆解与分析？请说明思路（可结合假设场景）。";
            case "你在做技术方案选型时，通常考虑哪些因素？请举例说明。" ->
                "在做技术方案选型时，通常应考虑哪些因素？请举例说明（可为教材或公开案例）。";
            case "请分享一次你处理线上故障的经历，从发现到修复的完整过程。" ->
                "假设生产环境出现异常，从发现到修复通常需要哪些步骤？请结合典型故障说明推理过程。";
            case "你如何保证代码质量？介绍你实践过的有效手段。" ->
                "为保证代码质量，常见有效手段有哪些？请结合你熟悉或练习中的做法说明。";
            case "描述一个你做过的技术优化案例，优化的动机、方案和效果。" ->
                "请说明技术优化中常见的动机、方案思路与效果衡量方式（可为假设场景）。";
            case "你在团队协作中遇到过最大的分歧是什么？如何解决的？" ->
                "团队协作中出现技术分歧时，常见的解决路径有哪些？请结合假设情境说明。";
            default -> question;
        };
    }

    private List<String> sanitizeFollowUps(List<String> followUps) {
        if (followUpCount == 0 || followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        return followUps.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(followUpCount)
            .collect(Collectors.toList());
    }

    private String buildFollowUpCategory(String category, int order) {
        String base = (category == null || category.isBlank()) ? "追问" : category;
        return base + "（追问" + order + "）";
    }

    private String buildDefaultFollowUp(String mainQuestion, int order, boolean resumeAvailable) {
        if (!resumeAvailable) {
            if (order == 1) {
                return "围绕\"" + mainQuestion + "\"，请结合假设场景或你学过的类似案例，说明关键思路与步骤。";
            }
            return "围绕\"" + mainQuestion + "\"，若在生产环境出现异常，你会如何分析与处理？（可说明推理过程）";
        }
        if (order == 1) {
            return "基于\"" + mainQuestion + "\"，请结合你亲自做过的一个真实场景展开说明。";
        }
        return "基于\"" + mainQuestion + "\"，如果线上出现异常，你会如何定位并给出修复方案？";
    }
}
