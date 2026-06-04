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
    /** 현재 재생 배속 반환: 0.25, 0.5, 1, 1.5, 2 등 */
    getPlaybackRate(): number
    /** 영상 재생 배속 설정 */
    setPlaybackRate(suggestedRate: number): void
    /** 영상 재생 */
    playVideo(): void
    /** 영상 일시정지 */
    pauseVideo(): void
    /** 지정한 초 위치로 이동 */
    seekTo(seconds: number, allowSeekAhead?: boolean): void
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
        // onPlaybackRateChange 타입 추가
        // YouTube IFrame API는 배속 변경 시 이 이벤트를 발생시키며,
        // 폴링에만 의존하면 변경 감지가 최대 1초 지연되거나 누락될 수 있다.
        onPlaybackRateChange?: (event: { data: number }) => void
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
/** 고배속 감지 임계값: 이 배속 이상이면 video_speed_up 이벤트 추적 시작 */
const SPEED_THRESHOLD = 2.0
/** 2배속 최소 유지 시간(초): 이 시간 이상 유지해야 video_speed_up 이벤트 발송 */
const SPEED_MIN_DURATION_SEC = 5

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
    /**
     * 1초 폴링마다 현재 재생 위치·시간·배속을 전달한다.
     * 커스텀 플레이어 컨트롤 UI(타임라인, 속도 표시 등)에 사용한다.
     */
    onTimeUpdate?: (current: number, duration: number, isPlaying: boolean, rate: number) => void
}

export interface UseYouTubePlayerReturn {
    /**
     * YouTube 플레이어가 마운트될 div의 id 속성값.
     * JSX에서 <div id={playerDivId} /> 형태로 사용한다.
     */
    playerDivId: string
    playerHostRef: RefObject<HTMLDivElement | null>
    /** 외부에서 영상을 일시정지할 때 사용 (모달 오픈 시 등) */
    pausePlayer: () => void
    /** 외부에서 영상을 재생할 때 사용 */
    playVideo: () => void
    /** 외부에서 특정 시점으로 영상을 이동할 때 사용 */
    seekPlayer: (sec: number) => void
    /** 재생 배속 설정 (0.75 / 1 / 1.5 / 2 등) */
    setPlaybackRate: (rate: number) => void
}

/** 진행률 마일스톤 목록 (오름차순) */
const PROGRESS_MILESTONES = [10, 25, 40, 50, 75, 100]

