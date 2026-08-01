package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolDeclarationProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.jeongdonghee.lombokbuilderlinker.reference.AnnotationAttribute;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * 애노테이션의 이름 문자열이 빌더 멤버를 <b>선언</b>한다고 플랫폼에 알린다.
 *
 * <p>이게 이 전환의 핵심이다. 지금까지는 그 문자열을 <i>참조</i>로 만들었는데, 문자열의 실제 성격은
 * 참조가 아니라 선언이다 — 생성될 멤버의 이름을 여기서 정한다. 참조로 두면 ⌘+Click 이 "선언으로
 * 가기"로 동작해 갈 곳 없는 합성 멤버를 향하고, 후보가 여럿일 때 "Choose Declaration" 목록이 뜬다.
 * 선언으로 알리면 플랫폼이 선언 자리에서 하는 일(Show Usages 창, 이름 변경)을 그대로 해준다.
 */
public final class BuilderNameDeclarationProvider implements PsiSymbolDeclarationProvider {

    @Override
    public @NotNull Collection<? extends PsiSymbolDeclaration> getDeclarations(@NotNull PsiElement element,
                                                                              int offsetInElement) {
        PsiLiteralExpression literal = PsiTreeUtil.getParentOfType(element, PsiLiteralExpression.class, false);
        if (literal == null) {
            return List.of();
        }
        BuilderMemberSymbol symbol = BuilderMemberSymbol.of(AnnotationAttribute.of(literal));
        return symbol == null ? List.of() : List.of(new BuilderNameDeclaration(literal, symbol));
    }

    /** 문자열 리터럴 한 개가 심볼 한 개를 선언한다. 범위는 따옴표를 뺀 값 부분이다. */
    private record BuilderNameDeclaration(@NotNull PsiLiteralExpression literal,
                                          @NotNull BuilderMemberSymbol symbol) implements PsiSymbolDeclaration {

        @Override
        public @NotNull PsiElement getDeclaringElement() {
            return literal;
        }

        @Override
        public @NotNull TextRange getRangeInDeclaringElement() {
            return ElementManipulators.getValueTextRange(literal);
        }

        @Override
        public @NotNull Symbol getSymbol() {
            return symbol;
        }
    }
}
