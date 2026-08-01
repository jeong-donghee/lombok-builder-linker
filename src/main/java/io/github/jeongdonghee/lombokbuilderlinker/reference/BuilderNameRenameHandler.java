package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code @Builder} 가 만들어낼 멤버의 이름 변경(⇧F6)을 지원한다.
 *
 * <p>기본 이름 변경 리팩터링은 여기서 아무것도 하지 못한다 — 이름이 가리키는 대상이 Lombok 이
 * 만든 합성 멤버여서 이름을 바꿀 실체가 없기 때문이다. 그래서 애노테이션을 기준점으로 삼아,
 * 이름을 정하는 자리와 그 빌더를 쓰는 <b>호출부 전부</b>를 함께 고친다.
 *
 * <p>편집 방식은 대화상자가 아니라 편집기 안에서 바로 고치는 in-place 다 — 플랫폼의 기본 이름 변경과
 * 같은 사용감을 주기 위해서다. 실제 편집은 {@link BuilderNameInplaceRename} 이 맡는다.
 *
 * <p>지원 대상: {@code builderMethodName} · {@code buildMethodName} · {@code builderClassName} ·
 * {@code setterPrefix}. 마지막 것만 결이 다르다 — 접두사를 바꾸면 호출부마다 이름이 달라진다
 * ({@code withName} → {@code setName}). 그래서 호출부를 모을 때 접미사를 함께 들고 다닌다
 * ({@code BuilderNameRename.CallSite}).
 *
 * <p>{@code @Builder.ObtainVia} 는 여기서 다루지 않는다 — 그쪽은 직접 쓴 멤버를 가리키므로 평범한
 * 이름 변경이 이미 옳게 동작한다(그 멤버를 바꾸면 문자열이 따라온다).
 */
public final class BuilderNameRenameHandler implements RenameHandler, TitledHandler {

    /**
     * 처리기가 둘 이상 손을 들면 플랫폼이 이 이름으로 선택 팝업을 만든다
     * ({@code RenameHandlerRegistry.getHandlerTitle}). {@code TitledHandler} 를 구현하지 않으면
     * {@code toString()} 으로 떨어져 클래스명+해시가 그대로 보인다 — Lombok 쪽 처리기가 그 상태다.
     *
     * <p>평소에는 이 팝업이 뜨지 않아야 정상이다({@code RenameHandlerAmbiguityTest} 가 지킨다).
     * 이 이름은 그래도 새는 경우를 대비한 안전망이다.
     */
    @Override
    public @NotNull String getActionTitle() {
        return "Rename Lombok builder member";
    }

    /**
     * 이름을 정하는 애노테이션 문자열 위에서만 손을 든다.
     *
     * <p><b>호출부에서는 일부러 손을 들지 않는다.</b> 호출부의 캐럿 대상은 Lombok 합성 메서드이고,
     * Lombok 의 {@code LombokElementRenameVetoHandler} 가 바로 그 조건에서 손을 든다. 우리까지 손을 들면
     * {@code RenameHandlerRegistry} 가 표시 이름으로 둘을 모아 <b>선택 팝업</b>을 띄운다 — 그 목록에는
     * Lombok 쪽의 {@code toString()}(클래스명+해시)이 그대로 보인다. 플랫폼에 우선순위나 거부 수단은
     * 없다(바이트코드 확인: {@code doGetRenameHandlers} 는 {@code MemberInplaceRenameHandler} 하나만
     * 특별 취급한다).
     *
     * <p>그래서 이름 변경은 이름을 <b>정하는</b> 자리에서만 한다.
     */
    @Override
    public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
        return literalFrom(dataContext) != null;
    }

    @Override
    public boolean isRenaming(@NotNull DataContext dataContext) {
        return isAvailableOnDataContext(dataContext);
    }

    @Override
    public void invoke(@NotNull Project project,
                       @Nullable Editor editor,
                       @Nullable PsiFile file,
                       @Nullable DataContext dataContext) {
        if (dataContext == null) {
            return;
        }
        Editor target = editor != null ? editor : CommonDataKeys.EDITOR.getData(dataContext);
        if (target == null) {
            return;
        }
        PsiLiteralExpression literal = literalFrom(dataContext);
        if (literal == null) {
            return;
        }
        String current = BuilderNameRename.currentName(literal);
        if (current != null) {
            BuilderNameInplaceRename.startFromNameString(project, target, literal, current);
        }
    }

    @Override
    public void invoke(@NotNull Project project,
                       PsiElement @NotNull [] elements,
                       @Nullable DataContext dataContext) {
        invoke(project, null, null, dataContext);
    }

    /** 이름을 정하는 애노테이션 문자열 위인가. */
    private static @Nullable PsiLiteralExpression literalFrom(@NotNull DataContext dataContext) {
        PsiElement leaf = leafAtCaret(dataContext);
        return leaf == null ? null : BuilderNameRename.renamableLiteralAt(leaf);
    }

    private static @Nullable PsiElement leafAtCaret(@NotNull DataContext dataContext) {
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        if (editor == null || file == null) {
            return null;
        }
        return file.findElementAt(editor.getCaretModel().getOffset());
    }
}
