package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * <b>실제 Lombok 증강</b>을 상대로 한 진단 테스트.
 *
 * <p>다른 테스트들은 Lombok 이 만들어낼 멤버를 손으로 써서 검증한다. 그 방식으로는
 * {@code BuilderUsageTest} 가 통과했는데도 실제 IDE(2026-07-31, 샌드박스 IC 2024.3.1 +
 * Lombok 플러그인)에서는 생성자 Find Usages 가 빈 결과였다. 손으로 쓴 물리 메서드와
 * Lombok 이 만든 light 메서드가 다르게 동작한다는 뜻이다.
 *
 * <p>그래서 이 테스트는 단계를 <b>쪼개서</b> 어디서 끊기는지 지목한다. 세 단계를 각각 독립
 * 테스트로 두어 한 번 돌리면 세 결과를 모두 볼 수 있게 했다.
 * <ol>
 *   <li>Lombok 이 이름 바꾼 진입 메서드를 실제로 증강하는가</li>
 *   <li>그 증강 메서드 자체의 참조 검색은 되는가</li>
 *   <li>생성자 검색에 이 플러그인의 실행기가 기여하는가 ← 실패 지점으로 의심되는 곳</li>
 * </ol>
 */
public class RealLombokUsageTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;
                private final int retentionDays;

                @Builder(builderMethodName = "historyChannelBuilder")
                public ExactCase(String channelName, int retentionDays) {
                    this.channelName = channelName;
                    this.retentionDays = retentionDays;
                }
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() {
                    ExactCase built = ExactCase.historyChannelBuilder()
                        .channelName("history-1")
                        .retentionDays(7)
                        .build();
                }
            }
            """);
    }

    /** 전제 확인 — Lombok 플러그인이 테스트 환경에서 실제로 증강하고 있는지. 여기가 깨지면 나머지는 무의미하다. */
    public void testLombokAugmentsTheRenamedBuilderMethod() {
        PsiMethod[] entry = exactCase().findMethodsByName("historyChannelBuilder", false);
        assertEquals("Lombok 증강이 동작하지 않는다 — 이 테스트 환경에 Lombok 플러그인이 없다는 뜻",
            1, entry.length);
        assertFalse("증강된 메서드는 물리 요소가 아니어야 한다(light method)", entry[0].isPhysical());
    }

    /** 2단계 — 증강 메서드 자체의 참조 검색. 실제 IDE 에서 문자열 Find Usages 가 되므로 여기는 통과해야 한다. */
    public void testReferencesToAugmentedBuilderMethodAreFound() {
        PsiMethod[] entry = exactCase().findMethodsByName("historyChannelBuilder", false);
        assertEquals(1, entry.length);

        Collection<PsiReference> references = ReferencesSearch.search(entry[0], scope()).findAll();
        assertFalse("증강 메서드의 호출부를 찾지 못했다: " + describe(references), references.isEmpty());
    }

    /** 3단계 — 이 플러그인의 본체. 생성자를 찾을 때 빌더 호출부가 사용처로 보고되는가. */
    public void testReferencesToBuilderConstructorIncludeCallSites() {
        PsiMethod constructor = exactCase().getConstructors()[0];

        Collection<PsiReference> references = ReferencesSearch.search(constructor, scope()).findAll();
        assertFalse("생성자의 사용처로 빌더 호출부가 보고되지 않았다: " + describe(references),
            references.isEmpty());

        boolean fromCaller = references.stream().anyMatch(reference ->
            "Caller.java".equals(reference.getElement().getContainingFile().getName()));
        assertTrue("Caller.java 의 호출이 잡혀야 한다: " + describe(references), fromCaller);
    }

    /**
     * 4단계 — <b>실제 Find Usages 경로</b>. 3단계와 갈라지는 지점이다.
     *
     * <p>3단계({@code ReferencesSearch.search})는 통과하는데 실제 IDE 에서는 "no usages" 였다.
     * 자바의 Find Usages 는 메서드·생성자에 대해 {@code MethodReferencesSearch} 를 쓰고, 그쪽에는
     * {@code referencesSearch} 확장점 기여가 들어가지 않기 때문이다({@code ReferencesSearch} 는
     * 반대로 메서드 검색을 {@code MethodReferencesSearch} 에 위임하므로 한 방향만 통한다).
     *
     * <p>그래서 API 를 부르는 테스트는 통과하면서 기능은 동작하지 않는 상태가 됐다.
     * 이 테스트는 픽스처의 Find Usages 를 그대로 태워 그 착각이 다시 생기지 않게 못을 박는다.
     */
    public void testFindUsagesOnConstructorShowsBuilderCallSites() {
        PsiMethod constructor = exactCase().getConstructors()[0];

        Collection<UsageInfo> usages = myFixture.findUsages(constructor);
        assertFalse("Find Usages 에 빌더 호출부가 나와야 한다: " + usages, usages.isEmpty());

        boolean fromCaller = usages.stream().anyMatch(usage -> {
            PsiElement element = usage.getElement();
            return element != null && "Caller.java".equals(element.getContainingFile().getName());
        });
        assertTrue("Caller.java 의 호출이 나와야 한다: " + usages, fromCaller);
    }

    private PsiClass exactCase() {
        PsiClass found = JavaPsiFacade.getInstance(getProject()).findClass("ExactCase", scope());
        assertNotNull("ExactCase 를 찾지 못했다", found);
        return found;
    }

    private GlobalSearchScope scope() {
        return GlobalSearchScope.projectScope(getProject());
    }

    private static String describe(@NotNull Collection<PsiReference> references) {
        return references.isEmpty()
            ? "(0건)"
            : references.stream()
                .map(reference -> reference.getElement().getContainingFile().getName()
                    + ":" + reference.getElement().getText())
                .toList()
                .toString();
    }
}
