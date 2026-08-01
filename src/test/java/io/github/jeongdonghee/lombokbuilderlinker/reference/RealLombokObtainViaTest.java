package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;

/**
 * {@code @Builder.ObtainVia} 를 <b>실제 lombok 라이브러리</b>와 <b>중첩 클래스</b> 형태로 검증한다.
 *
 * <p>{@code ObtainViaReferenceTest} 는 애노테이션을 소스 스텁으로 넣고 최상위 클래스로 검증했다.
 * 그 조건에서는 통과했는데 실제 IDE({@code builder-zoo} 의 {@code NestedFeatures}) 에서는 rename 이
 * 동작하지 않는다는 보고가 있었다. 두 환경의 차이는 두 가지다 — 스텁 대신 실제 라이브러리, 그리고
 * 최상위 클래스 대신 <b>정적 중첩 클래스</b>. 그래서 이 테스트는 {@code builder-zoo} 의 모양을
 * 그대로 베껴 그 차이를 재현 대상으로 삼는다.
 */
public class RealLombokObtainViaTest extends LombokTestCase {

    /** builder-zoo 의 NestedFeatures 와 같은 모양 — 컨테이너 클래스 안의 정적 중첩 클래스. */
    private static final String NESTED_SOURCE = """
        import lombok.Builder;

        public final class NestedFeatures {

            @Builder(toBuilder = true)
            public static class X02_ObtainViaMethod {
                private String name;

                @Builder.ObtainVia(method = "computeLength")
                private int length;

                public int computeLength() { return name == null ? 0 : name.length(); }
            }

            @Builder(toBuilder = true)
            public static class X03_ObtainViaField {
                private String displayName;

                @Builder.ObtainVia(field = "displayName")
                private String alias;
            }
        }
        """;

    public void testMethodAttributeResolvesInsideNestedClass() {
        configureWithCaretOn("method = \"comput", "eLength\"");
        PsiElement resolved = resolveAtCaret();
        assertTrue("메서드로 해석돼야 한다: " + resolved, resolved instanceof PsiMethod);
        assertEquals("computeLength", ((PsiMethod) resolved).getName());
    }

    public void testFieldAttributeResolvesInsideNestedClass() {
        configureWithCaretOn("field = \"display", "Name\"");
        PsiElement resolved = resolveAtCaret();
        assertTrue("필드로 해석돼야 한다: " + resolved, resolved instanceof PsiField);
        assertEquals("displayName", ((PsiField) resolved).getName());
    }

    /** 핵심 — 실제 lombok + 중첩 클래스에서도 메서드 rename 이 문자열까지 따라오는가. */
    public void testRenamingMethodUpdatesTheAnnotationString() {
        myFixture.configureByText("NestedFeatures.java",
            NESTED_SOURCE.replace("public int computeLength()", "public int comput<caret>eLength()"));
        myFixture.renameElementAtCaret("computeSize");
        assertTrue("애노테이션 문자열이 함께 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("method = \"computeSize\""));
    }

    /** 필드 rename 도 마찬가지. */
    public void testRenamingFieldUpdatesTheAnnotationString() {
        myFixture.configureByText("NestedFeatures.java",
            NESTED_SOURCE.replace("private String displayName;", "private String display<caret>Name;"));
        myFixture.renameElementAtCaret("label");
        assertTrue("애노테이션 문자열이 함께 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("field = \"label\""));
    }

    private void configureWithCaretOn(String before, String after) {
        myFixture.configureByText("NestedFeatures.java",
            NESTED_SOURCE.replace(before + after, before + "<caret>" + after));
    }

    private PsiElement resolveAtCaret() {
        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("문자열에 참조가 심어지지 않았다", reference);
        return reference.resolve();
    }
}
