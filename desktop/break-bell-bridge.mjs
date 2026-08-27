import { createServer } from "node:http";
import { randomBytes, timingSafeEqual } from "node:crypto";
import { spawn } from "node:child_process";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { networkInterfaces } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const port = Number.parseInt(process.env.BREAK_BELL_PORT ?? "8765", 10);
const dataDirectory = join(process.env.LOCALAPPDATA ?? process.cwd(), "BreakBell");
const configPath = join(dataDirectory, "bridge.json");
const statusPath = join(dataDirectory, "status.json");
const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const reminderScriptPath = join(scriptDirectory, "Show-BreakBellReminder.ps1");

let reminderProcess = null;

mkdirSync(dataDirectory, { recursive: true });

let config;
try {
  config = JSON.parse(readFileSync(configPath, "utf8"));
} catch {
  config = { token: randomBytes(24).toString("base64url"), port };
  writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`, { mode: 0o600 });
}

function tokenMatches(value) {
  const supplied = Buffer.from((value ?? "").replace(/^Bearer\s+/i, ""));
  const expected = Buffer.from(config.token);
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

function writeStatus(payload) {
  const safeStatus = {
    source: "android",
    receivedAt: Date.now(),
    updatedAt: Number(payload.updatedAt) || Date.now(),
    isActive: Boolean(payload.isActive),
    phase: String(payload.phase ?? "IDLE"),
    workdayStartedAt: Number(payload.workdayStartedAt) || 0,
    phaseStartedAt: Number(payload.phaseStartedAt) || 0,
    phaseEndsAt: Number(payload.phaseEndsAt) || 0,
    currentBlockIndex: Number(payload.currentBlockIndex) || 0,
    completedBreaks: Number(payload.completedBreaks) || 0,
    currentBlock: {
      name: String(payload.currentBlock?.name ?? "Custom"),
      workMinutes: Number(payload.currentBlock?.workMinutes) || 0,
      breakMinutes: Number(payload.currentBlock?.breakMinutes) || 0,
    },
  };
  const temporaryPath = `${statusPath}.tmp`;
  writeFileSync(temporaryPath, `${JSON.stringify(safeStatus, null, 2)}\n`);
  renameSync(temporaryPath, statusPath);
  syncDesktopReminder(safeStatus);
}

function isBreakStatus(status) {
  if (!status?.isActive) return false;
  if (status.phase === "WAITING_FOR_BREAK") return true;
  return status.phase === "BREAK" && status.phaseEndsAt > Date.now();
}

function syncDesktopReminder(status) {
  if (!isBreakStatus(status)) return;
  if (reminderProcess && reminderProcess.exitCode === null) return;

  reminderProcess = spawn(
    "powershell.exe",
    [
      "-NoProfile",
      "-STA",
      "-WindowStyle",
      "Hidden",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      reminderScriptPath,
      "-StatusPath",
      statusPath,
    ],
    { stdio: "ignore", windowsHide: true },
  );
  reminderProcess.once("exit", () => {
    reminderProcess = null;
  });
}

function send(response, statusCode, body) {
  response.writeHead(statusCode, { "content-type": "application/json" });
  response.end(`${JSON.stringify(body)}\n`);
}

const server = createServer((request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    send(response, 200, { ok: true, service: "break-bell-bridge" });
    return;
  }

  if (request.method !== "POST" || request.url !== "/v1/status") {
    send(response, 404, { error: "not_found" });
    return;
  }
  if (!tokenMatches(request.headers.authorization)) {
    send(response, 401, { error: "unauthorized" });
    return;
  }

  let raw = "";
  request.setEncoding("utf8");
  request.on("data", (chunk) => {
    raw += chunk;
    if (raw.length > 16_384) request.destroy();
  });
  request.on("end", () => {
    try {
      writeStatus(JSON.parse(raw));
      send(response, 200, { ok: true });
    } catch {
      send(response, 400, { error: "invalid_status" });
    }
  });
});

server.listen(config.port ?? port, "0.0.0.0", () => {
  const addresses = Object.values(networkInterfaces())
    .flat()
    .filter((entry) => entry?.family === "IPv4" && !entry.internal)
    .map((entry) => `http://${entry.address}:${config.port ?? port}`);

  if (process.env.BREAK_BELL_QUIET !== "1") {
    process.stdout.write("Break Bell agent bridge is running.\n\n");
    process.stdout.write(`Pairing address: ${addresses[0] ?? `http://127.0.0.1:${config.port ?? port}`}\n`);
    process.stdout.write(`Pairing token:   ${config.token}\n`);
    process.stdout.write(`Status file:     ${statusPath}\n`);
    process.stdout.write("\nThe bridge stores timer phase only; it does not receive keyboard or screen content.\n");
  }

  try {
    syncDesktopReminder(JSON.parse(readFileSync(statusPath, "utf8")));
  } catch {
    // The phone has not published timer state yet.
  }
});
