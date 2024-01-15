# 🦁 Haru-Log 서버 개발 레포지토리 🦁

## ☁️ 프로젝트 개요 ☁️
- MA 아키텍처로 회원, 챌린지 관리, 채팅, 활동기록 등을 관리한다.
- 추후에 MSA 아키텍처로 전환한다.

### ☀️ 개발 기간 ☀️
- 2024/01/12 ~ 2024/02/16

### ⚙️ 개발 환경 ⚙️
- `java17`
- `Spring Boot 3.x`
- `Spring Security`
- `Mysql 8.x`
- `JPA`, `Query dsl`
- `Kafka`

### 담당 기능
| 이건        <팀장>                             | 박제영              | 이재은                       | 임현정                        |
|--------------------------------------------|------------------|---------------------------|----------------------------|
| 회원 기능, k8s 배포 파일 관리,<br/>운영 서버 관리, Jira 관리 | 게시글, 피드, <br/>상호작용 기능 | 기록, 챌린지 기능,<br/>테스트 서버 관리 | 채팅 기능, CI 관리,<br/>개발 서버 관리 |

### Git branch 전략
- Github Flow 전략 채택
- main 브랜치는 안정된 코드 조각의 모음
- **feature/GJNS-이슈번호-기능이름** 형식의 브랜치 사용
  - ex) feature/GJNS-7-user-entity
  - 하위 이슈 번호 사용
- **기능 단위의 커밋 필수**
  - 몰아서 커밋 지양하기
- 브랜치 push 후 main 브랜치로 pull request

### 커밋 메세지
- 커밋 메세지 형식은 다음과 같습니다.

> type(타입) : title(제목)
>
> body(본문, 생략 가능)
>
> Resolves : #issueNo, ...(해결한 이슈 , 생략 가능)
>
> See also : #issueNo, ...(참고 이슈, 생략 가능)

- feat : 새로운 기능을 추가하거나, 기존 기능을 요구사항 변경으로 인해 변경한 경우
- fix : 버그를 수정한 경우
- remove : 파일 혹은 코드를 삭제한 경우
- docs : 문서(주석) 추가/수정의 경우, 직접적인 코드의 변화 없이 문서만 추가 수정 했을 때
- refactor : 기능의 변경 없이, 코드를 리팩토링 한 경우
- test : 테스트 코드를 추가/수정한 경우
- chore : 기능/테스트, 문서, 스타일, 리팩토링 외에 배포, 빌드와 같이 프로젝트의 기타 작업들에 대해 추가/수정한 경우



## ❗️ 커밋 전 체크 리스트 ❗️

- ✅ 브랜치 체크
  - 사용 중인 브랜치가 feature 브랜치가 맞는지 확인 필수
- ✅ conflict 우려가 있는 파일 확인하기
- ✅ main에 직접 merge 금지
- ✅ 중요 리소스 정보 노출된 곳 없는지 확인
  - url, password 등 노출된 곳 없는지 확인
- ✅ local 설정이 올라가진 않았는지 확인
- ✅ 커밋 메세지 컨벤션 확인

---
작성자: 이건 </br>
작성 일시: 2024/01/12
