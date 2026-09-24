// Karma configuration for `ng test`. ChromeHeadlessNoSandbox is a custom launcher
// (see below) needed to run Chrome inside this project's containerized/CI
// environment, where the default sandbox is unavailable.
//
// No system Chrome/Chromium is assumed to be present. Puppeteer resolves its downloaded
// browser path for the current OS/architecture. Karma's config loader is synchronous but
// Puppeteer's executablePath() is async, so resolve it in a short child Node process.
if (!process.env.CHROME_BIN) {
  const {execFileSync} = require('child_process');
  try {
    process.env.CHROME_BIN = execFileSync(
        process.execPath,
        ['-e', "require('puppeteer').executablePath().then(path => process.stdout.write(path))"],
        {cwd: __dirname, encoding: 'utf8'}
    ).trim();
  } catch (error) {
    throw new Error(
        'Unable to locate Puppeteer Chromium. Run npm install or set CHROME_BIN to a Chrome executable.',
        {cause: error}
    );
  }
}

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [{type: 'html'}, {type: 'text-summary'}]
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: false,
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu']
      }
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    singleRun: true,
    restartOnFileChange: false
  });
};
