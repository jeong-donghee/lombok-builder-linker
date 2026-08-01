package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * 자바 Find Usages 가 실제로 쓰는 검색 경로에 빌더 호출부를 실어 보낸다.
 *
 * <p>처음에는 {@code referencesSearch} 확장점에 등록했는데, {@code ReferencesSearch.search(생성자)}
 * 를 직접 부르는 테스트는 통과하면서 <b>실제 IDE 의 Find Usages 는 계속 빈 결과</b>였다. 자바는
 * 메서드·생성자에 대해 {@link MethodReferencesSearch} 를 쓰고, 위임은 한 방향으로만 일어나기 때문이다
 * — {@code ReferencesSearch} 는 메서드 검색을 {@code MethodReferencesSearch} 로 넘기지만 그 반대는 없다.
 * 그래서 {@code referencesSearch} 에만 등록하면 UI 에는 아무 영향이 없다.
 *
 * <p>{@code methodReferencesSearch} 에 등록하면 두 경로가 모두 살아난다. 이 착각이 다시 생기지 않게
 * {@code RealLombokUsageTest} 가 픽스처의 Find Usages 를 직접 태워 검증한다.
 */
public final class BuilderMethodReferencesSearcher
    extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

    public BuilderMethodReferencesSearcher() {
        super(true); // 읽기 액션 안에서 실행
    }

    @Override
    public void processQuery(@NotNull MethodReferencesSearch.SearchParameters parameters,
                            @NotNull Processor<? super PsiReference> consumer) {
        BuilderCallSites.report(parameters.getMethod(), parameters.getEffectiveSearchScope(), consumer);
    }
}
