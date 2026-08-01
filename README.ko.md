# Lombok Builder Linker

[English](README.md) | 한국어

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Lombok `@Builder` 가 끊어버린 연결을 잇는다. 애노테이션의 이름 문자열에서 이동·Find Usages·
> Rename 이 동작하기 시작한다 — 지금까지 조용히 아무 일도 안 하던 자리에서.

![이름 문자열 ⌘+Click — 그 이름이 가리키는 메서드의 호출부가 뜬다](docs/usages.png)

링커(`ld`)가 컴파일러가 남긴 미해결 심볼 참조를 이어주듯, 이 플러그인은 편집기 안에서 같은 일을
한다. `@Builder(builderMethodName = "historyChannelBuilder")` 는 Lombok 이 만들어낼 메서드의
이름을 정하는 자리지만, IntelliJ 에게는 그냥 글자다. ⌘+Click 하면 *"Cannot find declaration to
go to"*, Find Usages 는 빈 결과, Rename 을 하면 문자열은 낡은 채로 남고 호출부가 깨진다.

## 지금 무엇이 깨지는가

아래는 전부 `@Builder` 의 문서화된 21개 사용 변형을 **전수 확인한 결과**다. 추론이 아니다.

| `@Builder` 를 붙인 자리 | 이동·사용처·이름 변경 |
|---|---|
| **클래스**에 | 정상 (4/4) |
| **생성자** 또는 **static·instance 메서드**에 | 깨짐 (5/5) |

