package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.SmartPointerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 편집기 안에서 바로 이름을 고치는 방식(in-place). 대화상자를 띄우지 않는다.
 *
 * <p>플랫폼의 기본 in-place 이름 변경({@code VariableInplaceRenamer})은 이름을 가진
 * {@code PsiNamedElement} 를 요구한다. 여기서 바꾸는 것은 Lombok 이 만들어낼 <b>합성 멤버</b>의
 * 이름이라 그런 실체가 없다. 그래서 같은 사용감을 라이브 템플릿으로 직접 만든다 — 편집기의 한 구간을
 * 템플릿 변수 하나로 바꿔 넣으면, 그 자리가 선택된 채로 편집 가능해진다.
 *
 * <p>시작 자리는 <b>이름을 정하는 애노테이션 문자열</b> 하나뿐이다 — 호출부에서는 Lombok 의
 * 이름 변경 처리기와 자리가 겹쳐 처리기 선택 팝업이 뜬다({@link BuilderNameRenameHandler} 주석).
 *
 * <p>왜 {@code TemplateBuilder} 를 쓰지 않는가: 공개 API 인 {@code TemplateBuilder.run(editor, true)}
 * 는 편집이 끝나는 시점을 알려주지 않는다. 다른 파일의 호출부는 편집이 끝난 뒤에 고쳐야 하므로
 * 완료 콜백이 반드시 필요하고, 콜백을 받을 수 있는
 * {@code TemplateBuilderImpl.buildInlineTemplate()} 은 컴파일 클래스패스에 없다(내부 API).
 * 그래서 {@link TemplateManager#createTemplate} 으로 템플릿을 직접 만들고
 * {@link TemplateManager#startTemplate(Editor, Template, com.intellij.codeInsight.template.TemplateEditingListener)}
 * 로 완료를 받는다.
 *
 * <p><b>되돌리기.</b> {@link #propagate} 가 실제 변경을 <b>전역 명령 하나</b>로 수행한다. 그래야
 * 어느 파일에서 ⌘Z 를 눌러도 문자열과 호출부가 함께 돌아온다.
 * {@code StartMarkAction}/{@code FinishMarkAction} 으로 편집 전체를 묶는 방법은 쓰지 않는다 —
 * 그 블록은 편집기 하나에 묶여서, 호출부 파일에서 ⌘Z 를 누르면 "이 동작이 건드리는 파일이 이미
 * 바뀌었다"며 되돌리기가 거부된다(실측 확인).
 * 되돌리기 전에는 무엇을 되돌리는지 확인을 묻는다 — {@link WriteCommandAction} 은 정책을 지정하지
 * 않으면 아무것도 묻지 않으므로({@code DO_NOT_REQUEST_CONFIRMATION}) 명시적으로 요청한다.
 */
final class BuilderNameInplaceRename {

    private static final String COMMAND_NAME = "Rename Builder Member";
    private static final String VARIABLE = "LOMBOK_BUILDER_MEMBER_NAME";

    private BuilderNameInplaceRename() {}

    /**
     * 이름을 정하는 애노테이션 문자열 자리에서 편집을 시작한다.
     *
     * @return 시작했으면 {@code true}. 값 구간을 찾지 못하면 {@code false}(이때는 아무것도 바꾸지 않는다).
     */
    static boolean startFromNameString(@NotNull Project project,
                                       @NotNull Editor editor,
                                       @NotNull PsiLiteralExpression literal,
                                       @NotNull String currentName) {
        TextRange valueRange = absoluteValueRange(literal);
        if (valueRange == null) {
            return false;
        }
        return start(project, editor, literal.getContainingFile(), valueRange, currentName,
            BuilderNameRename.callSites(literal));
    }

    private static boolean start(@NotNull Project project,
                                 @NotNull Editor editor,
                                 @NotNull PsiFile file,
                                 @NotNull TextRange editRange,
                                 @NotNull String currentName,
                                 @NotNull List<BuilderNameRename.CallSite> callSiteElements) {
        if (currentName.isEmpty() || editRange.isEmpty()) {
            return false;
        }
        Document document = editor.getDocument();
        SmartPointerManager pointers = SmartPointerManager.getInstance(project);

        // 이름을 고치기 시작하면 Lombok 이 새 이름으로 멤버를 다시 만들어내고, 옛 이름으로는
        // 호출부를 찾을 수 없게 된다. 그래서 편집을 시작하기 전에 호출부를 붙잡아 둔다.
        List<BuilderNameRename.CapturedCallSite> callSites = new ArrayList<>();
        for (BuilderNameRename.CallSite callSite : callSiteElements) {
            callSites.add(new BuilderNameRename.CapturedCallSite(
                pointers.createSmartPsiElementPointer(callSite.element()), callSite.suffix()));
        }
        int editStart = editRange.getStartOffset();
        Expression initialValue = new ConstantExpression(currentName);

        Template template = TemplateManager.getInstance(project).createTemplate("", "");
        template.setToReformat(false);
        template.setToIndent(false);
        template.setToShortenLongNames(false);
        // isAlwaysStopAt=true 라서 넣은 값이 선택된 상태로 멈춘다 — 바로 덮어 쓰면 되는 그 사용감.
        template.addVariable(VARIABLE, initialValue, initialValue, true);

        WriteCommandAction.writeCommandAction(project, file)
            .withName(COMMAND_NAME)
            .run(() -> {
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
                document.deleteString(editRange.getStartOffset(), editRange.getEndOffset());
                editor.getCaretModel().moveToOffset(editStart);
            });

        // 편집 구간을 따라다니는 표시자. 템플릿이 값을 넣은 <b>뒤에</b> 만들어야 하는데 완료 콜백은
        // 시작할 때 넘겨야 하므로, 콜백이 나중에 읽도록 상자에 담아 전달한다(콜백은 사용자가 편집을
        // 끝낸 뒤에 불리므로 그때는 이미 채워져 있다).
        AtomicReference<RangeMarker> edited = new AtomicReference<>();

        TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter() {
            @Override
            public void templateFinished(@NotNull Template finished, boolean brokenOff) {
                // 이 콜백은 템플릿을 끝낸 편집기 액션의 <b>명령 안에서</b> 실행된다(Enter 면
                // "Go to Next Code Template Tab"). 그 안에서 문서를 고치면 우리 변경이 그 명령에
                // 합쳐져 버려서 ⌘Z 한 번으로 되돌아가지 않는다. 그래서 명령 밖으로 미뤄
                // 우리 명령으로 실행한다.
                //
                // brokenOff(Esc) 여도 적용한다. 편집한 글자는 이미 문서에 남아 있으므로, 여기서 손을
                // 떼면 호출부만 옛 이름으로 남아 코드가 깨진 상태가 된다.
                ApplicationManager.getApplication().invokeLater(
                    () -> propagate(project, editor, file, edited.get(), currentName, callSites),
                    project.getDisposed());
            }
        });

        // greedy 로 두면 앞뒤로 이어 치는 글자까지 구간에 들어온다 — 편집이 끝난 시점의 값을 알아내는
        // 가장 확실한 방법이다(TemplateState 에 변수 구간을 묻는 것은 내부 API 다).
        RangeMarker marker = document.createRangeMarker(editStart, editStart + currentName.length());
        marker.setGreedyToLeft(true);
        marker.setGreedyToRight(true);
        edited.set(marker);
        return true;
    }

    /**
     * 편집이 끝난 뒤 실제 이름 변경을 적용한다.
     *
     * <p><b>인라인 편집은 새 이름을 받아내는 UI 일 뿐이다.</b> 편집기에 남은 글자를 일단 원래 이름으로
     * 되돌리고, 애노테이션과 호출부를 <b>한 번의 전역 명령</b>으로 함께 바꾼다. 플랫폼의 in-place
     * 리팩터링도 같은 구조다.
     *
     * <p>왜 이렇게까지 하는가: 되돌리기 스택은 <b>파일별</b>이다. 템플릿 타이핑(이 파일)과 호출부 수정
     * (다른 파일)이 서로 다른 명령으로 남으면, 호출부 파일에서 ⌘Z 를 눌렀을 때 플랫폼이
     * "이 동작이 건드리는 파일이 이미 바뀌었다"며 되돌리기를 거부한다. 관련 파일의 변경이 <b>같은
     * 명령 하나</b>에 들어 있어야 어느 쪽에서 눌러도 함께 돌아온다.
     */
    private static void propagate(@NotNull Project project,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @Nullable RangeMarker edited,
                                  @NotNull String currentName,
                                  @NotNull List<BuilderNameRename.CapturedCallSite> callSites) {
        // 미뤄서 실행되므로 그 사이에 편집기가 닫혔을 수 있다.
        if (editor.isDisposed() || !file.isValid() || edited == null || !edited.isValid()) {
            return;
        }
        Document document = editor.getDocument();
        String typed = document.getText(new TextRange(edited.getStartOffset(), edited.getEndOffset()));
        int start = edited.getStartOffset();
        int end = edited.getEndOffset();
        edited.dispose();

        // 생성될 멤버의 이름이므로 자바 식별자여야 한다. 아니면 원래 이름으로 되돌리고 끝낸다 —
        // 컴파일되지 않는 이름을 애노테이션과 호출부까지 퍼뜨리는 것보다 안전하다.
        boolean applies = !typed.equals(currentName)
            && PsiNameHelper.getInstance(project).isIdentifier(typed);

        WriteCommandAction.writeCommandAction(project, file)
            .withName(COMMAND_NAME)
            .run(() -> document.replaceString(start, end, currentName));

        if (!applies) {
            return;
        }
        WriteCommandAction.writeCommandAction(project, file)
            .withName(COMMAND_NAME)
            // 되돌리기 전에 무엇을 되돌리는지 확인을 묻는다. 지정하지 않으면 WriteCommandAction 은
            // DO_NOT_REQUEST_CONFIRMATION 으로 실행해 아무것도 묻지 않는다(플랫폼 바이트코드 확인).
            // 이 명령은 여러 파일을 한꺼번에 되돌리므로 이름을 보여주고 확인받는 편이 낫다.
            .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
            .run(() -> {
                CommandProcessor commands = CommandProcessor.getInstance();
                // 이 명령이 여러 파일에 걸쳐 있음을 알린다 — 이게 있어야 호출부 파일에서 ⌘Z 를 눌러도
                // 애노테이션까지 함께 돌아온다.
                commands.markCurrentCommandAsGlobal(project);
                // 편집 메뉴의 "Undo ..." 에 보이는 이름. 표시하지 않으면 템플릿을 끝낸 편집기 액션의
                // 이름("Go to Next Code Template Tab")이 그대로 노출된다.
                commands.setCurrentCommandName(COMMAND_NAME);

                PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
                // 이름을 정하는 문자열은 문서로 직접 고친다. 그 자리의 PSI 는 템플릿이 지웠다 다시 넣는
                // 사이에 새 요소로 바뀌므로 미리 잡아 둔 포인터로는 못 고친다.
                document.replaceString(start, start + currentName.length(), typed);
                documents.commitAllDocuments();
                BuilderNameRename.renameCallSites(callSites, typed);
            });
    }

    /** 따옴표를 제외한 값 구간을 문서 좌표로 돌려준다. */
    private static @Nullable TextRange absoluteValueRange(@NotNull PsiLiteralExpression literal) {
        TextRange relative = ElementManipulators.getValueTextRange(literal);
        if (relative.isEmpty()) {
            return null;
        }
        return relative.shiftRight(literal.getTextRange().getStartOffset());
    }

    /**
     * 계산 없이 고정된 문자열을 내놓는 템플릿 표현식.
     *
     * <p>플랫폼에 {@code ConstantNode} 라는 같은 역할의 클래스가 있지만 내부 API(impl) 라서 직접 만든다.
     * {@link Expression} 은 인터페이스가 아니라 추상 클래스다.
     */
    private static final class ConstantExpression extends Expression {

        private final String text;

        private ConstantExpression(String text) {
            this.text = text;
        }

        @Override
        public Result calculateResult(ExpressionContext context) {
            return new TextResult(text);
        }

        @Override
        public Result calculateQuickResult(ExpressionContext context) {
            return calculateResult(context);
        }

        @Override
        public LookupElement @Nullable [] calculateLookupItems(ExpressionContext context) {
            return null;
        }
    }
}
