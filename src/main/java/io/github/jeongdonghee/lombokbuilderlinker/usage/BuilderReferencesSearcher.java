package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ReferencesSearch} 경로에도 같은 기여를 실어 보낸다.
 *
 * <p>{@link BuilderMethodReferencesSearcher} 와 둘 다 필요하다. 실측으로 확인한 사실:
 * {@code methodReferencesSearch} 에만 등록하면 실제 Find Usages 는 동작하지만
 * {@code ReferencesSearch.search(생성자)} 는 빈 결과이고, {@code referencesSearch} 에만 등록하면
 * 그 반대가 된다. <b>두 경로는 서로의 기여를 가져가지 않는다.</b>
 *
 * <p>독립이라는 사실이 곧 중복 걱정이 없다는 뜻이기도 하다 — 각 경로는 자기 확장점의 기여만 받으므로
 * 같은 호출부가 두 번 보고되지 않는다. 두 경로를 각각 검증하는 테스트가
 * {@code RealLombokUsageTest} 의 3·4단계다.
 */
public final class BuilderReferencesSearcher
    extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public BuilderReferencesSearcher() {
        super(true); // 읽기 액션 안에서 실행
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters parameters,
                            @NotNull Processor<? super PsiReference> consumer) {
        PsiElement searched = parameters.getElementToSearch();
        if (searched instanceof PsiMethod method) {
            BuilderCallSites.report(method, parameters.getEffectiveSearchScope(), consumer);
        }
    }
}
