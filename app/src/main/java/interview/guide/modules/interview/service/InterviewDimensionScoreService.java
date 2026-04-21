package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewReportDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 面试五维能力评分服务
 * 基于每题得分与题目语义（分类/题干关键词）计算：
 * 技术深度、沟通表达、逻辑思维、项目经验、应变能力。
 */
@Service
public class InterviewDimensionScoreService {

    private static final List<String> COMMUNICATION_KEYWORDS = List.of(
        "表达", "沟通", "汇报", "介绍", "阐述", "解释", "描述", "communication", "present"
    );

    private static final List<String> LOGIC_KEYWORDS = List.of(
        "为什么", "如何", "分析", "推导", "步骤", "算法", "复杂度", "优化", "排查", "定位", "logic"
    );

    private static final List<String> PROJECT_KEYWORDS = List.of(
        "项目", "实战", "案例", "场景", "落地", "上线", "架构", "经验", "project", "production"
    );

    private static final List<String> ADAPTABILITY_KEYWORDS = List.of(
        "故障", "突发", "应急", "压力", "冲突", "挑战", "变更", "兜底", "恢复", "adapt", "incident"
    );

    public DimensionScores calculate(List<InterviewReportDTO.QuestionEvaluation> questionDetails,
                                     Integer overallScore) {
        int fallback = clamp(overallScore != null ? overallScore : 0);
        if (questionDetails == null || questionDetails.isEmpty()) {
            return DimensionScores.fallback(fallback);
        }

        double techSum = 0D;
        double techWeight = 0D;
        double commSum = 0D;
        double commWeight = 0D;
        double logicSum = 0D;
        double logicWeight = 0D;
        double projectSum = 0D;
        double projectWeight = 0D;
        double adaptSum = 0D;
        double adaptWeight = 0D;

        for (InterviewReportDTO.QuestionEvaluation questionEval : questionDetails) {
            int score = clamp(questionEval.score());
            String context = ((questionEval.category() == null ? "" : questionEval.category())
                + " "
                + (questionEval.question() == null ? "" : questionEval.question()))
                .toLowerCase(Locale.ROOT);

            // 技术深度默认覆盖全量题目，保证稳定性
            double techW = 1.0;
            double commW = containsAny(context, COMMUNICATION_KEYWORDS) ? 1.0 : 0.0;
            double logicW = containsAny(context, LOGIC_KEYWORDS) ? 1.0 : 0.0;
            double projectW = containsAny(context, PROJECT_KEYWORDS) ? 1.0 : 0.0;
            double adaptW = containsAny(context, ADAPTABILITY_KEYWORDS) ? 1.0 : 0.0;

            techSum += score * techW;
            techWeight += techW;

            commSum += score * commW;
            commWeight += commW;

            logicSum += score * logicW;
            logicWeight += logicW;

            projectSum += score * projectW;
            projectWeight += projectW;

            adaptSum += score * adaptW;
            adaptWeight += adaptW;
        }

        return new DimensionScores(
            averageOrFallback(techSum, techWeight, fallback),
            averageOrFallback(commSum, commWeight, fallback),
            averageOrFallback(logicSum, logicWeight, fallback),
            averageOrFallback(projectSum, projectWeight, fallback),
            averageOrFallback(adaptSum, adaptWeight, fallback)
        );
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int averageOrFallback(double sum, double weight, int fallback) {
        if (weight <= 0D) {
            return fallback;
        }
        return clamp((int) Math.round(sum / weight));
    }

    private int clamp(int score) {
        if (score < 0) return 0;
        return Math.min(score, 100);
    }

    public record DimensionScores(
        int technicalDepthScore,
        int communicationScore,
        int logicalThinkingScore,
        int projectExperienceScore,
        int adaptabilityScore
    ) {
        static DimensionScores fallback(int score) {
            return new DimensionScores(score, score, score, score, score);
        }
    }
}
