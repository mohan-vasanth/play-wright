@echo off
setlocal
call "%~dp0mvnw.cmd" -Dtradenix.live.tests=true -Dtest=OutDeclarationTestCase1Test -Dtradenix.out.test.data=data/out-declaration-batch-test-case.json test
