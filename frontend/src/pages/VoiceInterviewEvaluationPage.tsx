import { useEffect, useState, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, RefreshCw } from 'lucide-react';
import { EvaluationStatusResponse, VoiceEvaluationDetail, voiceInterviewApi } from '../api/voiceInterview';
import InterviewDetailPanel from '../components/InterviewDetailPanel';
import type { InterviewDetail } from '../api/history';

export default function VoiceInterviewEvaluationPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [evaluation, setEvaluation] = useState<VoiceEvaluationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [evaluateStatus, setEvaluateStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollingRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const triggerOnceRef = useRef(false);
  const pollCountRef = useRef(0);

  const sessionIdNumber = useMemo(() => {
    if (!sessionId) return null;
    const id = Number(sessionId);
    return Number.isFinite(id) ? id : null;
  }, [sessionId]);

  useEffect(() => {
    triggerOnceRef.current = false;
    pollCountRef.current = 0;
    loadEvaluation();
    return () => {
      if (pollingRef.current) {
        clearTimeout(pollingRef.current);
      }
    };
  }, [sessionIdNumber]);

  const loadEvaluation = async () => {
    if (!sessionIdNumber) {
      setError('无效的会话 ID');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const status = await voiceInterviewApi.getEvaluation(sessionIdNumber);
      await handleStatusResponse(status);
    } catch {
      try {
        const status = await voiceInterviewApi.generateEvaluation(sessionIdNumber);
        await handleStatusResponse(status);
      } catch (err) {
        console.error('Failed to trigger evaluation:', err);
        setError('触发评估失败，请重试');
        setLoading(false);
      }
    }
  };

  const handleStatusResponse = async (response: EvaluationStatusResponse) => {
    const status = response.evaluateStatus;
    setEvaluateStatus(status);

    if (status === 'COMPLETED' && response.evaluation) {
      setEvaluation(response.evaluation);
      setLoading(false);
    } else if (status === 'FAILED') {
      setError(response.evaluateError || '评估生成失败');
      setLoading(false);
    } else if (!status) {
      if (!sessionIdNumber) {
        setError('无效的会话 ID');
        setLoading(false);
        return;
      }
      if (!triggerOnceRef.current) {
        triggerOnceRef.current = true;
        try {
          const next = await voiceInterviewApi.generateEvaluation(sessionIdNumber);
          setEvaluateStatus(next.evaluateStatus);
          startPolling();
        } catch (err) {
          console.error('Failed to trigger evaluation:', err);
          setError('该会话尚未生成评估，触发失败，请重试');
          setLoading(false);
        }
      } else {
        startPolling();
      }
    } else {
      startPolling();
    }
  };

  const startPolling = useCallback(() => {
    if (pollingRef.current) {
      clearTimeout(pollingRef.current);
    }

    pollingRef.current = setTimeout(async () => {
      if (!sessionIdNumber) return;

      pollCountRef.current += 1;
      if (pollCountRef.current >= 60) {
        setError('评估生成超时，请稍后重试');
        setLoading(false);
        return;
      }

      try {
        const response = await voiceInterviewApi.getEvaluation(sessionIdNumber);
        const status = response.evaluateStatus;
        setEvaluateStatus(status);

        if (status === 'COMPLETED' && response.evaluation) {
          setEvaluation(response.evaluation);
          setLoading(false);
        } else if (status === 'FAILED') {
          setError(response.evaluateError || '评估生成失败');
          setLoading(false);
        } else if (!status && !triggerOnceRef.current) {
          triggerOnceRef.current = true;
          try {
            await voiceInterviewApi.generateEvaluation(sessionIdNumber);
          } catch (err) {
            console.error('Failed to trigger evaluation:', err);
          }
          startPolling();
        } else {
          startPolling();
        }
      } catch {
        setError('获取评估状态失败');
        setLoading(false);
      }
    }, 3000);
  }, [sessionIdNumber]);

  const handleRetry = async () => {
    if (!sessionIdNumber) return;
    setLoading(true);
    setError(null);
    setEvaluateStatus(null);
    triggerOnceRef.current = false;
    pollCountRef.current = 0;

    try {
      const status = await voiceInterviewApi.generateEvaluation(sessionIdNumber);
      await handleStatusResponse(status);
    } catch (err) {
      console.error('Failed to retry evaluation:', err);
      setError('重试失败，请稍后再试');
      setLoading(false);
    }
  };

  const interviewDetail = useMemo<InterviewDetail | null>(() => {
    if (!evaluation) return null;
    return {
      id: 0,
      sessionId: String(sessionIdNumber ?? ''),
      totalQuestions: evaluation.totalQuestions,
      status: 'COMPLETED',
      overallScore: evaluation.overallScore,
      overallFeedback: evaluation.overallFeedback,
      createdAt: '',
      completedAt: '',
      strengths: evaluation.strengths,
      improvements: evaluation.improvements,
      answers: evaluation.answers.map(a => ({
        questionIndex: a.questionIndex,
        question: a.question,
        category: a.category,
        userAnswer: a.userAnswer,
        score: a.score,
        feedback: a.feedback,
        referenceAnswer: a.referenceAnswer ?? undefined,
        keyPoints: a.keyPoints ?? undefined,
        answeredAt: '',
      })),
    };
  }, [evaluation, sessionId]);

  // Loading state
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <div className="w-10 h-10 border-3 border-slate-200 dark:border-slate-700 border-t-primary-500 rounded-full animate-spin mx-auto mb-4" />
          <p className="text-slate-600 dark:text-slate-300">
            {evaluateStatus === 'PROCESSING' ? 'AI 正在分析面试表现...' : '正在生成评估报告...'}
          </p>
          <p className="text-slate-400 text-sm mt-2">预计需要 10-30 秒</p>
        </div>
      </div>
    );
  }

  // Error state
  if (error && !evaluation) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <p className="text-slate-600 dark:text-slate-300 text-lg mb-2">评估报告生成失败</p>
          <p className="text-slate-400 text-sm mb-6">{error}</p>
          <div className="flex items-center gap-3 justify-center">
            <button
              onClick={handleRetry}
              className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 flex items-center gap-2"
            >
              <RefreshCw className="w-4 h-4" />
              重试
            </button>
            <button
              onClick={() => navigate('/interviews')}
              className="px-6 py-2 bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-lg hover:bg-slate-300 dark:hover:bg-slate-600"
            >
              返回列表
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!evaluation || !interviewDetail) {
    return null;
  }

  return (
    <div className="pb-10">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="surface-card flex items-center gap-3 px-6 py-5">
          <button
            onClick={() => navigate('/interviews')}
            className="rounded-lg border border-slate-200 bg-white p-2 text-slate-400 transition-colors hover:bg-slate-50 hover:text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <div className="section-kicker mb-2">语音评估</div>
            <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white">面试评估报告</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">语音会话 ID: {sessionId}</p>
          </div>
        </div>
        <InterviewDetailPanel interview={interviewDetail} />
      </div>
    </div>
  );
}
