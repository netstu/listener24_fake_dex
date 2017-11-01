### 对安装包里的dex文件进行加密,防止别人反编译

这里只是在本地写了两个插件,没有上传到jcenter上面

### 原理

    一个apk,把他的dex拿出来进行加密,这时候直接打包是不能执行的,因为dex是加密的,类加载器无法
    访问,所以这里使用了一个带application的dex,将加密后的dex拼接到后面,最后把加密后的dex的
    dex文件的长度拼接到最后,在application中先读取加密后的dex,先解密,然后通过类加载器加载dex

### 使用

* 第一步

        将fakedex模块和fakedex-plugin模块拷贝到项目中,并在settings.gradle中进行配置
      
* 第二步
        
        在命令行(Terminal)中生成插件
        
        gradlew :fakedex:pFPTML
        gradlew :fakedex-plugin:pFPTML
        
* 第三步
        
     修改工程的build.gradle,添加插件
        
> 示例
        
       buildscript {
           repositories {
               jcenter()
               mavenLocal()
           }
           dependencies {
               classpath 'com.android.tools.build:gradle:2.3.3'
               classpath 'com.pf.fakedex:fakedex-plugin:1.0.0'
           }
       }
       
       allprojects {
           repositories {
               jcenter()
               mavenLocal()
           }
       }
       
* 第四步

     修改app模块下的build.gradle
     
> 示例
     
     apply plugin: 'com.pf.fakedex'
     fake {
         // 加密的密码
         key 'asdfasdfasdfasdf'
     }
     
     dependencies {
         compile fileTree(dir: 'libs', include: ['*.jar'])
         androidTestCompile('com.android.support.test.espresso:espresso-core:2.2.2', {
             exclude group: 'com.android.support', module: 'support-annotations'
         })
         compile 'com.android.support:appcompat-v7:25.3.1'
         compile 'com.android.support.constraint:constraint-layout:1.0.2'
         testCompile 'junit:junit:4.12'
     
         compile group: 'com.pf.fakedex', name: 'fakedex', version: '1.0.0'
     }
     
* 第五步
     
     在命令行中打包  gradlew :app:assembleDebug
     
     打出来的包在/app/build/outputs/fake/outs/app-debug-signed.apk