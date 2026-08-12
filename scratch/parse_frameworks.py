import json

with open('scratch/excel_data.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

with open('scratch/parsed_summary.txt', 'w', encoding='utf-8') as out:
    for sheet_name, sheet_info in data.items():
        out.write(f"=== {sheet_name} ===\n")
        raw_rows = sheet_info.get('raw_rows', [])
        out.write(f"Rows count: {len(raw_rows)}\n")
        for i, row in enumerate(raw_rows):
            non_empty = [f"Col {idx}: {val}" for idx, val in enumerate(row) if val is not None]
            out.write(f"  Row {i+1}: {', '.join(non_empty)}\n")
        out.write("\n\n")

print("Finished writing to scratch/parsed_summary.txt")
