package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.find.usages.api.Usage;
import com.intellij.find.usages.api.UsageOptions;
import com.intellij.find.usages.impl.AllSearchOptions;
import com.intellij.find.usages.impl.ImplKt;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@code @SuperBuilder} 에 이름 문자열을 붙였을 때도 사용처가 잡히는가 — <b>상속 체인 너머까지</b>.
 *
 * <p>2026-09-07 실측으로 확인한 결과 <b>이미 다 동작한다</b>. {@code BuilderTarget} 이 묻는 것이
 * "클래스냐 메서드냐"와 표준 PSI 조회뿐이라 {@code @SuperBuilder} 의 두 클래스 구조
 * (추상 {@code XBuilder<C,B>} + {@code XBuilderImpl})가 그대로 얹혔다. record 때와 같은 이유이고,
 * 같은 이유로 <b>우연히 맞는 상태</b>다 — {@code BuilderTarget} 을 손대다 조용히 빠질 수 있어 못을 박는다.
 *
 * <p><b>픽스처 주의.</b> {@code buildMethodName} 은 부모와 자식이 <b>같아야 한다</b> — 자식 빌더의
 * build 가 부모의 것을 오버라이드하므로 다르게 주면 lombok 이 붙인 {@code @Override} 가 컴파일 오류가
 * 된다(2026-09-07, 실제로 컴파일해서 확인). 처음에는 다르게 써 두고 테스트가 통과했는데, IDE 증강은
 * 그 조합을 그대로 만들어 주기 때문이다 — {@code @SuperBuilder} 스텁의 {@code @Target} 때와 같은
 * 종류의 착각이다. 그래서 build 메서드 이름의 사용처는 여기서 따로 세지 않는다(부모·자식이 같은
 * 이름이라 어느 쪽 심볼로 세느냐에 따라 답이 갈린다).
 *
 * <p>{@code multiResolve} 로 재면 안 된다 — 생성된 멤버는 일부러 해석 결과를 비워 두기 때문에
 * (Lombok 의 rename veto 처리기와 팝업이 겹치는 것을 피하려고) 항상 "해석 실패"로 보인다.
 * 판정은 {@code BuilderNameDeclarationProvider} → {@code ImplKt.buildQuery} 로 해야 한다.
 *
 * <p>{@code @SuperBuilder} 는 추상 {@code XBuilder<C,B>} 와 {@code XBuilderImpl} 두 클래스를
 * 만든다. builder-zoo 의 S 그룹에는 이름 문자열이 없어서 이 경로는 한 번도 측정된 적이 없다.
 */
public class SuperBuilderUsageTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("SuperParent.java", """
            import lombok.experimental.SuperBuilder;
            @SuperBuilder(builderMethodName = "parentBuilder", buildMethodName = "create", setterPrefix = "with")
            public class SuperParent {
                private String parentField;
            }
            """);
        myFixture.addFileToProject("SuperChild.java", """
            import lombok.experimental.SuperBuilder;
            @SuperBuilder(builderMethodName = "childBuilder", buildMethodName = "create", setterPrefix = "set")
            public class SuperChild extends SuperParent {
                private String childField;
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() {
                    SuperParent p = SuperParent.parentBuilder().withParentField("p").create();
                    SuperChild c = SuperChild.childBuilder().withParentField("p").setChildField("c").create();
                }
            }
            """);
    }

    /** 우리 조회 코드가 두 클래스 중 <b>추상 쪽</b>을 집어야 한다 — 세터와 build 가 거기 있다. */
    public void testBuilderLookupPicksTheAbstractBuilder() {
        PsiClass owner = findClass("SuperParent");
        assertEquals("Lombok 은 두 클래스를 만든다",
            List.of("SuperParentBuilder", "SuperParentBuilderImpl"),
            Arrays.stream(owner.getInnerClasses()).map(PsiClass::getName).toList());

        BuilderTarget target = BuilderTarget.of(owner);
        assertNotNull("BuilderTarget 이 @SuperBuilder 를 읽지 못했다", target);
        PsiClass builderClass = target.findBuilderClass();
        assertNotNull("빌더 클래스를 찾지 못했다", builderClass);
        assertEquals("SuperParentBuilder", builderClass.getName());
        assertEquals("호출부의 정적 타입이 이 클래스다 — 여기 있는 create() 가 검색 대상이어야 한다",
            1, builderClass.findMethodsByName("create", false).length);

        PsiMethod entry = target.findBuilderMethod();
        assertNotNull("진입 메서드를 찾지 못했다", entry);
        assertEquals("parentBuilder", entry.getName());
    }

    public void testParentNameStringsFindCallSites() {
        assertUsages("SuperParent", "builderMethodName", 1);
        // 부모 세터 호출은 부모 체인과 자식 체인에 하나씩 흩어져 있다 — 둘 다 잡혀야 한다.
        assertUsages("SuperParent", "setterPrefix", 2);
    }

    public void testChildNameStringsFindCallSites() {
        assertUsages("SuperChild", "builderMethodName", 1);
        // 자식 빌더에는 자식 필드의 세터만 있다. 부모 세터는 부모의 접두사 몫이다.
        assertUsages("SuperChild", "setterPrefix", 1);
    }

    /** 팝업이 타는 경로 그대로: 선언이 만들어지는가 → 그 심볼의 사용처가 몇 건인가. */
    private void assertUsages(String className, String attributeName, int expected) {
        PsiClass owner = findClass(className);
        PsiAnnotation annotation = owner.getAnnotation("lombok.experimental.SuperBuilder");
        assertNotNull("@SuperBuilder 를 찾지 못했다", annotation);
        PsiElement value = annotation.findDeclaredAttributeValue(attributeName);
        assertNotNull(attributeName + " 값을 찾지 못했다", value);

        Collection<? extends PsiSymbolDeclaration> declarations =
            new BuilderNameDeclarationProvider().getDeclarations(value, 0);
        assertFalse(attributeName + " 이 이름을 정하는 자리로 인식되지 않았다", declarations.isEmpty());
        BuilderMemberSymbol symbol = (BuilderMemberSymbol) declarations.iterator().next().getSymbol();

        AllSearchOptions options = new AllSearchOptions(
            UsageOptions.createOptions(GlobalSearchScope.projectScope(getProject())), false);
        Collection<? extends Usage> usages =
            ImplKt.buildQuery(getProject(), symbol.getSearchTarget(), options).findAll();
        assertEquals(className + "." + attributeName + " 의 사용처: " + usages, expected, usages.size());
    }

    private PsiClass findClass(String name) {
        PsiClass found = JavaPsiFacade.getInstance(getProject())
            .findClass(name, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(name + " 를 찾지 못했다", found);
        return found;
    }
}
