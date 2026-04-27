import sys
import json
from youtube_transcript_api import YouTubeTranscriptApi

def check_has_subtitle(video_id):
    """수동/자동 자막 모두 포함하여 자막 존재 여부만 확인 (fetch 성공 여부로 판단)."""
    ytt = YouTubeTranscriptApi()
    for langs in [["ko"], ["en"]]:
        try:
            ytt.fetch(video_id, languages=langs)
            sys.exit(0)
        except Exception:
            continue
    try:
        ytt.fetch(video_id)
        sys.exit(0)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

def fetch_with_priority(video_id):
    """우선순위: 수동 한국어 → 수동 영어 → 자동 한국어 → 자동 영어 → 기타"""
    ytt = YouTubeTranscriptApi()

    try:
        transcript_list = ytt.list(video_id)

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

        # 위 네 가지 모두 없으면 첫 번째 자막 사용
        for t in transcript_list:
            return t.fetch()

    except Exception:
        pass

    # list() 자체가 실패한 경우 fetch() 직접 시도 (폴백)
    for langs in [["ko"], ["en"]]:
        try:
            return ytt.fetch(video_id, languages=langs)
        except Exception:
            continue

    return ytt.fetch(video_id)

def main():
    if len(sys.argv) < 2:
        print("Usage: python subtitle_scraper.py <video_id> [--check]", file=sys.stderr)
        sys.exit(1)

    video_id = sys.argv[1]

    if "--check" in sys.argv:
        check_has_subtitle(video_id)
        return

    try:
        transcript = fetch_with_priority(video_id)
        if transcript is None:
            raise Exception("No transcript found")

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
