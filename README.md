# StasisChess Mod

체스택(Chesstack) 변형 체스를 마인크래프트 안에서 플레이할 수 있게 만든 Fabric 모드입니다.

---

## 개발 기여 가이드

### 개발 환경 요구사항

| 항목 | 버전 |
|---|---|
| Java | 17 (JDK) |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.116.8+1.21.1 |
| Gradle | Wrapper 사용 (`./gradlew`) |

> IntelliJ IDEA 또는 Eclipse(Buildship)를 권장합니다. configuration cache는 지원하지 않습니다.

---

### 프로젝트 설정

```bash
# 저장소 클론
git clone <repo-url>
cd StasisChess_mod

# Minecraft 소스 디컴파일 및 IDE용 소스 생성
./gradlew genSources

# IntelliJ IDEA 사용 시
./gradlew idea

# Eclipse 사용 시
./gradlew eclipse
```

---

### 프로젝트 구조

```
src/
├── main/java/nand/modid/
│   ├── chess/
│   │   ├── core/          # 체스 엔진 핵심 (Board, GameState, Move, Piece, RuleSet)
│   │   ├── dsl/           # Chessembly DSL 인터프리터 및 파서
│   │   └── movegen/       # 합법 수 생성기
│   ├── game/              # Minecraft 통합 매니저 (MinecraftChessManager)
│   ├── item/              # 모드 아이템 정의
│   ├── comand/            # 커맨드 핸들러
│   └── registry/          # 아이템·블록 레지스트리
└── client/java/nand/modid/
    ├── mixin/client/      # 클라이언트 전용 Mixin
    └── render/            # 렌더링 관련 코드
docs/
├── api/                   # Java API 레퍼런스
├── chessembly/            # Chessembly DSL 개념 및 튜토리얼
└── chesstack/             # 체스택 게임 규칙 문서
```

#### 핵심 모듈 설명

- **`chess/core`** — 게임 규칙과 관계없는 순수 체스 엔진. `Board`, `GameState`, `Piece`, `RuleSet`이 여기 있습니다.
- **`chess/dsl/chessembly`** — 기물의 행마법을 스크립트로 정의하는 인터프리터. 새 기물을 추가할 때 이 DSL을 활용합니다.
- **`chess/movegen`** — Chessembly 스크립트를 실행해 합법 수 목록을 생성합니다.
- **`game`** — Minecraft 블록/엔티티와 체스 엔진을 연결하는 계층입니다.

---

### 빌드

```bash
# 개발용 빌드 실행 (클라이언트 실행)
./gradlew runClient

# 서버 실행
./gradlew runServer

# JAR 빌드 (build/libs/ 에 생성)
./gradlew build
```

---

### 기여 방법

1. `main` 브랜치에서 작업 브랜치를 생성합니다.

   ```bash
   # 예시
   git checkout -b feat/my-feature
   git checkout -b fix/bug-description
   git checkout -b docs/update-readme
   ```

2. 변경사항을 구현하고 커밋합니다.

3. Pull Request를 생성합니다. PR 제목에 변경 내용을 간결하게 서술해 주세요.

#### 브랜치 네이밍 규칙

| 접두사 | 용도 |
|---|---|
| `feat/` | 새 기능 추가 |
| `fix/` | 버그 수정 |
| `docs/` | 문서 수정 |
| `refactor/` | 리팩터링 |
| `chore/` | 빌드·설정 변경 |

---

### 새 기물 추가하기

새 체스 기물을 추가하는 절차는 [`docs/api/how-to-add-new-piece.md`](docs/api/how-to-add-new-piece.md)에 전체 과정이 단계별로 정리되어 있습니다. 아래는 간략한 체크리스트입니다.

- [ ] `Piece.java` — `PieceKind` enum에 상수 추가 (이름, 점수)
- [ ] `Piece.java` — `chessemblyScript()` 메서드에 행마법 DSL 스크립트 작성
- [ ] `Piece.java` — `fromString()` 파싱 등록
- [ ] `MinecraftChessManager.java` — Minecraft 블록 매핑 추가
- [ ] (선택) `GameState.java` — 실험용 포켓에 기물 추가
- [ ] (선택) 프로모션 대상 설정

> **포켓 점수 제한:** 기물 점수 합계는 **39점**을 초과할 수 없습니다. 점수 설계 기준은 [`docs/api/how-to-add-new-piece.md` 점수 & 스택 참고표](docs/api/how-to-add-new-piece.md)를 참조하세요.

---

### Chessembly DSL

기물의 행마법은 **Chessembly**라는 자체 DSL로 작성됩니다. 행마식·제어식·조건식·상태식으로 구성되며 자세한 내용은 아래 문서를 참고하세요.

- [Chessembly 개념 및 식 연쇄 규칙](docs/chessembly/CONCEPT.md)
- [Chessembly 식 전체 요약 (PROJECT.md)](docs/chessembly/PROJECT.md)
- [Chessembly 튜토리얼](docs/chessembly/TUTORIAL.md)
- [Chessembly DSL API 레퍼런스](docs/api/03-chessembly-dsl-api.md)

---

### 코드 컨벤션

- Java 17 문법을 사용합니다.
- 클래스·메서드·필드 이름은 **camelCase / PascalCase** Java 표준을 따릅니다.
- Source set은 `main`(서버/공용)과 `client`(클라이언트 전용)로 분리되어 있습니다. 클라이언트 전용 코드는 반드시 `client` source set에 배치하세요.
- Mixin은 용도와 대상이 명확하게 드러나는 이름을 사용합니다.

---

### 관련 문서

| 문서 | 설명 |
|---|---|
| [docs/api/README.md](docs/api/README.md) | Java API 전체 문서 목록 |
| [docs/api/00-quick-start.md](docs/api/00-quick-start.md) | API 빠른 시작 가이드 |
| [docs/chesstack/rule.md](docs/chesstack/rule.md) | 체스택 게임 규칙 |
| [docs/chesstack/stack.md](docs/chesstack/stack.md) | 스턴/이동 스택 시스템 |
| [docs/chesstack/move.md](docs/chesstack/move.md) | 착수·이동·계승·위장 행동 규칙 |

