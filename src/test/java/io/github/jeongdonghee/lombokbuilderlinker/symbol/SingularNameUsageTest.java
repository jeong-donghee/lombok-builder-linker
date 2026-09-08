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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * {@code @Singular("item")} 의 문자열이 <b>생성된 단수 adder</b> 를 가리키는가.
 *
 * <p>처음 실측(2026-07-31)에서 {@code @Singular} 를 "정상, 범위 제외"로 분류했는데, 그때 잰 것은
 * <b>값 없는</b> {@code @Singular} 였다(체크리스트 X04 행은 C 칸이 {@code -} 로 못박혀 있다).
 * 문자열 형태는 잰 적이 없었고, 실제로는 {@code builderMethodName} 과 같은 구조로 끊겨 있었다.
 *
 * <p>{@code setterPrefix} 가 함께 있으면 문자열과 실제 메서드 이름이 <b>다르다</b> —
 * {@code @Singular("item")} + {@code setterPrefix = "with"} 는 {@code withItem} 을 만든다
 * (lombok 의 {@code JavacSingularsRecipes} 가 접근자 이름 규칙을 적용한다). 그 자리도 함께 검사한다.
 */
public class SingularNameUsageTest extends LombokTestCase {

    /** 접두사 없음 — 문자열이 곧 메서드 이름이다. */
    public void testSingularNameFindsAdderCallSites() {
        myFixture.addFileToProject("Plain.java", """
            import java.util.List;
            import lombok.Builder;
            import lombok.Singular;
            @Builder
            public class Plain {
                @Singular("item")
                private List<String> items;
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { Plain.builder().item("a").build(); }
                void two() { Plain.builder().item("b").item("c").build(); }
            }
            """);

        Collection<? extends Usage> usages = runQuery("Plain", "items");
        assertEquals("단수 adder 호출 세 곳이 나와야 한다: " + usages, 3, usages.size());
    }

    /** 접두사 있음 — 문자열은 {@code item} 인데 호출부에 적히는 이름은 {@code withItem} 이다. */
    public void testSingularNameUnderSetterPrefix() {
        myFixture.addFileToProject("Prefixed.java", """
            import java.util.List;
            import lombok.Builder;
            import lombok.Singular;
            @Builder(setterPrefix = "with")
            public class Prefixed {
                @Singular("item")
                private List<String> items;
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { Prefixed.builder().withItem("a").build(); }
            }
            """);

        Collection<? extends Usage> usages = runQuery("Prefixed", "items");
        assertEquals("접두사 붙은 단수 adder 가 잡혀야 한다: " + usages, 1, usages.size());
    }

    /** {@code @Builder} 가 생성자에 붙어 {@code @Singular} 가 <b>파라미터</b>에 붙는 형태. */
    public void testSingularOnConstructorParameter() {
        myFixture.addFileToProject("OnCtor.java", """
            import java.util.List;
            import lombok.Builder;
            import lombok.Singular;
            public class OnCtor {
                private final List<String> items;
                @Builder
                public OnCtor(@Singular("item") List<String> items) { this.items = items; }
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { OnCtor.builder().item("a").build(); }
            }
            """);

        PsiClass owner = findClass("OnCtor");
        PsiModifierList modifiers = owner.getConstructors()[0].getParameterList()
            .getParameters()[0].getModifierList();
        assertNotNull(modifiers);
        PsiAnnotation singular = modifiers.findAnnotation("lombok.Singular");
        assertNotNull("@Singular 를 찾지 못했다", singular);

        Collection<? extends Usage> usages = runQuery(singular);
        assertEquals("파라미터에 붙은 경우도 잡혀야 한다: " + usages, 1, usages.size());
    }

    /** 값 없는 {@code @Singular} 는 정할 이름이 없다 — 손대지 않는다. */
    public void testSingularWithoutNameIsNotADeclaration() {
        myFixture.configureByText("Bare.java", """
            import java.util.List;
            import lombok.Builder;
            import lombok.Singular;
            @Builder
            public class Bare {
                @Singular
                private List<String> jobs;
            }
            """);
        PsiClass owner = findClass("Bare");
        PsiField field = owner.findFieldByName("jobs", false);
        assertNotNull(field);
        PsiModifierList modifiers = field.getModifierList();
        assertNotNull(modifiers);
        PsiAnnotation singular = modifiers.findAnnotation("lombok.Singular");
        assertNotNull(singular);
        assertNull("문자열이 없으므로 선언할 것이 없다",
            singular.findDeclaredAttributeValue("value"));
    }

    // ---------- 도우미 ----------

    private Collection<? extends Usage> runQuery(String className, String fieldName) {
        PsiClass owner = findClass(className);
        PsiField field = owner.findFieldByName(fieldName, false);
        assertNotNull(fieldName + " 를 찾지 못했다", field);
        PsiModifierList modifiers = field.getModifierList();
        assertNotNull(modifiers);
        PsiAnnotation singular = modifiers.findAnnotation("lombok.Singular");
        assertNotNull("@Singular 를 찾지 못했다", singular);
        return runQuery(singular);
    }

    private Collection<? extends Usage> runQuery(@NotNull PsiAnnotation singular) {
        PsiElement value = singular.findDeclaredAttributeValue("value");
        assertNotNull("@Singular 의 값 문자열을 찾지 못했다", value);

        Collection<? extends PsiSymbolDeclaration> declarations =
            new BuilderNameDeclarationProvider().getDeclarations(value, 0);
        assertFalse("선언을 만들지 못했다 — @Singular 문자열이 이름을 정하는 자리로 인식되지 않았다",
            declarations.isEmpty());
        BuilderMemberSymbol symbol = (BuilderMemberSymbol) declarations.iterator().next().getSymbol();

        AllSearchOptions options = new AllSearchOptions(
            UsageOptions.createOptions(GlobalSearchScope.projectScope(getProject())), false);
        return ImplKt.buildQuery(getProject(), symbol.getSearchTarget(), options).findAll();
    }

    private PsiClass findClass(String name) {
        PsiClass found = JavaPsiFacade.getInstance(getProject())
            .findClass(name, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(name + " 를 찾지 못했다", found);
        return found;
    }
}
