package io.github.jeongdonghee.lombokbuilderlinker;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * <b>실제 lombok 라이브러리</b>가 붙은 픽스처를 쓰는 테스트의 베이스.
 *
 * <p>왜 라이브러리여야 하는가: JetBrains 의 Lombok 지원 플러그인은 모듈 클래스패스에 lombok
 * 라이브러리가 있을 때만 빌더 멤버를 증강한다. 애노테이션을 소스 스텁으로만 넣으면 애노테이션
 * 자체는 해석되지만 <b>증강은 일어나지 않는다</b>(증강 메서드 조회가 0건). 실제 IDE 와 같은
 * 조건을 만들려면 라이브러리로 붙여야 한다.
 *
 * <p>{@code ProjectDescriptor} 가 {@link LightJavaCodeInsightFixtureTestCase} 의 protected
 * 중첩 클래스라 별도 유틸 클래스에서는 만들 수 없다. 그래서 서술자를 이 베이스 클래스 안에 둔다.
 *
 * <p>jar 경로는 하드코딩하지 않는다. 테스트 클래스패스의 {@code lombok.Builder} 로부터 그 클래스를
 * 담은 jar 를 되짚어 찾으므로 버전을 올려도 이 파일은 고칠 필요가 없다.
 */
public abstract class LombokTestCase extends LightJavaCodeInsightFixtureTestCase {

    protected static final LightProjectDescriptor JAVA_17_WITH_LOMBOK =
        new ProjectDescriptor(LanguageLevel.JDK_17) {
            @Override
            public void configureModule(@NotNull Module module,
                                        @NotNull ModifiableRootModel model,
                                        @NotNull ContentEntry contentEntry) {
                super.configureModule(module, model, contentEntry);
                File jar = new File(PathUtil.getJarPathForClass(lombok.Builder.class));
                PsiTestUtil.addLibrary(model, "lombok", jar.getParent(), jar.getName());
            }
        };

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_17_WITH_LOMBOK;
    }
}
