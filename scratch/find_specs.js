const fs = require('fs');

const transcriptPath = 'C:\\Users\\Amit Grover\\.gemini\\antigravity-ide\\brain\\1ea709f3-7e09-4248-af62-255c7cc6adb3\\.system_generated\\logs\\transcript.jsonl';

const lines = fs.readFileSync(transcriptPath, 'utf8').split('\n');
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (!line.trim()) continue;
  try {
    const obj = JSON.parse(line);
    if (obj.step_index === 168) {
      console.log(obj.content);
      break;
    }
  } catch (e) {
    // ignore
  }
}
