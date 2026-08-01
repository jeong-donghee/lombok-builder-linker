package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * ⌘+Click 이 <b>IDE 기본 사용처 팝업</b>을 띄우는지 — 우리가 만든 목록이 아니라.
 *
 * <p>이름 문자열은 <b>선언</b>이다 — 생성될 멤버의 이름을 여기서 정한다. 그래서 후보 목록을 직접
 * 띄우지 않고({@code gotoDeclarationHandler}) 문자열을 선언으로 알린 뒤
 * ({@code psi.declarationProvider}) 심볼을 찾기 대상으로 내놓는다({@code SearchTargetSymbol}).
 * 미리보기·파일 그룹핑까지 플랫폼이 더 잘 한다.
 *
 * <p>여기서 확인하는 것은 플랫폼의 판정 그 자체다. {@code GotoDeclarationOrUsageHandler2} 는
 * ⌘+Click 시 "선언으로 이동(GTD)"과 "사용처 보여주기(SU)" 중 무엇을 할지 정하고, 테스트용으로
 * 그 결정을 물어볼 수 있게 열어 두었다.
 */
public class BuilderNameUsagePopupTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { ExactCase.historyChannelBuilder().channelName("a").build(); }
            }
            """);
    }

    /** 이름 문자열 = 선언 → IDE 사용처 팝업. */
    public void testNameStringShowsTheIdeUsagesPopup() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "history<caret>ChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);

        assertEquals("이름 문자열에서는 IDE 사용처 팝업이 떠야 한다", GTDUOutcome.SU, outcomeAtCaret());
    }

    /**
     * 호출부는 평범한 "선언으로 이동" 이다 — 플랫폼 기본 착지점(Lombok 이 잡아둔 {@code @Builder}).
     *
     * <p>호출부를 이름 문자열로 보내지 않는다 — 이름을 준 빌더만 다르게 움직여 그냥
     * {@code builder()} 와 동작이 갈린다. 일관성 우선으로 플랫폼에 맡기고, 그 상태를 여기서 못 박는다.
     */
    public void testCallSiteIsAPlainGoToDeclaration() {
        myFixture.addFileToProject("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "historyChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        myFixture.configureByText("Use.java", """
            public class Use {
                void use() { ExactCase.history<caret>ChannelBuilder().channelName("a").build(); }
            }
            """);

        assertEquals("호출부는 선언으로 이동이어야 한다", GTDUOutcome.GTD, outcomeAtCaret());
    }

    /** {@code builderClassName} · {@code buildMethodName} 도 선언이다 — 같은 팝업이 떠야 한다. */
    public void testOtherNameStringsAlsoShowTheUsagesPopup() {
        configurePlain("buildMethodName = \"create\", builderClassName = \"Ma<caret>ker\"");
        assertEquals("builderClassName 에서도 사용처 팝업이어야 한다", GTDUOutcome.SU, outcomeAtCaret());

        configurePlain("buildMethodName = \"crea<caret>te\", builderClassName = \"Maker\"");
        assertEquals("buildMethodName 에서도 사용처 팝업이어야 한다", GTDUOutcome.SU, outcomeAtCaret());
    }

    /**
     * {@code setterPrefix} 도 IDE 사용처 팝업이다 — 그 접두사가 만든 세터들의 <b>호출부</b>가 뜬다.
     *
     * <p>접두사는 1:N 이라 검색 방식만 다르다(세터마다 따로 찾는다). 내용은
     * {@code BuilderMemberUsageSearchTest} 가 확인한다.
     *
     * <p>화면에서는 사용처가 전부 한 줄에 있으면 목록 대신 그 줄로 이동한다
     * ({@code ShowUsagesAction.areAllUsagesInOneLine}) — 빌더 체인에서는 흔한 일이고, 플랫폼 규칙이다.
     */
    public void testSetterPrefixShowsTheUsagesPopup() {
        configurePlain("setterPrefix = \"wi<caret>th\"");
        assertEquals("setterPrefix 에서도 사용처 팝업이어야 한다", GTDUOutcome.SU, outcomeAtCaret());
    }

    /** 접두사를 안 준 경우({@code setterPrefix = ""})는 정할 이름이 없다 — 빈자리로 둔다. */
    public void testEmptySetterPrefixIsInert() {
        configurePlain("setterPrefix = \"<caret>\"");
        assertNull("빈 접두사는 선언이 아니다", outcomeAtCaret());
    }

    /** ObtainVia 는 직접 쓴 멤버를 가리키므로 이동이다 — 사용처 팝업이 아니다. */
    public void testObtainViaGoesToTheRealMember() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(method = "comput<caret>eLength")
                private int length;
                public int computeLength() { return 0; }
            }
            """);

        assertEquals("ObtainVia 는 그 멤버로 이동해야 한다", GTDUOutcome.GTD, outcomeAtCaret());
    }

    private void configurePlain(@NotNull String attributes) {
        myFixture.configureByText("Plain.java", """
            import lombok.Builder;
            public class Plain {
                private final String name;

                @Builder(%s)
                public Plain(String name) { this.name = name; }
            }
            """.formatted(attributes));
    }

    /**
     * 플랫폼의 ⌘+Click 판정. 읽기 액션 밖에서 물어보는 변형을 쓴다 — EDT 에서 직접 부르면
     * 플랫폼이 스레드 규칙 위반으로 막는다(실측).
     */
    private GTDUOutcome outcomeAtCaret() {
        return GotoDeclarationOrUsageHandler2.Companion.testGTDUOutcomeInNonBlockingReadAction(
            myFixture.getEditor(), myFixture.getFile(), myFixture.getCaretOffset());
    }
}
