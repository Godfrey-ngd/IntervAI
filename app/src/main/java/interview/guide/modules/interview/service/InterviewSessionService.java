package interview.guide.modules.interview.service;

import interview.guide.common.constant.CommonConstants.InterviewDefaults;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.CreateInterviewRequest;
import interview.guide.modules.interview.model.HistoricalQuestion;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.SubmitAnswerRequest;
import interview.guide.modules.interview.model.SubmitAnswerResponse;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期，使用 Redis 缓存会话状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private static final String REVERSE_QA_CATEGORY_MARK = "反问";

    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final LlmProviderRegistry llmProviderRegistry;
    private final InterviewerPersonaService interviewerPersonaService;
    private final FollowUpGenerationService followUpGenerationService;
    private final InterviewFlowService interviewFlowService;
    private final InterviewQuestionProperties interviewQuestionProperties;
    private final InterviewReverseQaService reverseQaService;

    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: resumeId={}, sessionId={}",
                    request.resumeId(), unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String skillId = request.skillId() != null ? request.skillId() : InterviewDefaults.SKILL_ID;
        String difficulty = request.difficulty() != null ? request.difficulty() : InterviewDefaults.DIFFICULTY;
        String personaType = interviewerPersonaService.normalizePersonaType(request.personaType());

        log.info("创建新面试会话: {}, skill: {}, difficulty: {}, personaType: {}, questionCount: {}, resumeId: {}",
            sessionId, skillId, difficulty, personaType, request.questionCount(), request.resumeId());

        // 获取历史问题（通用模式按 skillId 查询，有简历时按 resumeId + skillId 精确匹配）
        List<HistoricalQuestion> historicalQuestions =
            persistenceService.getHistoricalQuestions(skillId, request.resumeId());

        // 获取 LLM 客户端
        ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(request.llmProvider());

        // 基于 Skill 生成面试问题
        List<InterviewQuestionDTO> questions = questionService.generateQuestionsBySkill(
            chatClient,
            skillId,
            difficulty,
            request.resumeText(),
            request.questionCount(),
            historicalQuestions,
            request.customCategories(),
            request.jdText(),
            personaType
        );
        questions = interviewFlowService.applyTextFlow(questions);

        // 保存到 Redis 缓存
        sessionCache.saveSession(
            sessionId,
            request.resumeText() != null ? request.resumeText() : "",
            request.resumeId(),
            questions,
            0,
            SessionStatus.CREATED
        );

        // 保存到数据库
        try {
            persistenceService.saveSession(sessionId, request.resumeId(),
                questions.size(), questions, request.llmProvider(), skillId, difficulty, personaType);
        } catch (Exception e) {
            log.warn("保存面试会话到数据库失败: {}", e.getMessage());
        }

        return new InterviewSessionDTO(
            sessionId,
            request.resumeText() != null ? request.resumeText() : "",
            questions.size(),
            0,
            questions,
            SessionStatus.CREATED
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionDTO getSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            return toDTO(cachedOpt.get());
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession);
    }

    /**
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId) {
        try {
            // 1. 先从 Redis 缓存查找
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(resumeId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
                if (cachedOpt.isPresent()) {
                    log.debug("从 Redis 缓存找到未完成会话: resumeId={}, sessionId={}", resumeId, sessionId);
                    return Optional.of(toDTO(cachedOpt.get()));
                }
            }

            // 2. 缓存未命中，从数据库查找
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            InterviewSessionEntity entity = entityOpt.get();
            CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    public InterviewSessionDTO findUnfinishedSessionOrThrow(Long resumeId) {
        return findUnfinishedSession(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "未找到未完成的面试会话"));
    }

    /**
     * 从数据库恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromDatabase(String sessionId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
            return entityOpt.map(this::restoreSessionFromEntity).orElse(null);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从实体恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                entity.getQuestionsJson(),
                new TypeReference<>() {}
            );

            // 恢复已保存的答案
            List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(entity.getSessionId());
            for (InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }

            SessionStatus status = convertStatus(entity.getStatus());

            // 保存到 Redis 缓存
            sessionCache.saveSession(
                entity.getSessionId(),
                entity.getResume() != null ? entity.getResume().getResumeText() : "",
                entity.getResume() != null ? entity.getResume().getId() : null,
                questions,
                entity.getCurrentQuestionIndex(),
                status
            );

            log.info("从数据库恢复会话到 Redis: sessionId={}, currentIndex={}, status={}",
                entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());

            // 返回缓存的会话
            return sessionCache.getSession(entity.getSessionId()).orElse(null);
        } catch (Exception e) {
            log.error("恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private SessionStatus convertStatus(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> SessionStatus.CREATED;
            case IN_PROGRESS -> SessionStatus.IN_PROGRESS;
            case COMPLETED -> SessionStatus.COMPLETED;
            case EVALUATED -> SessionStatus.EVALUATED;
        };
    }

    /**
     * 获取当前问题的响应（包含完成状态）
     */
    public Map<String, Object> getCurrentQuestionResponse(String sessionId) {
        InterviewQuestionDTO question = getCurrentQuestion(sessionId);
        if (question == null) {
            return Map.of(
                "completed", true,
                "message", "所有问题已回答完毕"
            );
        }
        return Map.of(
            "completed", false,
            "question", question
        );
    }

    /**
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        if (session.getCurrentIndex() >= questions.size()) {
            return null; // 所有问题已回答完
        }

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionCache.updateSessionStatus(sessionId, SessionStatus.IN_PROGRESS);

            // 同步到数据库
            try {
                persistenceService.updateSessionStatus(sessionId,
                    InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.warn("更新会话状态失败: {}", e.getMessage());
            }
        }

        return questions.get(session.getCurrentIndex());
    }

    /**
     * 提交答案（并进入下一题）
     * 如果是最后一题，自动触发异步评估
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        if (isReverseQaCategory(question)) {
            return finalizeReverseQaTurn(request.sessionId(), session, questions, index, question.question(),
                request.answer());
        }

        boolean questionsMutated = false;
        if (!question.isFollowUp()) {
            questionsMutated |= handleMainQuestionFollowUpPolicy(
                request.sessionId(), questions, index, request.answer());
        } else {
            questionsMutated |= handleFollowUpChainPolicy(
                request.sessionId(), questions, index, request.answer());
        }

        int newIndex = index + 1;
        boolean hasNextQuestion = newIndex < questions.size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? questions.get(newIndex) : null;
        SessionStatus newStatus = hasNextQuestion ? SessionStatus.IN_PROGRESS : SessionStatus.COMPLETED;

        sessionCache.updateQuestions(request.sessionId(), questions);
        sessionCache.updateCurrentIndex(request.sessionId(), newIndex);
        if (newStatus == SessionStatus.COMPLETED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.COMPLETED);
        }

        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
                request.answer(), 0, null
            );
            if (questionsMutated) {
                persistenceService.updateQuestionsJson(request.sessionId(), questions);
            }
            persistenceService.updateCurrentQuestionIndex(request.sessionId(), newIndex);
            persistenceService.updateSessionStatus(request.sessionId(),
                newStatus == SessionStatus.COMPLETED
                    ? InterviewSessionEntity.SessionStatus.COMPLETED
                    : InterviewSessionEntity.SessionStatus.IN_PROGRESS);

            if (!hasNextQuestion) {
                persistenceService.updateEvaluateStatus(request.sessionId(), AsyncTaskStatus.PENDING, null);
                evaluateStreamProducer.sendEvaluateTask(request.sessionId());
                log.info("会话 {} 已完成所有问题，评估任务已入队", request.sessionId());
            }
        } catch (Exception e) {
            log.warn("保存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 提交答案: 问题{}, 剩余{}题",
            request.sessionId(), index, questions.size() - newIndex);

        return new SubmitAnswerResponse(
            hasNextQuestion,
            nextQuestion,
            newIndex,
            questions.size(),
            null
        );
    }

    private SubmitAnswerResponse finalizeReverseQaTurn(String sessionId,
                                                       CachedSession cached,
                                                       List<InterviewQuestionDTO> questions,
                                                       int index,
                                                       String promptLine,
                                                       String candidateQuestion) {
        sessionCache.updateQuestions(sessionId, questions);

        Optional<InterviewSessionEntity> sessionEntityOpt = persistenceService.findBySessionId(sessionId);
        String llmProvider = sessionEntityOpt.map(InterviewSessionEntity::getLlmProvider)
            .orElse(InterviewDefaults.LLM_PROVIDER);
        ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(llmProvider);
        String reply = reverseQaService.generateInterviewerReply(
            chatClient,
            cached.getResumeText() != null ? cached.getResumeText() : "",
            promptLine,
            candidateQuestion
        );

        int newIndex = index + 1;
        InterviewQuestionDTO q = questions.get(index);

        sessionCache.updateCurrentIndex(sessionId, newIndex);
        sessionCache.updateSessionStatus(sessionId, SessionStatus.COMPLETED);

        try {
            persistenceService.saveAnswer(
                sessionId, index, q.question(), q.category(), candidateQuestion, 0, null
            );
            persistenceService.updateCurrentQuestionIndex(sessionId, newIndex);
            persistenceService.updateSessionStatus(sessionId, InterviewSessionEntity.SessionStatus.COMPLETED);
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
            evaluateStreamProducer.sendEvaluateTask(sessionId);
            log.info("会话 {} 反问环节已结束，评估任务已入队", sessionId);
        } catch (Exception e) {
            log.warn("反问环节持久化失败: {}", e.getMessage());
        }

        return new SubmitAnswerResponse(false, null, newIndex, questions.size(), reply);
    }

    private static boolean isReverseQaCategory(InterviewQuestionDTO question) {
        return question.category() != null && question.category().contains(REVERSE_QA_CATEGORY_MARK);
    }

    /**
     * 主问题答完后：按概率决定是否保留追问；保留则生成动态追问替换首条预置追问。
     */
    private boolean handleMainQuestionFollowUpPolicy(String sessionId,
                                                     List<InterviewQuestionDTO> questions,
                                                     int mainIdx,
                                                     String userAnswer) {
        double p = clampProbability(interviewQuestionProperties.getFollowUpProbability());
        if (ThreadLocalRandom.current().nextDouble() >= p) {
            log.info("会话 {} 主问题 {} 跳过追问（阈值概率 {}）", sessionId, mainIdx, p);
            removeAllFollowUpsForMain(questions, mainIdx);
            return true;
        }
        return replaceFirstFollowUpSlot(sessionId, questions, mainIdx, userAnswer);
    }

    /**
     * 追问答完后：按概率决定是否继续；继续则替换下一条同主追问或未达到深度上限时插入一条。
     */
    private boolean handleFollowUpChainPolicy(String sessionId,
                                             List<InterviewQuestionDTO> questions,
                                             int answeredFollowUpIdx,
                                             String userAnswer) {
        InterviewQuestionDTO answered = questions.get(answeredFollowUpIdx);
        Integer mainObj = answered.parentQuestionIndex();
        if (mainObj == null) {
            return false;
        }
        int mainIdx = mainObj;

        double chainP = clampProbability(interviewQuestionProperties.getFollowUpChainContinueProbability());
        if (ThreadLocalRandom.current().nextDouble() >= chainP) {
            log.info("会话 {} 追问 {} 后结束链（阈值概率 {}）", sessionId, answeredFollowUpIdx, chainP);
            removeFollowingSiblings(questions, answeredFollowUpIdx);
            return true;
        }

        int maxDepth = Math.max(1, interviewQuestionProperties.getFollowUpMaxDepthPerMain());
        int currentDepth = countConsecutiveFollowUpsForMain(questions, mainIdx);
        if (currentDepth >= maxDepth) {
            removeFollowingSiblings(questions, answeredFollowUpIdx);
            return true;
        }

        int nextIdx = answeredFollowUpIdx + 1;
        if (nextIdx < questions.size()) {
            InterviewQuestionDTO next = questions.get(nextIdx);
            if (next.isFollowUp() && Objects.equals(next.parentQuestionIndex(), mainIdx)) {
                return replaceFollowUpSlot(sessionId, questions, answeredFollowUpIdx, userAnswer, nextIdx, mainIdx);
            }
            // 下一条已是其他主问题，不在其间插入追问
            return false;
        }

        if (currentDepth < maxDepth) {
            return insertFollowUpAfter(sessionId, questions, answeredFollowUpIdx, userAnswer, mainIdx);
        }

        removeFollowingSiblings(questions, answeredFollowUpIdx);
        return true;
    }

    private boolean replaceFirstFollowUpSlot(String sessionId,
                                            List<InterviewQuestionDTO> questions,
                                            int mainQuestionIndex,
                                            String userAnswer) {
        int followUpIndex = findPendingFollowUpIndex(questions, mainQuestionIndex);
        if (followUpIndex < 0) {
            return false;
        }
        return replaceFollowUpSlot(sessionId, questions, mainQuestionIndex, userAnswer, followUpIndex,
            mainQuestionIndex);
    }

    private boolean replaceFollowUpSlot(String sessionId,
                                       List<InterviewQuestionDTO> questions,
                                       int answeredQuestionIndex,
                                       String userAnswer,
                                       int slotIndex,
                                       int mainIdx) {
        InterviewQuestionDTO targetFollowUp = questions.get(slotIndex);
        InterviewQuestionDTO rootMain = questions.get(mainIdx);
        try {
            Optional<InterviewSessionEntity> sessionEntityOpt = persistenceService.findBySessionId(sessionId);
            String skillId = sessionEntityOpt.map(InterviewSessionEntity::getSkillId).orElse(InterviewDefaults.SKILL_ID);
            String personaType = sessionEntityOpt.map(InterviewSessionEntity::getPersonaType).orElse("STRICT");
            String llmProvider = sessionEntityOpt.map(InterviewSessionEntity::getLlmProvider)
                .orElse(InterviewDefaults.LLM_PROVIDER);

            ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(llmProvider);
            String dynamicFollowUp = followUpGenerationService.generateFollowUp(
                chatClient,
                skillId,
                personaType,
                rootMain,
                userAnswer,
                questions,
                answeredQuestionIndex
            );

            InterviewQuestionDTO replaced = InterviewQuestionDTO.create(
                targetFollowUp.questionIndex(),
                dynamicFollowUp,
                targetFollowUp.type(),
                targetFollowUp.category(),
                targetFollowUp.topicSummary(),
                true,
                mainIdx
            );
            questions.set(slotIndex, replaced);
            log.info("会话 {} 动态追问已写入: mainIdx={}, slotIndex={}", sessionId, mainIdx, slotIndex);
            return true;
        } catch (Exception e) {
            log.warn("会话 {} 动态追问失败: {}", sessionId, e.getMessage());
            return false;
        }
    }

    private boolean insertFollowUpAfter(String sessionId,
                                       List<InterviewQuestionDTO> questions,
                                       int answeredFollowUpIdx,
                                       String userAnswer,
                                       int mainIdx) {
        InterviewQuestionDTO main = questions.get(mainIdx);
        String cat = main.category() != null ? main.category() + "（追问·追加）" : "追问（追加）";
        InterviewQuestionDTO stub = InterviewQuestionDTO.create(
            0,
            "（生成中）",
            main.type(),
            cat,
            null,
            true,
            mainIdx
        );
        questions.add(answeredFollowUpIdx + 1, stub);
        normalizeQuestionIndices(questions);
        int slot = answeredFollowUpIdx + 1;
        boolean ok = replaceFollowUpSlot(sessionId, questions, answeredFollowUpIdx, userAnswer, slot, mainIdx);
        if (!ok) {
            questions.remove(slot);
            normalizeQuestionIndices(questions);
        }
        return ok;
    }

    private static double clampProbability(double p) {
        if (p <= 0.0) {
            return 0.0;
        }
        if (p >= 1.0) {
            return 1.0;
        }
        return p;
    }

    private static void removeAllFollowUpsForMain(List<InterviewQuestionDTO> questions, int mainIdx) {
        int i = mainIdx + 1;
        while (i < questions.size()) {
            InterviewQuestionDTO c = questions.get(i);
            if (c.isFollowUp() && Objects.equals(c.parentQuestionIndex(), mainIdx)) {
                questions.remove(i);
            } else {
                break;
            }
        }
        normalizeQuestionIndices(questions);
    }

    private static void removeFollowingSiblings(List<InterviewQuestionDTO> questions, int answeredFollowUpIdx) {
        Integer parent = questions.get(answeredFollowUpIdx).parentQuestionIndex();
        if (parent == null) {
            return;
        }
        int i = answeredFollowUpIdx + 1;
        while (i < questions.size()) {
            InterviewQuestionDTO c = questions.get(i);
            if (c.isFollowUp() && Objects.equals(c.parentQuestionIndex(), parent)) {
                questions.remove(i);
            } else {
                break;
            }
        }
        normalizeQuestionIndices(questions);
    }

    private static int countConsecutiveFollowUpsForMain(List<InterviewQuestionDTO> questions, int mainIdx) {
        int n = 0;
        for (int i = mainIdx + 1; i < questions.size(); i++) {
            InterviewQuestionDTO q = questions.get(i);
            if (q.isFollowUp() && Objects.equals(q.parentQuestionIndex(), mainIdx)) {
                n++;
            } else {
                break;
            }
        }
        return n;
    }

    private static void normalizeQuestionIndices(List<InterviewQuestionDTO> questions) {
        Map<Integer, Integer> oldToNew = new HashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            oldToNew.put(questions.get(i).questionIndex(), i);
        }
        List<InterviewQuestionDTO> rebuilt = new ArrayList<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestionDTO q = questions.get(i);
            Integer op = q.parentQuestionIndex();
            Integer np = op == null ? null : oldToNew.get(op);
            if (op != null && np == null) {
                np = op;
            }
            rebuilt.add(new InterviewQuestionDTO(
                i,
                q.question(),
                q.type(),
                q.category(),
                q.topicSummary(),
                q.userAnswer(),
                q.score(),
                q.feedback(),
                q.isFollowUp(),
                np
            ));
        }
        questions.clear();
        questions.addAll(rebuilt);
    }

    private int findPendingFollowUpIndex(List<InterviewQuestionDTO> questions, int mainQuestionIndex) {
        for (int i = mainQuestionIndex + 1; i < questions.size(); i++) {
            InterviewQuestionDTO candidate = questions.get(i);
            if (!candidate.isFollowUp()) {
                break;
            }
            if (candidate.parentQuestionIndex() != null
                && candidate.parentQuestionIndex() == mainQuestionIndex
                && (candidate.userAnswer() == null || candidate.userAnswer().isBlank())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 暂存答案（不进入下一题）
     */
    public void saveAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.IN_PROGRESS);
        }

        // 保存答案到数据库（不更新currentIndex）
        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
                request.answer(), 0, null
            );
            persistenceService.updateSessionStatus(request.sessionId(),
                InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        } catch (Exception e) {
            log.warn("暂存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }

    /**
     * 提前交卷（触发异步评估）
     */
    public void completeInterview(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        // 更新 Redis 缓存
        sessionCache.updateSessionStatus(sessionId, SessionStatus.COMPLETED);

        // 更新数据库状态
        try {
            persistenceService.updateSessionStatus(sessionId,
                InterviewSessionEntity.SessionStatus.COMPLETED);
            // 设置评估状态为 PENDING
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
        } catch (Exception e) {
            log.warn("更新会话状态失败: {}", e.getMessage());
        }

        // 发送评估任务到 Redis Stream
        evaluateStreamProducer.sendEvaluateTask(sessionId);

        log.info("会话 {} 提前交卷，评估任务已入队", sessionId);
    }

    /**
     * 获取或恢复会话（优先从缓存获取）
     */
    private CachedSession getOrRestoreSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            // 刷新 TTL
            sessionCache.refreshSessionTTL(sessionId);
            return cachedOpt.get();
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return restoredSession;
    }

    /**
     * 生成评估报告
     */
    public InterviewReportDTO generateReport(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }

        log.info("生成面试报告: {}", sessionId);

        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        // 获取 LLM 客户端
        String provider = InterviewDefaults.LLM_PROVIDER;
        Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
        if (entityOpt.isPresent()) {
            provider = entityOpt.get().getLlmProvider();
        }
        ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(provider);

        InterviewReportDTO report = evaluationService.evaluateInterview(
            chatClient,
            sessionId,
            session.getResumeText(),
            questions
        );

        // 更新 Redis 缓存状态
        sessionCache.updateSessionStatus(sessionId, SessionStatus.EVALUATED);

        // 保存报告到数据库
        try {
            persistenceService.saveReport(sessionId, report);
        } catch (Exception e) {
            log.warn("保存报告到数据库失败: {}", e.getMessage());
        }

        return report;
    }

    /**
     * 将缓存会话转换为 DTO
     */
    private InterviewSessionDTO toDTO(CachedSession session) {
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        return new InterviewSessionDTO(
            session.getSessionId(),
            session.getResumeText(),
            questions.size(),
            session.getCurrentIndex(),
            questions,
            session.getStatus()
        );
    }
}
