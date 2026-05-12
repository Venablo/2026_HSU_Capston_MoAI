#!/usr/bin/env python3
"""
YouTube 자막 스크래퍼.

1차 경로: yt-dlp Python API (multi player_client 우회)
2차 경로: youtube-transcript-api (폴백, --no-fallback 으로 비활성화 가능)

계약:
- 성공 시 stdout: JSON 한 줄
    {"chunks": [{"text","start","duration"}, ...], "lang": "ko", "source": "yt-dlp"}
- 실패 시 stderr: 구조화된 JSON 한 줄 (stdout 은 절대 사용하지 않음)
    {"errorCode": "VIDEO_NOT_FOUND", "message": "...", "detail": "..."}
- 종료 코드는 Java SubtitleErrorCode enum 과 1:1 로 매핑됨 (CLAUDE.md / TODO.md 의 표 참조).
"""
import argparse
import contextlib
import json
import os
import re
import sys
import tempfile

# --- 종료 코드 (Java SubtitleErrorCode 와 1:1 매핑) -------------------------
EXIT_OK = 0
EXIT_VIDEO_NOT_FOUND = 10
EXIT_VIDEO_PRIVATE = 11
EXIT_VIDEO_AGE_RESTRICTED = 12
EXIT_VIDEO_REGION_BLOCKED = 13
EXIT_NO_SUBTITLES = 20
EXIT_RATE_LIMITED = 30
EXIT_NETWORK_ERROR = 31
EXIT_INVALID_VIDEO_ID = 40
EXIT_UNKNOWN = 99

# --- 에러 코드 문자열 (Java enum 이름과 동일) -------------------------------
EC_VIDEO_NOT_FOUND = "VIDEO_NOT_FOUND"
EC_VIDEO_PRIVATE = "VIDEO_PRIVATE"
EC_VIDEO_AGE_RESTRICTED = "VIDEO_AGE_RESTRICTED"
EC_VIDEO_REGION_BLOCKED = "VIDEO_REGION_BLOCKED"
EC_NO_SUBTITLES = "NO_SUBTITLES_AVAILABLE"
EC_RATE_LIMITED = "RATE_LIMITED"
EC_NETWORK = "NETWORK_ERROR"
EC_INVALID_ID = "INVALID_VIDEO_ID"
EC_UNKNOWN = "UNKNOWN_ERROR"

EXIT_BY_CODE = {
    EC_VIDEO_NOT_FOUND: EXIT_VIDEO_NOT_FOUND,
    EC_VIDEO_PRIVATE: EXIT_VIDEO_PRIVATE,
    EC_VIDEO_AGE_RESTRICTED: EXIT_VIDEO_AGE_RESTRICTED,
    EC_VIDEO_REGION_BLOCKED: EXIT_VIDEO_REGION_BLOCKED,
    EC_NO_SUBTITLES: EXIT_NO_SUBTITLES,
    EC_RATE_LIMITED: EXIT_RATE_LIMITED,
    EC_NETWORK: EXIT_NETWORK_ERROR,
    EC_INVALID_ID: EXIT_INVALID_VIDEO_ID,
    EC_UNKNOWN: EXIT_UNKNOWN,
}

# stderr 페이로드에 담길 사용자 메시지 (Java enum 의 한글 메시지와 동일하게 유지)
USER_MESSAGES = {
    EC_VIDEO_NOT_FOUND: "영상이 존재하지 않거나 삭제되었습니다",
    EC_VIDEO_PRIVATE: "비공개 영상은 자막을 가져올 수 없습니다",
    EC_VIDEO_AGE_RESTRICTED: "연령 제한 영상은 자막을 가져올 수 없습니다",
    EC_VIDEO_REGION_BLOCKED: "지역 제한으로 자막에 접근할 수 없습니다",
    EC_NO_SUBTITLES: "이 영상에는 사용 가능한 자막이 없습니다",
    EC_RATE_LIMITED: "YouTube 요청 한도 초과. 잠시 후 다시 시도해주세요",
    EC_NETWORK: "YouTube 서버 통신 실패",
    EC_INVALID_ID: "올바르지 않은 영상 ID",
    EC_UNKNOWN: "자막 추출 중 알 수 없는 오류",
}

# YouTube 영상 ID 는 정확히 11자리이며 [A-Za-z0-9_-] 만 허용
VIDEO_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")


def emit_error(error_code, detail=""):
    """구조화된 에러 JSON 을 stderr 에 한 줄로 출력한다."""
    payload = {
        "errorCode": error_code,
        "message": USER_MESSAGES.get(error_code, USER_MESSAGES[EC_UNKNOWN]),
        "detail": (detail or "")[:500],
    }
    sys.stderr.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stderr.flush()


