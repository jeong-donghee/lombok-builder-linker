package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiLiteralExpression;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * 어떤 이름 문자열이 이름 변경 대상인지 — 대상 판정만 다룬다.
 *
 * <p>실제로 문자열과 호출부가 함께 바뀌는지는 {@link BuilderNameInplaceRenameTest} 가
 * 편집기 편집 경로를 그대로 태워서 검증한다.
 */
public class BuilderNameRenameTest extends LombokTestCase {

    /** builderMethodName 은 대상이다 — 생성될 진입 메서드의 이름을 정하는 자리다. */
    public void testBuilderMethodNameIsRenamable() {
        configure("""
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "history<caret>ChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNotNull("builderMethodName 은 대상이어야 한다", renamableLiteral());
        assertEquals("historyChannelBuilder", BuilderNameRename.currentName(renamableLiteral()));
    }

    /** buildMethodName 도 대상이다. */
    public void testBuildMethodNameIsRenamable() {
        configure("""
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(buildMethodName = "cre<caret>ate")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNotNull("buildMethodName 은 대상이어야 한다", renamableLiteral());
        assertEquals("create", BuilderNameRename.currentName(renamableLiteral()));
    }

    /**
     * setterPrefix 도 대상이다. 다만 바뀌는 방식이 다르다 — 접두사를 바꾸면 세터 이름이 자리마다
     * 다르게 바뀐다({@code withName} → {@code setName}). 실제 결과는
     * {@link BuilderNameInplaceRenameTest} 가 확인한다.
     */
    public void testSetterPrefixIsRenamable() {
        configure("""
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(setterPrefix = "wi<caret>th")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNotNull("setterPrefix 도 대상이어야 한다", renamableLiteral());
        assertEquals("with", BuilderNameRename.currentName(renamableLiteral()));
    }

    /** ObtainVia 도 대상이 아니다 — 직접 쓴 멤버를 가리키므로 평범한 이름 변경이 이미 옳다. */
    public void testObtainViaIsNotHandledHere() {
        configure("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class ExactCase {
                private String name;
                @Builder.ObtainVia(method = "comput<caret>eLength")
                private int length;
                public int computeLength() { return 0; }
            }
            """);
        assertNull("ObtainVia 는 이 핸들러 대상이 아니어야 한다", renamableLiteral());
    }

    // ---------- 도우미 ----------

    private void configure(@NotNull String source) {
        myFixture.configureByText("ExactCase.java", source);
    }

    private PsiLiteralExpression renamableLiteral() {
        return BuilderNameRename.renamableLiteralAt(
            myFixture.getFile().findElementAt(myFixture.getCaretOffset()));
    }
}
