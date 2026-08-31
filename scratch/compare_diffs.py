import subprocess
import sys

# Get the current git diff with a/ and b/ prefixes (standard diff)
try:
    current_diff = subprocess.check_output(['git', 'diff', 'frontend/src/components/AdminPMPlayground.jsx']).decode('utf-8', errors='ignore')
except Exception as e:
    print("Error running git diff:", e)
    sys.exit(1)

with open('scratch/playground_diff.txt', 'r', encoding='utf-8', errors='ignore') as f:
    saved_diff = f.read()

# Normalize
current_lines = [line.strip() for line in current_diff.splitlines() if line.strip()]
saved_lines = [line.strip() for line in saved_diff.splitlines() if line.strip()]

# Strip BOM from saved lines
if saved_lines and saved_lines[0].startswith('\ufeff'):
    saved_lines[0] = saved_lines[0].replace('\ufeff', '')

mismatches = 0
for idx, (cl, sl) in enumerate(zip(current_lines, saved_lines)):
    if cl != sl:
        mismatches += 1
        if mismatches <= 10:
            print(f"Diff at line {idx+1}:")
            # Safe print
            sys.stdout.buffer.write(f"  Current: {cl}\n".encode('utf-8'))
            sys.stdout.buffer.write(f"  Saved:   {sl}\n".encode('utf-8'))

if mismatches == 0 and len(current_lines) == len(saved_lines):
    print("MATCH: Diff matches standard diff exactly.")
else:
    print(f"Total mismatched lines: {mismatches} out of {min(len(current_lines), len(saved_lines))} lines.")
    print("Current line count:", len(current_lines))
    print("Saved line count:", len(saved_lines))
