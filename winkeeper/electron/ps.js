const { execFile } = require('child_process');

function runPS(command, timeout = 10 * 60 * 1000) {
  const full = '[Console]::OutputEncoding=[Text.Encoding]::UTF8; ' + command;
  return new Promise((resolve) => {
    execFile('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', full],
      { windowsHide: true, maxBuffer: 64 * 1024 * 1024, timeout, encoding: 'utf8' },
      (err, stdout, stderr) => resolve({ ok: !err, code: err ? err.code : 0, stdout: stdout || '', stderr: stderr || '' }));
  });
}

// UAC onayı ile yükseltilmiş PowerShell'de çalıştırır.
function runElevated(command) {
  const enc = Buffer.from(command, 'utf16le').toString('base64');
  return runPS(
    `$p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru ` +
    `-ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-EncodedCommand','${enc}'; exit $p.ExitCode`
  );
}

module.exports = { runPS, runElevated };
