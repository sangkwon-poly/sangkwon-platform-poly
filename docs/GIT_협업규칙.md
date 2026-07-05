# Git 협업 규칙

4인 팀 협업 컨벤션. 이슈에서 시작해 브랜치, PR, 코드리뷰, 병합 순으로 진행합니다.
목표는 main을 항상 실행되는 상태로 두고, 모든 코드가 리뷰를 거쳐 들어오게 하는 것입니다.

## 1. 브랜치 전략 (main / develop / feature)

```
main       제출·시연용. 항상 실행되는 상태. 직접 push 금지, develop에서만 병합.
develop    통합 브랜치. 팀원 작업이 모이는 곳. 여기도 PR로만 들어온다.
feature/*  실제 작업 브랜치. 이슈 1개에 브랜치 1개. develop에서 분기해 develop로 PR.
```

전체 흐름은 이슈를 만들고, feature 브랜치를 따서 작업하고, develop로 PR을 올려 리뷰와 승인을 받아 병합하는 순서입니다.
병합되면 연결된 이슈는 자동으로 닫히고, 마일스톤마다 develop를 main으로 병합합니다.

## 2. 브랜치 이름 규칙

| 종류 | 형식 | 예시 |
|---|---|---|
| 기능 | `feature/{이슈번호}-{짧은영문}` | `feature/12-map-choropleth` |
| 버그 | `fix/{이슈번호}-{설명}` | `fix/23-login-npe` |
| 문서 | `docs/{설명}` | `docs/readme` |
| 리팩터 | `refactor/{설명}` | `refactor/member-service` |

## 3. 커밋 메시지 규칙 (Conventional Commits)

```
{type}: {설명} (#이슈번호)
```

| type | 용도 |
|---|---|
| feat | 기능 추가 |
| fix | 버그 수정 |
| docs | 문서 |
| refactor | 리팩터링(동작 변화 없음) |
| test | 테스트 |
| chore | 설정, 빌드 등 잡무 |

예: `feat: 상권 코로플레스 레이어 추가 (#12)`, `fix: 로그인 시 NPE 수정 (#23)`

## 4. 작업 순서 (이슈에서 PR까지)

1. 이슈 생성: WBS 작업을 이슈로 만든다. 담당자(Assignee), 라벨(도메인/우선순위), 마일스톤을 지정한다.
2. develop 최신화: `git switch develop && git pull`
3. 브랜치 생성: `git switch -c feature/12-map-choropleth`
4. 작업과 커밋: `git commit -m "feat: ... (#12)"`
5. push: `git push -u origin feature/12-map-choropleth`
6. PR 생성(GitHub): base는 `develop`. 본문에 `Closes #12`를 넣으면 병합 시 이슈가 닫힌다.
7. 리뷰어를 지정하고 코드리뷰를 받아 Approve.
8. 리뷰어가 병합한다(Squash and merge). 그리고 브랜치를 삭제한다.

## 5. 코드 리뷰 규칙 (평가 항목)

- PR당 최소 1명이 Approve해야 병합할 수 있다.
- 본인 PR은 본인이 병합하지 않는다. 리뷰어가 병합한다.
- "LGTM"만 남기지 말고 질문이든 개선 제안이든 코멘트를 최소 1개 단다.
- 리뷰는 24시간 안에 한다. 막히면 단톡으로 재촉한다.
- 리뷰 체크리스트:
  - [ ] 응답을 `ApiResponse`로 감쌌나
  - [ ] 예외를 `BusinessException` + `ErrorCode`로 처리했나 (try-catch 남발 X)
  - [ ] DTO 규약(Request/Response)을 지켰나, 엔티티를 직접 노출하지 않았나
  - [ ] 네이밍과 패키지 위치가 맞나 (자기 도메인 안)
  - [ ] 불필요하거나 중복된 코드는 없나
  - [ ] 로컬 실행과 테스트를 통과했나
  - [ ] 키나 비밀값을 커밋하지 않았나

## 6. 병합 규칙

| 대상 | 방식 | 이유 |
|---|---|---|
| feature 에서 develop | Squash and merge | 커밋 히스토리를 깔끔하게 (이슈당 1커밋) |
| develop 에서 main | Merge commit | 마일스톤 단위로 이력 보존 |

## 7. 브랜치 보호 설정 (GitHub Settings > Branches)

`main`, `develop` 둘 다 규칙을 겁니다.

- Require a pull request before merging (직접 push 차단)
- Require approvals: 1
- (권장) Require conversation resolution before merging

초기 세팅 커밋만 main에 직접 올리고, 이후 모든 작업은 이 규칙을 따릅니다.
