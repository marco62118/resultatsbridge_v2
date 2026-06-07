@echo off
echo Nettoyage du projet...
rd /s /q .gradle
rd /s /q .idea
rd /s /q build
rd /s /q app\build
del local.properties
echo Terminé ! Vous pouvez rouvrir le projet.
pause