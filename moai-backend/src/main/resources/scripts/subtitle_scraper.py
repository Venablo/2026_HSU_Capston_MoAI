import sys
import json
import time
import random
import os
import http.cookiejar
import requests
from youtube_transcript_api import YouTubeTranscriptApi

COOKIE_FILE = "cookies.txt"

def get_session_with_cookies(cookie_file):
    """
    [최신 버전 변경점] cookies.txt 파일을 읽어 requests.Session에 직접 주입합니다.
    """
    session = requests.Session()
    if os.path.exists(cookie_file):
        try:
            # 유튜브에서 추출한 Netscape 포맷의 쿠키를 파이썬에서 읽을 수 있게 변환
            cookie_jar = http.cookiejar.MozillaCookieJar(cookie_file)
            cookie_jar.load(ignore_discard=True, ignore_expires=True)
            session.cookies = cookie_jar
        except Exception as e:
            print(f"Warning: 쿠키 파일을 로드할 수 없습니다 ({e}).", file=sys.stderr)
    return session

def check_has_subtitle(video_id):
    try:
        session = get_session_with_cookies(COOKIE_FILE)
        # [최신 버전 변경점] 클래스를 인스턴스화 할 때 http_client로 세션(쿠키)을 전달합니다.
        ytt_api = YouTubeTranscriptApi(http_client=session)
        # list_transcripts() -> list() 로 메서드 이름이 변경되었습니다.
        ytt_api.list(video_id)
        sys.exit(0)
    except Exception as e:
        print(f"Error checking transcripts: {e}", file=sys.stderr)
        sys.exit(1)

def fetch_with_priority(video_id):
    session = get_session_with_cookies(COOKIE_FILE)
    ytt_api = YouTubeTranscriptApi(http_client=session)

    try:
        # 자막 목록 조회
        transcript_list = ytt_api.list(video_id)
    except Exception as e:
        raise Exception(f"목록 조회 실패: {e}")

    # 1. 원하는 언어/유형 순서대로 탐색
    for find_method, langs in [
        ("find_manually_created_transcript", ["ko"]),
        ("find_manually_created_transcript", ["en"]),
        ("find_generated_transcript",        ["ko"]),
        ("find_generated_transcript",        ["en"]),
    ]:
        try:
            return getattr(transcript_list, find_method)(langs).fetch()
        except Exception:
            continue

    # 2. 위 조건에 맞는게 없다면 첫 번째 자막 반환
    for t in transcript_list:
        try:
            return t.fetch()
        except Exception:
            continue

    return None

def main():
    if len(sys.argv) < 2:
        print("Usage: python subtitle_scraper.py <video_id> [--check]", file=sys.stderr)
        sys.exit(1)

    video_id = sys.argv[1]

    # 단건 실시간 처리 환경이라면 주석 처리하세요. 대량 수집 시에는 활성화 필수.
    time.sleep(random.uniform(1, 3))

    if "--check" in sys.argv:
        check_has_subtitle(video_id)
        return

    try:
        transcript = fetch_with_priority(video_id)
        if not transcript:
            raise Exception("No transcript found (어떤 자막도 추출하지 못했습니다).")

        result = [
            {
                "text": snippet.text,
                "start": snippet.start,
                "duration": snippet.duration,
            }
            for snippet in transcript
        ]

        json.dump(result, sys.stdout, ensure_ascii=False)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()