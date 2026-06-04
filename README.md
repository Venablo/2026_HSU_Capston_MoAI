# 🎓 MoAI - AI 기반 개인 맞춤형 학습 플랫폼

> AI 맞춤 커리큘럼 자동 설계 + 행동 분석 기반 능동 가이드 + 거꾸로 학습 + 지능형 스터디 매칭을 결합한 웹 기반 AI 학습 튜터 플랫폼

---

## 📌 프로젝트 개요

**MoAI**는 학습자가 목표와 실력을 입력하면 AI가 주차별 맞춤 커리큘럼을 자동 설계하고, 학습 중 행동 패턴을 실시간 분석하여 개입하는 AI 기반 학습 플랫폼입니다.

- 학습자의 목표·난이도·기간·일일 투자 시간을 바탕으로 LLM이 주차별 학습 플랜 자동 생성
- 영상 되감기·일시정지·스킵 등 학습 행동 패턴을 감지하여 AI가 보충 자료 및 돌발 퀴즈 제공
- 학습 종료 후 사용자가 '선생님'이 되어 AI에게 배운 내용을 설명하는 거꾸로 학습으로 메타인지 강화
- 강점·약점 키워드 분석 기반 AI 멘토-멘티 스터디 매칭으로 학습자 간 교육 불균형 해소

---

## 👥 팀원

| 이름 | 학번 | 역할 |
|------|------|------|
| 장승환 | 2171416 | 서버 인프라 (AWS EC2/RDS/S3, Docker, DevOps) |
| 문경수 | 2091133 | 백엔드 개발 (Spring Boot, REST API) |
| 강동명 | 2171212 | 프론트엔드 개발 (React, TypeScript) |
| 유예현 | 2171297 | AI/LLM 개발 (Gemini API, 커리큘럼 생성, 패턴 분석) |

- **담당 교수:** 김태리 교수님
- **소속:** 한성대학교 캡스톤디자인 2026

---

## 🛠 기술 스택

| 구분 | 기술 |
|------|------|
| **프론트엔드** | React 19, TypeScript |
| **백엔드** | Spring Boot 3.5, Java, Gradle |
| **데이터베이스** | MySQL 8.0 (AWS RDS), Redis |
| **AI API** | Google Gemini API |
| **인프라** | AWS EC2, AWS RDS, AWS S3, Docker |
| **버전 관리** | GitHub |

---

## 🌟 주요 기능

### 1. 🗺️ AI 맞춤 커리큘럼 자동 생성
- 학습 키워드 (#정보처리기사, #웹개발, #CS면접 등), 난이도(초급/중급/고급), 기간, 하루 투자 시간 입력
- LLM이 주차별 학습 목표·교안(PDF)·AI 추천 영상(YouTube)·퀴즈를 자동으로 구성
- 주차별 로드맵 미리보기 및 학습 현황 체크리스트 제공

### 2. 🔍 행동 분석 기반 능동 가이드 (AI 모니터링)
- **패턴 1 (반복 감지):** 특정 구간 2~3회 연속 뒤로 감기 → AI가 해당 개념 요약 보충 자료 알림 제시
- **패턴 2 (장시간 정지):** 3분 이상 화면 정지 또는 5분 이상 자리 비움 → 핵심 요약 노트 제공 알림
- **패턴 3 (무의미한 스킵):** 10초 단위 빠른 건너뛰기·배속 시청 감지 → 돌발 O/X 퀴즈 팝업 제시

### 3. 🔄 AI 대상 거꾸로 학습 (메타인지 강화)
- 주차 학습 완료 후 사용자가 '선생님', AI가 '학생' 역할로 대화
- AI가 실시간 역질문을 던져 심화 이해도 확인
- 평가 결과를 바탕으로 강점·약점 키워드 자동 추출

### 4. 🤝 AI 지능형 스터디 매칭
- 퀴즈 점수 + AI 거꾸로 학습 질문 데이터 → 학습 이해도 산출
- 특정 키워드에 강점(1개 이상)을 가진 학생과 약점(3개 이상)을 가진 학생 자동 매칭
- 멘토-멘티 거꾸로 학습 방식의 1:1 채팅 스터디 진행

### 5. 📝 주차 마무리 퀴즈
- 거꾸로 학습 완료 후 활성화되는 주관식 최종 이해도 확인 퀴즈
- AI 채점 후 레이더 차트 기반 영역별 이해도 분석 리포트 제공
- 문항별 사용자 답변 vs 모범 답안 비교 및 AI 해설 제공

---

## 🏗️ 프로젝트 구조

```
2026_HSU_Capston_MoAI
├── develop_back      # Spring Boot 백엔드
│   ├── src/main/java
│   │   └── ...       # Controller / Service / Repository / DTO
│   └── src/main/resources
│       └── application.yml
└── develop_front     # React + TypeScript 프론트엔드
    ├── src
    │   ├── components
    │   ├── pages
    │   └── api
    └── public
```

---

## ⚙️ 로컬 개발 환경 설정

### 사전 요구사항
- Java 17+
- Node.js 18+
- Docker Desktop
- MySQL 8.0

### 백엔드 실행

```bash
# 1. Redis 컨테이너 실행 (Spring Boot 시작 전 필수)
docker start moai-redis

# 2. 백엔드 빌드 및 실행
cd develop_back
./gradlew bootRun
```

> ⚠️ `application.yml`의 `ddl-auto`는 로컬 개발 시 `validate` 또는 `none`으로 설정. RDS 공용 DB 데이터 손실 방지를 위해 `create` 사용 금지.

### 프론트엔드 실행

```bash
cd develop_front
npm install
npm run dev
```

---

## ☁️ 인프라 구성

| 서비스 | 용도 |
|--------|------|
| AWS EC2 | 백엔드 서버 (포트 8080) |
| AWS RDS | MySQL 8.0 운영 DB |
| AWS S3 `capstone-file-storage-2026-moai` | 학습 자료(PDF 교안 등) 파일 저장 |
| AWS S3 `capstone-web-hosting-2026-moai` | 프론트엔드 정적 웹 호스팅 |
| Redis (Docker) | 임시 데이터 캐싱 |

---

## 🔗 관련 링크

- **GitHub:** https://github.com/Venablo/2026_HSU_Capston_MoAI
- **Figma:** https://www.figma.com/design/Zobgk1nhNhrtuPvUYIBcIJ/캡스톤-프로젝트
- **참고 문서:** [Google AI Studio](https://ai.google.dev) · [React 공식 문서](https://ko.react.dev) · [AWS](https://aws.amazon.com)

---

*2026 한성대학교 캡스톤디자인 — MoAI 팀*
