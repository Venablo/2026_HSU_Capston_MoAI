# 🚀 MoAI (Capstone Project 2026)

> **2026 HSU Capston Design Project** > 사용자의 성장을 돕는 인공지능 기반 학습 지원 플랫폼, **MoAI**입니다.

---

## 🏗️ Tech Stack (Backend)
- **Framework**: Spring Boot 3.x
- **Language**: Java 17
- **Database**: MySQL 8.0, Redis
- **Security**: Spring Security (BCrypt Encryption)
- **Infrastructure**: Docker, Docker Compose
- **Build Tool**: Gradle

---

## 🛠️ 개발 환경 구축 가이드

백엔드 프로젝트 실행을 위해 아래 단계를 순서대로 진행해 주세요.

### 1단계: Docker Desktop 설치
도커는 프로젝트에 필요한 데이터베이스(MySQL, Redis)를 가상으로 실행하는 도구입니다.

1. **다운로드**: [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)에 접속하여 설치 파일을 받습니다.
2. **설치 시 주의사항**: 설치 과정 중 **"Use WSL 2 instead of Hyper-V"** 체크박스가 나오면 반드시 **체크** 상태로 진행하세요.
3. **컴퓨터 재부팅**: 설치 완료 후 윈도우를 다시 시작해야 도커 커널이 정상 등록됩니다.
4. **도커 실행**: 바탕화면의 Docker Desktop 아이콘을 실행하고 대시보드가 뜰 때까지 기다립니다.

### 2단계: Docker Compose로 인프라 실행
터미널에서 `docker-compose.yml` 파일이 위치한 경로(현재 프로젝트 루트)로 이동한 뒤 아래 명령어를 입력합니다.

```bash
docker-compose up -d
```
확인: Docker Desktop 앱의 [Containers] 탭에서 moai-mysql, moai-redis 상태가 초록색(Running)인지 확인합니다.

### 3단계: IntelliJ 환경 변수 설정

보안을 위해 DB 비밀번호를 소스 코드에 직접 노출하지 않습니다. 실행 시 아래 환경 변수를 반드시 설정해야 합니다.

IntelliJ에서 프로젝트를 오픈합니다.

상단 실행 버튼 옆의 [MoaiApplication] 클릭 -> [Edit Configurations...] 선택.

Environment variables 항목의 입력창(오른쪽 아이콘)을 클릭합니다.

+ 버튼을 눌러 DB_PASSWORD 내용을 추가합니다.

[Apply] 및 [OK]를 누르고 서버를 실행(▶️)합니다.

### 4단계: 정상 작동 확인

서버 실행 로그(Console)에 다음과 같은 문구가 뜨면 성공입니다.

HikariPool-1 - Starting... (DB 연결 성공)

Tomcat initialized with port(s): 8080 (http) (웹 서버 기동 성공)

###🚨 트러블슈팅 (설치가 안 된다면?)
<details>
<summary><b>1. 포트 충돌 에러 (Port 3306 is already in use)</b></summary>
윈도우에 이미 MySQL이 개별적으로 설치되어 있을 확률이 높습니다.

**[작업 관리자] -> [서비스] 탭 -> 'MySQL' 서비스를 찾아 '중지'**시킨 후 다시 도커를 실행하세요.
</details>

<details>
<summary><b>2. WSL 2 관련 팝업이 뜨는 경우</b></summary>
도커 실행 시 파란색 링크가 포함된 팝업이 뜬다면, 해당 링크를 눌러 <b>MS 공식 WSL2 커널 패치</b>를 다운로드하여 설치하면 해결됩니다.
</details>

<details>
<summary><b>3. DB_PASSWORD 관련 에러가 발생하는 경우</b></summary>
3단계 환경 변수 설정을 건너뛰었을 때 발생합니다. application.yml 파일은 수정하지 마시고, 인텔리제이 설정에서 환경 변수를 다시 한번 확인해 주세요.
</details>