import { defineConfig, devices } from '@playwright/test'

const javaHome = '/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: [
    {
      command: `env JAVA_HOME=${javaHome} PATH=${javaHome}/bin:$PATH MATCH_DATA_JOB_ENABLED=false BOOKING_EXPIRY_JOB_ENABLED=false MAIL_ENABLED=false DEBUG=false LOGGING_LEVEL_ROOT=INFO LOGGING_LEVEL_COM_COURTLY=INFO LOGGING_LEVEL_ORG_HIBERNATE_SQL=INFO LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=INFO ./mvnw spring-boot:run`,
      cwd: '../backend',
      url: 'http://127.0.0.1:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5173',
      env: { VITE_API_BASE_URL: 'http://127.0.0.1:8080' },
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: true,
      timeout: 60_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
})
