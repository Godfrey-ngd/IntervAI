package interview.guide.modules.interview.model;

/**
 * 提交答案响应
 *
 * @param interviewerReply 「反问」环节时，面试官对候选人所提问题的文字回答；其余环节为 null
 */
public record SubmitAnswerResponse(
    boolean hasNextQuestion,
    InterviewQuestionDTO nextQuestion,
    int currentIndex,
    int totalQuestions,
    String interviewerReply
) {}
