package io.github.jeongdonghee.lombokbuilderlinker;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 편집기 안에서 진행되는 인라인 편집(라이브 템플릿) 세션을 <b>실제 IDE 와 같은 조건으로</b> 태우는
 * 테스트용 베이스.
 *
 * <p>여기 있는 {@link #finishTemplate()} 의 명령 중첩이 이 파일의 존재 이유다. 그것 없이 테스트하면
 * 되돌리기 동작이 실제보다 잘 되는 것처럼 보여 <b>거짓 통과</b>한다. 지우지 말 것.
 */
public abstract class InplaceRenameTestCase extends LombokTestCase {

    /** 템플릿은 테스트에서 기본적으로 비활성이다. 켜 두면 편집 세션을 실제처럼 태울 수 있다. */
    protected void enableTemplates() {
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }

    /**
     * 템플릿을 끝낸다 — <b>실제 IDE 와 같은 조건으로</b>.
     *
     * <p>Enter 로 인라인 편집을 끝내면 완료 콜백이 편집기 액션의 명령 안에서 실행된다
     * ("Go to Next Code Template Tab"). 그 중첩을 재현하지 않으면 우리 변경이 별개 명령으로 남아
     * 되돌리기가 실제보다 잘 동작하는 것처럼 보인다. 콜백은 그 명령 밖으로 미뤄져 실행되므로
     * 이벤트 큐도 비워 준다.
     */
    protected void finishTemplate() {
        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull("템플릿이 시작되지 않았다", state);
        CommandProcessor.getInstance().executeCommand(getProject(),
            () -> state.gotoEnd(false), "Go to Next Code Template Tab", null);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    /**
     * 현재 편집기에서 되돌리기 — 확인 대화상자에 "예"로 답한다.
     *
     * <p>대화상자는 <b>정확히 한 번</b> 떠야 하고, 그 문구에 우리 명령 이름이 보여야 한다. 이 두 가지가
     * 함께 검증하는 것: 확인 정책이 실제로 걸려 있다는 것, 그리고 되돌리는 대상이 우리 명령이라는 것.
     * 이름이 다르면 템플릿을 끝낸 편집기 액션 같은 남의 명령을 되돌리고 있다는 뜻이다
     * (예전에 "Undo Go to Next Code Template Tab?" 이 뜨던 상태).
     *
     * <p>되돌리기는 문서를 고친다. PSI 로 확인하려면 커밋이 필요하다 — 커밋 없이 읽으면 옛 PSI 가
     * 나와서 "되돌아가지 않았다"고 잘못 읽는다.
     */
    protected void undoInCurrentEditor() {
        List<String> asked = new ArrayList<>();
        TestDialogManager.setTestDialog(message -> {
            asked.add(message);
            return TestDialog.OK.show(message);
        });
        try {
            myFixture.performEditorAction(IdeActions.ACTION_UNDO);
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT);
        }
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        assertEquals("되돌리기 확인이 한 번 떠야 한다: " + asked, 1, asked.size());
        assertTrue("확인 문구에 우리 명령 이름이 보여야 한다: " + asked.get(0),
            asked.get(0).contains("Rename Builder Member"));
    }

    protected String fileText(@NotNull String name) {
        VirtualFile file = myFixture.findFileInTempDir(name);
        assertNotNull(name + " 을 찾지 못했다", file);
        PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
        assertNotNull(name + " 의 PSI 를 찾지 못했다", psiFile);
        return psiFile.getText();
    }

    protected static int countOf(@NotNull String haystack, @NotNull String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
