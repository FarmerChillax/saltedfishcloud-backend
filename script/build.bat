@echo off
if exist ..\src\main\resources\application.yml (
	echo application.xml�Ѵ���
) else (
	echo ����application.xml
	copy ..\src\main\resources\application.simple.yml ..\src\main\resources\application.yml
)
if exist ..\target\*.jar (
	del ..\target\*.jar
)
cd ..
call mvn package
echo ������ɣ���һ�������޸�start.bat�ű�������Ŀ����������������Ŀ��