package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.Usage;
import com.intellij.find.usages.api.UsageOptions;
import com.intellij.find.usages.impl.AllSearchOptions;
import com.intellij.find.usages.impl.ImplKt;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * <b>사용처 팝업에 실제로 내용이 들어오는가</b> — 팝업이 쓰는 경로를 그대로 태운다.
 *
 * <p>왜 이 경로인가: 문자열이 선언이라고 알리는 것만으로는 팝업이 채워지지 않는다.
 * {@code ImplKt.buildQuery} 가 {@code SearchService.searchParameters(UsageSearchParameters)} 로
 * <b>{@code searcher} 확장점에 등록된 검색기들</b>에게만 묻기 때문이다(플랫폼 바이트코드 확인).
 * 그 검색기를 등록하지 않으면 팝업이 "No usages found in Project Files" 로 뜬다.
 *
 * <p>그래서 여기서는 플랫폼이 만드는 질의를 그대로 실행한다. 내부 API 를 쓰지만, 그게 팝업이 쓰는
 * 바로 그 경로다 — 우리 코드가 아니라 <b>사용자가 보는 결과</b>를 확인하기 위한 선택이다.
 */
public class BuilderMemberUsageSearchTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "historyChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { ExactCase.historyChannelBuilder().channelName("a").build(); }
                void two() { ExactCase.historyChannelBuilder().channelName("b").build(); }
            }
            """);
        // 이름만 같은 다른 클래스 — 팝업에 섞이면 안 된다.
        myFixture.addFileToProject("Decoy.java", """
            public class Decoy {
                static Decoy historyChannelBuilder() { return null; }
                void use() { Decoy.historyChannelBuilder(); }
            }
            """);
    }

    /** 팝업 질의가 호출부 두 곳을 내놓아야 한다. */
    public void testUsagePopupQueryFindsCallSites() {
        Collection<? extends Usage> usages = runPopupQuery();

        assertFalse("사용처 팝업이 비어 있다 — searcher 확장점 등록이 빠졌는지 확인할 것", usages.isEmpty());
        assertEquals("호출부 두 곳이 나와야 한다: " + describe(usages), 2, usages.size());
    }

    /** 이름만 같은 다른 클래스의 메서드는 섞이지 않는다. */
    public void testUsagePopupQueryExcludesSameNamedMemberOfAnotherClass() {
        assertTrue("이름만 같은 다른 클래스가 섞였다: " + describe(runPopupQuery()),
            runPopupQuery().stream().noneMatch(usage -> describe(List.of(usage)).contains("Decoy.java")));
    }

    /** 선언 자리(애노테이션 문자열) 자신은 사용처가 아니다. */
    public void testDeclarationItselfIsNotAUsage() {
        assertTrue("선언 자리가 사용처로 들어갔다: " + describe(runPopupQuery()),
            runPopupQuery().stream().noneMatch(usage -> describe(List.of(usage)).contains("ExactCase.java")));
    }

    /**
     * {@code setterPrefix} — 접두사가 만든 <b>세터들의 호출부 전부</b>가 잡혀야 한다.
     *
     * <p>검색 방식이 이 자리만 다르다: 접두사는 이름이 아니라서 인덱스에서 그 낱말을 찾는 방식이
     * 통하지 않는다 — 호출부에 적힌 이름은 {@code withName} · {@code withCount} 다. 그래서
     * {@code BuilderMemberUsageSearcher} 가 세터마다 질의를 하나씩 만든다.
     *
     * <p>픽스처는 실제로 쓰는 모양 그대로다 — 패키지 있는 <b>중첩 클래스</b>, 한 줄 체인 호출.
     * (화면에서는 사용처가 전부 한 줄에 있으면 플랫폼이 목록 대신 그 줄로 이동한다. 검색 결과는
     * 그것과 무관하게 여기 숫자대로 나온다.)
     */
    public void testSetterPrefixFindsEverySetterCall() {
        myFixture.addFileToProject("builderzoo/Naming.java", """
            package builderzoo;
            import lombok.Builder;
            public final class Naming {
                private Naming() {}

                @Builder(setterPrefix = "with")
                public static class N04_SetterPrefix {
                    private String name;
                    private int count;
                }
            }
            """);
        myFixture.addFileToProject("builderzoo/CallSites.java", """
            package builderzoo;
            public class CallSites {
                void u() {
                    var n04 = Naming.N04_SetterPrefix.builder().withName("a").withCount(1).build();
                }
            }
            """);

        Collection<? extends Usage> usages = runNestedPrefixQuery();

        assertEquals("한 줄 체인의 세터 두 곳이 모두 나와야 한다: " + describe(usages), 2, usages.size());
    }

    /** 접두사가 없는 다른 빌더의 세터는 이 접두사의 사용처가 아니다. */
    public void testSetterPrefixDoesNotCatchUnprefixedSetters() {
        myFixture.addFileToProject("builderzoo/Naming.java", """
            package builderzoo;
            import lombok.Builder;
            public final class Naming {
                private Naming() {}

                @Builder(setterPrefix = "with")
                public static class N04_SetterPrefix {
                    private String name;
                }
            }
            """);
        myFixture.addFileToProject("builderzoo/CallSites.java", """
            package builderzoo;
            public class CallSites {
                void mine() { Naming.N04_SetterPrefix.builder().withName("a").build(); }
                void other() { ExactCase.historyChannelBuilder().channelName("a").build(); }
            }
            """);

        Collection<? extends Usage> usages = runNestedPrefixQuery();

        assertEquals("접두사 붙은 세터만 나와야 한다: " + describe(usages), 1, usages.size());
    }

    /**
     * {@code buildMethodName} 과 {@code builderClassName} 도 같은 검색을 탄다.
     *
     * <p>세 종류가 한 경로(생성된 멤버의 PSI 참조 검색)로 합쳐졌으므로, 한 종류만 확인하면 나머지가
     * 조용히 비어도 모른다.
     */
    public void testBuildMethodAndBuilderClassUsages() {
        myFixture.addFileToProject("Named.java", """
            import lombok.Builder;
            public class Named {
                private final String name;

                @Builder(buildMethodName = "create", builderClassName = "Maker")
                public Named(String name) { this.name = name; }
            }
            """);
        myFixture.addFileToProject("NamedCaller.java", """
            public class NamedCaller {
                void one() {
                    Named.Maker m = Named.builder();
                    Named n = m.name("a").create();
                }
                void two() { Named.builder().name("b").create(); }
            }
            """);

        Collection<? extends Usage> build = runPopupQuery("Named", "buildMethodName");
        assertEquals("create() 호출 두 곳이 나와야 한다: " + describe(build), 2, build.size());

        Collection<? extends Usage> builderClass = runPopupQuery("Named", "builderClassName");
        assertEquals("Maker 를 타입으로 쓴 한 곳이 나와야 한다: " + describe(builderClass), 1, builderClass.size());
    }

    // ---------- 도우미 ----------

    /** 중첩 클래스의 {@code setterPrefix} 심볼로 팝업 질의를 돌린다. */
    private Collection<? extends Usage> runNestedPrefixQuery() {
        PsiClass nested = JavaPsiFacade.getInstance(getProject())
            .findClass("builderzoo.Naming.N04_SetterPrefix", GlobalSearchScope.allScope(getProject()));
        assertNotNull("중첩 클래스를 찾지 못했다", nested);
        PsiAnnotation annotation = nested.getAnnotation("lombok.Builder");
        assertNotNull("@Builder 를 찾지 못했다", annotation);
        PsiElement value = annotation.findDeclaredAttributeValue("setterPrefix");

        Collection<? extends PsiSymbolDeclaration> declarations =
            new BuilderNameDeclarationProvider().getDeclarations(value, 0);
        assertFalse("선언을 만들지 못했다", declarations.isEmpty());
        BuilderMemberSymbol symbol = (BuilderMemberSymbol) declarations.iterator().next().getSymbol();

        AllSearchOptions options = new AllSearchOptions(
            UsageOptions.createOptions(GlobalSearchScope.projectScope(getProject())), false);
        return ImplKt.buildQuery(getProject(), symbol.getSearchTarget(), options).findAll();
    }

    /** ⌘+Click 사용처 팝업이 실행하는 질의. */
    private Collection<? extends Usage> runPopupQuery() {
        return runPopupQuery("ExactCase", "builderMethodName");
    }

    private Collection<? extends Usage> runPopupQuery(String className, String attributeName) {
        SearchTarget target = searchTargetOf(className, attributeName);
        AllSearchOptions options = new AllSearchOptions(
            UsageOptions.createOptions(GlobalSearchScope.projectScope(getProject())), false);
        return ImplKt.buildQuery(getProject(), target, options).findAll();
    }

    private SearchTarget searchTargetOf(String className, String attributeName) {
        PsiClass owner = JavaPsiFacade.getInstance(getProject())
            .findClass(className, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(className + " 을 찾지 못했다", owner);
        PsiAnnotation annotation = owner.getConstructors()[0].getAnnotation("lombok.Builder");
        assertNotNull("@Builder 를 찾지 못했다", annotation);
        PsiElement value = annotation.findDeclaredAttributeValue(attributeName);
        assertTrue("문자열 리터럴이어야 한다: " + value, value instanceof PsiLiteralExpression);

        Collection<? extends PsiSymbolDeclaration> declarations =
            new BuilderNameDeclarationProvider().getDeclarations(value, 0);
        assertFalse("선언을 만들지 못했다", declarations.isEmpty());
        BuilderMemberSymbol symbol = (BuilderMemberSymbol) declarations.iterator().next().getSymbol();
        return symbol.getSearchTarget();
    }

    private static String describe(@NotNull Collection<? extends Usage> usages) {
        return usages.stream().map(Object::toString).toList().toString();
    }
}
