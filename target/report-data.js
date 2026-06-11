window.__REPORT_DATA__ = {
  "status" : "FAILURE",
  "suiteName" : "com.automation.playwright_framework.IptDeclarationTestCase1Test",
  "sourceFile" : "TEST-com.automation.playwright_framework.IptDeclarationTestCase1Test.xml",
  "tests" : 1,
  "failures" : 0,
  "errors" : 1,
  "skipped" : 0,
  "durationSeconds" : "369.975",
  "updatedAt" : "2026-06-11T09:48:37.609Z",
  "primaryIssue" : "Error {\n  message='Target page, context or browser has been closed\n  name='TargetClosedError\n  stack='TargetClosedError:Target page, context or browser has been closed\nError\n    at captureRawStack (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\stackTrace.js:32:17)\n    at LongStandingScope._race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:87:58)\n    at LongStandingScope.race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:80:17)\n    at FrameDispatcher._handleCommand (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:90:36)\n    at DispatcherConnection.dispatch (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:309:39)\n}",
  "testCases" : [ {
    "name" : "submitIptDeclarationTestCase1UsingJsonData",
    "className" : "com.automation.playwright_framework.IptDeclarationTestCase1Test",
    "status" : "FAILURE",
    "durationSeconds" : "369.818",
    "message" : "Error {\n  message='Target page, context or browser has been closed\n  name='TargetClosedError\n  stack='TargetClosedError:Target page, context or browser has been closed\nError\n    at captureRawStack (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\stackTrace.js:32:17)\n    at LongStandingScope._race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:87:58)\n    at LongStandingScope.race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:80:17)\n    at FrameDispatcher._handleCommand (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:90:36)\n    at DispatcherConnection.dispatch (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:309:39)\n}",
    "detail" : "com.microsoft.playwright.impl.TargetClosedError: \nError {\n  message='Target page, context or browser has been closed\n  name='TargetClosedError\n  stack='TargetClosedError:Target page, context or browser has been closed\nError\n    at captureRawStack (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\stackTrace.js:32:17)\n    at LongStandingScope._race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:87:58)\n    at LongStandingScope.race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:80:17)\n    at FrameDispatcher._handleCommand (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:90:36)\n    at DispatcherConnection.dispatch (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:309:39)\n}\n\tat com.microsoft.playwright.impl.WaitableResult.get(WaitableResult.java:54)\n\tat com.microsoft.playwright.impl.ChannelOwner.runUntil(ChannelOwner.java:138)\n\tat com.microsoft.playwright.impl.Connection.sendMessage(Connection.java:131)\n\tat com.microsoft.playwright.impl.ChannelOwner.sendMessage(ChannelOwner.java:124)\n\tat com.microsoft.playwright.impl.FrameImpl.waitForTimeout(FrameImpl.java:996)\n\tat com.microsoft.playwright.impl.PageImpl.waitForTimeout(PageImpl.java:1532)\n\tat com.automation.DeclarationsPage.waitForDeclarationCompletion(DeclarationsPage.java:361)\n\tat com.automation.playwright_framework.IptDeclarationTestCase1Test.finalizeSubmittedDeclaration(IptDeclarationTestCase1Test.java:325)\n\tat com.automation.playwright_framework.IptDeclarationTestCase1Test.submitBatchDeclarations(IptDeclarationTestCase1Test.java:116)\n\tat com.automation.playwright_framework.IptDeclarationTestCase1Test.submitIptDeclarationTestCase1UsingJsonData(IptDeclarationTestCase1Test.java:55)\nCaused by: com.microsoft.playwright.impl.TargetClosedError: Error {\n  message='Target page, context or browser has been closed\n  name='TargetClosedError\n  stack='TargetClosedError:Target page, context or browser has been closed\nError\n    at captureRawStack (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\stackTrace.js:32:17)\n    at LongStandingScope._race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:87:58)\n    at LongStandingScope.race (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\utils\\isomorphic\\manualPromise.js:80:17)\n    at FrameDispatcher._handleCommand (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:90:36)\n    at DispatcherConnection.dispatch (C:\\Users\\mabis\\AppData\\Local\\Temp\\playwright-java-14438458654353544009\\package\\lib\\server\\dispatchers\\dispatcher.js:309:39)\n}\n\tat com.microsoft.playwright.impl.Connection.dispatch(Connection.java:259)\n\tat com.microsoft.playwright.impl.Connection.processOneMessage(Connection.java:214)\n\tat com.microsoft.playwright.impl.ChannelOwner.runUntil(ChannelOwner.java:136)\n\t... 8 more"
  } ],
  "batchSummary" : {
    "total" : 1,
    "successes" : 1,
    "issues" : 0,
    "failures" : 0,
    "drafts" : 0
  },
  "batchCases" : [ {
    "index" : 1,
    "status" : "SUCCESS",
    "message" : "Declaration submitted successfully.",
    "declarationTypeCode" : "12",
    "declarationTypeDisplay" : "12 - DNG",
    "jobStatus" : "SUB",
    "jobId" : "5115",
    "declarationNumber" : "TDX2606110072",
    "jobCreatedBy" : "mohan",
    "responseMessage" : "Declaration submitted successfully.",
    "errorMessage" : "N/A",
    "responseSummary" : "Job ID: 5115\r\n\r\nMessage Ref: TDX2606110072\r\n\r\nDeclaration Type: 12\r\n\r\nStatus: SUB\r\n\r\nJob Created By: mohan\r\n\r\nResponse Message: Declaration submitted successfully.\r\n\r\nError Message: N/A",
    "toastText" : null,
    "invalidCount" : 0,
    "diagnosticsText" : "{\r\n  \"toastText\" : \"\",\r\n  \"responseMessage\" : \"Declaration submitted successfully.\",\r\n  \"errorMessage\" : \"N/A\",\r\n  \"notificationMessages\" : [ ],\r\n  \"invalidElements\" : [ ],\r\n  \"jobId\" : \"5115\",\r\n  \"jobStatus\" : \"SUB\",\r\n  \"declarationType\" : \"12\",\r\n  \"declarationNumber\" : \"TDX2606110072\",\r\n  \"jobCreatedBy\" : \"mohan\",\r\n  \"responseSummary\" : \"Job ID: 5115\\r\\n\\r\\nMessage Ref: TDX2606110072\\r\\n\\r\\nDeclaration Type: 12\\r\\n\\r\\nStatus: SUB\\r\\n\\r\\nJob Created By: mohan\\r\\n\\r\\nResponse Message: Declaration submitted successfully.\\r\\n\\r\\nError Message: N/A\"\r\n}",
    "diagnosticsFile" : "ipt-batch-submit-validation-1.json",
    "screenshotFile" : "ipt-batch-submit-1.png",
    "statusScreenshotFile" : "ipt-batch-submit-status-1.png",
    "updatedAt" : "2026-06-11T09:48:37.609Z",
    "updatedAtMillis" : 1781171317609
  } ]
};
