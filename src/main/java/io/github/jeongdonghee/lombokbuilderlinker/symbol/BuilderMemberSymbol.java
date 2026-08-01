package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.symbol.SearchTargetSymbol;
import com.intellij.model.Pointer;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import io.github.jeongdonghee.lombokbuilderlinker.reference.AnnotationAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Lombok 이 만들어낼 빌더 멤버 하나를 가리키는 심볼.
 *
 * <p>왜 심볼이 필요한가: 이 멤버는 소스에 실체가 없다. 이름을 정하는 애노테이션 문자열과 그것을
 * 쓰는 호출부만 존재하고, 그 사이의 메서드는 Lombok 이 만든 합성 멤버다. PSI 요소를 기준으로
 * 삼으면 "선언"으로 취급받을 실체가 없어 Show Usages 창도, 기본 이름 변경도 걸리지 않는다.
 * 심볼은 PSI 에 묶이지 않는 의미 단위라서 그 빈자리를 메울 수 있다.
 *
 * <p><b>PSI 를 들고 있지 않다</b> — 클래스의 정규화된 이름과 멤버 이름, 종류만으로 신원을 정한다.
 * 그래서 {@link Pointer#hardPointer} 로 그대로 들고 다녀도 안전하다(심볼은 읽기 액션 하나 안에서만
 * 유효해야 하는데, 값만 담고 있으면 그 제약이 문제되지 않는다).
 */
public final class BuilderMemberSymbol implements SearchTargetSymbol {

    /** 어떤 이름 속성이 만들어낸 멤버인가. */
    public enum Kind {
        /** {@code builderMethodName} — 빌더를 얻는 static 진입 메서드. */
        BUILDER_METHOD,
        /** {@code buildMethodName} — 빌더 클래스 안의 build 메서드. */
        BUILD_METHOD,
        /** {@code builderClassName} — 생성되는 빌더 클래스. */
        BUILDER_CLASS,
        /**
         * {@code setterPrefix} — 접두사 하나가 생성된 세터 <b>여러 개</b>에 대응한다(1:N).
         *
         * <p>다른 종류와 달리 {@link #memberName()} 이 멤버 이름이 아니라 <b>접두사</b>다 —
         * 호출부에 적히는 이름은 {@code withName} · {@code withCount} 다. 그래서 사용처는
         * {@code BuilderMemberUsageSearcher} 가 세터를 하나씩 찾아 모은다.
         */
        SETTER_PREFIX
    }

    private final String hostClassName;
    private final String memberName;
    private final Kind kind;

    private BuilderMemberSymbol(@NotNull String hostClassName, @NotNull String memberName, @NotNull Kind kind) {
        this.hostClassName = hostClassName;
        this.memberName = memberName;
        this.kind = kind;
    }

    /**
     * 애노테이션의 이름 속성 자리에서 심볼을 만든다.
     * 우리가 다루는 속성이 아니거나 담은 클래스를 알 수 없으면 {@code null}.
     */
    public static @Nullable BuilderMemberSymbol of(@Nullable AnnotationAttribute attribute) {
        if (attribute == null || attribute.value().isEmpty()) {
            return null;
        }
        Kind kind = kindOf(attribute);
        if (kind == null) {
            return null;
        }
        BuilderTarget target = BuilderTarget.ofAnnotation(attribute.annotation());
        if (target == null) {
            return null;
        }
        // build 메서드와 세터는 빌더 클래스 안에 생긴다. 진입 메서드와 빌더 클래스는 바깥 클래스에 생긴다.
        PsiClass host = kind == Kind.BUILD_METHOD || kind == Kind.SETTER_PREFIX
            ? target.findBuilderClass()
            : target.hostClass();
        String hostName = host == null ? null : host.getQualifiedName();
        if (hostName == null) {
            // 익명·지역 클래스처럼 정규화된 이름이 없으면 신원을 정할 수 없다 — 손대지 않는다.
            return null;
        }
        return new BuilderMemberSymbol(hostName, attribute.value(), kind);
    }

    private static @Nullable Kind kindOf(@NotNull AnnotationAttribute attribute) {
        PsiAnnotation annotation = attribute.annotation();
        if (!LombokAnnotations.isBuilderAnnotation(LombokAnnotations.qualifiedName(annotation))) {
            return null;
        }
        return switch (attribute.attributeName()) {
            case LombokAnnotations.ATTR_BUILDER_METHOD_NAME -> Kind.BUILDER_METHOD;
            case LombokAnnotations.ATTR_BUILD_METHOD_NAME -> Kind.BUILD_METHOD;
            case LombokAnnotations.ATTR_BUILDER_CLASS_NAME -> Kind.BUILDER_CLASS;
            case LombokAnnotations.ATTR_SETTER_PREFIX -> Kind.SETTER_PREFIX;
            default -> null;
        };
    }

    /** 이 멤버가 놓이는 클래스의 정규화된 이름. */
    public @NotNull String hostClassName() {
        return hostClassName;
    }

    /**
     * 생성될 멤버의 이름. 참조 검색에서 인덱스 조회 문자열로 쓴다.
     * {@link Kind#SETTER_PREFIX} 일 때만 이름이 아니라 <b>접두사</b>다.
     */
    public @NotNull String memberName() {
        return memberName;
    }

    /** 사용처 창 머리에 보일 이름. 접두사는 이름이 아니므로 그렇게 보이도록 적는다. */
    public @NotNull String displayName() {
        return kind == Kind.SETTER_PREFIX ? memberName + "*" : memberName;
    }

    public @NotNull Kind kind() {
        return kind;
    }

    /**
     * 이 심볼을 찾기 대상으로 내놓는다 — Show Usages 창이 여기서 나온다.
     * {@code SearchTargetSymbol} 을 구현하면 확장점 등록 없이 플랫폼이 알아서 집어간다.
     */
    @Override
    public @NotNull SearchTarget getSearchTarget() {
        return new BuilderMemberSearchTarget(this);
    }

    // 이름 변경은 이 심볼로 처리하지 않는다. RenameableSymbol 구현과
    // rename.symbolRenameTargetFactory 등록을 둘 다 실측했으나 자바 파일에서는 둘 다 걸리지 않았다
    // ("this element cannot be renamed") — 자바의 기본 이름 변경 처리기가 캐럿을 먼저 가져간다.
    // 그래서 ⇧F6 은 reference 패키지의 RenameHandler + in-place 템플릿이 맡는다.

    @Override
    public @NotNull Pointer<BuilderMemberSymbol> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BuilderMemberSymbol symbol
            && hostClassName.equals(symbol.hostClassName)
            && memberName.equals(symbol.memberName)
            && kind == symbol.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostClassName, memberName, kind);
    }

    @Override
    public String toString() {
        return "BuilderMemberSymbol(" + hostClassName + "#" + memberName + ", " + kind + ")";
    }
}
