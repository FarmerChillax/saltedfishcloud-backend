@echo off
@REM 应用程序参数设置

@REM server_port  服务器端口
@REM public_root  公共网盘存储位置
@REM store_root 私人网盘及用户数据存储位置
@REM store_type 初始存储方式，可选unique或raw
@REM reg_code 注册邀请码
@REM sync_delay  同步延迟，单位分支，-1关闭
@REM sync_launch  启动后立即同步
@REM ftp_port   FTP服务端口

@REM ftp_passive_port FTP被动模式数据端口
    @REM FTP服务被动模式端口范围
    @REM 2300 : 被动模式仅使用2300做数据端口
    @REM 2300-2399 : 指定闭区间端口范围
    @REM 2300- : 2300开始到往后的所有端口
    @REM 2300, 2305, 2400- : 指定2300，2305和2400开始到往后的所有端口
@REM ftp_passive_addr FTP服务被动模式地址（在外网环境需要改为公网地址）
set server_port=8087
set public_root=data/public
set store_root=data/xyy
set store_type=unique
set reg_code=10241024
set sync_delay=5
set sync_launch=false
set ftp_port=21
set ftp_passive_addr=localhost
set ftp_passive_port=1140-5140

@REM 数据源设置
set db_host=127.0.0.1
set db_port=3306
set db_name=xyy
set db_username=root
set db_password=mojintao233
set db_params="useSSL=false&serverTimezone=UTC"

set redis_host=127.0.0.1
set redis_port=6379


set jdbc_url=jdbc:mysql://%db_host%:%db_port%/%db_name%?%db_params%

for /F %%i in ('cmd /r dir "../target" /b ^| findstr "saltedfishcloud-.*.jar$"') do ( set jar_name=%%i )
java -jar ../target/%jar_name% ^
--server.port=%server_port% ^
--spring.datasource.druid.url=%jdbc_url% ^
--spring.datasource.druid.username=%db_username% ^
--spring.datasource.druid.password=%db_password% ^
--spring.datasource.redis.host=%redis_host% ^
--spring.datasource.redis.part=%redis_port% ^
--spring.datasource.redis.password=% ^
--public-root=%public_root% ^
--store-root=%store_root% ^
--store-type=%store_type% ^
--RegCode=%reg_code% ^
--sync-delay=%sync_delay% ^
--sync-launch=%sync_launch% ^
--ftp-port=%ftp_port% ^
--ftp-passive-addr=%ftp_passive_addr% ^
--ftp-passive-port=%ftp_passive_port% %*