붙이는 자리가 전부를 가른다. 그리고 깨지는 쪽이 하필
[Lombok 공식문서가 권하는 형태](https://projectlombok.org/features/Builder)다 — 직접 쓴 생성자가
있으면 생성자에 붙이라고 되어 있다.

거기에 더해 **이름 문자열 여섯 자리는 참조가 아예 없다** — `builderMethodName` ·
`buildMethodName` · `builderClassName` · `setterPrefix` · `@Builder.ObtainVia(method)` ·
`@Builder.ObtainVia(field)`.

가장 위험한 것은 `ObtainVia` 다. 이 자리는 **직접 쓴** 메서드를 가리키는데, IDE 는 그 연결을 못 보고
해당 메서드를 미사용으로 취급한다. 그래서 그 메서드를 Rename 하면 문자열은 없는 이름을 가리킨 채
남고, Safe Delete 는 경고 없이 지운다. `toBuilder()` 가 실행되기 전까지 아무것도 안 터진다.

관련 JetBrains 이슈(모두 Open):
[IDEA-293203](https://youtrack.jetbrains.com/issue/IDEA-293203) ·
[IDEA-314445](https://youtrack.jetbrains.com/issue/IDEA-314445) ·
[IDEA-343275](https://youtrack.jetbrains.com/issue/IDEA-343275) ·
[IDEA-345743](https://youtrack.jetbrains.com/issue/IDEA-345743).
`@Builder` 가 **instance 메서드**에 붙는 경우는 어느 이슈에도 없다.

## 기능

- **이름 문자열이 진짜 참조가 된다.** 여섯 자리 전부. 이동·Find Usages·Rename·Safe Delete 는 같은
  기계를 쓰므로 참조 하나로 넷이 함께 살아난다. 특히 `ObtainVia` 가 가리키는 멤버가 더는 조용히
  지워지지 않는다.
- **이름 문자열 ⌘+Click 은 IDE 기본 사용처 팝업을 띄운다** — 그 이름이 가리키는 멤버의 호출부가,
  다른 곳과 똑같은 미리보기·그룹핑으로 나온다. 그 문자열을 플랫폼에 실제 성격 그대로 알리기
  때문이다: 참조가 아니라 **선언**.
- **⇧F6 하나로 이름과 모든 호출부가 바뀐다.** 편집은 대화상자가 아니라 편집기 안에서 바로 한다.
  편집이 끝나면 애노테이션 문자열과 호출부 전부가 **명령 하나**로 함께 바뀌므로, ⌘Z 한 번이면
  어느 파일에서 눌러도 둘 다 돌아온다. 되돌리기 전에는 무엇을 되돌리는지 확인을 묻는다.
- **`setterPrefix` 는 세터마다 다르게 바뀐다.** 접두사는 이름이 아니라 규칙이다. `with` 를 `set`
  으로 바꾸면 `withName` 은 `setName`, `withCount` 는 `setCount` 가 된다 — 각자의 뒷부분을 지킨 채로.
  사용처 팝업도 그 접두사가 만든 세터들의 호출부를 모아 보여준다.
- **`@Builder` 가 붙은 생성자·메서드의 사용처가 돌아온다.** 그것을 실제로 부르는 코드는 생성된
  `build()` 뿐이고 소스가 없다 — 그래서 호출부가 바로 옆에 있는데도 Find Usages 는 비었고 Code
  Vision 은 *"no usages"* 를 띄웠다. 이제 빌더 호출부가 그 선언의 사용처로 보고되고, 선언이
  미사용으로 회색 처리되지 않는다.
- **Lombok 내부에 의존하지 않는다.** IDE 에 번들된 Lombok 지원이 만들어 둔 멤버를 표준 PSI 로
  읽을 뿐이다. Community 와 Ultimate 모두에서 동작하고, Lombok 지원이 꺼져 있으면 조용히 아무것도
  하지 않는다.

## 설치

- **IDE 에서:** Settings/Preferences → Plugins → Marketplace → **Lombok Builder Linker** 검색 → Install
- **직접:** 플러그인 ZIP 을 받아 Plugins → ⚙ → *Install Plugin from Disk…*

IntelliJ IDEA **2024.3 이상**, **Lombok 플러그인**(IDE 번들), 그리고 모듈 클래스패스의 `lombok`
라이브러리가 필요하다 — 이 플러그인이 이어주는 대상이 Lombok 지원이 만들어내는 멤버이기 때문이다.

## 사용법

설정할 것은 없다. 진입 메서드 이름을 바꾼 빌더가 있다고 하자.

```java
public class Channel {
    private final String name;

    @Builder(builderMethodName = "historyChannelBuilder")
    public Channel(String name) {
        this.name = name;
    }
}
```

- `"historyChannelBuilder"` 문자열에서 **⌘+Click** → `Channel.historyChannelBuilder()` 호출부가
  사용처 팝업에 뜬다(위 스크린샷).
- 같은 문자열에서 **⇧F6** → 그 자리에서 바로 이름을 고칠 수 있고, Enter 를 누르면 애노테이션과
  모든 호출부가 함께 바뀐다.

  ![⇧F6 을 누르면 이름 문자열이 그 자리에서 편집 상태가 된다](docs/rename.png)
- **⌘Z** → 한 번이면 둘 다 되돌아간다. 무엇을 되돌리는지 확인을 묻는다.
- 문자열에서 **⌥F7** → 사용처 목록 창. 호출부가 전부 나온다.

`@Builder.ObtainVia` 는 직접 쓴 메서드와의 연결이 유지된다.

```java
@Builder(toBuilder = true)
public class Sample {
    private String name;

    @Builder.ObtainVia(method = "computeLength")
    private int length;

    public int computeLength() {
        return name == null ? 0 : name.length();
    }
}
```

`computeLength()` 가 더는 회색이 아니고, 이름을 바꾸면 문자열이 따라오며, Safe Delete 는 지우는
대신 경고한다.

## 한계

- **이름 변경은 애노테이션 문자열에서 시작한다. 호출부에서는 안 된다.** `builder()` 위의 캐럿
  대상은 Lombok 이 만든 메서드이고, 그 자리는 Lombok 플러그인이 이름 변경을 직접 가져간다. 여기서
  우리까지 손을 들면 ⇧F6 을 누를 때마다 처리기 선택 팝업이 뜬다. 문자열에서 바꾸면 호출부는 어차피
  전부 따라온다.
- **값이 비어 있는 이름 문자열은 손대지 않는다.** `builderMethodName = ""` 은 공식문서상 "진입
  메서드를 만들지 말라"는 뜻이라 가리킬 대상이 없다. `setterPrefix` 도 빈 접두사로는 바꿀 수 없다 —
  그건 이름이 아니라 이름 규칙 자체를 바꾸는 일이다.
- **상수로 준 이름은 다루지 않는다.** 문자열 리터럴만 잇는다.
  `builderMethodName = SOME_CONSTANT` 는 추측하지 않고 그대로 둔다.
- **목록 대신 바로 이동할 수 있다.** 사용처가 하나거나, 전부 한 줄에 있으면(빌더 체인에서 흔하다)
  IntelliJ 는 팝업 없이 그 자리로 간다. 플랫폼 기본 동작이며, ⌥F7 은 항상 전체 목록을 보여준다.
- **`@Singular` · `@SuperBuilder` · `@Builder.Default` 는 손댈 것이 없다.** 이미 정상 동작하는 것을
  실측으로 확인했고, 그래서 건드리지 않는다.

## 개발

```bash
./gradlew runIde       # 플러그인이 설치된 샌드박스 IDE 실행
./gradlew test         # 실제 Lombok 증강을 상대로 하는 PSI 테스트 (IDE 창 없이)
./gradlew buildPlugin  # 배포용 ZIP 빌드
```

빌드 전에 샌드박스 IDE 를 닫아야 한다. `prepareSandbox` 가 실행 중인 IDE 가 쓰고 있는 Lombok jar
까지 다시 쓰는데, Lombok 지원은 그 핫 리로드를 견디지 못한다.

## 라이선스

[MIT](LICENSE) © jeong-donghee
