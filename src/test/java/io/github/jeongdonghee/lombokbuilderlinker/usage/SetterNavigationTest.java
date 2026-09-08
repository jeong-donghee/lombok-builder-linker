package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;

import java.util.concurrent.TimeUnit;

/**
 * 세터 호출부에서 ⌘+Click 하면 그 세터의 <b>출처</b>(필드·파라미터)로 간다 — 이건 Lombok 플러그인이
 * 이미 해 주는 일이다. <b>우리가 하지 않기로 한 것</b>을 못 박아 두는 테스트다.
 *
 * <p>왜 못을 박는가(2026-09-07): "호출부에서 파라미터로 가는 이동이 끊겨 있으니 잇자"는 제안이
 * 있었는데, 실측해 보니 이미 되고 있었다. Lombok 이 세터 light 메서드의 navigation element 를
 * 출처로 잡아두기 때문이다. 그 전제가 깨지면(= Lombok 이 그걸 그만두면) 우리 판단의 근거도 사라지므로,
 * 그때 조용히 지나가지 않도록 여기서 감시한다.
 *
 * <p>{@code build()} · {@code builder()} 는 이야기가 다르다 — 그쪽 navigation element 는 애노테이션이다.
 */
public class SetterNavigationTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("OnClass.java", """
            import lombok.Builder;
            @Builder
            public class OnClass { private String userName; }
            """);
        myFixture.addFileToProject("OnCtor.java", """
            import lombok.Builder;
            public class OnCtor {
                private final String userName;
                @Builder
                public OnCtor(String userName) { this.userName = userName; }
            }
            """);
        myFixture.configureByText("Caller.java", """
            public class Caller {
                void use() {
                    OnClass a = OnClass.builder().userName("a").build();
                    OnCtor b = OnCtor.builder().userName("b").build();
                }
            }
            """);
    }

    /** 클래스에 붙은 경우 — 착지점은 필드. */
    public void testCallSiteNavigatesToTheField() {
        PsiElement expected = findClass("OnClass").findFieldByName("userName", false);
        assertNavigatesTo("OnClass.builder()", expected);
    }

    /** 생성자에 붙은 경우 — 착지점은 생성자 파라미터. */
    public void testCallSiteNavigatesToTheConstructorParameter() {
        PsiElement expected = findClass("OnCtor").getConstructors()[0]
            .getParameterList().getParameters()[0];
        assertNavigatesTo("OnCtor.builder()", expected);
    }

    private void assertNavigatesTo(String anchor, PsiElement expected) {
        String text = myFixture.getFile().getText();
        int at = text.indexOf(".userName", text.indexOf(anchor)) + 3;

        assertEquals("⌘+Click 은 사용처 팝업이 아니라 선언으로 이동이어야 한다", "GTD", gtduOutcome(at));

        PsiReference reference = myFixture.getFile().findReferenceAt(at);
        assertNotNull("호출부가 참조로 해석되지 않았다", reference);
        PsiElement resolved = reference.resolve();
        assertNotNull("호출부가 세터로 해석되지 않았다 — Lombok 증강이 없는 환경인지 확인할 것", resolved);
        assertSame("착지점이 세터의 출처여야 한다", expected, resolved.getNavigationElement());
    }

    /**
     * ⌘+Click 이 실제로 고르는 동작. GTD = 선언으로 이동, SU = 사용처 팝업.
     *
     * <p>이 API 는 EDT 에서 부를 수 없어(플랫폼 스레딩 규칙) 풀 스레드의 읽기 액션 안에서 부른다.
     */
    private String gtduOutcome(int offset) {
        try {
            return ApplicationManager.getApplication()
                .executeOnPooledThread(() -> String.valueOf(ReadAction.compute(
                    (ThrowableComputable<Object, RuntimeException>) () ->
                        GotoDeclarationOrUsageHandler2.testGTDUOutcome(
                            myFixture.getEditor(), myFixture.getFile(), offset))))
                .get(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("제스처 판정을 얻지 못했다", failure);
        }
    }

    private PsiClass findClass(String name) {
        PsiClass found = JavaPsiFacade.getInstance(getProject())
            .findClass(name, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(name + " 를 찾지 못했다", found);
        return found;
    }
}
