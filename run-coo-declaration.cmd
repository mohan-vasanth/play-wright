@echo off
setlocal
call "%~dp0mvnw.cmd" -Dtradenix.live.tests=true -Dtest=CooDeclarationTestCase1Test -Dtradenix.coo.test.data=data/coo-declaration-batch-test-case.json -Dplaywright.headless=false -Dplaywright.slowmo.ms=250 test
