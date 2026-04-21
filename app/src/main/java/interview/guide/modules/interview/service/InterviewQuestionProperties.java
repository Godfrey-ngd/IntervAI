package interview.guide.modules.interview.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.interview")
public class InterviewQuestionProperties {

    /**
     * 答完主问题后触发追问的概率（0～1）。1 表示始终追问，0 表示始终跳过预置追问。
     */
    private double followUpProbability = 0.72;

    /**
     * 答完一条追问后，继续下一条追问（含动态插入）的概率（0～1）。
     */
    private double followUpChainContinueProbability = 0.55;

    /**
     * 同一主问题下最多允许的追问条数（含首轮与链式追加）。
     */
    private int followUpMaxDepthPerMain = 4;

    private int followUpCount = 1;
    private String questionSystemPromptPath = "classpath:prompts/interview-question-skill-system.st";
    private String questionUserPromptPath = "classpath:prompts/interview-question-skill-user.st";
}
