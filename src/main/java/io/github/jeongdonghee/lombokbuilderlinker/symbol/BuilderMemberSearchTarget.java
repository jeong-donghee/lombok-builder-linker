package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.UsageHandler;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.presentation.TargetPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * 빌더 멤버 심볼을 "찾기 대상"으로 내놓는다 — 이게 <b>Show Usages 창</b>을 만드는 조각이다.
 *
 * <p>왜 이것만으로 창이 뜨는가: 사용처를 찾는 일은 이미
 * {@link BuilderMemberUsageSearcher} 가 하고 있고, 애노테이션 문자열이 선언이라는 것도
 * {@link BuilderNameDeclarationProvider} 가 알려준다. 남은 것은 "이 심볼을 찾기 대상으로 다뤄도
 * 된다"고 플랫폼에 알리는 것뿐이다. 그러면 선언 자리에서 하는 일(미리보기·그룹핑이 있는 그 창)을
 * 플랫폼이 그대로 해준다.
 *
 * <p>대상을 <b>내놓는</b> 것은 확장점 등록이 필요 없다 — {@code BuilderMemberSymbol} 이
 * {@code SearchTargetSymbol} 을 구현해 자기 자신이 이 대상을 내놓는다. 다만 그 대상의 사용처를
 * <b>찾는</b> 일은 별개다: {@code searcher} 확장점에 등록한 {@link BuilderMemberUsageSearcher} 가
 * 맡는다. 그게 없으면 팝업은 뜨지만 비어 있다(실측).
 */
final class BuilderMemberSearchTarget implements SearchTarget {

    private final BuilderMemberSymbol symbol;

    BuilderMemberSearchTarget(@NotNull BuilderMemberSymbol symbol) {
        this.symbol = symbol;
    }

    /** 사용처를 실제로 찾는 {@link BuilderMemberUsageSearcher} 가 이 심볼을 필요로 한다. */
    @NotNull BuilderMemberSymbol symbol() {
        return symbol;
    }

    @Override
    public @NotNull Pointer<BuilderMemberSearchTarget> createPointer() {
        // 심볼이 값만 담고 있어 PSI 무효화와 무관하다.
        return Pointer.hardPointer(this);
    }

    /** 창 머리와 목록에 보이는 이름. 어느 클래스의 멤버인지도 함께 보여준다. */
    @Override
    public @NotNull TargetPresentation presentation() {
        return TargetPresentation.builder(symbol.displayName())
            .containerText(symbol.hostClassName())
            .presentation();
    }

    @Override
    public @NotNull UsageHandler getUsageHandler() {
        return UsageHandler.createEmptyUsageHandler(symbol.displayName());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BuilderMemberSearchTarget target && symbol.equals(target.symbol);
    }

    @Override
    public int hashCode() {
        return symbol.hashCode();
    }
}
