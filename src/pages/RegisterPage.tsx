/**
 * ============================================================================
 * RegisterPage.tsx  —  4단계 회원가입 위저드
 * ============================================================================
 *
 * 백엔드 연동 지점:
 *   POST /api/auth/register  (apiService.register)
 *
 * 전송 데이터 (RegisterRequest 타입 — src/types/api.ts):
 *   {
 *     "login_id":                 string,    // Step 1 — loginId
 *     "password":                 string,    // Step 1 — password
 *     "name":                     string,    // Step 1 — name
 *     "nickname":                 string,    // Step 1 — nickname
 *     "email":                    string,    // Step 1 — email
 *     "birth_date":               string,    // Step 2 — birthDate ("YYYY-MM-DD")
 *     "interest_keywords":        string[],  // Step 3 — interestKeywords (# 제거)
 *     "study_suggestion_enabled": boolean    // Step 2 — studySuggestionEnabled
 *   }
 *
 * 화면 전환 조건:
 *   navigate('/') 는 반드시 API success: true 확인 후에만 실행된다.
 *   성공 토스트 800ms 표시 후 전환.
 * ============================================================================
 */

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    ChevronRight,
    Eye,
    EyeOff,
    CheckCircle2,
    X,
} from 'lucide-react'

// 백엔드 POST /api/auth/register 연동 지점
import { register } from '../services/apiService'
// 성공/실패 토스트 알림
import { useToast } from '../hooks/useToast'
import Toast from '../components/Toast'

import '../styles/RegisterPage.css'

// ─────────────────────────────────────────────────────────────────────────────
// 관심 키워드 프리셋 목록 (Step 3)
// 백엔드 전송 시 # 접두사를 제거하여 interest_keywords 배열에 담음
// ─────────────────────────────────────────────────────────────────────────────
const PRESET_KEYWORDS = [
    '#정보처리기사', '#웹개발',   '#CS 면접',   '#SQL/DB',
    '#알고리즘',     '#자료구조', '#운영체제',   '#네트워크',
    '#Java',         '#Python',   '#JavaScript', '#React',
    '#Spring',       '#Docker',   '#Git',        '#리눅스',
]

// 단계 라벨
const STEPS = ['기본 정보', '추가 정보', '관심 키워드', '가입 확인'] as const

// ─────────────────────────────────────────────────────────────────────────────
// 폼 상태 타입 — 키 이름은 API 필드와 1:1 매핑 기준으로 설계
// ─────────────────────────────────────────────────────────────────────────────
interface FormData {
    // ── Step 1: 기본 정보 ── (백엔드 필드명 → login_id, password, name, nickname, email)
    name:            string   // → name
    loginId:         string   // → login_id
    email:           string   // → email
    nickname:        string   // → nickname
    password:        string   // → password
    passwordConfirm: string   // UI 전용 — API 미전송 (클라이언트 검증 후 폐기)

    // ── Step 2: 추가 정보 ── (백엔드 필드명 → birth_date, study_suggestion_enabled)
    birthDate:              string   // → birth_date  ("YYYY-MM-DD")
    studySuggestionEnabled: boolean  // → study_suggestion_enabled

    // ── Step 3: 관심 키워드 ── (백엔드 필드명 → interest_keywords)
    interestKeywords: string[]  // → interest_keywords (# 제거 후 전송)
    customKeyword:    string    // UI 전용 — 직접 입력 임시 버퍼 (API 미전송)

    // ── Step 4: 이용 동의 ── (API 미전송 — 클라이언트 전용 UX)
    agreedTos:       boolean
    agreedMarketing: boolean
}

const INITIAL_FORM: FormData = {
    name: '', loginId: '', email: '', nickname: '',
    password: '', passwordConfirm: '',
    birthDate: '', studySuggestionEnabled: true,
    interestKeywords: [], customKeyword: '',
    agreedTos: false, agreedMarketing: false,
}

