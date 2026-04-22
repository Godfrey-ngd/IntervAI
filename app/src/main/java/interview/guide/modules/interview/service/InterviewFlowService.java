package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.dto.CreateSessionRequest;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InterviewFlowService {

    private static final String INTRO_CATEGORY = "自我介绍";
    private static final String TECH_CATEGORY = "技术问题";
    private static final String PROJECT_CATEGORY = "项目深挖";
    private static final String BEHAVIOR_CATEGORY = "行为面试";
    private static final String REVERSE_CATEGORY = "反问";

    private static final String INTRO_QUESTION =
        "我们先从自我介绍开始：请你用 1 分钟介绍你的技术背景、最近两年的核心方向，以及你最有代表性的项目。";

    private static final String REVERSE_QUESTION =
        "最后一个环节：现在你来反问我一个问题，建议聚焦岗位预期、团队协作方式或技术挑战。";

    public VoiceInterviewSessionEntity.InterviewPhase determineFirstPhase(CreateSessionRequest request) {
        if (Boolean.TRUE.equals(request.getIntroEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.INTRO;
        }
        if (Boolean.TRUE.equals(request.getTechEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.TECH;
        }
        if (Boolean.TRUE.equals(request.getProjectEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.PROJECT;
        }
        if (Boolean.TRUE.equals(request.getHrEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.HR;
        }
        return VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
    }

    public VoiceInterviewSessionEntity.InterviewPhase getNextPhase(VoiceInterviewSessionEntity session) {
        VoiceInterviewSessionEntity.InterviewPhase current = session.getCurrentPhase();
        if (current == null) {
            return getFirstEnabledPhase(session);
        }

        return switch (current) {
            case INTRO -> Boolean.TRUE.equals(session.getTechEnabled())
                ? VoiceInterviewSessionEntity.InterviewPhase.TECH
                : Boolean.TRUE.equals(session.getProjectEnabled())
                    ? VoiceInterviewSessionEntity.InterviewPhase.PROJECT
                    : Boolean.TRUE.equals(session.getHrEnabled())
                        ? VoiceInterviewSessionEntity.InterviewPhase.HR
                        : VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
            case TECH -> Boolean.TRUE.equals(session.getProjectEnabled())
                ? VoiceInterviewSessionEntity.InterviewPhase.PROJECT
                : Boolean.TRUE.equals(session.getHrEnabled())
                    ? VoiceInterviewSessionEntity.InterviewPhase.HR
                    : VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
            case PROJECT -> Boolean.TRUE.equals(session.getHrEnabled())
                ? VoiceInterviewSessionEntity.InterviewPhase.HR
                : VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
            case HR, COMPLETED -> VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
        };
    }

    public boolean shouldTransitionToNextPhase(VoiceInterviewSessionEntity session,
                                               LocalDateTime phaseStartTime,
                                               int questionCount,
                                               VoiceInterviewProperties properties) {
        VoiceInterviewSessionEntity.InterviewPhase currentPhase = session.getCurrentPhase();
        if (currentPhase == null || currentPhase == VoiceInterviewSessionEntity.InterviewPhase.COMPLETED) {
            return false;
        }

        Duration phaseDuration = Duration.between(phaseStartTime, LocalDateTime.now());
        VoiceInterviewProperties.DurationConfig config = getPhaseConfig(currentPhase, properties);

        if (phaseDuration.toMinutes() >= config.getMaxDuration()) {
            return true;
        }
        if (questionCount >= config.getMaxQuestions()) {
            return true;
        }
        return phaseDuration.toMinutes() >= config.getSuggestedDuration()
            && questionCount >= config.getMinQuestions();
    }

    public List<InterviewQuestionDTO> applyTextFlow(List<InterviewQuestionDTO> originalQuestions) {
        if (originalQuestions == null || originalQuestions.isEmpty()) {
            return originalQuestions;
        }

        List<Integer> mainIndices = new ArrayList<>();
        for (int i = 0; i < originalQuestions.size(); i++) {
            if (!originalQuestions.get(i).isFollowUp()) {
                mainIndices.add(i);
            }
        }
        if (mainIndices.isEmpty()) {
            return originalQuestions;
        }

        Map<Integer, FlowStage> stageMap = assignStage(mainIndices);
        List<InterviewQuestionDTO> result = new ArrayList<>(originalQuestions.size());

        for (int i = 0; i < originalQuestions.size(); i++) {
            InterviewQuestionDTO q = originalQuestions.get(i);
            Integer mainIndex = q.isFollowUp() ? q.parentQuestionIndex() : i;
            FlowStage stage = stageMap.getOrDefault(mainIndex, FlowStage.TECH);

            String mappedCategory = switch (stage) {
                case INTRO -> INTRO_CATEGORY;
                case TECH -> TECH_CATEGORY;
                case PROJECT -> PROJECT_CATEGORY;
                case BEHAVIOR -> BEHAVIOR_CATEGORY;
                case REVERSE_QUESTION -> REVERSE_CATEGORY;
            };

            String mappedQuestion = q.question();
            if (!q.isFollowUp() && stage == FlowStage.INTRO) {
                mappedQuestion = INTRO_QUESTION;
            }
            if (!q.isFollowUp() && stage == FlowStage.REVERSE_QUESTION) {
                mappedQuestion = REVERSE_QUESTION;
            }

            result.add(new InterviewQuestionDTO(
                q.questionIndex(),
                mappedQuestion,
                q.type(),
                mappedCategory,
                q.topicSummary(),
                q.userAnswer(),
                q.score(),
                q.feedback(),
                q.isFollowUp(),
                q.parentQuestionIndex()
            ));
        }

        return result;
    }

    private Map<Integer, FlowStage> assignStage(List<Integer> mainIndices) {
        Map<Integer, FlowStage> stageMap = new HashMap<>();
        int total = mainIndices.size();

        int first = mainIndices.get(0);
        int last = mainIndices.get(total - 1);
        stageMap.put(first, FlowStage.INTRO);

        if (total == 1) {
            stageMap.put(last, FlowStage.REVERSE_QUESTION);
            return stageMap;
        }

        stageMap.put(last, FlowStage.REVERSE_QUESTION);

        int middleCount = Math.max(0, total - 2);
        int techCount = middleCount == 0 ? 0 : Math.max(1, middleCount / 2);
        int projectCount = middleCount == 0 ? 0 : Math.max(1, (middleCount - techCount) / 2);

        for (int i = 1; i < total - 1; i++) {
            int middleIndex = i - 1;
            FlowStage stage;
            if (middleIndex < techCount) {
                stage = FlowStage.TECH;
            } else if (middleIndex < techCount + projectCount) {
                stage = FlowStage.PROJECT;
            } else {
                stage = FlowStage.BEHAVIOR;
            }
            stageMap.put(mainIndices.get(i), stage);
        }

        return stageMap;
    }

    private VoiceInterviewSessionEntity.InterviewPhase getFirstEnabledPhase(VoiceInterviewSessionEntity session) {
        if (Boolean.TRUE.equals(session.getIntroEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.INTRO;
        }
        if (Boolean.TRUE.equals(session.getTechEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.TECH;
        }
        if (Boolean.TRUE.equals(session.getProjectEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.PROJECT;
        }
        if (Boolean.TRUE.equals(session.getHrEnabled())) {
            return VoiceInterviewSessionEntity.InterviewPhase.HR;
        }
        return VoiceInterviewSessionEntity.InterviewPhase.COMPLETED;
    }

    private VoiceInterviewProperties.DurationConfig getPhaseConfig(
        VoiceInterviewSessionEntity.InterviewPhase phase,
        VoiceInterviewProperties properties
    ) {
        return switch (phase) {
            case INTRO -> properties.getPhase().getIntro();
            case TECH -> properties.getPhase().getTech();
            case PROJECT -> properties.getPhase().getProject();
            case HR -> properties.getPhase().getHr();
            default -> new VoiceInterviewProperties.DurationConfig(0, 0, 0, 0, 0);
        };
    }

    private enum FlowStage {
        INTRO,
        TECH,
        PROJECT,
        BEHAVIOR,
        REVERSE_QUESTION
    }
}