def classify_yt_dlp_error(message):
    """yt-dlp 의 DownloadError 메시지를 분석하여 에러 코드로 분류한다."""
    msg = (message or "").lower()
    if "private" in msg:
        return EC_VIDEO_PRIVATE
    if "age" in msg and ("restrict" in msg or "confirm" in msg or "sign in" in msg):
        return EC_VIDEO_AGE_RESTRICTED
    if "geo" in msg or "country" in msg or "region" in msg:
        return EC_VIDEO_REGION_BLOCKED
    if "429" in msg or "too many requests" in msg or "rate limit" in msg:
        return EC_RATE_LIMITED
    if (
        "video unavailable" in msg
        or "removed" in msg
        or "does not exist" in msg
        or "no such video" in msg
        or "this video is not available" in msg
    ):
        return EC_VIDEO_NOT_FOUND
    if (
        "network" in msg
        or "connection" in msg
        or "timed out" in msg
        or "resolve" in msg
        or "ssl" in msg
    ):
        return EC_NETWORK
    return None


def transform_json3(data):
    """yt-dlp 의 json3 이벤트를 [{text, start, duration}] 형태로 변환한다 (초 단위, 소수점 3자리)."""
    chunks = []
    for ev in data.get("events", []) or []:
        start_ms = ev.get("tStartMs")
        dur_ms = ev.get("dDurationMs") or 0
        segs = ev.get("segs") or []
        if start_ms is None:
            continue
        text = "".join((seg.get("utf8") or "") for seg in segs).strip()
        if not text:
            continue
        chunks.append({
            "text": text,
            "start": round(start_ms / 1000.0, 3),
            "duration": round(dur_ms / 1000.0, 3),
        })
    return chunks


