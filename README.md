# playwright-framework

Playwright-based TradeNix declaration automation.

Run the IPT declaration flow:

```bat
.\mvnw.cmd -Dtest=IptDeclarationTestCase1Test -Dtradenix.ipt.test.data=data/ipt-declaration-batch-test-case.json test
```

Run the OUT declaration flow:

```bat
.\run-out-declaration.cmd
```

Direct OUT command:

```bat
.\mvnw.cmd -Dtest=OutDeclarationTestCase1Test -Dtradenix.out.test.data=data/out-declaration-batch-test-case.json test
```
