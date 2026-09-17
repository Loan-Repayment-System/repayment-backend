# CLAUDE.md

이 저장소의 코드 컨벤션과 작업 플로우 정본은 [AGENTS.md](./AGENTS.md)다.
아래 import로 전부 가져오므로, **규칙을 고칠 때는 `AGENTS.md`만 고친다.**

@AGENTS.md

---

## Claude Code 전용

- `AGENTS.md`의 "GitHub 작업 플로우"는 사용자 확인 없이 연속 실행해도 되는 사전 승인 범위다.
  작업 브랜치는 `develop`에서 분기하고 PR base도 `develop`이다.
- 통합 테스트는 Docker가 필요하다. `./gradlew test`가 컨테이너 기동에서 실패하면 Docker Desktop 실행 여부부터 확인한다.
- 로컬 MySQL 포트는 개발자마다 다를 수 있다 (`.env`의 `MYSQL_PORT`).
- 커밋 메시지 끝에 `Co-Authored-By: Claude <noreply@anthropic.com>` 트레일러를 붙인다.
