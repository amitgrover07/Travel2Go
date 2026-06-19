const { execSync } = require('child_process');
const fs = require('fs');

try {
  const stdout = execSync('git show ca2aa9ec1b431e0b799d0648e9d00e03e22c21d6 -- frontend/src/pages/PackageDetails.jsx', { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
  fs.writeFileSync('scratch/package_details_diff.diff', stdout, 'utf8');
  console.log('Saved diff to scratch/package_details_diff.diff');
} catch (e) {
  console.error(e);
}
