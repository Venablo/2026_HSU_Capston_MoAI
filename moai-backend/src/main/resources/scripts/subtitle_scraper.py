import sys
import json
from youtube_transcript_api import YouTubeTranscriptApi

def main():
    if len(sys.argv) < 2:
        print("Usage: python subtitle_scraper.py <video_id>", file=sys.stderr)
        sys.exit(1)

    video_id = sys.argv[1]

    try:
        ytt = YouTubeTranscriptApi()

        # 한국어 우선, 영어 폴백, 언어 미지정 시 아무 언어
        transcript = None
        for langs in [["ko"], ["en"]]:
            try:
                transcript = ytt.fetch(video_id, languages=langs)
                break
            except Exception:
                continue

        if transcript is None:
            transcript = ytt.fetch(video_id)

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
