package com.pf.fake

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

class FakePlugin implements Plugin<Project> {

    Project project

    @Override
    void apply(Project project) {
        this.project = project
        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('只能与android application同时使用')
        }
        def aar = loadFakeDex()
        // 添加扩展,用来配置aes密钥
        project.extensions.add('fake', FakeExtensions)
        project.afterEvaluate {
            // 对变体进行遍历
            project.android.applicationVariants.all {
                ApplicationVariantImpl variant ->
                    def taskName = "${variant.flavorName.capitalize()}${variant.buildType.name.capitalize()}"

                    // 创建任务,向Manifest中插入一条meta-data,保存配置的aes密钥
                    FakeManifestTask fakeManifestTask = project.tasks.create("fakeManifest${taskName}", FakeManifestTask)
                    // 获得将要打包的Manifest
                    def manifestFile = variant.outputs.first().processManifest.manifestOutputFile
                    fakeManifestTask.manifestFile = manifestFile
                    // 任务执行的时间:已经存在manifest文件,并且在打包之前
                    fakeManifestTask.mustRunAfter variant.outputs.first().processManifest
                    variant.outputs.first().processResources.dependsOn fakeManifestTask

                    def fakePath = "${project.buildDir}/${AndroidProject.FD_OUTPUTS}/fake"
                    // 加密任务
                    FakeDexTask fakeDexTask = project.tasks.create("fakeDex${taskName}", FakeDexTask)
                    fakeDexTask.aarFile = aar
                    fakeDexTask.apkFile = variant.outputs.first().outputFile
                    fakeDexTask.outputs.file(fakePath)
                    fakeDexTask.baseName = "${project.name}-${variant.baseName}"

                    FakePackageTask fakePackageTask = project.tasks.create("fakePackage${taskName}", FakePackageTask)
                    fakePackageTask.outputs.file("${fakePath}/outs")
                    fakePackageTask.inputs.file("${fakePath}")
                    fakePackageTask.baseName = "${project.name}-${variant.baseName}"
                    fakePackageTask.signingConfig = variant.variantData.variantConfiguration.signingConfig

                    fakePackageTask.dependsOn fakeDexTask
                    def assembleTask = project.tasks.getByName("assemble${taskName}")
                    def packageTask = project.tasks.getByName("package${taskName}")
                    assembleTask.dependsOn fakePackageTask
                    fakeDexTask.mustRunAfter packageTask
            }
        }
    }

    def loadFakeDex() {
        // 创建一个依赖分组
        def config = project.configurations.create('fakeClasspath')
        // 创建需要拉取的工件信息
        def notation = [group  : 'com.pf.fakedex',
                        name   : 'fakedex',
                        version: '1.0.0']
        // 添加依赖 仓库解析 工件 并拉取
        Dependency dependency = project.dependencies.add(config.name, notation)
        def file = config.fileCollection(dependency).singleFile
        project.logger.quiet("FAKE:获取 ${notation} 依赖 ${file}")
        file
    }
}