const fs = require('fs');

const adminPath = 'frontend/src/pages/Admin.jsx';
const content = fs.readFileSync(adminPath, 'utf8');
const lines = content.split('\n');
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (line.includes('activeDropdown') || line.includes('setView(')) {
    console.log(`Line ${i + 1}: ${line.trim()}`);
  }
}
