package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.github.jeongdonghee.lombokbuilderlinker.LombokStubs;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code @Builder} 의 이름 속성 → Lombok 이 만들어낸 멤버를 찾아내는가.
 *
 * <p>테스트에는 Lombok 애노테이션 프로세서가 없어 빌더가 실제로 생성되지 않으므로,
 * Lombok 이 만들어낼 멤버를 픽스처에 손으로 써 준다. 플러그인의 조회 경로는 표준 PSI
 * ({@code findMethodsByName} / {@code findInnerClassByName})라 증강으로 생긴 멤버든
 * 손으로 쓴 멤버든 동일하게 동작한다.
 *
 * <p><b>계약 주의.</b> 합성 멤버는 {@link LombokMemberReference#generatedTargets()} 로만 얻고
 * {@code multiResolve} 에는 노출하지 않는다 — 노출하면 Lombok 플러그인의 이름 변경 거부 처리기가
 * 함께 손을 들어 ⇧F6 에서 처리기 선택 팝업이 뜬다. 자세한 이유는 {@link RenameHandlerAmbiguityTest}.
 * 그래서 아래 테스트들은 "해석된다"가 아니라 "찾아낸다 + 해석에는 노출하지 않는다"를 확인한다.
 */
public class BuilderNameReferenceTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_17;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LombokStubs.add(myFixture);
    }

    /** builderMethodName → 담은 클래스의 진입 메서드. 실측 ★ 케이스와 같은 형태(생성자에 붙음). */
    public void testBuilderMethodNameOnConstructorFindsEntryMethod() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            class Sample {
                private final String name;
                @Builder(builderMethodName = "sample<caret>Builder")
                Sample(String name) { this.name = name; }
                static SampleBuilder sampleBuilder() { return null; }
                static class SampleBuilder { Sample build() { return null; } }
            }
            """);
        assertEquals(List.of("sampleBuilder"), methodNamesOfGeneratedTargets());
        assertNotExposedToResolution();
    }

    /** 클래스에 붙은 경우도 같은 경로를 탄다. */
    public void testBuilderMethodNameOnClassFindsEntryMethod() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(builderMethodName = "make<caret>One")
            class Sample {
                private String name;
                static SampleBuilder makeOne() { return null; }
                static class SampleBuilder { Sample build() { return null; } }
            }
            """);
        assertEquals(List.of("makeOne"), methodNamesOfGeneratedTargets());
        assertNotExposedToResolution();
    }

    /** buildMethodName → 빌더 <b>클래스 안</b>의 메서드. 빌더 클래스 이름은 기본값(타입명 + Builder). */
    public void testBuildMethodNameFoundInsideBuilderClass() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            class Sample {
                private final String name;
                @Builder(buildMethodName = "cre<caret>ate")
                Sample(String name) { this.name = name; }
                static SampleBuilder builder() { return null; }
                static class SampleBuilder { Sample create() { return null; } }
            }
            """);
        List<PsiElement> targets = generatedTargets();
        assertEquals("빌더 클래스의 메서드 하나여야 한다: " + targets, 1, targets.size());
        PsiMethod method = (PsiMethod) targets.get(0);
        assertEquals("create", method.getName());
        assertEquals("SampleBuilder",
            method.getContainingClass() == null ? null : method.getContainingClass().getName());
        assertNotExposedToResolution();
    }

    /** builderClassName → 생성된 빌더 클래스. */
    public void testBuilderClassNameFindsInnerClass() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            class Sample {
                private final String name;
                @Builder(builderClassName = "Ma<caret>ker")
                Sample(String name) { this.name = name; }
                static Maker builder() { return null; }
                static class Maker { Sample build() { return null; } }
            }
            """);
        List<PsiElement> targets = generatedTargets();
        assertEquals("내부 클래스 하나여야 한다: " + targets, 1, targets.size());
        assertTrue("클래스여야 한다: " + targets.get(0), targets.get(0) instanceof PsiClass);
        assertEquals("Maker", ((PsiClass) targets.get(0)).getName());
        assertNotExposedToResolution();
    }

    /**
     * setterPrefix 는 1:N 이다 — 접두사 하나가 생성된 세터 여러 개에 대응하므로 전부 찾아낸다.
     * 그래야 이동 착지점 계산이 세터 전체의 호출부를 본다.
     */
    public void testSetterPrefixFindsEveryPrefixedSetter() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            class Sample {
                private final String name;
                private final int count;
                @Builder(setterPrefix = "wi<caret>th")
                Sample(String name, int count) { this.name = name; this.count = count; }
                static SampleBuilder builder() { return null; }
                static class SampleBuilder {
                    SampleBuilder withName(String n) { return this; }
                    SampleBuilder withCount(int c) { return this; }
                    Sample build() { return null; }
                }
            }
            """);
        assertEquals(List.of("withCount", "withName"), methodNamesOfGeneratedTargets());
        assertNotExposedToResolution();
    }

    /** builderMethodName = "" 는 진입 메서드를 만들지 말라는 뜻 — 가리킬 대상이 없으니 참조도 없다. */
    public void testSuppressedBuilderMethodNameGetsNoReference() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            class Sample {
                private final String name;
                @Builder(builderMethodName = "<caret>", toBuilder = true)
                Sample(String name) { this.name = name; }
            }
            """);
        assertNull("빈 문자열에는 참조를 만들지 않아야 한다", referenceAtCaret());
    }

    // ---------- 도우미 ----------

    private PsiReference referenceAtCaret() {
        return myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    }

    private LombokMemberReference memberReferenceAtCaret() {
        PsiReference reference = referenceAtCaret();
        assertNotNull("문자열에 참조가 심어지지 않았다", reference);
        assertTrue("우리 참조여야 한다: " + reference, reference instanceof LombokMemberReference);
        return (LombokMemberReference) reference;
    }

    private List<PsiElement> generatedTargets() {
        return memberReferenceAtCaret().generatedTargets();
    }

    private List<String> methodNamesOfGeneratedTargets() {
        return generatedTargets().stream()
            .filter(PsiMethod.class::isInstance)
            .map(element -> ((PsiMethod) element).getName())
            .sorted()
            .toList();
    }

    /** 합성 멤버가 해석 결과로 새어 나가면 ⇧F6 에서 처리기 선택 팝업이 뜬다. */
    private void assertNotExposedToResolution() {
        LombokMemberReference reference = memberReferenceAtCaret();
        assertNull("합성 멤버는 resolve() 로 노출되지 않아야 한다", reference.resolve());
        assertEquals("합성 멤버는 multiResolve() 로 노출되지 않아야 한다",
            0, ((PsiPolyVariantReference) reference).multiResolve(false).length);
    }
}