def try_yt_dlp(video_id, langs):
    """
    yt-dlp 로 자막 추출을 시도한다.

    반환값:
      성공: ({"chunks","lang","source"}, None, None)
      실패: (None, error_code, detail_string)
    """
    try:
        from yt_dlp import YoutubeDL
        from yt_dlp.utils import DownloadError
    except ImportError as e:
        return None, EC_UNKNOWN, "yt-dlp 미설치: {}".format(e)

    with tempfile.TemporaryDirectory(prefix="moai_subs_") as tmpdir:
        opts = {
            "writeautomaticsub": True,
            "writesubtitles": True,
            "subtitlesformat": "json3",
            "subtitleslangs": list(langs),
            "skip_download": True,
            "outtmpl": os.path.join(tmpdir, "%(id)s.%(ext)s"),
            "quiet": True,
            "no_warnings": True,
            # progress / 일반 로그까지 모두 stderr 로 우회 (stdout 오염 방지)
            "noprogress": True,
            "logtostderr": True,
            "consoletitle": False,
            "extractor_args": {
                "youtube": {"player_client": ["android", "web", "ios", "tv_embedded"]}
            },
        }

        try:
            # 옵션만으로 막지 못한 잔여 stdout 누출까지 차단하기 위해 호출 영역 자체를 stderr 로 redirect
            with contextlib.redirect_stdout(sys.stderr):
                with YoutubeDL(opts) as ydl:
                    ydl.download(["https://www.youtube.com/watch?v={}".format(video_id)])
        except DownloadError as e:
            code = classify_yt_dlp_error(str(e))
            return None, code or EC_NO_SUBTITLES, str(e)
        except Exception as e:
            return None, EC_NETWORK, "yt-dlp 예기치 못한 오류: {}".format(e)

        # 자막 파일 선택: 요청 언어 우선순위 → 그래도 없으면 첫 번째 json3 파일
        chosen_path = None
        chosen_lang = None
        for lang in langs:
            for fname in sorted(os.listdir(tmpdir)):
                if fname.endswith(".{}.json3".format(lang)):
                    chosen_path = os.path.join(tmpdir, fname)
                    chosen_lang = lang
                    break
            if chosen_path:
                break

        if not chosen_path:
            for fname in sorted(os.listdir(tmpdir)):
                if fname.endswith(".json3"):
                    chosen_path = os.path.join(tmpdir, fname)
                    parts = fname.split(".")
                    # 파일명 형식: <videoId>.<lang>.json3
                    if len(parts) >= 3:
                        chosen_lang = parts[-2]
                    break

        if not chosen_path:
            return None, EC_NO_SUBTITLES, "yt-dlp 가 json3 자막 파일을 생성하지 못함"

        try:
            with open(chosen_path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            return None, EC_UNKNOWN, "json3 파싱 실패: {}".format(e)

        chunks = transform_json3(data)
        if not chunks:
            return None, EC_NO_SUBTITLES, "json3 내부에 추출 가능한 세그먼트가 없음"

        return {
            "chunks": chunks,
            "lang": chosen_lang or langs[0],
            "source": "yt-dlp",
        }, None, None


def try_youtube_transcript_api(video_id, langs):
    """레거시 폴백 경로. try_yt_dlp 와 동일한 반환 형태를 사용한다."""
    try:
        from youtube_transcript_api import YouTubeTranscriptApi
        from youtube_transcript_api._errors import (
            TranscriptsDisabled,
            NoTranscriptFound,
            VideoUnavailable,
        )
    except ImportError as e:
        return None, EC_NO_SUBTITLES, "youtube-transcript-api 미설치: {}".format(e)

    try:
        api = YouTubeTranscriptApi()
        try:
            transcript_list = api.list(video_id)
        except VideoUnavailable as e:
            return None, EC_VIDEO_NOT_FOUND, str(e)
        except TranscriptsDisabled as e:
            return None, EC_NO_SUBTITLES, str(e)

        transcript = None
        chosen_lang = None
        # 수동 자막 → 자동 자막 → 그래도 없으면 첫 번째 가능한 자막
        for finder in ("find_manually_created_transcript", "find_generated_transcript"):
            for lang in langs:
                try:
                    transcript = getattr(transcript_list, finder)([lang]).fetch()
                    chosen_lang = lang
                    break
                except NoTranscriptFound:
                    continue
                except Exception:
                    continue
            if transcript is not None:
                break

        if transcript is None:
            for t in transcript_list:
                try:
                    transcript = t.fetch()
                    chosen_lang = getattr(t, "language_code", None)
                    break
                except Exception:
                    continue

        if transcript is None:
            return None, EC_NO_SUBTITLES, "youtube-transcript-api 로 어떤 자막도 가져오지 못함"

        chunks = []
        for snippet in transcript:
            # 라이브러리 버전에 따라 객체/딕셔너리 두 형태 모두 대응
            text = getattr(snippet, "text", None)
            if text is None and isinstance(snippet, dict):
                text = snippet.get("text")
            start = getattr(snippet, "start", None)
            if start is None and isinstance(snippet, dict):
                start = snippet.get("start")
            duration = getattr(snippet, "duration", None)
            if duration is None and isinstance(snippet, dict):
                duration = snippet.get("duration", 0)
            if text is None or start is None:
                continue
            chunks.append({
                "text": text,
                "start": float(start),
                "duration": float(duration or 0),
            })

        if not chunks:
            return None, EC_NO_SUBTITLES, "youtube-transcript-api 로 가져온 자막이 비어있음"

        return {
            "chunks": chunks,
            "lang": chosen_lang or (langs[0] if langs else "en"),
            "source": "youtube-transcript-api",
        }, None, None

    except Exception as e:
        msg = str(e).lower()
        if "429" in msg or "too many" in msg:
            return None, EC_RATE_LIMITED, str(e)
        return None, EC_NETWORK, "youtube-transcript-api 오류: {}".format(e)


# 영상 자체의 상태가 원인인 에러들. 폴백을 시도해도 동일 결과가 나오므로 즉시 종료한다.
_TERMINAL_VIDEO_CODES = {
    EC_VIDEO_PRIVATE,
    EC_VIDEO_AGE_RESTRICTED,
    EC_VIDEO_REGION_BLOCKED,
    EC_VIDEO_NOT_FOUND,
    EC_INVALID_ID,
}


def main():
    parser = argparse.ArgumentParser(description="MoAI YouTube 자막 스크래퍼")
    parser.add_argument("video_id")
    parser.add_argument("--langs", default="ko,en",
                        help="우선순위가 적용된 언어 목록 (콤마 구분, 기본값: ko,en)")
    parser.add_argument("--no-fallback", action="store_true",
                        help="youtube-transcript-api 폴백 비활성화")
    args = parser.parse_args()

    if not VIDEO_ID_RE.match(args.video_id or ""):
        emit_error(EC_INVALID_ID, "입력값: {!r}".format(args.video_id))
        sys.exit(EXIT_INVALID_VIDEO_ID)

    langs = [l.strip() for l in (args.langs or "").split(",") if l.strip()]
    if not langs:
        langs = ["ko", "en"]

    # 1차: yt-dlp
    result, primary_code, primary_detail = try_yt_dlp(args.video_id, langs)
    if result is not None:
        sys.stdout.write(json.dumps(result, ensure_ascii=False) + "\n")
        sys.exit(EXIT_OK)

    # 2차: youtube-transcript-api (옵션 비활성화 또는 단정적 영상 상태 오류면 스킵)
    if not args.no_fallback and primary_code not in _TERMINAL_VIDEO_CODES:
        fb_result, fb_code, fb_detail = try_youtube_transcript_api(args.video_id, langs)
        if fb_result is not None:
            sys.stdout.write(json.dumps(fb_result, ensure_ascii=False) + "\n")
            sys.exit(EXIT_OK)
        # 1차 에러가 애매(None/UNKNOWN/NO_SUBTITLES)할 때만 폴백 에러를 채택
        if primary_code in (None, EC_UNKNOWN, EC_NO_SUBTITLES) and fb_code:
            primary_code = fb_code
            primary_detail = fb_detail

    error_code = primary_code or EC_NO_SUBTITLES
    emit_error(error_code, primary_detail or "")
    sys.exit(EXIT_BY_CODE.get(error_code, EXIT_UNKNOWN))


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        emit_error(EC_UNKNOWN, "처리되지 않은 예외: {}".format(e))
        sys.exit(EXIT_UNKNOWN)
