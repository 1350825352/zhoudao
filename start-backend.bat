@echo off
cd /d h:\traeproject\ceshisikongapiAI
start "sikong-backend" /MIN "" "C:\Program Files\Java\jdk-17\bin\java.exe" -jar target\sikong-cloud-api-1.0.0.jar --spring.profiles.active=base,company-a > target\springboot_start.log 2> target\springboot_err.log
