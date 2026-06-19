const fs = require('fs');

const path = 'frontend/src/pages/PackageDetails.jsx';
const content = fs.readFileSync(path, 'utf8');
const lines = content.split('\n');
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (line.includes('Starting from') || line.includes('Starting From')) {
    console.log(`Line ${i + 1}: ${line.trim()}`);
    console.log(lines.slice(i - 2, i + 15).join('\n'));
  }
}