// ─────────────────────────────────────────────────────────────────────────────
// 단계별 유효성 검사
// ─────────────────────────────────────────────────────────────────────────────
function validateStep(step: number, form: FormData): string {
    if (step === 1) {
        if (!form.name.trim())     return '이름을 입력해 주세요.'
        if (!form.loginId.trim())  return '아이디를 입력해 주세요.'
        if (!form.email.trim())    return '이메일을 입력해 주세요.'
        if (!form.nickname.trim()) return '닉네임을 입력해 주세요.'
        if (form.password.length < 8) return '비밀번호는 8자 이상이어야 합니다.'
        if (form.password !== form.passwordConfirm) return '비밀번호가 일치하지 않습니다.'
    }
    if (step === 2) {
        if (!form.birthDate) return '생년월일을 입력해 주세요.'
    }
    if (step === 3) {
        if (form.interestKeywords.length === 0) return '관심 키워드를 하나 이상 선택해 주세요.'
    }
    if (step === 4) {
        if (!form.agreedTos) return '필수 이용약관에 동의해 주세요.'
    }
    return ''
}

// ─────────────────────────────────────────────────────────────────────────────
// 컴포넌트
// ─────────────────────────────────────────────────────────────────────────────
export default function RegisterPage() {
    const navigate = useNavigate()
    const { toast, showToast } = useToast()

    const [step,    setStep]    = useState(1)
    const [form,    setForm]    = useState<FormData>(INITIAL_FORM)
    const [loading, setLoading] = useState(false)
    const [stepError, setStepError] = useState('')  // 단계별 인라인 에러
    const [showPw,  setShowPw]  = useState(false)
    const [showPwC, setShowPwC] = useState(false)

    // 단일 필드 업데이트 헬퍼
    function setField<K extends keyof FormData>(field: K, value: FormData[K]) {
        setForm(prev => ({ ...prev, [field]: value }))
        setStepError('')
    }

    // 관심 키워드 토글 — interestKeywords 배열 갱신
    const toggleKeyword = (kw: string) => {
        setField(
            'interestKeywords',
            form.interestKeywords.includes(kw)
                ? form.interestKeywords.filter(k => k !== kw)
                : [...form.interestKeywords, kw],
        )
    }

    // 직접 입력 키워드 추가
    const addCustomKeyword = () => {
        const kw = form.customKeyword.trim()
        if (kw && !form.interestKeywords.includes(kw)) {
            setField('interestKeywords', [...form.interestKeywords, kw])
        }
        setField('customKeyword', '')
    }

    // 다음 단계
    const next = () => {
        const err = validateStep(step, form)
        if (err) { setStepError(err); return }
        setStepError('')
        setStep(s => s + 1)
    }

    // 이전 단계
    const back = () => { setStepError(''); setStep(s => s - 1) }

    // ── 백엔드 POST /api/auth/register 연동 지점 ─────────────────────────────
    const handleRegister = async () => {
        const err = validateStep(4, form)
        if (err) { setStepError(err); return }

        setLoading(true)
        setStepError('')

        try {
            /**
             * ✅ 백엔드 POST /api/auth/register 연동 지점
             *
             * 요청 바디 (RegisterRequest — src/types/api.ts):
             *   {
             *     "login_id":                 "...",          // form.loginId
             *     "password":                 "...",          // form.password (BCrypt — 백엔드 처리)
             *     "name":                     "...",          // form.name
             *     "nickname":                 "...",          // form.nickname
             *     "email":                    "...",          // form.email
             *     "birth_date":               "YYYY-MM-DD",  // form.birthDate
             *     "interest_keywords":        ["웹개발", …], // form.interestKeywords (# 제거)
             *     "study_suggestion_enabled": true|false      // form.studySuggestionEnabled
             *   }
             *
             * 성공 응답 (success: true):
             *   { "userId": string, "nickname": string, "message": string }
             *   → 토큰 미발급 — 별도 로그인 필요
             *
             * 실패 응답 → unwrap() 이 Error throw → catch 블록으로 이동
             *   - 409: 아이디 또는 이메일 중복
             *   - 400: 요청 형식 오류
             *
             * 미전송 UI 전용 필드:
             *   - passwordConfirm: 클라이언트 검증 후 폐기
             *   - customKeyword:   interestKeywords 에 합산 후 폐기
             *   - agreedTos, agreedMarketing: 클라이언트 UX 전용
             */
            await register({
                login_id:                 form.loginId,
                password:                 form.password,
                name:                     form.name,
                nickname:                 form.nickname,
                email:                    form.email,
                birth_date:               form.birthDate,
                // # 접두사 제거 후 전송 — 백엔드 interest_keywords 필드
                interest_keywords:        form.interestKeywords.map(k => k.replace(/^#/, '')),
                // 스터디 매칭 허용 여부 — 백엔드 study_suggestion_enabled 필드
                study_suggestion_enabled: form.studySuggestionEnabled,
            })

            // ✅ 화면 전환은 API success: true 확인 후에만 실행됨
            showToast('success', '회원가입이 완료되었습니다! 로그인해 주세요.')
            setTimeout(() => navigate('/', { replace: true }), 800)

        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : '회원가입에 실패했습니다.'
            showToast('error', msg)
            setStepError(msg)

        } finally {
            setLoading(false)
        }
    }

    const progressPct = (step / 4) * 100

    return (
        <>
            {/* ── 성공/실패 토스트 — 화면 상단 중앙 고정 ── */}
            <Toast toast={toast} />

            <div className="reg">
                {/* ── 좌측 브랜드 패널 ── */}
                <div className="reg__left animate-slide-up">
                    <div className="reg__brand-name">MoAI</div>
                    <div className="reg__brand-sub">INTELLIGENT TUTOR</div>

                    <h1 className="reg__headline">
                        지금 가입하고<br />
                        <span className="reg__headline-accent">나만의 AI 튜터</span>를<br />
                        만나보세요.
                    </h1>
                    <p className="reg__description">
                        행동 분석 · 거꾸로 학습 · 지능형 멘토 매칭<br />
                        MoAI가 당신의 목표를 현실로 만듭니다.
                    </p>

                    {/* 단계 안내 — 현재 단계 강조 */}
                    <div className="reg__step-guide">
                        {STEPS.map((label, idx) => {
                            const num = idx + 1
                            const isDone   = step > num
                            const isActive = step === num
                            const dotCls   = isDone   ? 'reg__step-guide-dot--done'
                                           : isActive ? 'reg__step-guide-dot--active'
                                                      : 'reg__step-guide-dot--inactive'
                            const lblCls   = isDone   ? 'reg__step-guide-label--done'
                                           : isActive ? 'reg__step-guide-label--active'
                                                      : 'reg__step-guide-label--inactive'
                            return (
                                <div key={num}>
                                    <div className="reg__step-guide-item">
                                        <div className={`reg__step-guide-dot ${dotCls}`}>
                                            {isDone ? <CheckCircle2 size={16} /> : num}
                                        </div>
                                        <span className={`reg__step-guide-label ${lblCls}`}>
                                            {label}
                                        </span>
                                    </div>
                                    {idx < STEPS.length - 1 && (
                                        <div className="reg__step-guide-connector" />
                                    )}
                                </div>
                            )
                        })}
                    </div>
                </div>

                {/* ── 우측 폼 패널 ── */}
                <div className="reg__right">
                    <div className="reg__form-wrap animate-fade-in">
                        {/* 진행률 바 */}
                        <div className="reg__progress-bar">
                            <div className="reg__progress-fill" style={{ width: `${progressPct}%` }} />
                        </div>

                        {/* ════════════════════════════════════════════════════
                            Step 1 — 기본 정보
                            백엔드 필드: login_id, password, name, nickname, email
                        ════════════════════════════════════════════════════ */}
                        {step === 1 && (
                            <>
                                <div className="reg__step-title">기본 정보 입력</div>
                                <div className="reg__step-sub">회원가입에 필요한 기본 정보를 입력해 주세요.</div>

                                <div className="reg__fields">
                                    {/* 이름 / 아이디 — 백엔드: name / login_id */}
                                    <div className="reg__row">
                                        <div>
                                            <label className="reg__label">
                                                이름<span className="reg__required">*</span>
                                            </label>
                                            <input
                                                className="reg__input"
                                                placeholder="홍길동"
                                                value={form.name}
                                                onChange={e => setField('name', e.target.value)}
                                                autoComplete="name"
                                            />
                                        </div>
                                        <div>
                                            <label className="reg__label">
                                                아이디<span className="reg__required">*</span>
                                            </label>
                                            <input
                                                className="reg__input"
                                                placeholder="moai_user01"
                                                value={form.loginId}
                                                onChange={e => setField('loginId', e.target.value)}
                                                autoComplete="username"
                                            />
                                        </div>
                                    </div>

                                    {/* 이메일 — 백엔드: email */}
                                    <div>
                                        <label className="reg__label">
                                            이메일<span className="reg__required">*</span>
                                        </label>
                                        <input
                                            type="email"
                                            className="reg__input"
                                            placeholder="example@email.com"
                                            value={form.email}
                                            onChange={e => setField('email', e.target.value)}
                                            autoComplete="email"
                                        />
                                    </div>

                                    {/* 닉네임 — 백엔드: nickname */}
                                    <div>
                                        <label className="reg__label">
                                            닉네임<span className="reg__required">*</span>
                                        </label>
                                        <input
                                            className="reg__input"
                                            placeholder="화면에 표시될 이름"
                                            value={form.nickname}
                                            onChange={e => setField('nickname', e.target.value)}
                                            autoComplete="nickname"
                                        />
                                    </div>

                                    {/* 비밀번호 — 백엔드: password (BCrypt 해싱은 백엔드 처리) */}
                                    <div>
                                        <label className="reg__label">
                                            비밀번호<span className="reg__required">*</span>
                                        </label>
                                        <div className="reg__pw-wrap">
                                            <input
                                                type={showPw ? 'text' : 'password'}
                                                className="reg__input"
                                                placeholder="비밀번호를 입력하세요 (8자 이상)"
                                                value={form.password}
                                                onChange={e => setField('password', e.target.value)}
                                                style={{ paddingRight: '44px' }}
                                                autoComplete="new-password"
                                            />
                                            <button
                                                type="button"
                                                className="reg__pw-toggle"
                                                onClick={() => setShowPw(v => !v)}
                                                aria-label={showPw ? '비밀번호 숨기기' : '비밀번호 보기'}
                                            >
                                                {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                                            </button>
                                        </div>
                                    </div>

                                    {/* 비밀번호 확인 — UI 전용, API 미전송 */}
                                    <div>
                                        <label className="reg__label">
                                            비밀번호 확인<span className="reg__required">*</span>
                                        </label>
                                        <div className="reg__pw-wrap">
                                            <input
                                                type={showPwC ? 'text' : 'password'}
                                                className="reg__input"
                                                placeholder="비밀번호를 다시 입력하세요"
                                                value={form.passwordConfirm}
                                                onChange={e => setField('passwordConfirm', e.target.value)}
                                                style={{ paddingRight: '44px' }}
                                                autoComplete="new-password"
                                            />
                                            <button
                                                type="button"
                                                className="reg__pw-toggle"
                                                onClick={() => setShowPwC(v => !v)}
                                                aria-label={showPwC ? '비밀번호 숨기기' : '비밀번호 보기'}
                                            >
                                                {showPwC ? <EyeOff size={16} /> : <Eye size={16} />}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </>
                        )}

                        {/* ════════════════════════════════════════════════════
                            Step 2 — 추가 정보
                            백엔드 필드: birth_date, study_suggestion_enabled
                        ════════════════════════════════════════════════════ */}
                        {step === 2 && (
                            <>
                                <div className="reg__step-title">추가 정보 입력</div>
                                <div className="reg__step-sub">학습 매칭을 위한 추가 정보를 입력해 주세요.</div>

                                <div className="reg__fields">
                                    {/* 생년월일 — 백엔드: birth_date ("YYYY-MM-DD") */}
                                    <div>
                                        <label className="reg__label">
                                            생년월일<span className="reg__required">*</span>
                                        </label>
                                        <input
                                            type="date"
                                            className="reg__input"
                                            value={form.birthDate}
                                            max={new Date().toISOString().split('T')[0]}
                                            onChange={e => setField('birthDate', e.target.value)}
                                        />
                                    </div>

                                    {/* 스터디 매칭 허용 토글 — 백엔드: study_suggestion_enabled */}
                                    <div>
                                        <label className="reg__label">스터디 매칭</label>
                                        <div className="reg__toggle-row">
                                            <div className="reg__toggle-info">
                                                <div className="reg__toggle-label">스터디 매칭 허용</div>
                                                <div className="reg__toggle-desc">
                                                    AI가 관심 키워드 기반으로 최적의 스터디 파트너를 매칭해 드립니다.
                                                    멘토·멘티 역할을 자동으로 분석하여 학습 효율을 높입니다.
                                                </div>
                                            </div>
                                            <label className="reg__toggle-switch">
                                                <input
                                                    type="checkbox"
                                                    checked={form.studySuggestionEnabled}
                                                    onChange={e => setField('studySuggestionEnabled', e.target.checked)}
                                                />
                                                <span className="reg__toggle-slider" />
                                            </label>
                                        </div>
                                    </div>
                                </div>
                            </>
                        )}

                        {/* ════════════════════════════════════════════════════
                            Step 3 — 관심 키워드
                            백엔드 필드: interest_keywords (# 제거 후 전송)
                        ════════════════════════════════════════════════════ */}
                        {step === 3 && (
                            <>
                                <div className="reg__step-title">관심 키워드 선택</div>
                                <div className="reg__step-sub">
                                    관심 있는 학습 주제를 선택하세요. AI 매칭에 활용됩니다.
                                </div>

                                {/* 프리셋 칩 */}
                                <div className="reg__keyword-grid">
                                    {PRESET_KEYWORDS.map(kw => (
                                        <button
                                            key={kw}
                                            type="button"
                                            className={`reg__keyword-chip${
                                                form.interestKeywords.includes(kw)
                                                    ? ' reg__keyword-chip--selected'
                                                    : ''
                                            }`}
                                            onClick={() => toggleKeyword(kw)}
                                        >
                                            {kw}
                                        </button>
                                    ))}
                                </div>

                                {/* 직접 입력 — interestKeywords 에 추가 후 customKeyword 초기화 */}
                                <div className="reg__custom-input-row">
                                    <input
                                        className="reg__input"
                                        placeholder="직접 입력 (예: #머신러닝)"
                                        value={form.customKeyword}
                                        onChange={e => setField('customKeyword', e.target.value)}
                                        onKeyDown={e => e.key === 'Enter' && addCustomKeyword()}
                                    />
                                    <button
                                        type="button"
                                        className="reg__add-btn"
                                        onClick={addCustomKeyword}
                                    >
                                        추가
                                    </button>
                                </div>

                                {/* 직접 입력한 커스텀 키워드 칩 (프리셋 외) */}
                                {form.interestKeywords.filter(k => !PRESET_KEYWORDS.includes(k)).length > 0 && (
                                    <div className="reg__selected-chips">
                                        {form.interestKeywords
                                            .filter(k => !PRESET_KEYWORDS.includes(k))
                                            .map(kw => (
                                                <span key={kw} className="reg__selected-chip">
                                                    {kw}
                                                    <button
                                                        type="button"
                                                        onClick={() => toggleKeyword(kw)}
                                                        aria-label={`${kw} 제거`}
                                                    >
                                                        <X size={12} />
                                                    </button>
                                                </span>
                                            ))}
                                    </div>
                                )}
                            </>
                        )}

                        {/* ════════════════════════════════════════════════════
                            Step 4 — 가입 확인
                            입력 요약 테이블 + 이용약관 동의 (API 미전송)
                        ════════════════════════════════════════════════════ */}
                        {step === 4 && (
                            <>
                                <div className="reg__step-title">가입 정보 확인</div>
                                <div className="reg__step-sub">입력한 정보를 확인하고 가입을 완료하세요.</div>

                                {/* 요약 테이블 — API 전송 필드 대응 주석 포함 */}
                                <table className="reg__summary-table">
                                    <tbody>
                                        {/* login_id */}
                                        <tr>
                                            <td>아이디</td>
                                            <td>{form.loginId}</td>
                                        </tr>
                                        {/* nickname */}
                                        <tr>
                                            <td>닉네임</td>
                                            <td>{form.nickname}</td>
                                        </tr>
                                        {/* name */}
                                        <tr>
                                            <td>이름</td>
                                            <td>{form.name}</td>
                                        </tr>
                                        {/* email */}
                                        <tr>
                                            <td>이메일</td>
                                            <td>{form.email}</td>
                                        </tr>
                                        {/* birth_date */}
                                        <tr>
                                            <td>생년월일</td>
                                            <td>{form.birthDate}</td>
                                        </tr>
                                        {/* study_suggestion_enabled */}
                                        <tr>
                                            <td>스터디 매칭</td>
                                            <td>{form.studySuggestionEnabled ? '허용' : '거부'}</td>
                                        </tr>
                                        {/* interest_keywords */}
                                        <tr>
                                            <td>관심 키워드</td>
                                            <td>
                                                <div className="reg__summary-keywords">
                                                    {form.interestKeywords.map(kw => (
                                                        <span key={kw} className="reg__summary-kw">{kw}</span>
                                                    ))}
                                                </div>
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>

                                {/* 이용 동의 체크박스 — UI 전용, API 미전송 */}
                                <div className="reg__checkbox-list">
                                    <label className="reg__checkbox-row">
                                        <input
                                            type="checkbox"
                                            checked={form.agreedTos}
                                            onChange={e => setField('agreedTos', e.target.checked)}
                                        />
                                        <span>
                                            이용약관 및 개인정보 처리방침에 동의합니다.
                                            <span className="reg__checkbox-badge reg__checkbox-badge--required">필수</span>
                                        </span>
                                    </label>
                                    <label className="reg__checkbox-row">
                                        <input
                                            type="checkbox"
                                            checked={form.agreedMarketing}
                                            onChange={e => setField('agreedMarketing', e.target.checked)}
                                        />
                                        <span>
                                            마케팅 정보 수신에 동의합니다.
                                            <span className="reg__checkbox-badge reg__checkbox-badge--optional">선택</span>
                                        </span>
                                    </label>
                                </div>
                            </>
                        )}

                        {/* 단계별 인라인 에러 메시지 */}
                        {stepError && <p className="reg__error">{stepError}</p>}

                        {/* 하단 버튼 */}
                        <div className="reg__btn-row">
                            {step > 1 && (
                                <button className="reg__btn-back" onClick={back}>
                                    이전
                                </button>
                            )}

                            {step < 4 ? (
                                <button className="reg__btn-next" onClick={next}>
                                    다음 단계
                                    <ChevronRight size={16} />
                                </button>
                            ) : (
                                /* 회원가입 완료 — 백엔드 POST /api/auth/register 호출 */
                                <button
                                    className="reg__btn-next reg__btn-next--full"
                                    onClick={handleRegister}
                                    disabled={loading}
                                >
                                    {loading ? '처리 중...' : '회원가입 완료'}
                                </button>
                            )}
                        </div>

                        {step === 1 && (
                            <div className="reg__login-hint">
                                이미 계정이 있으신가요?{' '}
                                <span
                                    className="reg__login-link"
                                    onClick={() => navigate('/')}
                                >
                                    로그인하기
                                </span>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </>
    )
}
