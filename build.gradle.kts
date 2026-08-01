import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    // IntelliJ 플러그인 빌드/실행(runIde)/테스트를 담당하는 공식 Gradle 플러그인 (2.x)
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.github.jeongdonghee"
version = "1.0.0"

repositories {
    mavenCentral()
    // IntelliJ 플랫폼 아티팩트(IDE 자체 등)를 받아오는 저장소들
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // gradle.properties 의 platformType/platformVersion 으로 타겟 IDE 결정
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // 번들 플러그인 의존 (쉼표 구분 문자열 -> 리스트)
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins").map { csv ->
                csv.split(',').map(String::trim).filter(String::isNotEmpty)
            }
        )

        // 개발·테스트 전용 마켓플레이스 플러그인 (Lombok 지원).
        // 실제 증강된 빌더 멤버를 상대로 검증해야 하므로 테스트/샌드박스에 필요하다.
        plugins(
            providers.gradleProperty("platformPlugins").map { csv ->
                csv.split(',').map(String::trim).filter(String::isNotEmpty)
            }
        )

        // IDE 를 띄우지 않고 PSI 분석·참조 해석을 검증하는 테스트 프레임워크.
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")

    // 테스트 전용. Lombok 지원 플러그인은 lombok "라이브러리"가 모듈 클래스패스에 있을 때만
    // 빌더를 증강한다 — 애노테이션 소스 스텁으로는 조건을 못 맞춘다. 그래서 실제 jar 를 붙이고,
    // 테스트에서 그 경로를 찾아 픽스처 모듈의 라이브러리로 등록한다(LombokProjectDescriptor).
    // 배포물과는 무관하다.
    testImplementation("org.projectlombok:lombok:1.18.36")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }
}

// 설정 검색 인덱스 생성 태스크는 헤드리스 IDE 를 띄워 runIde 샌드박스와 충돌한다. 선택사항이라 끈다.
tasks.named("buildSearchableOptions") {
    enabled = false
}
