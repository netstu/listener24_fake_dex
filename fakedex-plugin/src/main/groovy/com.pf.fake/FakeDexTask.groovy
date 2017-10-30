package com.pf.fake

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import java.security.MessageDigest
import java.util.zip.Adler32

class FakeDexTask extends DefaultTask {

    File apkFile
    File aarFile
    String baseName

    FakeDexTask() {
        group = '加固Dex'
        description '加密Dex'

        // 确保每次都会执行
        outputs.upToDateWhen {
            false
        }

        // 初始化密码
        String key = project.fake.key
        if (key != null && !key.isEmpty()) {
            AES.init(key)
        } else {
            AES.init(AES.DEFAULT_PWD)
        }
    }

    @TaskAction
    def run() {
        def outDir = outputs.files.singleFile
        def fakeDex = new File(outDir, 'fakeDex')

        // 解压aar到 build/outputs/fake/fakeDex
        Zip.unZip(aarFile, fakeDex)

        File classesJar
        fakeDex.listFiles().each {
            if (it.name == 'classes.jar') {
                classesJar = it
            } else {
                it.delete()
            }
        }

        def aarDex = new File("${classesJar.parent}/classes.dex")
        // dx --dex --output=path/dex classesJar
        def result1 = "dx.bat --dex --output=${aarDex} ${classesJar}"
        project.logger.quiet("FAKE:操作Dex,准备执行,result1:${result1}")
        def result = result1.execute()
        project.logger.quiet("FAKE:操作Dex完毕,result:${result}")
        def out = new StringBuffer()
        def err = new StringBuffer()
        result.waitForProcessOutput(out, err)
        if (result.exitValue() != 0) {
            project.logger.quiet("FAKE:执行dx失败")
            throw new GradleException("执行dx失败")
        }

        // 加密
        // 解压apk
        def unZipFile = new File(outDir, baseName)
        Zip.unZip(apkFile, unZipFile)
        // 查找apk中所有的dex文件
        def dexFiles = unZipFile.listFiles().findAll {
            it.name.endsWith('.dex')
        }
        dexFiles.each {
            // 加密dex
            def rawBytes = it.bytes
            project.logger.quiet("FAKE: ${it.name} 加密前${rawBytes.length}")
            def fakeDexData = AES.encrypt(rawBytes)
            project.logger.quiet("FAKE: ${it.name} 加密后${fakeDexData.length}")
            it.withOutputStream {
                it.write(fakeDexData)
            }
        }

        // 获取classes.dex,将它拼接到aar的dex后面
        // classesX.dex 不需要管
        def mainDex = dexFiles.find { it.name == 'classes.dex' }
        def mainBytes = mainDex.bytes
        def aarBytes = aarDex.bytes
        project.logger.quiet("FAKE: aar dex ${aarBytes.length}")
        def newDex = new byte[mainBytes.length + aarBytes.length + 4]
        project.logger.quiet("FAKE: 合并后 dex ${newDex.length}")

        // aar:0,1,2,3
        // main: 4,5,6,7
        // 合并后:0,1,2,3,4,5,6,7,4
        // 拷贝数据
        // 先拷贝aar的dex数据
        System.arraycopy(aarBytes, 0, newDex, 0, aarBytes.length)
        // 拷贝main dex长度
        System.arraycopy(mainBytes, 0, newDex, aarBytes.length, mainBytes.length)
        // 拷贝main dex长度
        System.arraycopy(Utils.int2Bytes(mainBytes.length), 0, newDex, aarBytes.length + mainBytes.length, 4)

        // 更换dex头信息中的文件长度信息
        def file_size = Utils.int2Bytes(newDex.length)
        System.arraycopy(file_size, 0, newDex, 32, 4)

        def md = MessageDigest.getInstance('SHA-1')
        // 从32个字节开始,计算sha1值
        md.update(newDex, 32, newDex.length - 32)
        def sha1 = md.digest()
        // 从第12位开始拷贝20字节内容
        // 替换签名
        System.arraycopy(sha1, 0, newDex, 12, 20)

        // 计算checksum
        Adler32 adler = new Adler32()
        adler.update(newDex, 12, newDex.length - 12)
        int value = adler.getValue()
        def checkSum = Utils.int2Bytes(value)
        System.arraycopy(checkSum, 0, newDex, 8, 4)

        mainDex.delete()
        mainDex.withOutputStream {
            it.write(newDex)
        }
    }
}