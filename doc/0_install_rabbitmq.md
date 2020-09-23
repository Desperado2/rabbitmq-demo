## 安装RabbitMQ

### 安装Erlang

1.  下载Erlang安装包，选择自己相应的版本下载。

   下载页面: https://www.erlang.org/downloads

   windows64位下载：http://erlang.org/download/otp_win64_23.0.exe

2.  安装erlang

   首先按照正常的安装方式安装下载的安装包。

   安装完成之后，在安装目录中会出现一个Install.exe文件，双击该文件运行，会生成一个bin目录出来。这样才算安装完毕。

3. 配置环境变量

   配置系统环境变量:

   名称 : ERLANG_HOME

   值：erlang的安装目录(D:\soft\erl-23.0)

   添加PATH变量

   值：erlang的安装目录的bin目录(D:\soft\erl-23.0\bin)

4. 验证是否安装成功

   打开cmd，输入erl，如果进入erl交互环境，则表示安装成功，如果未成功，有可能是环境变量没有生效，可尝试重启。

### 安装RabbitMQ   

1. 下载RabbbitMQ安装包

   下载地址: https://www.rabbitmq.com/download.html

   windows64下载地址: https://dl.bintray.com/rabbitmq/all/rabbitmq-server/3.8.8/rabbitmq-server-3.8.8.exe

2. 安装RabbitMQ

   按照正常安装即可，下一步，下一步即可。

3. 启动RabbitMQ

4. 进入RabbitMQ安装目录下面的sbin目录下面,执行如下命令

   ```bash
   sbin> rabbitmq-server.bat start
   ```

   如果显示找不到 ERLANG_HOME not set correctly.，则可以修改RabbitMQ的rabbitmq-server.bat文件,将文件中的ERLANG_HOME替换为erlang的绝对安装地址。

   如果显示出如下信息，表示启动成功。

   ```
    Starting broker... completed with 0 plugins.
   ```

   

5. 启动web页面

   进入RabbitMQ安装目录下面的sbin目录下面,执行如下命令，如果不报错，则表示启动成功。

   ```bash
   > rabbitmq-plugins enable rabbitmq_management
   ```

   

6. 访问web页面

   默认访问地址: http://localhost:15672

   默认用户名:guest

   默认密码:guest

   

   

   

   