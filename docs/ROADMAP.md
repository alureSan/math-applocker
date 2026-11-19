# Roadmap

Ideas and planned work for future sessions. Nothing here is implemented yet.

## 1. Configurable operand digit count

Add a setting for how many digits generated operands should have, instead of the
fixed per-age ranges.

Design notes:

- New preference, e.g. `operand_digits` (1–4, default derived from the age preset).
- `QuestionGenerator` should derive its ranges from the digit count
  (`1 → 1..9`, `2 → 10..99`, `3 → 100..999`, `4 → 1000..9999`), while keeping:
  - division always exact (pick divisor + result, then multiply),
  - non-negative subtraction for kid presets,
  - multiplication ranges capped so results stay readable.
- UI: add a "Digits" selector (chips or slider) to **Math rules & timing**, and
  show it in the Home summary.
- Consider per-operation overrides later if the single setting feels limiting.

## 2. Custom question sets (numeric + text answers)

Replace the single custom-question list with named sets that support both types
of answers.

Design notes:

- Room schema v2:
  - `question_sets(id, name)` — e.g. "Biology exam".
  - `custom_questions(id, set_id, text, answer, answer_type)` where
    `answer_type` is `NUMBER` or `TEXT`.
  - Provide a migration from v1 (existing questions go into a "Custom" set with
    `answer_type = NUMBER`).
- Management UI:
  - List of sets with add/rename/delete.
  - Per-set list of questions; add/edit/delete.
  - Answer field switches between numeric and free text based on the type.
- Mixing:
  - Let the parent enable any combination of sets to mix into challenges.
  - Store enabled set IDs in preferences.
- Overlay input:
  - Numeric questions keep the existing keypad.
  - Text questions need an IME inside the overlay window. This is the tricky
    part: the overlay must be focusable and allow the IME (window flag
    `FLAG_ALT_FOCUSABLE_IM`/no `FLAG_NOT_FOCUSABLE`, IME insets handling), or
    text questions should open a transparent/dialog activity for input while
    keeping the locked app blocked.
  - Compare text answers case-insensitively, trimmed, and optionally ignore
    accents; document the matching rule in the UI.
- Localization: all new labels in English and Spanish.

## Smaller follow-ups

- Reboot / `MY_PACKAGE_REPLACED` autostart test on EMUI and Samsung.
- Optional PIN brute-force backoff persistence test.
- Consider per-app unlock windows instead of one global unlock window.
- Add a short in-app "How it works" page explaining the three special permissions.
