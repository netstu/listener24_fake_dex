package com.pf.fake

import com.android.build.gradle.internal.dsl.SigningConfig
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class FakePackageTask extends DefaultTask {

    SigningConfig signingConfig
    String baseName

    FakePackageTask() {
        group = '加固Dex'
        description '加密dex'
        //每次都会执行
        outputs.upToDateWhen {
            false
        }
    }

    @TaskAction
    def run() {
        def dir = new File(inputs.files.singleFile, baseName)
        def outs = outputs.files.singleFile
        outs.mkdirs()
        def unsignedApk = new File(outs, "${baseName}-unsigned.apk")
        Zip.zip(dir, unsignedApk)
        if (!signingConfig) {
            return
        }
        def signedApk = new File(outs, "${baseName}-signed.apk")

        def cmd = [
                "jarsigner", "-verbose", "-sigalg", "MD5withRSA",
                "-digestalg", "SHA1",
                "-keystore", signingConfig.storeFile,
                "-storepass", signingConfig.storePassword,
                "-keypass", signingConfig.keyPassword,
                "-signedjar", signedApk.absolutePath,
                unsignedApk.absolutePath,
                signingConfig.keyAlias
        ]
        def stdout = new StringBuffer()
        def stderr = new StringBuffer()
        project.logger.quiet("FAKE: 签名 ${signedApk}")

        def result = cmd.execute()
        result.waitForProcessOutput(stdout, stderr)
        if (result.exitValue() != 0) {
            def output = "Fake: stdout: ${stdout}. stderr: ${stderr}"
            throw new GradleException(output)
        }
    }
}