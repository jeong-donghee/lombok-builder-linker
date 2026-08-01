package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 문자열 리터럴이 "어느 애노테이션의 어느 속성 자리"인지 알아낸 결과.
 *
 * <p>참조를 만들 때(제공자)와 실제로 해석할 때(참조 객체) 양쪽에서 같은 판단이 필요하다.
 * 참조 객체가 상태를 들고 있으면 PSI 가 바뀐 뒤 낡은 값을 보게 되므로, 매번 리터럴에서
 * 거꾸로 찾아 올라오도록 이 도우미를 공유한다.
 */
public record AnnotationAttribute(@NotNull PsiAnnotation annotation,
                           @NotNull String attributeName,
                           @NotNull String value) {

    /** 리터럴이 {@code @Foo(attr = "value")} 형태의 값 자리가 아니면 {@code null}. */
    public static @Nullable AnnotationAttribute of(@Nullable PsiElement element) {
        if (!(element instanceof PsiLiteralExpression literal)) {
            return null;
        }
        if (!(literal.getValue() instanceof String value)) {
            return null;
        }
        if (!(literal.getParent() instanceof PsiNameValuePair pair)) {
            return null;
        }
        String name = pair.getName();
        if (name == null) {
            // @Foo("x") 같은 단일값 형태. 우리가 다루는 속성은 모두 이름을 명시하므로 대상이 아니다.
            return null;
        }
        PsiElement parameterList = pair.getParent();
        if (parameterList == null || !(parameterList.getParent() instanceof PsiAnnotation annotation)) {
            return null;
        }
        return new AnnotationAttribute(annotation, name, value);
    }
}
