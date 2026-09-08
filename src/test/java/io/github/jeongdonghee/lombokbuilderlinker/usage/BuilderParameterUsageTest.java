package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiParameter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;

import java.util.Collection;

/**
 * {@code @Builder} 가 생성자·정적 메서드에 붙었을 때, <b>파라미터</b>에서 세터 호출부가 잡히는가.
 *
 * <p>실측(2026-09-07)으로 확정한 구멍이다. 세터의 출처가 <b>필드</b>면(클래스에 {@code @Builder})
 * Lombok 플러그인의 {@code LombokFieldFindUsagesHandlerFactory} 가 이미 이어 준다. 그런데 그 처리기는
 * 이름 그대로 필드만 다뤄서, 출처가 <b>파라미터</b>인 경우는 0건이었다.
 *
 * <p>반대 방향(⌘+Click)은 이미 동작하므로 손대지 않았다 — 세 경우 모두 플랫폼 판정이 {@code GTD}
 * 였고 착지점이 파라미터 자신이었다.
 */
public class BuilderParameterUsageTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("OnCtor.java", """
            import lombok.Builder;
            public class OnCtor {
                private final String userName;
                @Builder
                public OnCtor(String userName) { this.userName = userName; }
            }
            """);
        myFixture.addFileToProject("OnFactory.java", """
            import lombok.Builder;
            public class OnFactory {
                private final String userName;
                private OnFactory(String userName) { this.userName = userName; }
                @Builder
                public static OnFactory of(String userName) { return new OnFactory(userName); }
            }
            """);
        myFixture.addFileToProject("OnClass.java", """
            import lombok.Builder;
            @Builder
            public class OnClass {
                private String userName;
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() {
                    OnCtor a = OnCtor.builder().userName("a").build();
                    OnFactory b = OnFactory.builder().userName("b").build();
                    OnClass c = OnClass.builder().userName("c").build();
                }
            }
            """);
    }

    /** 생성자 파라미터 — 이 플러그인이 잇는 자리. */
    public void testConstructorParameterFindsSetterCallSite() {
        assertCallerUsages(findClass("OnCtor").getConstructors()[0]
            .getParameterList().getParameters()[0]);
    }

    /** 정적 팩터리 메서드의 파라미터도 같다. */
    public void testFactoryMethodParameterFindsSetterCallSite() {
        assertCallerUsages(findClass("OnFactory").findMethodsByName("of", false)[0]
            .getParameterList().getParameters()[0]);
    }

    /**
     * 클래스에 붙은 경우는 출처가 필드라 Lombok 플러그인이 이미 이어 준다.
     * 우리가 또 보고하면 같은 호출부가 두 번 나온다 — 그래서 손대지 않는다.
     */
    public void testClassLevelFieldIsLeftToLombok() {
        PsiClass owner = findClass("OnClass");
        Collection<UsageInfo> usages = myFixture.findUsages(owner.findFieldByName("userName", false));
        assertEquals("필드 쪽은 Lombok 이 이미 이어 주므로 정확히 한 번만 나와야 한다: " + usages,
            1, callerUsages(usages));
    }

    /**
     * <b>액션 경로</b>로도 같은 결과가 나와야 한다 — ⌘+Click(사용처 팝업)과 ⌥F7 이 타는 길이다.
     *
     * <p>{@code myFixture.findUsages} 는 API 를 직접 부르는 길이라, 예전에 그쪽만 통과하고 실제
     * IDE 는 빈 결과인 적이 있었다({@code RealLombokUsageTest} 4단계 주석). 그래서 여기서는 액션을
     * 그대로 태운다.
     *
     * <p>참고: 사용처가 <b>하나뿐이면</b> 플랫폼은 팝업 대신 그 자리로 바로 이동한다
     * ({@code ShowUsagesAction}). 그래서 플러그인이 없을 때는 "아무 일도 안 일어난 것처럼" 보인다 —
     * 실은 같은 파일의 필드 대입 한 곳으로 옮겨간 것이다.
     */
    public void testFindUsagesActionShowsSetterCallSites() {
        myFixture.configureByText("Action.java", """
            import lombok.Builder;
            public class Action {
                private final String userName;
                @Builder
                public Action(String user<caret>Name) { this.userName = userName; }
            }
            """);
        myFixture.addFileToProject("ActionCaller.java", """
            public class ActionCaller {
                void use() { Action.builder().userName("a").build(); }
            }
            """);

        var usages = myFixture.testFindUsagesUsingAction("Action.java");
        assertEquals("필드 대입 한 곳 + 세터 호출부 한 곳: " + usages, 2, usages.size());
        assertTrue("세터 호출부가 목록에 있어야 한다: " + usages,
            usages.stream().anyMatch(usage -> usage.toString().contains("userName")
                && usage.toString().contains("\"a\"")));
    }

    /** 파라미터 이름을 바꾸면 세터 호출부도 함께 바뀐다. */
    public void testRenamingConstructorParameterRewritesCallSite() {
        myFixture.configureByText("Renamed.java", """
            import lombok.Builder;
            public class Renamed {
                private final String userName;
                @Builder
                public Renamed(String user<caret>Name) { this.userName = userName; }
            }
            """);
        myFixture.addFileToProject("RenamedCaller.java", """
            public class RenamedCaller {
                void use() { Renamed.builder().userName("a").build(); }
            }
            """);

        myFixture.renameElementAtCaret("loginName");

        String caller = fileText("RenamedCaller.java");
        assertTrue("세터 호출부가 새 이름으로 바뀌어야 한다:\n" + caller, caller.contains(".loginName(\"a\")"));
    }

    /** {@code setterPrefix} 가 걸려 있으면 접두사를 유지한 채 바뀌어야 한다. */
    public void testRenamingParameterKeepsTheSetterPrefix() {
        myFixture.configureByText("Prefixed.java", """
            import lombok.Builder;
            public class Prefixed {
                private final String userName;
                @Builder(setterPrefix = "with")
                public Prefixed(String user<caret>Name) { this.userName = userName; }
            }
            """);
        myFixture.addFileToProject("PrefixedCaller.java", """
            public class PrefixedCaller {
                void use() { Prefixed.builder().withUserName("a").build(); }
            }
            """);

        myFixture.renameElementAtCaret("loginName");

        String caller = fileText("PrefixedCaller.java");
        assertTrue("withUserName -> withLoginName 이어야 한다:\n" + caller,
            caller.contains(".withLoginName(\"a\")"));
    }

    // ---------- 도우미 ----------

    private void assertCallerUsages(PsiParameter parameter) {
        Collection<UsageInfo> usages = myFixture.findUsages(parameter);
        assertEquals("Caller.java 의 세터 호출이 나와야 한다: " + usages, 1, callerUsages(usages));
    }

    private static int callerUsages(Collection<UsageInfo> usages) {
        return (int) usages.stream().filter(usage -> {
            PsiElement element = usage.getElement();
            return element != null && element.getContainingFile().getName().startsWith("Caller");
        }).count();
    }

    private String fileText(String name) {
        VirtualFile file = myFixture.findFileInTempDir(name);
        assertNotNull(name + " 을 찾지 못했다", file);
        PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
        assertNotNull(name + " 의 PSI 를 찾지 못했다", psiFile);
        return psiFile.getText();
    }

    private PsiClass findClass(String name) {
        PsiClass found = JavaPsiFacade.getInstance(getProject())
            .findClass(name, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(name + " 를 찾지 못했다", found);
        return found;
    }
}
