package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.SmartPointerManager;
import io.github.jeongdonghee.lombokbuilderlinker.InplaceRenameTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * 편집기 안에서 바로 고치는 in-place 이름 변경.
 *
 * <p>여기서 못 박는 것은 세 가지다.
 * <ol>
 *   <li>템플릿을 끝까지 태웠을 때 문자열과 호출부가 함께 바뀐다 — 실제 코드 경로
 *       ({@link BuilderNameInplaceRename#startFromNameString}) 를 그대로 태운다.</li>
 *   <li><b>호출부는 편집을 시작하기 전에 붙잡아 두어야 한다.</b> 문자열이 바뀌면 Lombok 이 새 이름으로
 *       멤버를 다시 만들어내고, 그 시점에는 옛 이름을 부르던 호출부를 더 이상 찾을 수 없다.
 *       이 순서가 뒤집히면 조용히 아무것도 안 고치게 되므로 회귀 테스트로 남긴다.</li>
 *   <li><b>되돌리기가 한 단계여야 한다.</b> 안 묶으면 ⌘Z 한 번에 한쪽만 돌아가 코드가 깨진 중간
 *       상태가 남는다.</li>
 * </ol>
 */
public class BuilderNameInplaceRenameTest extends InplaceRenameTestCase {

    /** 템플릿을 끝까지 태우면 문자열과 호출부가 함께 바뀐다. */
    public void testFinishingTheInplaceSessionUpdatesStringAndCallSites() {
        configureExactCase();
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { ExactCase.historyChannelBuilder().channelName("a").build(); }
                void two() { ExactCase.historyChannelBuilder().channelName("b").build(); }
            }
            """);

        startInplace();
        myFixture.type("historyBuilder");
        finishTemplate();

        assertTrue("애노테이션 문자열이 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("builderMethodName = \"historyBuilder\""));

        String caller = fileText("Caller.java");
        assertFalse("옛 이름이 호출부에 남았다:\n" + caller, caller.contains("historyChannelBuilder"));
        assertEquals("두 호출부 모두 새 이름이어야 한다", 2, countOf(caller, "historyBuilder()"));
    }

    /**
     * 되돌리기(⌘Z) 한 번이 <b>문자열과 호출부를 함께</b> 되돌려야 한다.
     *
     * <p>안 묶어 두면 편집기 편집과 호출부 수정이 별개 명령이 되어, 한 번 되돌렸을 때 한쪽만 돌아가고
     * 코드가 깨진 중간 상태가 남는다.
     */
    public void testSingleUndoRevertsBothTheStringAndTheCallSites() {
        configureExactCase();
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { ExactCase.historyChannelBuilder().channelName("a").build(); }
                void two() { ExactCase.historyChannelBuilder().channelName("b").build(); }
            }
            """);

        startInplace();
        myFixture.type("historyBuilder");
        finishTemplate();

        // 먼저 실제로 바뀌었는지 확인해 둔다 — 안 바뀐 상태를 "되돌려졌다"고 착각하지 않도록.
        assertTrue(myFixture.getFile().getText().contains("\"historyBuilder\""));
        assertEquals(2, countOf(fileText("Caller.java"), "historyBuilder()"));

        undoInCurrentEditor();

        assertTrue("되돌리기 한 번으로 애노테이션 문자열이 돌아와야 한다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("\"historyChannelBuilder\""));
        String caller = fileText("Caller.java");
        assertFalse("호출부에 새 이름이 남았다:\n" + caller, caller.contains("historyBuilder()"));
        assertEquals("호출부도 함께 돌아와야 한다", 2, countOf(caller, "historyChannelBuilder()"));
    }

    /**
     * <b>호출부 파일에서</b> 되돌려도 애노테이션 문자열까지 함께 돌아와야 한다.
     *
     * <p>되돌리기는 기본적으로 편집기 단위다 — 그 파일에 영향을 준 명령만 되돌린다. 그래서 이름을
     * 바꾼 뒤 호출부로 이동해 ⌘Z 를 누르면 호출부만 옛 이름으로 돌아가고 문자열은 새 이름으로 남아,
     * 코드가 깨진 상태가 된다. 명령을 전역으로 표시해 막는다.
     */
    public void testUndoFromTheCallSiteFileAlsoRevertsTheString() {
        configureExactCase();
        PsiFile caller = myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { ExactCase.historyChannelBuilder().channelName("a").build(); }
                void two() { ExactCase.historyChannelBuilder().channelName("b").build(); }
            }
            """);

        startInplace();
        myFixture.type("historyBuilder");
        finishTemplate();

        // 이름을 바꾼 뒤 호출부 파일로 이동해서 되돌린다 — 신고된 그 순서.
        myFixture.openFileInEditor(caller.getVirtualFile());
        undoInCurrentEditor();

        assertEquals("호출부가 돌아와야 한다", 2, countOf(fileText("Caller.java"), "historyChannelBuilder()"));
        assertTrue("애노테이션 문자열도 함께 돌아와야 한다:\n" + fileText("ExactCase.java"),
            fileText("ExactCase.java").contains("\"historyChannelBuilder\""));
    }

    /** 편집을 시작하면 값이 선택된 채로 멈춘다 — 바로 덮어 쓸 수 있어야 한다. */
    public void testInplaceSessionStartsWithCurrentNameSelected() {
        configureExactCase();

        startInplace();

        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull("템플릿이 시작되지 않았다", state);
        assertEquals("현재 이름이 선택된 상태여야 한다", "historyChannelBuilder",
            myFixture.getEditor().getSelectionModel().getSelectedText());

        finishTemplate();
    }

    /** 아무것도 바꾸지 않고 끝내면 호출부도 그대로다. */
    public void testFinishingWithoutChangingLeavesEverythingAlone() {
        configureExactCase();
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { ExactCase.historyChannelBuilder().channelName("a").build(); }
            }
            """);

        startInplace();
        finishTemplate();

        assertTrue(myFixture.getFile().getText().contains("builderMethodName = \"historyChannelBuilder\""));
        assertTrue("호출부가 그대로여야 한다", fileText("Caller.java").contains("historyChannelBuilder()"));
    }

    /**
     * 순서 계약: 문자열을 먼저 바꾸고 나면 호출부를 찾을 수 없다.
     *
     * <p>그래서 {@code start} 는 편집을 시작하기 <b>전에</b> 호출부를 붙잡아 둔다.
     * 미리 붙잡은 포인터는 문자열이 바뀐 뒤에도 그대로 쓸 수 있다는 것까지 함께 확인한다.
     */
    public void testCallSitesMustBeCapturedBeforeTheStringChanges() {
        configureExactCase();
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void one() { ExactCase.historyChannelBuilder().channelName("a").build(); }
            }
            """);
        PsiLiteralExpression literal = renamableLiteral();
        assertNotNull(literal);

        List<BuilderNameRename.CapturedCallSite> captured = new ArrayList<>();
        SmartPointerManager pointers = SmartPointerManager.getInstance(getProject());
        for (BuilderNameRename.CallSite callSite : BuilderNameRename.callSites(literal)) {
            captured.add(new BuilderNameRename.CapturedCallSite(
                pointers.createSmartPsiElementPointer(callSite.element()), callSite.suffix()));
        }
        assertEquals("편집 전에는 호출부를 찾을 수 있어야 한다", 1, captured.size());

        // 템플릿 편집이 문서에 남기는 결과와 같은 상태를 만든다.
        WriteCommandAction.writeCommandAction(getProject(), myFixture.getFile())
            .run(() -> ElementManipulators.handleContentChange(literal, "historyBuilder"));

        assertTrue("문자열이 바뀐 뒤에는 옛 호출부를 찾을 수 없어야 한다(그래서 미리 모아 둔다)",
            BuilderNameRename.callSites(renamableLiteral()).isEmpty());

        WriteCommandAction.writeCommandAction(getProject())
            .run(() -> BuilderNameRename.renameCallSites(captured, "historyBuilder"));

        assertTrue("미리 모아 둔 포인터로는 고칠 수 있어야 한다:\n" + fileText("Caller.java"),
            fileText("Caller.java").contains("historyBuilder()"));
    }

    /**
     * <b>접두사 이름 변경</b> — 호출부마다 이름이 다르게 바뀐다.
     *
     * <p>{@code with} → {@code set} 이면 {@code withName} 은 {@code setName} 으로,
     * {@code withCount} 는 {@code setCount} 로 가야 한다. 하나의 새 이름을 전부에 붙이면 안 된다.
     */
    public void testRenamingSetterPrefixRewritesEachSetterWithItsOwnName() {
        enableTemplates();
        myFixture.configureByText("Prefixed.java", """
            import lombok.Builder;
            public class Prefixed {
                private final String name;
                private final int count;

                @Builder(setterPrefix = "wi<caret>th")
                public Prefixed(String name, int count) { this.name = name; this.count = count; }
            }
            """);
        myFixture.addFileToProject("PrefixCaller.java", """
            public class PrefixCaller {
                void one() {
                    Prefixed.builder()
                        .withName("a")
                        .withCount(1)
                        .build();
                }
                void two() { Prefixed.builder().withName("b").build(); }
            }
            """);

        startInplace();
        myFixture.type("set");
        finishTemplate();

        assertTrue("애노테이션의 접두사가 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("setterPrefix = \"set\""));
        String caller = fileText("PrefixCaller.java");
        assertEquals("withName 두 곳이 setName 이어야 한다: " + caller, 2, countOf(caller, "setName("));
        assertEquals("withCount 한 곳이 setCount 여야 한다: " + caller, 1, countOf(caller, "setCount("));
        assertFalse("옛 접두사가 남았다: " + caller, caller.contains("with"));
    }

    /** 접두사 변경도 되돌리기 한 번으로 두 파일이 함께 돌아와야 한다. */
    public void testUndoRevertsAPrefixRename() {
        enableTemplates();
        myFixture.configureByText("Prefixed.java", """
            import lombok.Builder;
            public class Prefixed {
                private final String name;

                @Builder(setterPrefix = "wi<caret>th")
                public Prefixed(String name) { this.name = name; }
            }
            """);
        myFixture.addFileToProject("PrefixCaller.java", """
            public class PrefixCaller {
                void one() { Prefixed.builder().withName("a").build(); }
            }
            """);

        startInplace();
        myFixture.type("set");
        finishTemplate();
        assertTrue(fileText("PrefixCaller.java").contains("setName("));

        undoInCurrentEditor();

        assertTrue("접두사가 돌아오지 않았다:\n" + fileText("Prefixed.java"),
            fileText("Prefixed.java").contains("setterPrefix = \"with\""));
        assertTrue("호출부가 돌아오지 않았다:\n" + fileText("PrefixCaller.java"),
            fileText("PrefixCaller.java").contains("withName("));
    }

    // ---------- 도우미 ----------

    private void configureExactCase() {
        enableTemplates();
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "history<caret>ChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
    }

    private void startInplace() {
        PsiLiteralExpression literal = renamableLiteral();
        assertNotNull("이름 변경 대상 문자열을 찾지 못했다", literal);
        String current = BuilderNameRename.currentName(literal);
        assertNotNull(current);
        assertTrue("in-place 편집을 시작하지 못했다",
            BuilderNameInplaceRename.startFromNameString(getProject(), myFixture.getEditor(), literal, current));
    }

    private PsiLiteralExpression renamableLiteral() {
        return BuilderNameRename.renamableLiteralAt(
            myFixture.getFile().findElementAt(myFixture.getCaretOffset()));
    }
}