// ── 훅 구현 ───────────────────────────────────────────────────────────────────
export function useYouTubePlayer({
                                     videoId,
                                     onPatternDetected,
                                     onProgressMilestone,
                                     onTimeUpdate,
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

    // 고배속(>= 2x) 구간 추적: 배속 시작 시각과 해당 시점의 영상 위치
    const speedStartedAtRef  = useRef<number | null>(null)
    const speedStartSecRef   = useRef<number>(0)
    // 현재 2배속 세션에서 이미 이벤트를 발송했는지 — 중복 발송 방지
    const speedEventFiredRef = useRef<boolean>(false)

    // 탭 이탈 횟수 누적 카운터
    const tabDepartureCountRef = useRef<number>(0)

    // 영상 종료 여부 — 종료 후 패턴 감지 억제에 사용
    const videoEndedRef = useRef<boolean>(false)

    // seekPlayer 호출 후 되감기/스킵 오탐 억제 — Date.now() ms 기준 만료 시각
    const seekSuppressUntilRef = useRef<number>(0)

    // onPatternDetected는 외부 props라 render마다 참조가 바뀔 수 있다.
    // ref로 감싸서 오래된 클로저(stale closure) 문제를 방지한다.
    const onPatternRef = useRef(onPatternDetected)
    useEffect(() => { onPatternRef.current = onPatternDetected }, [onPatternDetected])

    const onProgressRef = useRef(onProgressMilestone)
    useEffect(() => { onProgressRef.current = onProgressMilestone }, [onProgressMilestone])

    const onTimeUpdateRef = useRef(onTimeUpdate)
    useEffect(() => { onTimeUpdateRef.current = onTimeUpdate }, [onTimeUpdate])

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

            // 영상 종료 후에는 패턴 감지를 억제한다 (모니터링 모달 방지)
            if (videoEndedRef.current) return

            if (state === 1) {
                // ── 재생 중(playing): 위치 변화로 되감기 및 스킵 감지 ─────────
                const delta = current - lastTimeRef.current

                const now = Date.now()
                if (delta < -REWIND_THRESHOLD_SEC && now >= seekSuppressUntilRef.current) {
                    // 현재 위치 < 이전 위치 - 5초 → 되감기(rewind) 감지
                    // seekPlayer 호출 직후 2초는 억제 (퀴즈 오답 되감기 오탐 방지)
                    onPatternRef.current('video_rewind', {
                        video_id:          videoId,
                        rewind_target_sec: current,
                    })
                } else if (delta > SKIP_THRESHOLD_SEC && now >= seekSuppressUntilRef.current) {
                    // 현재 위치 > 이전 위치 + 30초 → 스킵(skip) 감지
                    onPatternRef.current('video_skip', {
                        video_id:      videoId,
                        skip_from_sec: lastTimeRef.current,
                        skip_to_sec:   current,
                    })
                }

                // ── 고배속(2x 이상) 감지 ──────────────────────────────────
                // 폴링에서는 구간 시작/종료 로직을 제거하고
                // onPlaybackRateChange 이벤트에서 처리한다.
                // 폴링은 오직 "이미 열린 구간의 5초 도달 여부"만 확인한다.
                if (speedStartedAtRef.current !== null && !speedEventFiredRef.current) {
                    const duration_sec = (Date.now() - speedStartedAtRef.current) / 1000
                    if (duration_sec >= SPEED_MIN_DURATION_SEC) {
                        onPatternRef.current('video_speed_up', {
                            video_id:        videoId,
                            speed_start_sec: speedStartSecRef.current,
                            duration_sec,
                        })
                        speedEventFiredRef.current = true
                    }
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

                // 커스텀 UI용 시간 콜백
                if (onTimeUpdateRef.current) {
                    const dur = durationRef.current || player.getDuration()
                    onTimeUpdateRef.current(current, dur, true, player.getPlaybackRate())
                }

            } else if (state === 2) {
                // ── 일시정지 중(paused): 장시간 일시정지 감지 ───────────────

                // 2배속 구간 중 일시정지 처리
                // pauseStartedAtRef가 null인 시점 = 방금 막 일시정지된 첫 폴링 틱.
                // 이 시점의 Date.now()를 구간 종료 시각으로 사용해야 일시정지
                // 대기 시간이 duration_sec에 포함되는 것을 막을 수 있다.
                // 이후 폴링 틱부터는 pauseStartedAtRef에 기록된 시각을 기준으로
                // 계산하여 일시정지 시간이 누적되지 않도록 한다.
                if (speedStartedAtRef.current !== null) {
                    if (!speedEventFiredRef.current) {
                        // 일시정지 시작 시각이 기록되어 있으면 그 시각까지만,
                        // 없으면(첫 폴링) 현재 시각 기준으로 계산
                        const speedEndTime = pauseStartedAtRef.current ?? Date.now()
                        const duration_sec = (speedEndTime - speedStartedAtRef.current) / 1000
                        if (duration_sec >= SPEED_MIN_DURATION_SEC) {
                            onPatternRef.current('video_speed_up', {
                                video_id:        videoId,
                                speed_start_sec: speedStartSecRef.current,
                                duration_sec,
                            })
                        }
                    }
                    speedStartedAtRef.current  = null
                    speedEventFiredRef.current = false
                }

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

                // 커스텀 UI용 시간 콜백 (일시정지 중에도 슬라이더 위치 유지)
                if (onTimeUpdateRef.current) {
                    const dur = durationRef.current || player.getDuration()
                    onTimeUpdateRef.current(lastTimeRef.current, dur, false, player.getPlaybackRate())
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
        speedStartedAtRef.current  = null
        speedStartSecRef.current   = 0
        speedEventFiredRef.current = false
        tabDepartureCountRef.current = 0
        durationRef.current = 0
        reportedMilestonesRef.current = new Set()
        videoEndedRef.current = false

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

                    // onPlaybackRateChange 이벤트 핸들러 추가
                    // YouTube IFrame API는 배속 변경 시 onStateChange가 아닌
                    // 이 이벤트를 발생시킨다. 폴링(1초 간격)에만 의존하면 빠른
                    // 배속 변경이 누락되거나 구간 경계가 부정확해진다.
                    onPlaybackRateChange: (event) => {
                        const newRate = event.data

                        if (newRate < SPEED_THRESHOLD) {
                            // 배속이 임계값 아래로 내려감 → 구간 즉시 종료
                            // 폴링 틱을 기다리지 않고 정확한 시점에 리셋한다.
                            speedStartedAtRef.current  = null
                            speedEventFiredRef.current = false
                        } else if (speedStartedAtRef.current === null) {
                            // 배속이 임계값 이상으로 올라감 → 구간 즉시 시작
                            // 폴링 틱 지연(최대 1초) 없이 정확한 시작 시각을 기록한다.
                            speedStartedAtRef.current  = Date.now()
                            speedStartSecRef.current   = playerRef.current?.getCurrentTime() ?? 0
                            speedEventFiredRef.current = false
                        }
                        // speedStartedAtRef !== null && newRate >= SPEED_THRESHOLD:
                        // 이미 구간이 열려 있는 상태에서 배속이 또 변경된 경우(예: 2x → 1.5x → 2x).
                        // 기존 구간을 유지하고 시작 시각을 바꾸지 않는다.
                        // (구간을 리셋하고 싶다면 이 조건에 리셋 로직을 추가할 것)
                    },

                    onStateChange: (event) => {
                        // 영상 종료(0)시 폴링 정지, 재생 재개(1)시 폴링 재시작
                        if (event.data === 0) {
                            stopPolling()
                            videoEndedRef.current = true
                            // 영상 종료 시점에 2배속 구간이 열려 있고 미발송이면 이벤트 발송
                            if (speedStartedAtRef.current !== null) {
                                if (!speedEventFiredRef.current) {
                                    const duration_sec = (Date.now() - speedStartedAtRef.current) / 1000
                                    if (duration_sec >= SPEED_MIN_DURATION_SEC) {
                                        onPatternRef.current('video_speed_up', {
                                            video_id:        videoId,
                                            speed_start_sec: speedStartSecRef.current,
                                            duration_sec,
                                        })
                                    }
                                }
                                speedStartedAtRef.current  = null
                                speedEventFiredRef.current = false
                            }
                            // 영상 종료 → 100% 마일스톤
                            if (onProgressRef.current && !reportedMilestonesRef.current.has(100)) {
                                reportedMilestonesRef.current.add(100)
                                onProgressRef.current(100)
                            }
                        }
                        if (event.data === 1) {
                            // 재생 재개(재시작 포함) — 종료 플래그 해제
                            videoEndedRef.current = false
                            startPolling()
                        }
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
                // 영상 종료 후 탭 이탈은 학습 패턴이 아니므로 무시
                if (videoEndedRef.current) return

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

    const pausePlayer = useCallback(() => {
        playerRef.current?.pauseVideo()
    }, [])

    const playVideo = useCallback(() => {
        playerRef.current?.playVideo()
    }, [])

    const seekPlayer = useCallback((sec: number) => {
        seekSuppressUntilRef.current = Date.now() + 2000
        playerRef.current?.seekTo(sec, true)
    }, [])

    const setPlaybackRate = useCallback((rate: number) => {
        playerRef.current?.setPlaybackRate(rate)
        // onPlaybackRateChange 이벤트와 동일한 로직으로 즉시 ref 상태 정리
        if (rate < SPEED_THRESHOLD) {
            speedStartedAtRef.current  = null
            speedEventFiredRef.current = false
        } else if (speedStartedAtRef.current === null) {
            speedStartedAtRef.current  = Date.now()
            speedStartSecRef.current   = playerRef.current?.getCurrentTime() ?? 0
            speedEventFiredRef.current = false
        }
    }, [])

    return { playerDivId, playerHostRef, pausePlayer, playVideo, seekPlayer, setPlaybackRate }
}