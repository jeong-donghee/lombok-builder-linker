package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.search.SearchService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * 선언 제공자와 자바 참조 검색기 — 이름 문자열을 "선언"으로 다루는 축.
 *
 * <p>여기서 확인하는 것: 문자열이 심볼을 선언한다고 알리는가, 그리고 그 심볼이 찾기 대상을
 * 내놓는가. 이 둘이 되면 Show Usages 창은 플랫폼 기본 동작으로 따라온다.
 *
 * <p>사용처를 실제로 찾아오는 쪽({@code BuilderMemberUsageSearcher})은
 * {@code BuilderMemberUsageSearchTest} 가 팝업 경로 그대로 확인한다.
 */
public class BuilderSymbolTest extends LombokTestCase {

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
        // 이름이 같지만 다른 클래스의 멤버 — 걸러져야 한다.
        myFixture.addFileToProject("Decoy.java", """
            public class Decoy {
                static Decoy historyChannelBuilder() { return null; }
                void use() { Decoy.historyChannelBuilder(); }
            }
            """);
    }

    /** 애노테이션 문자열이 심볼을 선언한다. */
    public void testAnnotationStringDeclaresTheSymbol() {
        PsiLiteralExpression literal = builderMethodNameLiteral();

        Collection<? extends PsiSymbolDeclaration> declarations =
            new BuilderNameDeclarationProvider().getDeclarations(literal, 0);

        assertEquals("선언이 하나 나와야 한다", 1, declarations.size());
        PsiSymbolDeclaration declaration = declarations.iterator().next();
        assertSame("선언 요소는 그 문자열이어야 한다", literal, declaration.getDeclaringElement());
        assertTrue("심볼 타입이 다르다: " + declaration.getSymbol(),
            declaration.getSymbol() instanceof BuilderMemberSymbol);

        BuilderMemberSymbol symbol = (BuilderMemberSymbol) declaration.getSymbol();
        assertEquals("historyChannelBuilder", symbol.memberName());
        assertEquals("ExactCase", symbol.hostClassName());
        assertEquals(BuilderMemberSymbol.Kind.BUILDER_METHOD, symbol.kind());
    }

    /**
     * 심볼이 찾기 대상을 내놓는가. 이게 Show Usages 창의 조건이다.
     *
     * <p>사용처를 찾는 일은 위 테스트에서 이미 확인됐으므로, 여기서는 "찾기 대상으로 다뤄진다"와
     * 창에 보일 이름이 맞는지만 본다.
     */
    public void testSymbolProvidesSearchTargetForShowUsages() {
        BuilderMemberSymbol symbol = symbolOfBuilderMethodName();

        SearchTarget target = symbol.getSearchTarget();
        assertNotNull("찾기 대상이 없으면 Show Usages 창이 뜨지 않는다", target);

        TargetPresentation presentation = target.presentation();
        assertEquals("historyChannelBuilder", presentation.getPresentableText());
        assertEquals("ExactCase", presentation.getContainerText());
        assertNotNull("사용처 처리기가 있어야 한다", target.getUsageHandler());
    }

    /** 같은 심볼은 같은 찾기 대상이어야 한다 — 플랫폼이 대상을 값으로 비교한다. */
    public void testSearchTargetsOfEqualSymbolsAreEqual() {
        assertEquals(symbolOfBuilderMethodName().getSearchTarget(),
            symbolOfBuilderMethodName().getSearchTarget());
    }

    // 이름 변경은 이 심볼로 처리하지 않는다 — RenameableSymbol / rename.symbolRenameTargetFactory
    // 둘 다 자바 파일에서 걸리지 않는 것을 실측으로 확인했다. 그 검증은
    // reference 패키지의 BuilderNameInplaceRenameTest 에 있다.

    // ---------- 도우미 ----------

    private PsiLiteralExpression builderMethodNameLiteral() {
        PsiClass exactCase = JavaPsiFacade.getInstance(getProject())
            .findClass("ExactCase", GlobalSearchScope.projectScope(getProject()));
        assertNotNull("ExactCase 를 찾지 못했다", exactCase);
        PsiAnnotation annotation = exactCase.getConstructors()[0].getAnnotation("lombok.Builder");
        assertNotNull("@Builder 를 찾지 못했다", annotation);
        PsiElement value = annotation.findDeclaredAttributeValue("builderMethodName");
        assertTrue("문자열 리터럴이어야 한다: " + value, value instanceof PsiLiteralExpression);
        return (PsiLiteralExpression) value;
    }

    private BuilderMemberSymbol symbolOfBuilderMethodName() {
        Collection<? extends PsiSymbolDeclaration> declarations =
            new BuilderNameDeclarationProvider().getDeclarations(builderMethodNameLiteral(), 0);
        assertFalse("선언을 만들지 못했다", declarations.isEmpty());
        return (BuilderMemberSymbol) declarations.iterator().next().getSymbol();
    }

    private static String fileNameOf(@NotNull PsiElement element) {
        return element.getContainingFile() == null ? "(없음)" : element.getContainingFile().getName();
    }

    private static String describe(@NotNull Collection<PsiSymbolReference> references) {
        return references.stream()
            .map(reference -> fileNameOf(reference.getElement()) + ":" + reference.getElement().getText())
            .toList()
            .toString();
    }
}
