package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.github.jeongdonghee.lombokbuilderlinker.LombokStubs;
import org.jetbrains.annotations.NotNull;

/**
 * {@code @Builder.ObtainVia} 의 이름 문자열 → <b>직접 쓴</b> 멤버 참조.
 *
 * <p>이 플러그인에서 가장 값이 큰 자리다. 다른 속성들은 생성된 멤버를 가리키니 이동이 안 되면
 * 불편한 정도지만, {@code ObtainVia} 는 손으로 쓴 코드를 문자열로 가리키므로 참조가 없으면
 * Rename 이 문자열을 남겨두고 <b>조용히 깨진다</b>. 컴파일도 통과한다.
 */
public class ObtainViaReferenceTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_17;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LombokStubs.add(myFixture);
    }

    /** method = "..." 는 같은 클래스의 메서드로 해석된다. */
    public void testMethodAttributeResolvesToHandWrittenMethod() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String name;
                @Builder.ObtainVia(method = "comput<caret>eLength")
                private int length;
                public int computeLength() { return name == null ? 0 : name.length(); }
            }
            """);
        PsiElement resolved = resolveAtCaret();
        assertTrue("메서드로 해석돼야 한다: " + resolved, resolved instanceof PsiMethod);
        assertEquals("computeLength", ((PsiMethod) resolved).getName());
    }

    /** field = "..." 는 같은 클래스의 필드로 해석된다. */
    public void testFieldAttributeResolvesToHandWrittenField() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String displayName;
                @Builder.ObtainVia(field = "display<caret>Name")
                private String alias;
            }
            """);
        PsiElement resolved = resolveAtCaret();
        assertTrue("필드로 해석돼야 한다: " + resolved, resolved instanceof PsiField);
        assertEquals("displayName", ((PsiField) resolved).getName());
    }

    /**
     * 핵심 검증 — 메서드 이름을 바꾸면 애노테이션 문자열도 <b>같이</b> 바뀐다.
     * 참조를 심은 목적이 바로 이것이다(Rename 은 참조를 따라 움직인다).
     */
    public void testRenamingMethodUpdatesTheAnnotationString() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String name;
                @Builder.ObtainVia(method = "computeLength")
                private int length;
                public int comput<caret>eLength() { return 0; }
            }
            """);
        myFixture.renameElementAtCaret("computeSize");
        myFixture.checkResult("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String name;
                @Builder.ObtainVia(method = "computeSize")
                private int length;
                public int computeSize() { return 0; }
            }
            """);
    }

    /** 필드 이름 변경도 마찬가지. */
    public void testRenamingFieldUpdatesTheAnnotationString() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String display<caret>Name;
                @Builder.ObtainVia(field = "displayName")
                private String alias;
            }
            """);
        myFixture.renameElementAtCaret("label");
        myFixture.checkResult("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String label;
                @Builder.ObtainVia(field = "label")
                private String alias;
            }
            """);
    }

    /** 없는 이름은 조용히 해석 실패한다(soft) — 빨간 오류로 코드를 어지럽히지 않는다. */
    public void testUnknownNameResolvesToNothingWithoutError() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String name;
                @Builder.ObtainVia(method = "noSuch<caret>Method")
                private int length;
            }
            """);
        PsiReference reference = referenceAtCaret();
        assertNotNull("참조 자체는 만들어져야 한다", reference);
        assertNull("해석은 실패해야 한다", reference.resolve());
        assertTrue("soft 여야 한다", reference.isSoft());
    }

    /**
     * Lombok 이 아닌 애노테이션의 문자열에는 참조를 만들지 않는다.
     *
     * <p>이름이 우연히 실제 메서드와 같아도 손대지 않아야 한다 — 남의 애노테이션에 참조를
     * 심으면 그쪽 Rename 을 망친다.
     */
    public void testUnrelatedAnnotationGetsNoReference() {
        myFixture.configureByText("Sample.java", """
            import lombok.Builder;
            @interface Marker { String label() default ""; }
            @Builder(toBuilder = true)
            class Sample {
                @Marker(label = "comput<caret>eLength")
                private int length;
                public int computeLength() { return 0; }
            }
            """);
        assertNull("Lombok 이 아닌 애노테이션에는 참조가 없어야 한다", referenceAtCaret());
    }

    private PsiReference referenceAtCaret() {
        return myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    }

    private PsiElement resolveAtCaret() {
        PsiReference reference = referenceAtCaret();
        assertNotNull("문자열에 참조가 심어지지 않았다", reference);
        return reference.resolve();
    }
}
