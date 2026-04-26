/**
 * ============================================================================
 * src/hooks/useYouTubePlayer.ts  —  YouTube IFrame API 폴링 훅
 * ============================================================================
 *
 * 역할:
 *   YouTube IFrame API를 동적으로 로드하고 사용자 행동 패턴을 자동 감지한다.
 *
 *   감지하는 4가지 패턴:
 *   ┌──────────────────┬──────────────────────────────────────────────────────┐
 *   │ 패턴             │ 감지 방식                                             │
 *   ├──────────────────┼──────────────────────────────────────────────────────┤
 *   │ video_rewind     │ 1초 폴링: 현재 위치 < 이전 위치 - 5초               │
 *   │ video_skip       │ 1초 폴링: 현재 위치 > 이전 위치 + 30초              │
 *   │ video_pause      │ pause 상태에서 3분(180초) 이상 지속                  │
 *   │ tab_departure    │ document.visibilitychange 이벤트                    │
 *   └──────────────────┴──────────────────────────────────────────────────────┘
 *
 * 사용 방법 (StudyClassroom.tsx):
 *   const { playerDivId } = useYouTubePlayer({
 *     videoId: 'dQw4w9WgXcQ',
 *     onPatternDetected: (type, payload) => { ... }
 *   })
 *   // JSX에서: <div id={playerDivId} style={{ width: '100%', height: '100%' }} />
 *
 * 주의사항:
 *   - 컴포넌트 unmount 시 cleanup이 자동으로 수행된다.
 *   - 동일 페이지에 여러 플레이어가 있으면 playerDivId를 고유하게 변경해야 한다.
 *   - YouTube IFrame API는 HTTPS 환경 또는 localhost에서만 동작한다.
 * ============================================================================
 */

import { useEffect, useRef, useCallback, type RefObject } from 'react'
import type { EventType, LearningEventPayload } from '../types/api'

// ── YouTube IFrame API 최소 타입 선언 ─────────────────────────────────────────
// @types/youtube 패키지를 설치하지 않고 필요한 타입만 선언한다.
// 실제 YouTube IFrame API 문서: https://developers.google.com/youtube/iframe_api_reference

/** YouTube 플레이어 인스턴스 인터페이스 (필요한 메서드만 선언) */
interface YTPlayer {
    /** 현재 재생 상태 반환: 1=playing, 2=paused, 0=ended, 3=buffering, -1=unstarted */
    getPlayerState(): number
    /** 현재 재생 위치를 초(seconds) 단위로 반환 */
    getCurrentTime(): number
    /** 영상 전체 길이를 초(seconds) 단위로 반환 */
    getDuration(): number
    /** 플레이어 인스턴스를 DOM에서 제거하고 메모리 해제 */
    destroy(): void
}

/** YT.Player 생성자 옵션 */
interface YTPlayerOptions {
    videoId: string
    playerVars?: {
        autoplay?: 0 | 1
        modestbranding?: 0 | 1
        rel?: 0 | 1
        [key: string]: number | string | undefined
    }
    events?: {
        onReady?: () => void
        onStateChange?: (event: { data: number }) => void
    }
}

// window 객체에 YouTube IFrame API 프로퍼티 추가
declare global {
    interface Window {
        YT: {
            Player: new (elementId: string, options: YTPlayerOptions) => YTPlayer
        }
        /** YouTube IFrame API 로드 완료 시 자동 호출되는 전역 콜백 */
        onYouTubeIframeAPIReady: () => void
    }
}

// ── 패턴 감지 임계값 상수 ─────────────────────────────────────────────────────
/** 되감기 감지 임계값: 현재 위치가 이전 위치보다 이 값(초)보다 뒤에 있으면 되감기로 판단 */
const REWIND_THRESHOLD_SEC = 5
/** 스킵 감지 임계값: 현재 위치가 이전 위치보다 이 값(초)보다 앞에 있으면 스킵으로 판단 */
const SKIP_THRESHOLD_SEC = 30
/** 폴링 간격(ms): 1초마다 getCurrentTime()을 호출하여 위치를 추적 */
const POLL_INTERVAL_MS = 1_000
/** 장시간 일시정지 임계값(초): pause 상태가 이 시간 이상 지속되면 video_pause 이벤트 발송 */
const LONG_PAUSE_THRESHOLD_SEC = 180

// ── 훅 인터페이스 ─────────────────────────────────────────────────────────────
export interface UseYouTubePlayerOptions {
    /** 재생할 YouTube 영상 ID (예: "dQw4w9WgXcQ") */
    videoId: string
    /**
     * 패턴이 감지될 때마다 호출되는 콜백.
     * 여기서 sendEventLog()를 호출하여 백엔드에 이벤트를 전송한다.
     *
     * @param eventType - 감지된 이벤트 종류
     * @param payload   - 이벤트 상세 데이터 (구간 정보 등)
     */
    onPatternDetected: (eventType: EventType, payload: LearningEventPayload) => void
    /**
     * 영상 시청 진행률 마일스톤(10%, 25%, 40%, 50%, 75%, 100%)에 도달할 때 호출.
     * updateProgress API 호출에 사용한다.
     *
     * @param rate - 0~100 진행률
     */
    onProgressMilestone?: (rate: number) => void
}

