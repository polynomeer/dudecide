# Chat Import Fixtures

Synthetic KakaoTalk exports, one per format and locale variant, used by the parser tests.

These files were written by hand from the known export shapes, not copied from real exports. Before a parser change ships, add an anonymized real export for the affected format and app version next to the synthetic one:

- replace every name with a placeholder
- replace every number, link, and address
- keep line shapes, separators, and system lines exactly as exported

Each parser change needs a fixture that fails without the change.
