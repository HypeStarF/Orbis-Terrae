# Dataset provenance process

1. Identify the exact dataset product and version; a family name is insufficient.
2. Save the official source URL and retrieval date.
3. Calculate a SHA-256 checksum for every downloaded source file.
4. Capture the applicable license or terms and their access date.
5. Record whether raw redistribution, modified redistribution, and derived-output redistribution are
   permitted.
6. Draft required attribution before data enters an official atlas.
7. Record preprocessing commands and software versions.
8. Have a second review before changing a matrix status to `APPROVED_BUNDLE`.
9. Keep raw data outside Git unless its terms and size make inclusion explicitly acceptable.
10. Refuse atlas compilation for official builds when required provenance fields are missing.