export interface UseYouTubePlayerReturn {
    /**
     * YouTube 플레이어가 마운트될 div의 id 속성값.
     * JSX에서 <div id={playerDivId} /> 형태로 사용한다.
     */
    playerDivId: string
    playerHostRef: RefObject<HTMLDivElement | null>
}

/** 진행률 마일스톤 목록 (오름차순) */
const PROGRESS_MILESTONES = [10, 25, 40, 50, 75, 100]

// ── 훅 구현 ───────────────────────────────────────────────────────────────────
export function useYouTubePlayer({
    videoId,
    onPatternDetected,
    onProgressMilestone,
}: UseYouTubePlayerOptions): UseYouTubePlayerReturn {
    // 플레이어가 렌더링될 div의 고유 ID
    const playerDivIdRef = useRef(`yt-player-${Math.random().toString(36).slice(2)}`)
    const playerDivId = playerDivIdRef.current
    const playerHostRef = useRef<HTMLDivElement | null>(null)

    // YT.Player 인스턴스 — re-render 시에도 유지되어야 하므로 ref로 관리
    const playerRef = useRef<YTPlayer | null>(null)

    // setInterval ID — cleanup 시 clearInterval에 사용
    const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

    // 직전 폴링 시의 재생 위치(초) — 되감기/스킵 계산의 기준점
    const lastTimeRef = useRef<number>(0)

    // 일시정지가 시작된 시각(Date.now() ms) — null이면 현재 재생 중
    const pauseStartedAtRef = useRef<number | null>(null)

    // 탭 이탈 횟수 누적 카운터
    const tabDepartureCountRef = useRef<number>(0)

    // onPatternDetected는 외부 props라 render마다 참조가 바뀔 수 있다.
    // ref로 감싸서 오래된 클로저(stale closure) 문제를 방지한다.
    const onPatternRef = useRef(onPatternDetected)
    useEffect(() => { onPatternRef.current = onPatternDetected }, [onPatternDetected])

    const onProgressRef = useRef(onProgressMilestone)
    useEffect(() => { onProgressRef.current = onProgressMilestone }, [onProgressMilestone])

    // 이미 알린 마일스톤 추적 (중복 알림 방지)
    const reportedMilestonesRef = useRef<Set<number>>(new Set())
    // 영상 전체 길이 캐시 (YTPlayer API: getDuration)
    const durationRef = useRef<number>(0)

    // ── 폴링 시작 ────────────────────────────────────────────────────────────
    const startPolling = useCallback(() => {
        // 이미 폴링 중이면 중복 시작 방지
        if (pollIntervalRef.current !== null) return

        pollIntervalRef.current = setInterval(() => {
            const player = playerRef.current
            if (!player) return

            const state   = player.getPlayerState()
            const current = player.getCurrentTime()

            if (state === 1) {
                // ── 재생 중(playing): 위치 변화로 되감기 및 스킵 감지 ─────────
                const delta = current - lastTimeRef.current

                if (delta < -REWIND_THRESHOLD_SEC) {
                    // 현재 위치 < 이전 위치 - 5초 → 되감기(rewind) 감지
                    onPatternRef.current('video_rewind', {
                        video_id:          videoId,
                        rewind_target_sec: current,
                    })
                } else if (delta > SKIP_THRESHOLD_SEC) {
                    // 현재 위치 > 이전 위치 + 30초 → 스킵(skip) 감지
                    onPatternRef.current('video_skip', {
                        video_id:      videoId,
                        skip_from_sec: lastTimeRef.current,
                        skip_to_sec:   current,
                    })
                }

                // ── 진행률 마일스톤 감지 ───────────────────────────────────
                if (onProgressRef.current) {
                    const duration = durationRef.current || player.getDuration()
                    if (duration > 0) {
                        durationRef.current = duration
                        const pct = Math.floor((current / duration) * 100)
                        for (const milestone of PROGRESS_MILESTONES) {
                            if (pct >= milestone && !reportedMilestonesRef.current.has(milestone)) {
                                reportedMilestonesRef.current.add(milestone)
                                onProgressRef.current(milestone)
                            }
                        }
                    }
                }

                // 재생 중에는 일시정지 타이머 초기화
                pauseStartedAtRef.current = null
                lastTimeRef.current = current

            } else if (state === 2) {
                // ── 일시정지 중(paused): 장시간 일시정지 감지 ───────────────
                if (pauseStartedAtRef.current === null) {
                    // 방금 일시정지됨 → 시작 시각 기록
                    pauseStartedAtRef.current = Date.now()
                } else {
                    const pausedSec = (Date.now() - pauseStartedAtRef.current) / 1000
                    if (pausedSec >= LONG_PAUSE_THRESHOLD_SEC) {
                        // 3분 이상 정지 → video_pause 이벤트 발송
                        onPatternRef.current('video_pause', {
                            video_id:           videoId,
                            pause_start_sec:    lastTimeRef.current,
                            pause_duration_sec: pausedSec,
                            trigger_type:       'long_pause',
                        })
                        // 연속 발동 방지: 타이머를 현재 시각으로 리셋
                        // → 다음 3분이 지났을 때 다시 감지됨
                        pauseStartedAtRef.current = Date.now()
                    }
                }
            }
        }, POLL_INTERVAL_MS)
    }, [videoId])

    // ── 폴링 정지 ────────────────────────────────────────────────────────────
    const stopPolling = useCallback(() => {
        if (pollIntervalRef.current !== null) {
            clearInterval(pollIntervalRef.current)
            pollIntervalRef.current = null
        }
    }, [])

    // ── YouTube IFrame API 초기화 및 탭 이탈 감지 등록 ───────────────────────
    useEffect(() => {
        stopPolling()
        if (playerRef.current) {
            playerRef.current.destroy()
            playerRef.current = null
        }
        lastTimeRef.current = 0
        pauseStartedAtRef.current = null
        tabDepartureCountRef.current = 0
        durationRef.current = 0
        reportedMilestonesRef.current = new Set()

        const host = playerHostRef.current
        if (host) {
            host.innerHTML = ''
        }

        if (!videoId || !host) return

        const mountNode = document.createElement('div')
        mountNode.id = playerDivId
        mountNode.style.width = '100%'
        mountNode.style.height = '100%'
        host.appendChild(mountNode)

        let cancelled = false

        /**
         * YouTube IFrame API 준비 완료 후 플레이어 인스턴스를 생성한다.
         * window.onYouTubeIframeAPIReady 콜백 또는 API가 이미 로드된 경우 직접 호출된다.
         */
        const initPlayer = () => {
            if (cancelled || !document.getElementById(playerDivId)) {
                return
            }
            playerRef.current = new window.YT.Player(playerDivId, {
                videoId,
                playerVars: {
                    autoplay:        0, // 자동 재생 비활성화 (사용자가 직접 재생)
                    modestbranding:  1, // YouTube 로고 최소화
                    rel:             0, // 같은 채널 영상만 관련 영상으로 표시
                },
                events: {
                    onReady: () => {
                        // 플레이어 DOM 준비 완료 → 폴링 시작
                        startPolling()
                    },
                    onStateChange: (event) => {
                        // 영상 종료(0)시 폴링 정지, 재생 재개(1)시 폴링 재시작
                        if (event.data === 0) {
                            stopPolling()
                            // 영상 종료 → 100% 마일스톤
                            if (onProgressRef.current && !reportedMilestonesRef.current.has(100)) {
                                reportedMilestonesRef.current.add(100)
                                onProgressRef.current(100)
                            }
                        }
                        if (event.data === 1) startPolling()
                    },
                },
            })
        }

        // YouTube IFrame API 로드 여부 확인
        if (window.YT?.Player) {
            // 이미 로드된 경우 바로 초기화
            initPlayer()
        } else {
            // 처음 로드: <script> 태그를 <head>에 동적으로 삽입
            // YouTube API 스크립트가 로드 완료되면 window.onYouTubeIframeAPIReady를 호출한다.
            if (!document.querySelector('script[src*="youtube.com/iframe_api"]')) {
                const tag = document.createElement('script')
                tag.src   = 'https://www.youtube.com/iframe_api'
                document.head.appendChild(tag)
            }
            // API 준비 완료 콜백 등록 — YouTube API가 자동으로 이 함수를 호출함
            window.onYouTubeIframeAPIReady = initPlayer
        }

        // ── 탭 이탈 감지 ─────────────────────────────────────────────────────
        // document.visibilitychange 이벤트: 사용자가 다른 탭/앱으로 전환할 때 발생
        const handleVisibilityChange = () => {
            if (document.visibilityState === 'hidden') {
                tabDepartureCountRef.current += 1
                const currentTime = playerRef.current?.getCurrentTime() ?? 0

                onPatternRef.current('tab_departure', {
                    video_id:        videoId,
                    departure_sec:   currentTime,
                    return_sec:      currentTime, // 복귀 시점은 알 수 없으므로 동일값 전달
                    departure_count: tabDepartureCountRef.current,
                    current_keyword: '',          // 백엔드가 curriculum_id 기반으로 현재 키워드를 조회
                })
            }
        }

        document.addEventListener('visibilitychange', handleVisibilityChange)

        // ── cleanup (컴포넌트 unmount 시 실행) ──────────────────────────────
        return () => {
            cancelled = true
            stopPolling()
            document.removeEventListener('visibilitychange', handleVisibilityChange)

            // 플레이어 인스턴스를 파괴하여 메모리 누수 방지
            if (playerRef.current) {
                playerRef.current.destroy()
                playerRef.current = null
            }
            host.innerHTML = ''
        }
    }, [videoId, playerDivId, startPolling, stopPolling])

    return { playerDivId, playerHostRef }
}
