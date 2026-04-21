import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  Bot,
  BrainCircuit,
  CheckCircle2,
  Eye,
  EyeOff,
  Loader2,
  Mic,
  ShieldCheck,
  Sparkles,
  UserPlus,
} from 'lucide-react';
import { useAuthSession } from '../hooks/useAuthSession';

type AuthMode = 'login' | 'register';

interface LoginFormState {
  account: string;
  password: string;
}

interface RegisterFormState {
  username: string;
  email: string;
  password: string;
  displayName: string;
}

const initialLoginForm: LoginFormState = {
  account: '',
  password: '',
};

const initialRegisterForm: RegisterFormState = {
  username: '',
  email: '',
  password: '',
  displayName: '',
};

export default function AuthPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { session, login, register, logout, loading } = useAuthSession();
  const [mode, setMode] = useState<AuthMode>(searchParams.get('mode') === 'register' ? 'register' : 'login');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [loginForm, setLoginForm] = useState<LoginFormState>(initialLoginForm);
  const [registerForm, setRegisterForm] = useState<RegisterFormState>(initialRegisterForm);

  const fromPath = useMemo(() => searchParams.get('from') || '/history', [searchParams]);

  useEffect(() => {
    const urlMode = searchParams.get('mode');
    if (urlMode === 'register' || urlMode === 'login') {
      setMode(urlMode);
    }
  }, [searchParams]);

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('mode', nextMode);
      return next;
    });
    setErrorMessage('');
  };

  const handleLoginSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);
    setErrorMessage('');

    try {
      await login({ ...loginForm });
      navigate(fromPath, { replace: true });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '登录失败');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRegisterSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);
    setErrorMessage('');

    try {
      await register({ ...registerForm });
      navigate(fromPath, { replace: true });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '注册失败');
    } finally {
      setIsSubmitting(false);
    }
  };

  const heroBullets = [
    '登录 / 注册已接入后端接口',
    '会话令牌自动注入到 API 请求头',
    '支持获取当前用户与安全退出',
  ];

  const brandHighlights = [
    {
      title: '智能简历分析',
      description: '多格式解析 + 异步评估，快速定位简历改进点。',
      icon: BrainCircuit,
    },
    {
      title: '模拟面试训练',
      description: '支持文字与语音双模式，覆盖多方向高频面试题。',
      icon: Mic,
    },
    {
      title: 'AI 驱动问答',
      description: '知识库检索增强，让回答更贴近真实业务场景。',
      icon: Bot,
    },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-indigo-50 px-4 py-8 text-slate-900 dark:from-slate-950 dark:via-slate-950 dark:to-slate-900 dark:text-slate-50">
      <div className="mx-auto grid min-h-[calc(100vh-4rem)] max-w-7xl items-center gap-6 lg:grid-cols-[1.05fr_0.95fr]">
        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="surface-frame relative overflow-hidden p-8 md:p-10"
        >
          <div className="pointer-events-none absolute -top-16 -right-10 h-52 w-52 rounded-full bg-cyan-300/20 blur-3xl dark:bg-cyan-500/10" />
          <div className="pointer-events-none absolute -bottom-20 -left-10 h-52 w-52 rounded-full bg-primary-400/20 blur-3xl dark:bg-primary-500/10" />

          <div className="relative mb-6 inline-flex items-center gap-3 rounded-full border border-primary-200/70 bg-white/80 px-4 py-2 text-sm font-medium text-primary-700 shadow-sm dark:border-primary-900/70 dark:bg-primary-950/30 dark:text-primary-300">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-r from-primary-500 to-cyan-500 text-white">
              <Sparkles className="h-4 w-4" />
            </span>
            IntervAI · Enterprise Experience
          </div>

          <h1 className="section-title text-3xl md:text-[3.2rem] md:leading-[1.12]">
            用更专业的方式，
            <span className="block bg-gradient-to-r from-primary-600 to-cyan-500 bg-clip-text text-transparent">
              练出面试竞争力
            </span>
          </h1>

          <p className="section-subtitle mt-5 max-w-2xl text-base leading-7">
           我们聚焦简历优化、模拟面试与知识库问答三大能力，
            帮你把练习过程变成可量化、可复盘、可持续提升的求职系统。
          </p>

          <div className="mt-8 space-y-3">
            {heroBullets.map((item) => (
              <div key={item} className="flex items-center gap-3 rounded-2xl border border-white/70 bg-white/80 px-4 py-3 text-sm text-slate-600 shadow-sm backdrop-blur-md dark:border-slate-800/70 dark:bg-slate-950/55 dark:text-slate-300">
                <CheckCircle2 className="h-4 w-4 flex-shrink-0 text-emerald-500" />
                <span>{item}</span>
              </div>
            ))}
          </div>

          <div className="mt-8 grid gap-3">
            {brandHighlights.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.title}
                  className="group rounded-2xl border border-white/75 bg-white/75 p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md dark:border-slate-800/70 dark:bg-slate-950/55"
                >
                  <div className="flex items-start gap-3">
                    <span className="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-600 dark:bg-primary-950/55 dark:text-primary-300">
                      <Icon className="h-4 w-4" />
                    </span>
                    <div>
                      <p className="text-sm font-semibold text-slate-900 dark:text-white">{item.title}</p>
                      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{item.description}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="mt-8 rounded-2xl border border-white/70 bg-gradient-to-r from-slate-900 to-slate-800 p-5 text-slate-100 dark:border-slate-700/80 dark:from-slate-900 dark:to-slate-800">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-300">Slogan</p>
            <p className="mt-2 text-lg font-semibold leading-relaxed">
             从能力到价值。
            </p>
          </div>
        </motion.section>

        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.05 }}
          className="surface-card p-4 shadow-[0_16px_70px_rgba(79,70,229,0.13)] md:p-6 dark:shadow-[0_20px_70px_rgba(2,6,23,0.5)]"
        >
          <div className="flex items-center justify-between gap-4 rounded-[1.5rem] border border-slate-200/70 bg-slate-50/80 px-4 py-3 dark:border-slate-800/70 dark:bg-slate-900/60">
            <div>
              <p className="text-sm font-semibold text-slate-900 dark:text-white">账号访问</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">登录后会自动回到你原本想打开的页面</p>
            </div>
            <div className="rounded-full bg-primary-50 px-3 py-1 text-xs font-semibold text-primary-700 dark:bg-primary-950/50 dark:text-primary-300">
              Auth
            </div>
          </div>

          {session && (
            <div className="mt-4 rounded-[1.5rem] border border-emerald-200/80 bg-emerald-50/80 p-4 dark:border-emerald-900/60 dark:bg-emerald-950/30">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-300">
                  <UserPlus className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <p className="font-semibold text-emerald-800 dark:text-emerald-200">当前已登录</p>
                  <p className="mt-1 text-sm text-emerald-700/80 dark:text-emerald-200/80">
                    {session.user.displayName} · {session.user.username}
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => navigate(fromPath, { replace: true })}
                      className="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-4 py-2 text-sm font-medium text-white transition-transform hover:-translate-y-0.5"
                    >
                      继续使用
                      <ArrowRight className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={async () => {
                        await logout();
                        setSearchParams((prev) => {
                          const next = new URLSearchParams(prev);
                          next.set('mode', 'login');
                          return next;
                        });
                      }}
                      className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 transition-colors hover:bg-emerald-50 dark:border-emerald-900/60 dark:bg-slate-950 dark:text-emerald-200 dark:hover:bg-slate-900"
                    >
                      退出登录
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className="mt-4 flex gap-2 rounded-full border border-slate-200/80 bg-slate-100/80 p-1 dark:border-slate-800 dark:bg-slate-900">
            {([
              { key: 'login' as const, label: '登录' },
              { key: 'register' as const, label: '注册' },
            ]).map((item) => {
              const active = mode === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => switchMode(item.key)}
                  className={`flex-1 rounded-full px-4 py-2 text-sm font-medium transition-all ${active ? 'bg-white text-primary-700 shadow-sm dark:bg-slate-950 dark:text-primary-300' : 'text-slate-500 dark:text-slate-400'}`}
                >
                  {item.label}
                </button>
              );
            })}
          </div>

          <div className="mt-5">
            {mode === 'login' ? (
              <form onSubmit={handleLoginSubmit} className="space-y-4">
                <div>
                  <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">账号</label>
                  <input
                    value={loginForm.account}
                    onChange={(e) => setLoginForm((prev) => ({ ...prev, account: e.target.value }))}
                    placeholder="用户名或邮箱"
                    className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 outline-none transition focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                    autoComplete="username"
                  />
                </div>
                <div>
                  <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">密码</label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={loginForm.password}
                      onChange={(e) => setLoginForm((prev) => ({ ...prev, password: e.target.value }))}
                      placeholder="请输入密码"
                      className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 pr-12 text-slate-900 outline-none transition focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                      autoComplete="current-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((prev) => !prev)}
                      className="absolute inset-y-0 right-0 flex items-center px-4 text-slate-400 transition hover:text-slate-600 dark:hover:text-slate-200"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>
                {errorMessage && (
                  <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200">
                    {errorMessage}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={isSubmitting || loading}
                  className="gradient-button w-full disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}
                  登录进入平台
                </button>
              </form>
            ) : (
              <form onSubmit={handleRegisterSubmit} className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">用户名</label>
                    <input
                      value={registerForm.username}
                      onChange={(e) => setRegisterForm((prev) => ({ ...prev, username: e.target.value }))}
                      placeholder="例如：alice"
                      className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 outline-none transition focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                      autoComplete="username"
                    />
                  </div>
                  <div>
                    <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">昵称</label>
                    <input
                      value={registerForm.displayName}
                      onChange={(e) => setRegisterForm((prev) => ({ ...prev, displayName: e.target.value }))}
                      placeholder="你的展示名称"
                      className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 outline-none transition focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                    />
                  </div>
                </div>

                <div>
                  <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">邮箱</label>
                  <input
                    type="email"
                    value={registerForm.email}
                    onChange={(e) => setRegisterForm((prev) => ({ ...prev, email: e.target.value }))}
                    placeholder="name@example.com"
                    className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 outline-none transition focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                    autoComplete="email"
                  />
                </div>

                <div>
                  <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">密码</label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={registerForm.password}
                      onChange={(e) => setRegisterForm((prev) => ({ ...prev, password: e.target.value }))}
                      placeholder="至少 6 位"
                      className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 pr-12 text-slate-900 outline-none transition focus:border-primary-500 focus:ring-4 focus:ring-primary-500/10 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50"
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((prev) => !prev)}
                      className="absolute inset-y-0 right-0 flex items-center px-4 text-slate-400 transition hover:text-slate-600 dark:hover:text-slate-200"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                {errorMessage && (
                  <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200">
                    {errorMessage}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isSubmitting || loading}
                  className="gradient-button w-full disabled:cursor-not-allowed disabled:opacity-70"
                >
                  {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                  创建账号并继续
                </button>
              </form>
            )}
          </div>

          <p className="mt-4 text-center text-xs leading-5 text-slate-500 dark:text-slate-400">
            登录后将返回 <span className="font-semibold text-slate-700 dark:text-slate-200">{fromPath}</span>。
            当前已启用账号接口与会话持久化。
          </p>

          <div className="mt-4 flex items-center justify-center gap-2 text-xs text-slate-400 dark:text-slate-500">
            <Loader2 className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : 'opacity-0'}`} />
            <span>{loading ? '正在同步会话...' : '会话已准备就绪'}</span>
          </div>
        </motion.section>
      </div>

      <div className="mx-auto mt-6 max-w-7xl text-center text-xs text-slate-400 dark:text-slate-500">
        <Link to="/history" className="transition-colors hover:text-primary-600 dark:hover:text-primary-300">
          返回应用首页
        </Link>
      </div>
    </div>
  );
}
