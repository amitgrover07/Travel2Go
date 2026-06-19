const fs = require('fs');

const content = fs.readFileSync('scratch/step_168.md', 'utf8');
const lines = content.split('\n');
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (line.toLowerCase().includes('matrix') || line.toLowerCase().includes('sedan') || line.toLowerCase().includes('innova') || line.toLowerCase().includes('tempo')) {
    console.log(`Line ${i}: ${line}`);
    // print some surrounding lines
    const start = Math.max(0, i - 4);
    const end = Math.min(lines.length - 1, i + 10);
    console.log('--- Context ---');
    console.log(lines.slice(start, end).join('\n'));
    console.log('===============\n');
  }
}
