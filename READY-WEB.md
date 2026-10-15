# 免安装依赖的前端启动方式

适合 npm 下载缓慢、只想先运行界面的情况。保留完整源码开发方式；本目录的前端产物是源码构建出的固定版本，仍然连接真实的 Spring Boot 和 MySQL。

1. 保持 IDEA 中 `CampusApplication` 运行成功，后端端口为 8080。
2. 将整个 ZIP **解压后**，双击根目录 `START-WEB.bat`。
3. Windows 会自动打开浏览器，显示校园集市；黑色窗口保持打开。

此方式只需要已经安装的 Node.js，**不用运行 npm ci、npm install 或 npm run dev**。若没有自动打开浏览器，手动输入黑色窗口打印的本机地址（默认 127.0.0.1:5173）。不要双击 ready-web/index.html。

买家 buyer、卖家 seller、管理员 admin，演示密码均为 Campus123!。

单独下载的 `campus-frontend-ready.zip` 解压到新文件夹即可，不用覆盖你原来已经配置好的 backend。

源码修改后，免安装版本不会自动变化：正常开发仍在 frontend 运行 npm ci 和 npm run dev。需要更新免安装版本时，在项目根目录执行 `python scripts/build-ready-web.py`（该步骤需要已安装的前端依赖）。

脚本只绑定本机地址，用 Node 内置 HTTP 服务托管 ready-web，并转发 /api、/uploads、/samples 到后端。不包含独立数据库或模拟接口。可通过 WEB_PORT 修改本机前端端口、API_ORIGIN 修改后端地址。关闭旧前端窗口后再双击，避免端口冲突。
