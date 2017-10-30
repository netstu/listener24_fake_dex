package com.pf.fake

import groovy.xml.Namespace
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class FakeManifestTask extends DefaultTask {

    def static final FAKE_KEY = 'fake_key'
    def static final NODE_NAME = 'meta-data'
    File manifestFile

    FakeManifestTask() {
        group = '加固Dex'
        description '将AES的key插入Manifest中'
        // 确保每次都会执行
        outputs.upToDateWhen {
            false
        }
    }

    @TaskAction
    def run() {
        // 获取build.gradle中配置的key
        String key = project.extensions.fake.key
        if (key == null || key.isEmpty()) {
            return
        }

        project.logger.quiet("FAKE:操作Manifest 增加meta-data ${key}")

        // 拿到命名空间
        def ns = new Namespace('http://schemas.android.com/apk/res/android', 'android')
        def xml = new XmlParser().parse(manifestFile)
        // 获取application节点
        Node application = xml.application[0]
        // 拿到application下所有的meta-data节点
        def metaDataTags = application[NODE_NAME]
        // 找到所有
        metaDataTags.findAll {
            Node node ->
                node.attributes()[ns.name] == FAKE_KEY
        }.each {
            Node node ->
                node.parent().remove(node)
        }

        // 插入一个meta-data节点
        // <meta-data android:name="fake_key" android:value="key"/>
        application.appendNode(NODE_NAME, [(ns.name): FAKE_KEY, (ns.value): key])

        def pw = new XmlNodePrinter(new PrintWriter(manifestFile, "UTF-8"))
        pw.print(xml)
    }
}